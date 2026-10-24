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
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.EnumSet

class JadxRunner {

    data class Options(val skipResources: Boolean)

    data class Summary(val errors: Int, val warnings: Int)

    suspend fun decompile(
        input: File,
        outDir: File,
        options: Options,
        onProgress: suspend (done: Int, total: Int) -> Unit,
    ): Summary = withContext(Dispatchers.Default) {
        JadxDecompiler(buildArgs(input, outDir, options)).use { decompiler ->
            decompiler.load()
            val tasks = decompiler.saveTaskExecutor
            tasks.execute()
            val total = tasks.tasksCount
            try {
                while (tasks.isRunning) {
                    ensureActive()
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
    }

    private fun buildArgs(input: File, outDir: File, options: Options): JadxArgs {
        val args = JadxArgs()
        args.setInputFile(input)
        args.setOutDir(outDir)
        args.setThreadsCount(threadCount())
        args.setSkipResources(options.skipResources)
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

    private fun threadCount(): Int =
        (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, MAX_THREADS)

    private companion object {
        const val PROGRESS_INTERVAL_MS = 500L
        const val MAX_THREADS = 4
    }
}
