package com.jadxdroid.app.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okio.BufferedSink
import okio.BufferedSource
import okio.HashingSink
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Checks GitHub Releases and streams the APK through Okio, hashing the bytes while they are written. */
object UpdateRepository {

    private const val REPO = "FDzaki-dev/JadxDroid"
    private const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"
    private const val ACCEPT_JSON = "application/vnd.github+json"
    private const val ACCEPT_BINARY = "application/octet-stream"
    private const val USER_AGENT = "JadxDroid-Updater"
    private const val TIMEOUT_MS = 20_000
    private const val HTTP_OK = 200
    private const val SEGMENT_BYTES = 8L * 1024
    private const val PROGRESS_INTERVAL_MS = 250L
    private const val SHA256_HEX_LENGTH = 64
    private const val EOF = -1L
    private val trustedHosts = listOf("github.com", "githubusercontent.com")

    suspend fun fetchLatest(): ReleaseInfo = withContext(Dispatchers.IO) {
        ReleaseParser.parse(readText(LATEST_URL, ACCEPT_JSON))
    }

    /**
     * Streams [asset] into [target] and returns its SHA-256. The hash is computed during the write and
     * compared with SHA256SUMS.txt; a mismatch or any failure deletes the partial file.
     */
    suspend fun download(
        asset: ReleaseAsset,
        target: File,
        onProgress: suspend (done: Long, total: Long) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val expected = expectedChecksum(asset)
        target.parentFile?.mkdirs()
        try {
            val actual = streamTo(asset, target, onProgress)
            check(actual.equals(expected, ignoreCase = true)) {
                "SHA-256 tidak cocok: diharapkan $expected, didapat $actual"
            }
            actual
        } catch (throwable: Throwable) {
            target.delete()
            throw throwable
        }
    }

    private suspend fun streamTo(
        asset: ReleaseAsset,
        target: File,
        onProgress: suspend (done: Long, total: Long) -> Unit,
    ): String {
        val hashing = HashingSink.sha256(target.sink())
        withConnection(asset.url, ACCEPT_BINARY) { connection ->
            connection.inputStream.source().buffer().use { source ->
                hashing.buffer().use { sink -> copyWithProgress(source, sink, asset.size, onProgress) }
            }
        }
        return hashing.hash.hex()
    }

    private suspend fun copyWithProgress(
        source: BufferedSource,
        sink: BufferedSink,
        total: Long,
        onProgress: suspend (done: Long, total: Long) -> Unit,
    ) {
        var done = 0L
        var lastReport = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = source.read(sink.buffer, SEGMENT_BYTES)
            if (read == EOF) break
            done += read
            sink.emitCompleteSegments()
            val now = System.currentTimeMillis()
            if (now - lastReport >= PROGRESS_INTERVAL_MS) {
                lastReport = now
                onProgress(done, total)
            }
        }
        onProgress(done, total)
    }

    private fun expectedChecksum(asset: ReleaseAsset): String {
        val line = readText(asset.checksumUrl, ACCEPT_BINARY)
            .lineSequence()
            .firstOrNull { it.trim().endsWith(asset.name) }
        val hash = line?.trim()?.substringBefore(' ')
        return requireNotNull(hash?.takeIf { it.length == SHA256_HEX_LENGTH }) {
            "Checksum ${asset.name} tidak ada di SHA256SUMS.txt"
        }
    }

    private fun readText(url: String, accept: String): String =
        withConnection(url, accept) { it.inputStream.source().buffer().use { source -> source.readUtf8() } }

    private inline fun <T> withConnection(url: String, accept: String, block: (HttpURLConnection) -> T): T {
        val target = URL(url)
        require(isTrusted(target)) { "Host tidak dipercaya: ${target.host}" }
        val connection = target.openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.setRequestProperty("Accept", accept)
        connection.setRequestProperty("User-Agent", USER_AGENT)
        try {
            val code = connection.responseCode
            if (code != HTTP_OK) throw IOException("HTTP $code ${connection.responseMessage} untuk $url")
            // GitHub redirects release assets to its CDN; the final host must still be GitHub's.
            check(isTrusted(connection.url)) { "Pengalihan ke host tidak dipercaya: ${connection.url.host}" }
            return block(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun isTrusted(url: URL): Boolean =
        url.protocol == "https" && trustedHosts.any { url.host == it || url.host.endsWith(".$it") }
}
