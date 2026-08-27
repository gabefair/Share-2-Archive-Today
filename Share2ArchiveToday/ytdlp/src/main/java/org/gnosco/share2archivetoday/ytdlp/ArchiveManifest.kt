package org.gnosco.share2archivetoday.ytdlp

import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject

/**
 * Chain-of-custody record written alongside every download.
 *
 * yt-dlp's own .info.json describes the video; this describes the *capture*: which URL
 * was shared, when it was fetched, which yt-dlp produced it, a digest of every file
 * saved, and whether the bytes were re-containered on device (in which case the file is
 * deliberately not bit-identical to the origin stream).
 */
object ArchiveManifest {

    const val SCHEMA = "share2archive/download-manifest/1"
    const val FILE_SUFFIX = ".manifest.json"

    data class Artifact(
        val displayName: String,
        val file: File,
        val kind: String,
    )

    data class Capture(
        val originalUrl: String,
        val resolvedUrl: String?,
        val appVersion: String,
        val appFlavor: String,
        val ytdlpVersion: String,
        /** Non-null when Media3 rewrote the container (mux or audio extraction). */
        val remuxTool: String?,
        val remuxOperation: String?,
        /**
         * Link that submits the source page to archive.today. Recorded so the media
         * file stays connected to an archived rendering of the page it came from -
         * title, description and surrounding context that the video alone loses.
         */
        val pageArchiveUrl: String? = null,
        val notes: List<String> = emptyList(),
    )

    fun build(
        capture: Capture,
        provenance: JSONObject?,
        artifacts: List<Artifact>,
    ): String {
        val root = JSONObject()
        root.put("schema", SCHEMA)
        root.put("captured_utc", nowUtc())

        root.put(
            "capture",
            JSONObject().apply {
                put("original_url", capture.originalUrl)
                put("resolved_url", capture.resolvedUrl ?: JSONObject.NULL)
                put("app", "Share 2 Archive Today")
                put("app_version", capture.appVersion)
                put("app_flavor", capture.appFlavor)
                put("ytdlp_version", capture.ytdlpVersion)
                put("downloader", "yt-dlp (native downloaders; no ffmpeg on device)")
                put("page_archive_url", capture.pageArchiveUrl ?: JSONObject.NULL)
            },
        )

        root.put(
            "processing",
            JSONObject().apply {
                val remuxed = capture.remuxTool != null
                put("remuxed_on_device", remuxed)
                put("remux_tool", capture.remuxTool ?: JSONObject.NULL)
                put("remux_operation", capture.remuxOperation ?: JSONObject.NULL)
                put("transcoded", false)
                put(
                    "integrity_note",
                    if (remuxed) {
                        "Streams were copied into a new container on device. " +
                            "Elementary stream data is unchanged, but the file is not " +
                            "byte-identical to the origin stream."
                    } else {
                        "Saved as delivered by the origin server; no on-device rewriting."
                    },
                )
            },
        )

        if (capture.notes.isNotEmpty()) {
            root.put("notes", JSONArray(capture.notes))
        }

        root.put("source", provenance ?: JSONObject())

        val files = JSONArray()
        for (artifact in artifacts) {
            if (!artifact.file.isFile) continue
            files.put(
                JSONObject().apply {
                    put("name", artifact.displayName)
                    put("kind", artifact.kind)
                    put("bytes", artifact.file.length())
                    put("sha256", sha256(artifact.file) ?: JSONObject.NULL)
                },
            )
        }
        root.put("files", files)

        return root.toString(2)
    }

    fun sha256(file: File): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    private fun nowUtc(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }
}
