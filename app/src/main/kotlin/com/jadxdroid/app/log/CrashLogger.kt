package com.jadxdroid.app.log

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import okio.buffer
import okio.sink
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes raw exceptions (no paraphrasing) to Documents/JadxDroid/logs through MediaStore.
 */
object CrashLogger {

    private const val RELATIVE_PATH = "Documents/JadxDroid/logs"
    private val lock = Any()

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(appContext, "crash", "thread=${thread.name}\n${rawTrace(throwable)}") }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun logFailure(context: Context, tag: String, throwable: Throwable) {
        runCatching { write(context.applicationContext, tag, rawTrace(throwable)) }
    }

    fun rawTrace(throwable: Throwable): String {
        val writer = StringWriter()
        PrintWriter(writer).use { throwable.printStackTrace(it) }
        return writer.toString()
    }

    private fun write(context: Context, tag: String, body: String) {
        synchronized(lock) {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "${tag}_$stamp.txt")
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: return
            val header = "time=$stamp device=${Build.MANUFACTURER} ${Build.MODEL} sdk=${Build.VERSION.SDK_INT}\n"
            resolver.openOutputStream(uri)?.sink()?.buffer()?.use { it.writeUtf8(header + body) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        }
    }
}
