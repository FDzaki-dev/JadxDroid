package com.jadxdroid.app.update

import org.json.JSONObject

data class ReleaseAsset(
    val name: String,
    val url: String,
    val size: Long,
    val checksumUrl: String,
)

data class ReleaseInfo(
    val tag: String,
    val versionName: String,
    val versionCode: Long,
    val publishedDate: String,
    val notes: String,
    val asset: ReleaseAsset,
)

/** Turns the GitHub "latest release" JSON into a [ReleaseInfo]. Pure, so it needs no network. */
object ReleaseParser {

    private const val CHECKSUM_NAME = "SHA256SUMS.txt"
    private const val POSITION_BASE = 100L
    private const val MAX_NOTES_CHARS = 1_500
    private const val NOTES_FOOTER = "\n---"
    private val versionPattern = Regex("""(\d+)\.(\d+)\.(\d+)-android\.(\d+)""")
    private val bullet = Regex("^- ")

    /** Same value as versionCode in app/build.gradle.kts, so a tag compares directly with the installed code. */
    fun versionCodeOf(versionName: String): Long? {
        val match = versionPattern.matchEntire(versionName) ?: return null
        val parts = match.groupValues.drop(1).map { it.toLongOrNull() ?: return null }
        return parts.fold(0L) { code, part -> code * POSITION_BASE + part }
    }

    fun parse(json: String): ReleaseInfo {
        val root = JSONObject(json)
        val tag = root.getString("tag_name")
        val versionName = tag.removePrefix("v")
        val versionCode = requireNotNull(versionCodeOf(versionName)) { "Tag rilis tidak dikenali: $tag" }
        val assets = root.getJSONArray("assets")
        val byName = (0 until assets.length()).map { assets.getJSONObject(it) }.associateBy { it.getString("name") }
        val apk = requireNotNull(byName["JadxDroid-$versionName.apk"]) { "APK rilis $tag tidak ditemukan" }
        val sums = requireNotNull(byName[CHECKSUM_NAME]) { "$CHECKSUM_NAME rilis $tag tidak ditemukan" }
        return ReleaseInfo(
            tag = tag,
            versionName = versionName,
            versionCode = versionCode,
            publishedDate = root.optString("published_at").substringBefore('T'),
            notes = cleanNotes(root.optString("body")),
            asset = ReleaseAsset(
                name = apk.getString("name"),
                url = apk.getString("browser_download_url"),
                size = apk.getLong("size"),
                checksumUrl = sums.getString("browser_download_url"),
            ),
        )
    }

    /** Release notes arrive as Markdown plus a download footer; show only the notes as plain text. */
    private fun cleanNotes(body: String): String {
        val cleaned = body.substringBefore(NOTES_FOOTER)
            .lines()
            .joinToString("\n") { it.trimEnd().replaceFirst(bullet, "• ").replace("`", "") }
            .trim()
        return if (cleaned.length > MAX_NOTES_CHARS) cleaned.take(MAX_NOTES_CHARS) + "…" else cleaned
    }
}
