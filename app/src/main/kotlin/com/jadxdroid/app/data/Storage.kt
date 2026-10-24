package com.jadxdroid.app.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object Storage {

    const val FALLBACK_NAME = "input.bin"

    private const val INPUT_DIR = "input"
    private const val OUTPUT_DIR = "jadx-out"
    private const val EXPORT_RELATIVE_PATH = "Documents/JadxDroid"
    private const val PROGRESS_STEP = 25
    private val unsafeChars = Regex("[^A-Za-z0-9._-]")

    fun displayName(context: Context, uri: Uri): String {
        val fromProvider = context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        return fromProvider?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: FALLBACK_NAME
    }

    fun sanitize(name: String): String =
        name.replace(unsafeChars, "_").trim('.', '_').ifBlank { FALLBACK_NAME }

    /** Streams the picked document into app cache; jadx needs a real File. */
    fun copyToCache(context: Context, uri: Uri, fileName: String): File {
        val dir = File(context.cacheDir, INPUT_DIR)
        dir.deleteRecursively()
        check(dir.mkdirs()) { "Gagal membuat direktori input: $dir" }
        val target = File(dir, sanitize(fileName))
        val input = context.contentResolver.openInputStream(uri) ?: error("Tidak bisa membuka input: $uri")
        input.source().buffer().use { source ->
            target.sink().buffer().use { sink -> sink.writeAll(source) }
        }
        return target
    }

    fun prepareOutputDir(context: Context): File {
        val dir = File(context.filesDir, OUTPUT_DIR)
        dir.deleteRecursively()
        check(dir.mkdirs()) { "Gagal membuat direktori output: $dir" }
        return dir
    }

    fun cleanup(context: Context) {
        File(context.cacheDir, INPUT_DIR).deleteRecursively()
        File(context.filesDir, OUTPUT_DIR).deleteRecursively()
    }

    /** Zips [sourceDir] into Documents/JadxDroid via MediaStore; returns the relative path shown to the user. */
    suspend fun exportZip(
        context: Context,
        sourceDir: File,
        baseName: String,
        onProgress: suspend (done: Int, total: Int) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val files = sourceDir.walkTopDown().filter { it.isFile }.toList()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "${sanitize(baseName)}_$stamp.zip"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, EXPORT_RELATIVE_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: error("MediaStore menolak insert: $fileName")
        try {
            val stream = resolver.openOutputStream(uri) ?: error("Tidak bisa menulis: $uri")
            stream.sink().buffer().use { buffered ->
                ZipOutputStream(buffered.outputStream()).use { zip ->
                    val zipSink = zip.sink()
                    files.forEachIndexed { index, file ->
                        ensureActive()
                        zip.putNextEntry(ZipEntry(file.relativeTo(sourceDir).invariantSeparatorsPath))
                        file.source().buffer().use { it.readAll(zipSink) }
                        zip.closeEntry()
                        if (index % PROGRESS_STEP == 0) onProgress(index + 1, files.size)
                    }
                }
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (throwable: Throwable) {
            resolver.delete(uri, null, null)
            throw throwable
        }
        "$EXPORT_RELATIVE_PATH/$fileName"
    }
}
