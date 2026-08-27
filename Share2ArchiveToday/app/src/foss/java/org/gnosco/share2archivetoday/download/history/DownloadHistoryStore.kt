package org.gnosco.share2archivetoday.download.history

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(
    val id: String,
    val url: String,
    val title: String,
    val mediaStoreUri: String?,
    val success: Boolean,
    val error: String?,
    val timestamp: Long,
    val estimatedBytes: Long?,
    /** Canonical single-video URL from yt-dlp; used to match playlist vs direct shares. */
    val webpageUrl: String? = null,
    /** yt-dlp video id when known. */
    val videoId: String? = null,
    /** Enough of the last request to resume the same work dir / formats. */
    val retry: RetrySpec? = null,
)

/**
 * Parameters needed to re-enqueue a download without re-probing.
 * Absent on older history entries — those fall back to sharing the URL again.
 */
data class RetrySpec(
    val url: String,
    val originalUrl: String,
    val videoFormatId: String?,
    val audioFormatIds: List<String>,
    val combinedFormatId: String?,
    val needsMux: Boolean,
    val audioOnly: Boolean,
    val requiresVideoExtract: Boolean,
    val archiveMetadata: Boolean,
    val includeComments: Boolean,
    val estimatedBytes: Long?,
    val wifiOnly: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("url", url)
        put("originalUrl", originalUrl)
        put("videoFormatId", videoFormatId)
        put("audioFormatIds", JSONArray(audioFormatIds))
        put("combinedFormatId", combinedFormatId)
        put("needsMux", needsMux)
        put("audioOnly", audioOnly)
        put("requiresVideoExtract", requiresVideoExtract)
        put("archiveMetadata", archiveMetadata)
        put("includeComments", includeComments)
        put("estimatedBytes", estimatedBytes)
        put("wifiOnly", wifiOnly)
    }

    companion object {
        fun parse(o: JSONObject): RetrySpec {
            val audio = o.optJSONArray("audioFormatIds") ?: JSONArray()
            return RetrySpec(
                url = o.getString("url"),
                originalUrl = o.optString("originalUrl", o.getString("url")),
                videoFormatId = o.optString("videoFormatId").ifEmpty { null },
                audioFormatIds = buildList {
                    for (i in 0 until audio.length()) add(audio.getString(i))
                },
                combinedFormatId = o.optString("combinedFormatId").ifEmpty { null },
                needsMux = o.optBoolean("needsMux"),
                audioOnly = o.optBoolean("audioOnly"),
                requiresVideoExtract = o.optBoolean("requiresVideoExtract"),
                archiveMetadata = o.optBoolean("archiveMetadata"),
                includeComments = o.optBoolean("includeComments"),
                estimatedBytes = if (o.isNull("estimatedBytes")) null else o.getLong("estimatedBytes"),
                wifiOnly = o.optBoolean("wifiOnly"),
            )
        }
    }
}

/** Tiny JSON-backed download history (no launcher UI dependency). */
class DownloadHistoryStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(): List<HistoryEntry> {
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) {
                add(parse(arr.getJSONObject(i)))
            }
        }.sortedByDescending { it.timestamp }
    }

    fun findSuccessful(url: String): HistoryEntry? =
        all().firstOrNull { it.success && urlsMatch(it, url) && !it.mediaStoreUri.isNullOrBlank() }

    fun findSuccessfulByVideoId(videoId: String?): HistoryEntry? {
        if (videoId.isNullOrBlank()) return null
        return all().firstOrNull {
            it.success && it.videoId == videoId && !it.mediaStoreUri.isNullOrBlank()
        }
    }

    fun findById(id: String): HistoryEntry? = all().firstOrNull { it.id == id }

    fun add(entry: HistoryEntry) {
        val list = all().toMutableList()
        list.removeAll { it.id == entry.id }
        list.add(0, entry)
        persist(list.take(MAX_ENTRIES))
    }

    fun remove(id: String) {
        persist(all().filterNot { it.id == id })
    }

    fun clear() {
        prefs.edit().putString(KEY, "[]").apply()
    }

    private fun persist(list: List<HistoryEntry>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun uriStillValid(context: Context, entry: HistoryEntry): Boolean {
        val uri = entry.mediaStoreUri?.let(Uri::parse) ?: return false
        return runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        }.getOrDefault(false)
    }

    private fun urlsMatch(entry: HistoryEntry, url: String): Boolean {
        if (entry.url == url) return true
        if (!entry.webpageUrl.isNullOrBlank() && entry.webpageUrl == url) return true
        return false
    }

    private fun parse(o: JSONObject) = HistoryEntry(
        id = o.getString("id"),
        url = o.getString("url"),
        title = o.optString("title"),
        mediaStoreUri = o.optString("mediaStoreUri").ifEmpty { null },
        success = o.optBoolean("success"),
        error = o.optString("error").ifEmpty { null },
        timestamp = o.optLong("timestamp"),
        estimatedBytes = if (o.isNull("estimatedBytes")) null else o.getLong("estimatedBytes"),
        webpageUrl = o.optString("webpageUrl").ifEmpty { null },
        videoId = o.optString("videoId").ifEmpty { null },
        retry = o.optJSONObject("retry")?.let { RetrySpec.parse(it) },
    )

    private fun toJson(e: HistoryEntry) = JSONObject().apply {
        put("id", e.id)
        put("url", e.url)
        put("title", e.title)
        put("mediaStoreUri", e.mediaStoreUri)
        put("success", e.success)
        put("error", e.error)
        put("timestamp", e.timestamp)
        put("estimatedBytes", e.estimatedBytes)
        put("webpageUrl", e.webpageUrl)
        put("videoId", e.videoId)
        e.retry?.let { put("retry", it.toJson()) }
    }

    companion object {
        private const val PREFS = "download_history"
        private const val KEY = "entries"
        private const val MAX_ENTRIES = 200
    }
}
