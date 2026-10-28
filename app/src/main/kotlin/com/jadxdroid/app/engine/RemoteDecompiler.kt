package com.jadxdroid.app.engine

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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/** A failure that happened inside the decompiler process; [trace] is its raw stack trace, unchanged. */
class RemoteFailure(val trace: String) : Exception(trace)

/**
 * Client side of [JadxService]. [decompile] returns null when the decompiler process cannot be started, so the
 * caller can fall back to decompiling in this process (slower for the UI, but never a regression).
 */
class RemoteDecompiler(context: Context) {

    private val appContext = context.applicationContext

    private sealed interface Event {
        data class Progress(val done: Int, val total: Int) : Event
        data class Done(val summary: JadxRunner.Summary) : Event
        data class Failed(val trace: String) : Event
        data object Died : Event
    }

    suspend fun decompile(
        input: File,
        outDir: File,
        skipResources: Boolean,
        onProgress: suspend (done: Int, total: Int) -> Unit,
    ): JadxRunner.Summary? {
        val events = Channel<Event>(Channel.UNLIMITED)
        val connected = CompletableDeferred<Messenger>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                connected.complete(Messenger(service))
            }

            override fun onServiceDisconnected(name: ComponentName) {
                events.trySend(Event.Died)
            }

            override fun onBindingDied(name: ComponentName) {
                events.trySend(Event.Died)
            }
        }
        val flags = Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT
        val bound = appContext.bindService(Intent(appContext, JadxService::class.java), connection, flags)
        try {
            val service = if (bound) withTimeoutOrNull(CONNECT_TIMEOUT_MS) { connected.await() } else null
            if (service == null) return null
            val reply = Messenger(
                Handler(Looper.getMainLooper()) { message ->
                    message.toEvent()?.let { events.trySend(it) }
                    true
                },
            )
            val start = Message.obtain(null, JadxService.MSG_START)
            start.replyTo = reply
            start.data = bundleOf(
                JadxService.KEY_INPUT to input.absolutePath,
                JadxService.KEY_OUT_DIR to outDir.absolutePath,
                JadxService.KEY_SKIP_RESOURCES to skipResources,
            )
            service.send(start)
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
                is Event.Failed -> throw RemoteFailure(event.trace)
                Event.Died -> throw RemoteFailure(DIED_MESSAGE)
            }
        }
    }

    private fun Message.toEvent(): Event? = when (what) {
        JadxService.MSG_PROGRESS -> Event.Progress(arg1, arg2)
        JadxService.MSG_DONE -> Event.Done(
            JadxRunner.Summary(data.getInt(JadxService.KEY_ERRORS), data.getInt(JadxService.KEY_WARNINGS)),
        )
        JadxService.MSG_FAILED -> Event.Failed(data.getString(JadxService.KEY_TRACE).orEmpty())
        else -> null
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val DIED_MESSAGE =
            "Proses dekompilasi (:jadx) berhenti mendadak; kemungkinan dimatikan sistem karena memori."
    }
}
