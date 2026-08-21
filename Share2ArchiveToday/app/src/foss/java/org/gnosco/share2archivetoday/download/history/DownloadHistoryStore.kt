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
)

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
        all().firstOrNull { it.success && it.url == url && !it.mediaStoreUri.isNullOrBlank() }

    fun add(entry: HistoryEntry) {
        val list = all().toMutableList()
        list.removeAll { it.id == entry.id }
        list.add(0, entry)
        val trimmed = list.take(MAX_ENTRIES)
        val arr = JSONArray()
        trimmed.forEach { arr.put(toJson(it)) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun uriStillValid(context: Context, entry: HistoryEntry): Boolean {
        val uri = entry.mediaStoreUri?.let(Uri::parse) ?: return false
        return runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        }.getOrDefault(false)
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
    }

    companion object {
        private const val PREFS = "download_history"
        private const val KEY = "entries"
        private const val MAX_ENTRIES = 200
    }
}
