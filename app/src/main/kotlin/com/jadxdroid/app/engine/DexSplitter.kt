package com.jadxdroid.app.engine

import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.IOException
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Reads APK structure and streams single entries to disk. Nothing here loads an entry into the Java heap,
 * which matters because Android caps the heap at a few hundred MB.
 */
object DexSplitter {

    private const val DEX_SUFFIX = ".dex"

    data class DexEntry(val name: String, val size: Long)

    data class Plan(val entries: List<DexEntry>) {
        val totalBytes: Long get() = entries.sumOf { it.size }
    }

    /** Lists dex entries of a zip/APK; empty plan for anything that is not a readable zip. */
    fun inspect(input: File): Plan = try {
        ZipFile(input).use { zip ->
            Plan(
                zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.endsWith(DEX_SUFFIX) }
                    .map { DexEntry(it.name, it.size.coerceAtLeast(0L)) }
                    .sortedBy { it.name }
                    .toList(),
            )
        }
    } catch (ignored: IOException) {
        Plan(emptyList())
    }

    fun extractDex(input: File, entry: DexEntry, target: File) {
        ZipFile(input).use { zip ->
            val zipEntry = requireNotNull(zip.getEntry(entry.name)) { "Entri tidak ditemukan: ${entry.name}" }
            zip.getInputStream(zipEntry).source().buffer().use { source ->
                target.sink().buffer().use { sink -> sink.writeAll(source) }
            }
        }
    }

    /**
     * Writes a small archive with the manifest, resources.arsc and the res folder,
     * so resources decode without any dex.
     */
    fun extractResourcesArchive(input: File, target: File): Int =
        ZipFile(input).use { zip ->
            ZipOutputStream(target.sink().buffer().outputStream()).use { out ->
                out.setLevel(Deflater.BEST_SPEED)
                copyResourceEntries(zip, out)
            }
        }

    private fun copyResourceEntries(zip: ZipFile, out: ZipOutputStream): Int {
        val outSink = out.sink()
        var copied = 0
        for (entry in zip.entries().asSequence().filter { !it.isDirectory && isResourceEntry(it.name) }) {
            out.putNextEntry(ZipEntry(entry.name))
            zip.getInputStream(entry).source().buffer().use { it.readAll(outSink) }
            out.closeEntry()
            copied++
        }
        return copied
    }

    private fun isResourceEntry(name: String): Boolean =
        name == "AndroidManifest.xml" || name == "resources.arsc" || name.startsWith("res/")
}
