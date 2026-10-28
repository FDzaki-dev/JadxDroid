package com.jadxdroid.app.engine

import com.jadxdroid.app.log.CrashLogger
import jadx.android.AndroidPluginLoader
import jadx.android.MemoryGuard
import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import jadx.api.impl.NoOpCodeCache
import jadx.api.impl.SimpleCodeWriter
import jadx.api.security.JadxSecurityFlag.SANITIZE_STRINGS
import jadx.api.security.JadxSecurityFlag.SECURE_ZIP_READER
import jadx.api.security.JadxSecurityFlag.VERIFY_APP_PACKAGE
import jadx.api.security.impl.JadxSecurity
import jadx.api.usage.impl.EmptyUsageInfoCache
import jadx.api.utils.tasks.ITaskExecutor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.util.EnumSet
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private const val NOTES_FILE = "JADXDROID-NOTES.txt"
private const val PROGRESS_INTERVAL_MS = 500L
private const val WORKER_POLL_MS = 100L
private val WORKER_STOP_TIMEOUT = 30.seconds

class JadxRunner {

    data class Options(val skipResources: Boolean)

    data class Summary(val errors: Int, val warnings: Int)

    /**
     * Whole-APK decompile when it fits the heap, per-DEX mode for huge APKs. A run that ends in OutOfMemoryError is
     * stopped completely (worker threads included) and repeated once with a single thread and aggressive class
     * release; if that fails too, the APK is split per DEX. Every earlier failure is kept and reported raw.
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
            val reason = "ukuran DEX total melebihi anggaran heap"
            decompilePerDex(input, outDir, options, plan, reason, emptyList(), onProgress)
        } else {
            decompileWhole(input, outDir, options, plan, onProgress)
        }
    }

    private suspend fun decompileWhole(
        input: File,
        outDir: File,
        options: Options,
        plan: DexSplitter.Plan,
        onProgress: suspend (done: Int, total: Int) -> Unit,
    ): Summary {
        val earlier = mutableListOf<Throwable>()
        val normalThreads = threadCount()
        return try {
            retryOnOutOfMemory(
                alreadyEconomy = false,
                work = { economy ->
                    val threads = if (economy) 1 else normalThreads
                    runJadx(buildArgs(input, outDir, options.skipResources, threads), economy, onProgress)
                },
                onRetry = { failure ->
                    earlier += failure
                    clearOutput(outDir)
                },
            ).also {
                if (earlier.isNotEmpty()) writeNotes(outDir, retryHeader(normalThreads), rawTraces(earlier))
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Throwable) {
            if (!error.isOutOfMemory() || plan.entries.size < 2) {
                earlier.forEach { error.addSuppressed(it) }
                throw error
            }
            earlier += error
            clearOutput(outDir)
            val reason = "OutOfMemoryError pada mode utuh (dua percobaan)"
            decompilePerDex(input, outDir, options, plan, reason, earlier, onProgress)
        }
    }

    /** Runs [work] once; when the heap runs out, repeats it a single time in economy mode (unless already there). */
    private suspend fun <T> retryOnOutOfMemory(
        alreadyEconomy: Boolean,
        work: suspend (economy: Boolean) -> T,
        onRetry: (Throwable) -> Unit,
    ): T = try {
        work(alreadyEconomy)
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (error: Throwable) {
        if (!error.isOutOfMemory() || alreadyEconomy) throw error
        onRetry(error)
        work(true)
    }

    private suspend fun runJadx(
        args: JadxArgs,
        economy: Boolean,
        onProgress: suspend (done: Int, total: Int) -> Unit,
    ): Summary {
        MemoryGuard.begin(economy)
        return try {
            JadxDecompiler(args).use { decompiler ->
                decompiler.load()
                awaitTasks(decompiler.saveTaskExecutor, onProgress)
                Summary(decompiler.errorsCount, decompiler.warnsCount)
            }
        } finally {
            MemoryGuard.end()
        }
    }

    /** Each classesN.dex is decompiled on its own, so only one dex worth of classes is in memory at a time. */
    private suspend fun decompilePerDex(
        input: File,
        outDir: File,
        options: Options,
        plan: DexSplitter.Plan,
        reason: String,
        earlier: List<Throwable>,
        onProgress: suspend (done: Int, total: Int) -> Unit,
    ): Summary {
        val workDir = Files.createTempDirectory("jadx-perdex-").toFile()
        val notes = mutableListOf<String>()
        val normalThreads = threadCount()
        val startEconomy = earlier.isNotEmpty()
        val threadsFor = { economy: Boolean -> if (economy) 1 else normalThreads }
        try {
            val passes = plan.entries.size + if (options.skipResources) 0 else 1
            var index = 0
            var errors = 0
            var warnings = 0
            var succeeded = 0

            suspend fun pass(label: String, work: suspend (suspend (Int, Int) -> Unit, Boolean) -> Summary) {
                currentCoroutineContext().ensureActive()
                val base = index * PASS_UNITS
                val report: suspend (Int, Int) -> Unit = { done, total ->
                    val scaled = if (total > 0) (done.toLong() * PASS_UNITS / total).toInt() else 0
                    onProgress(base + scaled, passes * PASS_UNITS)
                }
                try {
                    val summary = retryOnOutOfMemory(
                        alreadyEconomy = startEconomy,
                        work = { economy -> work(report, economy) },
                        onRetry = { notes += "GAGAL $label (heap habis, diulang dengan 1 thread): $it" },
                    )
                    errors += summary.errors
                    warnings += summary.warnings
                    succeeded++
                    notes += "OK    $label (error=${summary.errors}, peringatan=${summary.warnings}," +
                        " kelas dilepas=${MemoryGuard.released()})"
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
                pass("resources (AndroidManifest.xml, resources.arsc, res/**)") { report, economy ->
                    DexSplitter.extractResourcesArchive(input, archive)
                    val args = buildArgs(archive, outDir, skipResources = false, threads = threadsFor(economy))
                    runJadx(args, economy, report).also { archive.delete() }
                }
            }
            for (entry in plan.entries) {
                val dexFile = File(workDir, "pass$index.dex")
                pass(entry.name) { report, economy ->
                    DexSplitter.extractDex(input, entry, dexFile)
                    val args = buildArgs(dexFile, outDir, skipResources = true, threads = threadsFor(economy))
                    runJadx(args, economy, report).also { dexFile.delete() }
                }
            }

            writeNotes(outDir, perDexHeader(reason, options), notes + rawTraces(earlier))
            if (succeeded == 0) {
                val failure = IllegalStateException("Semua pass per-DEX gagal:\n" + notes.joinToString("\n"))
                earlier.forEach { failure.addSuppressed(it) }
                throw failure
            }
            return Summary(errors, warnings)
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun buildArgs(input: File, outDir: File, skipResources: Boolean, threads: Int): JadxArgs {
        val args = JadxArgs()
        args.setInputFile(input)
        args.setOutDir(outDir)
        args.setThreadsCount(threads)
        args.setSkipResources(skipResources)
        // Result is streamed to disk; keeping every decompiled class in memory would exhaust the Android heap.
        args.setCodeCache(NoOpCodeCache.INSTANCE)
        // Same choices as the upstream CLI: nothing here navigates code, so skip the GUI metadata writer
        // (per-class annotation and line maps) and do not keep the whole usage graph alive after it is applied.
        // Both cut CPU time and heap churn, which is what makes the UI stutter under a large decompile.
        args.setCodeWriterProvider { SimpleCodeWriter(it) }
        args.setUsageInfoCache(EmptyUsageInfoCache())
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
        val limit = if (smallHeap) SMALL_HEAP_THREADS else MAX_THREADS
        return (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, limit)
    }

    private fun Throwable.isOutOfMemory(): Boolean =
        generateSequence(this) { it.cause }.take(MAX_CAUSE_DEPTH).any { it is OutOfMemoryError }

    private companion object {
        const val MAX_THREADS = 4
        const val SMALL_HEAP_THREADS = 3
        const val SMALL_HEAP_BYTES = 640L * 1024 * 1024
        const val SPLIT_HEAP_RATIO = 8
        const val PASS_UNITS = 1000
        const val MAX_CAUSE_DEPTH = 8
    }
}

/** Runs the save tasks and reports progress; on any failure or cancel the run is halted completely first. */
private suspend fun awaitTasks(tasks: ITaskExecutor, onProgress: suspend (done: Int, total: Int) -> Unit) {
    try {
        tasks.execute()
        val total = tasks.tasksCount
        while (tasks.isRunning) {
            currentCoroutineContext().ensureActive()
            onProgress(tasks.progress, total)
            delay(PROGRESS_INTERVAL_MS)
        }
        tasks.awaitTermination()
    } catch (failure: Throwable) {
        haltWorkers(tasks)
        throw failure
    }
}

/**
 * After a failure or cancel the executor only stops taking new batches. Threads that are mid-batch keep running
 * and keep their classes alive, so they are interrupted and awaited before anything else uses the heap.
 */
private suspend fun haltWorkers(tasks: ITaskExecutor) {
    withContext(NonCancellable) {
        tasks.terminate()
        val started = TimeSource.Monotonic.markNow()
        while (MemoryGuard.interruptWorkers() > 0 && started.elapsedNow() < WORKER_STOP_TIMEOUT) {
            delay(WORKER_POLL_MS)
        }
    }
}

private fun clearOutput(outDir: File) {
    outDir.listFiles()?.forEach { it.deleteRecursively() }
}

private fun rawTraces(failures: List<Throwable>): List<String> {
    if (failures.isEmpty()) return emptyList()
    return listOf("", "Kegagalan sebelumnya (mentah):") + failures.map { CrashLogger.rawTrace(it) }
}

private fun retryHeader(threads: Int): List<String> = listOf(
    "JadxDroid: mode utuh hemat memori (1 thread, kelas dilepas agresif)",
    "Alasan: OutOfMemoryError pada percobaan pertama ($threads thread); hasilnya dibuang dan diulang.",
)

private fun perDexHeader(reason: String, options: JadxRunner.Options): List<String> {
    val resources = if (options.skipResources) {
        "dilewati"
    } else {
        "hanya AndroidManifest.xml, resources.arsc, res/** (assets dan lib tidak diekspor)"
    }
    return listOf(
        "JadxDroid: mode per-DEX (hemat memori)",
        "Alasan: $reason",
        "Setiap DEX didekompilasi terpisah; referensi lintas-DEX tidak terselesaikan penuh.",
        "Resources: $resources",
    )
}

private fun writeNotes(outDir: File, header: List<String>, body: List<String>) {
    File(outDir, NOTES_FILE).writeText((header + "" + body).joinToString("\n", postfix = "\n"))
}
