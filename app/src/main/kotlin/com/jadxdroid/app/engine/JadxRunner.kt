package com.jadxdroid.app.engine

import jadx.android.AndroidPluginLoader
import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import jadx.api.impl.NoOpCodeCache
import jadx.api.security.JadxSecurityFlag.SANITIZE_STRINGS
import jadx.api.security.JadxSecurityFlag.SECURE_ZIP_READER
import jadx.api.security.JadxSecurityFlag.VERIFY_APP_PACKAGE
import jadx.api.security.impl.JadxSecurity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.util.EnumSet

class JadxRunner {

    data class Options(val skipResources: Boolean)

    data class Summary(val errors: Int, val warnings: Int)

    /**
     * Whole-APK decompile when it fits the heap; per-DEX mode for huge APKs or after an OutOfMemoryError.
     */
    suspend fun decompile(
        input: File,
        outDir: File,
        options: Options,
        onProgress: suspend (done: Int, total: Int) -> Unit,
    ): Summary = withContext(Dispatchers.Default) {
        val plan = DexSplitter.inspect(input)
        val splittable = plan.entries.size > 1
        val tooBigForHeap = plan.totalBytes > Runtime.getRuntime().maxMemory() / SPLIT_HEAP_RATIO
        if (splittable && tooBigForHeap) {
            decompilePerDex(input, outDir, options, plan, "ukuran DEX total melebihi anggaran heap", onProgress)
        } else {
            try {
                runJadx(buildArgs(input, outDir, options.skipResources), onProgress)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                if (!error.isOutOfMemory() || !splittable) throw error
                outDir.listFiles()?.forEach { it.deleteRecursively() }
                decompilePerDex(input, outDir, options, plan, "OutOfMemoryError pada mode utuh", onProgress)
            }
        }
    }

    private suspend fun runJadx(args: JadxArgs, onProgress: suspend (done: Int, total: Int) -> Unit): Summary =
        JadxDecompiler(args).use { decompiler ->
            decompiler.load()
            val tasks = decompiler.saveTaskExecutor
            tasks.execute()
            val total = tasks.tasksCount
            try {
                while (tasks.isRunning) {
                    currentCoroutineContext().ensureActive()
                    onProgress(tasks.progress, total)
                    delay(PROGRESS_INTERVAL_MS)
                }
                tasks.awaitTermination()
            } catch (cancel: CancellationException) {
                tasks.terminate()
                runCatching { tasks.awaitTermination() }
                throw cancel
            }
            Summary(decompiler.errorsCount, decompiler.warnsCount)
        }

    /** Each classesN.dex is decompiled on its own, so only one dex worth of classes is in memory at a time. */
    private suspend fun decompilePerDex(
        input: File,
        outDir: File,
        options: Options,
        plan: DexSplitter.Plan,
        reason: String,
        onProgress: suspend (done: Int, total: Int) -> Unit,
    ): Summary {
        val workDir = Files.createTempDirectory("jadx-perdex-").toFile()
        val notes = mutableListOf<String>()
        try {
            val passes = plan.entries.size + if (options.skipResources) 0 else 1
            var index = 0
            var errors = 0
            var warnings = 0
            var succeeded = 0

            suspend fun pass(label: String, work: suspend (report: suspend (Int, Int) -> Unit) -> Summary) {
                currentCoroutineContext().ensureActive()
                val base = index * PASS_UNITS
                val report: suspend (Int, Int) -> Unit = { done, total ->
                    val scaled = if (total > 0) (done.toLong() * PASS_UNITS / total).toInt() else 0
                    onProgress(base + scaled, passes * PASS_UNITS)
                }
                try {
                    val summary = work(report)
                    errors += summary.errors
                    warnings += summary.warnings
                    succeeded++
                    notes += "OK    $label (error=${summary.errors}, peringatan=${summary.warnings})"
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (failure: Throwable) {
                    errors++
                    notes += "GAGAL $label: $failure"
                }
                index++
            }

            if (!options.skipResources) {
                val archive = File(workDir, "resources-only.zip")
                pass("resources (AndroidManifest.xml, resources.arsc, res/**)") { report ->
                    DexSplitter.extractResourcesArchive(input, archive)
                    runJadx(buildArgs(archive, outDir, skipResources = false), report).also { archive.delete() }
                }
            }
            for (entry in plan.entries) {
                val dexFile = File(workDir, "pass$index.dex")
                pass(entry.name) { report ->
                    DexSplitter.extractDex(input, entry, dexFile)
                    runJadx(buildArgs(dexFile, outDir, skipResources = true), report).also { dexFile.delete() }
                }
            }

            writeNotes(outDir, reason, options, notes)
            check(succeeded > 0) { "Semua pass per-DEX gagal:\n" + notes.joinToString("\n") }
            return Summary(errors, warnings)
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun writeNotes(outDir: File, reason: String, options: Options, notes: List<String>) {
        val resources = if (options.skipResources) {
            "dilewati"
        } else {
            "hanya AndroidManifest.xml, resources.arsc, res/** (assets dan lib tidak diekspor)"
        }
        File(outDir, NOTES_FILE).writeText(
            buildString {
                appendLine("JadxDroid: mode per-DEX (hemat memori)")
                appendLine("Alasan: $reason")
                appendLine("Setiap DEX didekompilasi terpisah; referensi lintas-DEX tidak terselesaikan penuh.")
                appendLine("Resources: $resources")
                appendLine()
                notes.forEach { appendLine(it) }
            },
        )
    }

    private fun buildArgs(input: File, outDir: File, skipResources: Boolean): JadxArgs {
        val args = JadxArgs()
        args.setInputFile(input)
        args.setOutDir(outDir)
        args.setThreadsCount(threadCount())
        args.setSkipResources(skipResources)
        // Result is streamed to disk; keeping every decompiled class in memory would exhaust the Android heap.
        args.setCodeCache(NoOpCodeCache.INSTANCE)
        // Explicit plugin list: no dependency on ServiceLoader / META-INF/services in the APK.
        args.setPluginLoader(AndroidPluginLoader())
        // SECURE_XML_PARSER is left out on purpose: it needs Xerces-only DocumentBuilderFactory features,
        // which Android's parser rejects (jadx would fail while reading the manifest attribute tables).
        args.setSecurity(JadxSecurity(EnumSet.of(VERIFY_APP_PACKAGE, SECURE_ZIP_READER, SANITIZE_STRINGS)))
        args.setUseHeadersForDetectResourceExtensions(false)
        return args
    }

    private fun threadCount(): Int {
        val smallHeap = Runtime.getRuntime().maxMemory() <= SMALL_HEAP_BYTES
        return (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, if (smallHeap) 2 else MAX_THREADS)
    }

    private fun Throwable.isOutOfMemory(): Boolean =
        generateSequence(this) { it.cause }.take(MAX_CAUSE_DEPTH).any { it is OutOfMemoryError }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 500L
        const val MAX_THREADS = 4
        const val SMALL_HEAP_BYTES = 640L * 1024 * 1024
        const val SPLIT_HEAP_RATIO = 8
        const val PASS_UNITS = 1000
        const val MAX_CAUSE_DEPTH = 8
        const val NOTES_FILE = "JADXDROID-NOTES.txt"
    }
}
