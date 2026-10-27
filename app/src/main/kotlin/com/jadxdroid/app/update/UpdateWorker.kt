package com.jadxdroid.app.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.jadxdroid.app.log.CrashLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Downloads and verifies the release APK. WorkManager keeps the state, so it survives rotation. */
class UpdateWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val tag = inputData.getString(KEY_TAG).orEmpty()
        val versionCode = inputData.getLong(KEY_VERSION_CODE, 0L)
        val asset = ReleaseAsset(
            name = inputData.getString(KEY_ASSET_NAME).orEmpty(),
            url = inputData.getString(KEY_ASSET_URL).orEmpty(),
            size = inputData.getLong(KEY_ASSET_SIZE, 0L),
            checksumUrl = inputData.getString(KEY_CHECKSUM_URL).orEmpty(),
        )
        if (asset.name.isEmpty() || asset.url.isEmpty()) return failure(tag, versionCode, "Data unduhan tidak lengkap")
        return try {
            val file = withContext(Dispatchers.IO) { freshTarget(asset.name) }
            val sha256 = UpdateRepository.download(asset, file) { done, total ->
                setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total))
            }
            check(UpdateInstaller.archiveMatches(applicationContext, file, versionCode)) {
                "Paket unduhan tidak cocok dengan rilis $tag (nama paket atau versionCode berbeda)"
            }
            Result.success(
                workDataOf(
                    KEY_TAG to tag,
                    KEY_VERSION_CODE to versionCode,
                    KEY_PATH to file.absolutePath,
                    KEY_SHA256 to sha256,
                ),
            )
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (throwable: Throwable) {
            CrashLogger.logFailure(applicationContext, "update-download-failure", throwable)
            withContext(Dispatchers.IO) { updateDir().deleteRecursively() }
            failure(tag, versionCode, CrashLogger.rawTrace(throwable).take(MAX_FAILURE_CHARS))
        }
    }

    private fun failure(tag: String, versionCode: Long, message: String): Result =
        Result.failure(workDataOf(KEY_TAG to tag, KEY_VERSION_CODE to versionCode, KEY_FAILURE to message))

    private fun updateDir(): File = File(applicationContext.cacheDir, UPDATE_DIR)

    private fun freshTarget(assetName: String): File {
        val dir = updateDir()
        dir.deleteRecursively()
        check(dir.mkdirs()) { "Gagal membuat direktori pembaruan: $dir" }
        return File(dir, assetName)
    }

    companion object {
        const val UNIQUE_NAME = "app-update"
        const val KEY_TAG = "tag"
        const val KEY_VERSION_CODE = "version_code"
        const val KEY_ASSET_NAME = "asset_name"
        const val KEY_ASSET_URL = "asset_url"
        const val KEY_ASSET_SIZE = "asset_size"
        const val KEY_CHECKSUM_URL = "checksum_url"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_PATH = "path"
        const val KEY_SHA256 = "sha256"
        const val KEY_FAILURE = "failure"

        private const val UPDATE_DIR = "update"
        private const val MAX_FAILURE_CHARS = 3000

        fun inputOf(release: ReleaseInfo): Data = workDataOf(
            KEY_TAG to release.tag,
            KEY_VERSION_CODE to release.versionCode,
            KEY_ASSET_NAME to release.asset.name,
            KEY_ASSET_URL to release.asset.url,
            KEY_ASSET_SIZE to release.asset.size,
            KEY_CHECKSUM_URL to release.asset.checksumUrl,
        )
    }
}
