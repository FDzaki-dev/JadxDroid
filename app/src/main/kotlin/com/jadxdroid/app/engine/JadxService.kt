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
 * The process lives for a single run: when the client unbinds, it ends, which returns the whole heap and stops
 * any worker thread still running.
 */
class JadxService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val messenger = Messenger(
        Handler(Looper.getMainLooper()) { message ->
            if (message.what == MSG_START) start(message)
            true
        },
    )

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
        val options = JadxRunner.Options(data.getBoolean(KEY_SKIP_RESOURCES))
        job = scope.launch {
            lowerThreadPriorities()
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
        Message.obtain(null, MSG_FAILED).apply { data = bundleOf(KEY_TRACE to CrashLogger.rawTrace(failure)) }
    }

    /**
     * Every thread of this process, including the runtime's GC threads, runs below the UI process: a collection
     * that uses all cores at normal priority is what makes the foreground app stutter.
     */
    private fun lowerThreadPriorities() {
        val priority = Process.THREAD_PRIORITY_BACKGROUND + Process.THREAD_PRIORITY_MORE_FAVORABLE
        File(THREADS_DIR).list()?.forEach { name ->
            name.toIntOrNull()?.let { tid -> runCatching { Process.setThreadPriority(tid, priority) } }
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
        private const val THREADS_DIR = "/proc/self/task"
    }
}
