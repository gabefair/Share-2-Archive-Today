package org.gnosco.share2archivetoday.ytdlp

import java.io.File

/**
 * Container-to-MIME mapping.
 *
 * Downloads used to be labelled video/mp4 regardless of what yt-dlp actually wrote, so
 * a WebM or MKV landed in Downloads with an .mp4 name. For an archive the container has
 * to be described accurately, so both the extension and the MIME type are derived from
 * the real file.
 */
object MediaTypes {

    private val VIDEO = mapOf(
        "mp4" to "video/mp4",
        "m4v" to "video/mp4",
        "webm" to "video/webm",
        "mkv" to "video/x-matroska",
        "flv" to "video/x-flv",
        "mov" to "video/quicktime",
        "3gp" to "video/3gpp",
        "ts" to "video/mp2t",
        "avi" to "video/x-msvideo",
        "ogv" to "video/ogg",
    )

    private val AUDIO = mapOf(
        "m4a" to "audio/mp4",
        "mp3" to "audio/mpeg",
        "opus" to "audio/opus",
        "ogg" to "audio/ogg",
        "oga" to "audio/ogg",
        "aac" to "audio/aac",
        "wav" to "audio/wav",
        "flac" to "audio/flac",
        "weba" to "audio/webm",
    )

    private val SIDECAR = mapOf(
        "json" to "application/json",
        "txt" to "text/plain",
        "description" to "text/plain",
        "log" to "text/plain",
        "vtt" to "text/vtt",
        "srt" to "application/x-subrip",
        "ass" to "text/plain",
        "ssa" to "text/plain",
        "ttml" to "application/ttml+xml",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "webp" to "image/webp",
    )

    /**
     * @param preferAudio true when the user asked for audio only, which decides how
     *   ambiguous containers (mp4, webm, ogg) are labelled.
     */
    fun forFile(file: File, preferAudio: Boolean = false): String =
        forExtension(file.extension, preferAudio)

    fun forExtension(extension: String, preferAudio: Boolean = false): String {
        val ext = extension.lowercase().removePrefix(".")
        if (preferAudio) {
            AUDIO[ext]?.let { return it }
            // A video container holding only audio: mp4 -> audio/mp4, webm -> audio/webm.
            VIDEO[ext]?.let { return it.replaceFirst("video/", "audio/") }
        }
        VIDEO[ext]?.let { return it }
        AUDIO[ext]?.let { return it }
        SIDECAR[ext]?.let { return it }
        return if (preferAudio) "audio/*" else "application/octet-stream"
    }

    fun sidecarMime(fileName: String): String {
        val lower = fileName.lowercase()
        if (lower.endsWith(".info.json")) return "application/json"
        if (lower.endsWith(".description")) return "text/plain"
        val ext = lower.substringAfterLast('.', "")
        return SIDECAR[ext] ?: "application/octet-stream"
    }
}
