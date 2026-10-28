package com.jadxdroid.app.engine

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import androidx.core.os.bundleOf
import com.jadxdroid.app.log.CrashLogger
import jadx.android.MemoryGuard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/**
 * Runs one decompile in its own process (":jadx", see the manifest). Heap, garbage collection and CPU load of the
 * decompiler then belong to that process, so they cannot stall the UI process the way they do in a shared one.
 * That process has no UI to protect, so its worker threads run at foreground priority: a lower one only loses
 * CPU time to every other app, which is what makes a decompile crawl once the user leaves for another app.
 * The process lives for a single run: when the client unbinds, it ends, which returns the whole heap and stops
 * any worker thread still running.
 */
open class JadxService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val messenger = Messenger(
        Handler(Looper.getMainLooper()) { message ->
            if (message.what == MSG_START) start(message)
            true
        },
    )

    override fun onCreate() {
        super.onCreate()
        MemoryGuard.setWorkerPriority(Process.THREAD_PRIORITY_FOREGROUND)
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
        Process.killProcess(Process.myPid())
    }

    private fun start(message: Message) {
        val client = message.replyTo ?: return
        if (job != null) return
        val data = message.data
        val input = File(data.getString(KEY_INPUT).orEmpty())
        val outDir = File(data.getString(KEY_OUT_DIR).orEmpty())
        val strategy = JadxRunner.Strategy.entries.getOrElse(data.getInt(KEY_STRATEGY)) { JadxRunner.Strategy.CHAIN }
        val earlier = data.getStringArrayList(KEY_EARLIER).orEmpty().map { RemoteFailure(it) }
        val options = JadxRunner.Options(data.getBoolean(KEY_SKIP_RESOURCES), strategy, earlier)
        job = scope.launch {
            val reply = decompileReply(client, input, outDir, options)
            runCatching { client.send(reply) }
        }
    }

    private suspend fun decompileReply(
        client: Messenger,
        input: File,
        outDir: File,
        options: JadxRunner.Options,
    ): Message = try {
        val summary = JadxRunner().decompile(input, outDir, options) { done, total ->
            runCatching { client.send(Message.obtain(null, MSG_PROGRESS, done, total)) }
        }
        Message.obtain(null, MSG_DONE).apply {
            data = bundleOf(KEY_ERRORS to summary.errors, KEY_WARNINGS to summary.warnings)
        }
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (failure: Throwable) {
        Message.obtain(null, MSG_FAILED).apply {
            data = bundleOf(KEY_TRACE to CrashLogger.rawTrace(failure), KEY_OOM to failure.isOutOfMemory())
        }
    }

    companion object {
        const val MSG_START = 1
        const val MSG_PROGRESS = 2
        const val MSG_DONE = 3
        const val MSG_FAILED = 4
        const val KEY_INPUT = "input"
        const val KEY_OUT_DIR = "out_dir"
        const val KEY_SKIP_RESOURCES = "skip_resources"
        const val KEY_ERRORS = "errors"
        const val KEY_WARNINGS = "warnings"
        const val KEY_TRACE = "trace"
        const val KEY_OOM = "oom"
        const val KEY_STRATEGY = "strategy"
        const val KEY_EARLIER = "earlier"
    }
}

/** Same service in a second process (":jadx2"): a retry after a failed run never reuses the first process. */
class JadxFallbackService : JadxService()
