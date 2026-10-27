package com.jadxdroid.app.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.jadxdroid.app.Phase
import com.jadxdroid.app.R
import com.jadxdroid.app.data.Storage
import com.jadxdroid.app.engine.JadxRunner
import com.jadxdroid.app.log.CrashLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class DecompileWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val uri = inputData.getString(KEY_URI)?.toUri()
            ?: return Result.failure(workDataOf(KEY_FAILURE to "Input URI tidak ada pada data worker"))
        val skipResources = inputData.getBoolean(KEY_SKIP_RESOURCES, false)
        val displayName = withContext(Dispatchers.IO) { Storage.displayName(applicationContext, uri) }

        enterForeground()
        val wakeLock = acquireWakeLock()
        return try {
            report(Phase.COPYING, displayName)
            val input = withContext(Dispatchers.IO) { Storage.copyToCache(applicationContext, uri, displayName) }
            val outDir = withContext(Dispatchers.IO) { Storage.prepareOutputDir(applicationContext) }

            report(Phase.DECOMPILING, displayName)
            val summary = JadxRunner().decompile(input, outDir, JadxRunner.Options(skipResources)) { done, total ->
                report(Phase.DECOMPILING, displayName, done, total)
            }

            report(Phase.EXPORTING, displayName)
            val path = Storage.exportZip(applicationContext, outDir, input.nameWithoutExtension) { done, total ->
                report(Phase.EXPORTING, displayName, done, total)
            }
            Result.success(
                workDataOf(
                    KEY_NAME to displayName,
                    KEY_RESULT_PATH to path,
                    KEY_ERRORS to summary.errors,
                    KEY_WARNINGS to summary.warnings,
                ),
            )
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (throwable: Throwable) {
            CrashLogger.logFailure(applicationContext, "decompile-failure", throwable)
            Result.failure(
                workDataOf(
                    KEY_NAME to displayName,
                    KEY_FAILURE to CrashLogger.rawTrace(throwable).take(MAX_FAILURE_CHARS),
                ),
            )
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { Storage.cleanup(applicationContext) }
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    // Every setProgress is a database write plus a WorkInfo re-query and a recomposition. Under a CPU-bound
    // decompile that churn is visible as UI stutter, so a report goes out only when the phase changes, or the
    // whole percent changed and at least MIN_REPORT_INTERVAL_MS has passed since the previous write.
    private var lastPhase: Phase? = null
    private var lastPercent = NO_PERCENT
    private var lastReportAt = 0L

    private suspend fun report(phase: Phase, name: String, done: Int = 0, total: Int = 0) {
        val percent = if (total > 0) (done.toLong() * PERCENT_SCALE / total).toInt() else NO_PERCENT
        val now = SystemClock.elapsedRealtime()
        val samePhase = phase == lastPhase
        if (samePhase && (percent == lastPercent || now - lastReportAt < MIN_REPORT_INTERVAL_MS)) return
        lastPhase = phase
        lastPercent = percent
        lastReportAt = now
        setProgress(workDataOf(KEY_PHASE to phase.name, KEY_NAME to name, KEY_DONE to done, KEY_TOTAL to total))
    }

    private suspend fun enterForeground() {
        try {
            setForeground(foregroundInfo())
        } catch (denied: IllegalStateException) {
            // The system may refuse a foreground start; continue as ordinary background work.
            CrashLogger.logFailure(applicationContext, "foreground-denied", denied)
        }
    }

    private fun foregroundInfo(): ForegroundInfo {
        val cancelIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(applicationContext.getString(R.string.notification_title))
            .setContentText(applicationContext.getString(R.string.notification_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_delete, applicationContext.getString(R.string.action_cancel), cancelIntent)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun acquireWakeLock(): PowerManager.WakeLock {
        val powerManager = applicationContext.getSystemService(PowerManager::class.java)
        return powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply { acquire(WAKE_LOCK_TIMEOUT_MS) }
    }

    companion object {
        const val UNIQUE_NAME = "jadx-decompile"
        const val CHANNEL_ID = "decompile"
        const val KEY_URI = "uri"
        const val KEY_NAME = "name"
        const val KEY_SKIP_RESOURCES = "skip_resources"
        const val KEY_PHASE = "phase"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_RESULT_PATH = "result_path"
        const val KEY_ERRORS = "errors"
        const val KEY_WARNINGS = "warnings"
        const val KEY_FAILURE = "failure"

        private const val NOTIFICATION_ID = 1
        private const val WAKE_LOCK_TAG = "JadxDroid:decompile"
        private const val WAKE_LOCK_TIMEOUT_MS = 2L * 60 * 60 * 1000
        private const val MAX_FAILURE_CHARS = 3000
        private const val PERCENT_SCALE = 100
        private const val NO_PERCENT = -1
        private const val MIN_REPORT_INTERVAL_MS = 1_000L
    }
}
