package com.jadxdroid.app.update

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import com.jadxdroid.app.log.CrashLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okio.buffer
import okio.sink
import okio.source
import java.io.File

sealed interface InstallStatus {
    data object Idle : InstallStatus
    data object Committed : InstallStatus
    data class Failed(val message: String) : InstallStatus
}

/** Installs the verified APK through a PackageInstaller session, writing it with Okio. */
object UpdateInstaller {

    private const val SESSION_ENTRY = "update.apk"
    private val mutableStatus = MutableStateFlow<InstallStatus>(InstallStatus.Idle)

    val status: StateFlow<InstallStatus> = mutableStatus.asStateFlow()

    fun report(next: InstallStatus) {
        mutableStatus.value = next
    }

    /** True when [apk] is a readable package for this app with the expected versionCode. */
    @Suppress("DEPRECATION")
    fun archiveMatches(context: Context, apk: File, expectedCode: Long): Boolean {
        val info = context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0) ?: return false
        return info.packageName == context.packageName && info.longVersionCode == expectedCode
    }

    /** Blocking; call from an IO dispatcher. The system then asks the user to confirm the install. */
    fun commit(context: Context, apk: File) {
        report(InstallStatus.Committed)
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                writeApk(session, apk)
                session.commit(resultSender(context, sessionId))
            }
        } catch (throwable: Throwable) {
            runCatching { installer.abandonSession(sessionId) }
            throw throwable
        }
    }

    private fun writeApk(session: PackageInstaller.Session, apk: File) {
        val stream = session.openWrite(SESSION_ENTRY, 0, apk.length())
        stream.sink().buffer().use { sink ->
            apk.source().buffer().use { source -> sink.writeAll(source) }
            sink.flush()
            session.fsync(stream)
        }
    }

    private fun resultSender(context: Context, sessionId: Int): IntentSender {
        // The system fills in status extras, so the PendingIntent must be mutable; the target is explicit.
        val intent = Intent(context, InstallResultReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        return PendingIntent.getBroadcast(context, sessionId, intent, flags).intentSender
    }
}

/** Receives PackageInstaller results: opens the system confirmation, or reports the raw failure. */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> launchConfirmation(context, intent)
            PackageInstaller.STATUS_SUCCESS -> UpdateInstaller.report(InstallStatus.Idle)
            else -> reportFailure(context, status, message)
        }
    }

    // EXTRA_INTENT is set by the system installer for a PendingIntent created by this app, and this
    // receiver is not exported, so the confirmation intent is not attacker-controlled.
    @SuppressLint("UnsafeIntentLaunch")
    private fun launchConfirmation(context: Context, result: Intent) {
        val confirm = IntentCompat.getParcelableExtra(result, Intent.EXTRA_INTENT, Intent::class.java) ?: return
        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(confirm)
    }

    private fun reportFailure(context: Context, status: Int, message: String) {
        val raw = "status=$status $message"
        UpdateInstaller.report(InstallStatus.Failed(raw))
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                CrashLogger.logFailure(context, "update-install-failure", IllegalStateException(raw))
            } finally {
                pending.finish()
            }
        }
    }
}
