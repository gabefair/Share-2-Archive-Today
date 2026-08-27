package org.gnosco.share2archivetoday.ytdlp

import java.security.MessageDigest

/** Pure naming/identity helpers for downloads (unit-testable, no Android dependencies). */
object DownloadNaming {

    const val MAX_NAME_CHARS = 80

    private val ILLEGAL = Regex("[\\\\/:*?\"<>|\\x00-\\x1F]")

    /**
     * Strip path-hostile characters and truncate.
     *
     * Truncation is done on whole code points: cutting at a fixed number of UTF-16 units
     * can split a surrogate pair and leave an unpaired half in the file name.
     */
    fun sanitize(name: String, maxChars: Int = MAX_NAME_CHARS): String {
        val cleaned = ILLEGAL.replace(name, "_").trim().trimStart('.').trim()
        if (cleaned.isBlank()) return "download"
        if (cleaned.length <= maxChars) return cleaned
        var end = maxChars
        if (end > 0 && Character.isHighSurrogate(cleaned[end - 1])) end--
        return cleaned.substring(0, end).trim().ifBlank { "download" }
    }

    /**
     * Stable id for a (url, format) pair.
     *
     * A random id per attempt meant the scratch directory was never reused, so yt-dlp's
     * continuedl could never actually resume a download that had failed part-way.
     */
    fun downloadId(
        url: String,
        videoFormatId: String?,
        audioFormatIds: List<String>,
        combinedFormatId: String?,
    ): String {
        val key = listOf(
            url,
            videoFormatId.orEmpty(),
            audioFormatIds.joinToString(","),
            combinedFormatId.orEmpty(),
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }

    /** "<base><suffix>.<ext>", e.g. "Clip.video.mp4" for an unmerged video stream. */
    fun artifactName(baseName: String, nameSuffix: String, extension: String): String {
        val ext = extension.ifBlank { "bin" }.removePrefix(".")
        return "$baseName$nameSuffix.$ext"
    }

    /**
     * Re-stem a yt-dlp sidecar onto the shared base name.
     *
     * Everything after the first dot is kept so language-tagged subtitles ("en.vtt",
     * "es-419.srt") and compound suffixes (".info.json") survive intact.
     */
    fun sidecarName(baseName: String, sidecarFileName: String): String {
        val firstDot = sidecarFileName.indexOf('.')
        val suffix = if (firstDot >= 0) sidecarFileName.substring(firstDot) else ""
        return baseName + suffix
    }

    /** Folder for a download that saves more than one file. */
    fun folderName(baseName: String, shortId: String): String =
        if (shortId.isBlank()) baseName else "$baseName [$shortId]"
}
