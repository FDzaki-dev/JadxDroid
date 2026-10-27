package com.jadxdroid.app.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.jadxdroid.app.log.CrashLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class UpdatePhase {
    IDLE,
    CHECKING,
    UP_TO_DATE,
    AVAILABLE,
    DOWNLOADING,
    READY,
    INSTALLING,
    CHECK_FAILED,
    DOWNLOAD_FAILED,
    INSTALL_FAILED,
}

data class InstalledVersion(val name: String, val code: Long)

data class ReadyApk(val path: String, val sha256: String)

/** Everything the Update tab draws. [installed] is known at once, so the snapshot never starts empty. */
data class UpdateUiState(
    val installed: InstalledVersion,
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val release: ReleaseInfo? = null,
    val done: Long = 0L,
    val total: Long = 0L,
    val ready: ReadyApk? = null,
    val failure: String? = null,
)

private sealed interface ReleaseCheck {
    data object Idle : ReleaseCheck
    data object Running : ReleaseCheck
    data class Done(val release: ReleaseInfo) : ReleaseCheck
    data class Failed(val message: String) : ReleaseCheck
}

private const val STOP_TIMEOUT_MS = 5_000L
private const val MAX_FAILURE_CHARS = 3000

class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val workManager = WorkManager.getInstance(application)
    private val installed = readInstalled(application)
    private val releaseCheck = MutableStateFlow<ReleaseCheck>(ReleaseCheck.Idle)

    // The WorkManager record is the single source of truth for the download, as in MainViewModel.
    val uiState: StateFlow<UpdateUiState> = combine(
        releaseCheck,
        workManager.getWorkInfosForUniqueWorkFlow(UpdateWorker.UNIQUE_NAME),
        UpdateInstaller.status,
    ) { checkState, works, installStatus ->
        resolveState(installed, checkState, works.firstOrNull(), installStatus)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UpdateUiState(installed))

    /** Called when the tab opens: checks once, and again only after a failed check. */
    fun checkIfNeeded() {
        val current = releaseCheck.value
        if (current is ReleaseCheck.Idle || current is ReleaseCheck.Failed) checkForUpdate()
    }

    fun checkForUpdate() {
        if (releaseCheck.value is ReleaseCheck.Running) return
        releaseCheck.value = ReleaseCheck.Running
        viewModelScope.launch(Dispatchers.IO) {
            releaseCheck.value = try {
                ReleaseCheck.Done(UpdateRepository.fetchLatest())
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (throwable: Throwable) {
                CrashLogger.logFailure(getApplication<Application>(), "update-check-failure", throwable)
                ReleaseCheck.Failed(CrashLogger.rawTrace(throwable).take(MAX_FAILURE_CHARS))
            }
        }
    }

    fun download() {
        val release = uiState.value.release ?: return
        UpdateInstaller.report(InstallStatus.Idle)
        val request = OneTimeWorkRequestBuilder<UpdateWorker>()
            .setInputData(UpdateWorker.inputOf(release))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager.enqueueUniqueWork(UpdateWorker.UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelDownload() {
        workManager.cancelUniqueWork(UpdateWorker.UNIQUE_NAME)
    }

    fun install() {
        val ready = uiState.value.ready ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                UpdateInstaller.commit(getApplication<Application>(), File(ready.path))
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (throwable: Throwable) {
                CrashLogger.logFailure(getApplication<Application>(), "update-install-failure", throwable)
                UpdateInstaller.report(InstallStatus.Failed(CrashLogger.rawTrace(throwable).take(MAX_FAILURE_CHARS)))
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun readInstalled(application: Application): InstalledVersion {
    val info = application.packageManager.getPackageInfo(application.packageName, 0)
    return InstalledVersion(name = info.versionName.orEmpty(), code = info.longVersionCode)
}

private fun resolveState(
    installed: InstalledVersion,
    check: ReleaseCheck,
    work: WorkInfo?,
    install: InstallStatus,
): UpdateUiState {
    val base = UpdateUiState(installed = installed, release = (check as? ReleaseCheck.Done)?.release)
    return stateFromWork(base, work, install) ?: stateFromCheck(base, check)
}

private fun stateFromWork(base: UpdateUiState, work: WorkInfo?, install: InstallStatus): UpdateUiState? {
    val info = work ?: return null
    return when (info.state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED, WorkInfo.State.RUNNING -> base.copy(
            phase = UpdatePhase.DOWNLOADING,
            done = info.progress.getLong(UpdateWorker.KEY_DONE, 0L),
            total = info.progress.getLong(UpdateWorker.KEY_TOTAL, 0L),
        )
        WorkInfo.State.SUCCEEDED -> stateFromSuccess(base, info.outputData, install)
        WorkInfo.State.FAILED -> stateFromFailure(base, info.outputData)
        WorkInfo.State.CANCELLED -> null
    }
}

/** A finished download only counts while it is newer than the installed build and matches the checked release. */
private fun stateFromSuccess(base: UpdateUiState, output: Data, install: InstallStatus): UpdateUiState? {
    val ready = readyApk(output)
    val code = output.getLong(UpdateWorker.KEY_VERSION_CODE, 0L)
    val expected = base.release?.versionCode ?: code
    if (ready == null || code <= base.installed.code || code != expected) return null
    val phase = when (install) {
        InstallStatus.Idle -> UpdatePhase.READY
        InstallStatus.Committed -> UpdatePhase.INSTALLING
        is InstallStatus.Failed -> UpdatePhase.INSTALL_FAILED
    }
    return base.copy(phase = phase, ready = ready, failure = (install as? InstallStatus.Failed)?.message)
}

private fun stateFromFailure(base: UpdateUiState, output: Data): UpdateUiState? {
    val tag = output.getString(UpdateWorker.KEY_TAG)
    val sameRelease = base.release == null || base.release.tag == tag
    return if (sameRelease) {
        base.copy(phase = UpdatePhase.DOWNLOAD_FAILED, failure = output.getString(UpdateWorker.KEY_FAILURE))
    } else {
        null
    }
}

private fun readyApk(output: Data): ReadyApk? {
    val path = output.getString(UpdateWorker.KEY_PATH)
    val sha256 = output.getString(UpdateWorker.KEY_SHA256)
    return if (path != null && sha256 != null && File(path).exists()) ReadyApk(path, sha256) else null
}

private fun stateFromCheck(base: UpdateUiState, check: ReleaseCheck): UpdateUiState = when (check) {
    ReleaseCheck.Idle -> base
    ReleaseCheck.Running -> base.copy(phase = UpdatePhase.CHECKING)
    is ReleaseCheck.Done -> base.copy(
        phase = if (check.release.versionCode > base.installed.code) UpdatePhase.AVAILABLE else UpdatePhase.UP_TO_DATE,
    )
    is ReleaseCheck.Failed -> base.copy(phase = UpdatePhase.CHECK_FAILED, failure = check.message)
}
