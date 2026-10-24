package com.jadxdroid.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.jadxdroid.app.work.DecompileWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val workManager = WorkManager.getInstance(application)

    // The work database is the single source of truth, so state survives rotation and process death.
    val uiState: StateFlow<UiState> = workManager
        .getWorkInfosForUniqueWorkFlow(DecompileWorker.UNIQUE_NAME)
        .map { infos -> infos.firstOrNull()?.toUiState() ?: UiState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState())

    fun start(uri: Uri, skipResources: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                getApplication<Application>().contentResolver
                    .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val request = OneTimeWorkRequestBuilder<DecompileWorker>()
                .setInputData(
                    workDataOf(
                        DecompileWorker.KEY_URI to uri.toString(),
                        DecompileWorker.KEY_SKIP_RESOURCES to skipResources,
                    ),
                )
                .build()
            workManager.enqueueUniqueWork(DecompileWorker.UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }

    fun cancel() {
        workManager.cancelUniqueWork(DecompileWorker.UNIQUE_NAME)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

private fun WorkInfo.toUiState(): UiState = when (state) {
    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> UiState(phase = Phase.QUEUED)
    WorkInfo.State.RUNNING -> UiState(
        phase = Phase.entries.firstOrNull { it.name == progress.getString(DecompileWorker.KEY_PHASE) } ?: Phase.QUEUED,
        fileName = progress.getString(DecompileWorker.KEY_NAME),
        done = progress.getInt(DecompileWorker.KEY_DONE, 0),
        total = progress.getInt(DecompileWorker.KEY_TOTAL, 0),
    )
    WorkInfo.State.SUCCEEDED -> UiState(
        phase = Phase.DONE,
        fileName = outputData.getString(DecompileWorker.KEY_NAME),
        resultPath = outputData.getString(DecompileWorker.KEY_RESULT_PATH),
        errors = outputData.getInt(DecompileWorker.KEY_ERRORS, 0),
        warnings = outputData.getInt(DecompileWorker.KEY_WARNINGS, 0),
    )
    WorkInfo.State.FAILED -> UiState(
        phase = Phase.FAILED,
        fileName = outputData.getString(DecompileWorker.KEY_NAME),
        failure = outputData.getString(DecompileWorker.KEY_FAILURE),
    )
    WorkInfo.State.CANCELLED -> UiState(phase = Phase.CANCELLED)
}
