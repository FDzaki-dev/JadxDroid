package com.jadxdroid.app.engine

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import androidx.core.os.bundleOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * A failure that happened inside the decompiler process; [trace] is its raw stack trace, unchanged. [died] means
 * the process itself was lost (killed by the system), [outOfMemory] that the decompile ran out of heap.
 */
class RemoteFailure(
    val trace: String,
    val died: Boolean = false,
    val outOfMemory: Boolean = false,
) : Exception(trace)

/**
 * Client side of [JadxService]. [decompile] returns null when the decompiler process cannot be started, so the
 * caller can fall back to decompiling in this process (slower for the UI, but never a regression).
 *
 * Every step runs in a process of its own. When one runs out of memory (or is killed), the next step starts in a
 * different, fresh process with a lighter strategy; nothing a failed step left on its heap can reach the next one.
 */
class RemoteDecompiler(context: Context) {

    private val appContext = context.applicationContext

    private sealed interface Event {
        data class Progress(val done: Int, val total: Int) : Event
        data class Done(val summary: JadxRunner.Summary) : Event
        data class Failed(val trace: String, val outOfMemory: Boolean) : Event
        data object Died : Event
    }

    suspend fun decompile(
        input: File,
        outDir: File,
        skipResources: Boolean,
        onProgress: suspend (done: Int, total: Int) -> Unit,
    ): JadxRunner.Summary? = attempt(0, input, outDir, skipResources, emptyList(), onProgress)

    private suspend fun attempt(
        index: Int,
        input: File,
        outDir: File,
        skipResources: Boolean,
        earlier: List<String>,
        onProgress: suspend (done: Int, total: Int) -> Unit,
    ): JadxRunner.Summary? = try {
        run(SERVICES[index], STRATEGIES[index], input, outDir, skipResources, earlier, onProgress)
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (failure: RemoteFailure) {
        if (!(failure.outOfMemory || failure.died) || index == STRATEGIES.lastIndex) throw failure
        clearOutput(outDir)
        val trace = failure.trace.take(MAX_TRACE_CHARS)
        attempt(index + 1, input, outDir, skipResources, earlier + trace, onProgress)
    }

    private suspend fun run(
        service: Class<out Service>,
        strategy: JadxRunner.Strategy,
        input: File,
        outDir: File,
        skipResources: Boolean,
        earlier: List<String>,
        onProgress: suspend (done: Int, total: Int) -> Unit,
    ): JadxRunner.Summary? {
        val events = Channel<Event>(Channel.UNLIMITED)
        val connected = CompletableDeferred<Messenger>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                connected.complete(Messenger(binder))
            }

            override fun onServiceDisconnected(name: ComponentName) {
                events.trySend(Event.Died)
            }

            override fun onBindingDied(name: ComponentName) {
                events.trySend(Event.Died)
            }
        }
        val flags = Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT
        val bound = appContext.bindService(Intent(appContext, service), connection, flags)
        try {
            val messenger = if (bound) withTimeoutOrNull(CONNECT_TIMEOUT_MS) { connected.await() } else null
            if (messenger == null) return null
            val reply = Messenger(
                Handler(Looper.getMainLooper()) { message ->
                    message.toEvent()?.let { events.trySend(it) }
                    true
                },
            )
            val payload = bundleOf(
                JadxService.KEY_INPUT to input.absolutePath,
                JadxService.KEY_OUT_DIR to outDir.absolutePath,
                JadxService.KEY_SKIP_RESOURCES to skipResources,
                JadxService.KEY_STRATEGY to strategy.ordinal,
            )
            payload.putStringArrayList(JadxService.KEY_EARLIER, ArrayList(earlier))
            val start = Message.obtain(null, JadxService.MSG_START)
            start.replyTo = reply
            start.data = payload
            messenger.send(start)
            return awaitResult(events, onProgress)
        } finally {
            // Unbinding ends the decompiler process (see JadxService.onDestroy); this is also how a cancel stops it.
            appContext.unbindService(connection)
        }
    }

    private suspend fun awaitResult(
        events: ReceiveChannel<Event>,
        onProgress: suspend (done: Int, total: Int) -> Unit,
    ): JadxRunner.Summary {
        while (true) {
            when (val event = events.receive()) {
                is Event.Progress -> onProgress(event.done, event.total)
                is Event.Done -> return event.summary
                is Event.Failed -> throw RemoteFailure(event.trace, outOfMemory = event.outOfMemory)
                Event.Died -> throw RemoteFailure(DIED_MESSAGE, died = true)
            }
        }
    }

    private fun Message.toEvent(): Event? = when (what) {
        JadxService.MSG_PROGRESS -> Event.Progress(arg1, arg2)
        JadxService.MSG_DONE -> Event.Done(
            JadxRunner.Summary(data.getInt(JadxService.KEY_ERRORS), data.getInt(JadxService.KEY_WARNINGS)),
        )
        JadxService.MSG_FAILED -> Event.Failed(
            data.getString(JadxService.KEY_TRACE).orEmpty(),
            data.getBoolean(JadxService.KEY_OOM),
        )
        else -> null
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val MAX_TRACE_CHARS = 20_000
        const val DIED_MESSAGE =
            "Proses dekompilasi (:jadx) berhenti mendadak; kemungkinan dimatikan sistem karena memori."
        val STRATEGIES = listOf(JadxRunner.Strategy.WHOLE, JadxRunner.Strategy.PER_DEX)
        val SERVICES: List<Class<out Service>> = listOf(JadxService::class.java, JadxFallbackService::class.java)
    }
}
