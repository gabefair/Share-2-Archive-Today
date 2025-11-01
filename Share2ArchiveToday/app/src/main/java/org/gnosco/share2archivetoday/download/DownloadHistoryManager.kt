package org.gnosco.share2archivetoday.download

import org.gnosco.share2archivetoday.download.*

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

/**
 * Manages download history for video downloads
 * Stores download information in SharedPreferences as JSON
 */
class DownloadHistoryManager(private val context: Context) {
    
    companion object {
        private const val TAG = "DownloadHistoryManager"
        private const val PREFS_NAME = "download_history"
        private const val KEY_HISTORY = "download_history_list"
        private const val MAX_HISTORY_ITEMS = 50 // Keep last 50 downloads
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Add a download to history
     * @param url Original URL
     * @param title Video title
     * @param uploader Video uploader
     * @param quality Selected quality
     * @param filePath Downloaded file path
     * @param fileSize File size in bytes
     * @param success Whether download was successful
     * @param error Error message if failed
     */
    fun addDownload(
        url: String,
        title: String,
        uploader: String,
        quality: String,
        filePath: String?,
        fileSize: Long,
        success: Boolean,
        error: String? = null
    ) {
        try {
            val history = getDownloadHistory().toMutableList()
            
            // For successful downloads, check if this URL was already downloaded
            if (success) {
                val existingEntry = history.find { it.url == url && it.success }
                if (existingEntry != null) {
                    Log.d(TAG, "URL already exists in history, updating entry: $title")
                    // Remove the old entry and add the new one
                    history.removeAll { it.url == url && it.success }
                }
            }
            
            val downloadEntry = DownloadHistoryEntry(
                id = System.currentTimeMillis().toString(),
                url = url,
                title = title,
                uploader = uploader,
                quality = quality,
                filePath = filePath,
                fileSize = fileSize,
                success = success,
                error = error,
                timestamp = Date().time
            )
            
            // Add to beginning of list
            history.add(0, downloadEntry)
            
            // Keep only the most recent downloads
            val trimmedHistory = if (history.size > MAX_HISTORY_ITEMS) {
                history.take(MAX_HISTORY_ITEMS)
            } else {
                history
            }
            
            // Save to preferences
            val jsonArray = JSONArray()
            trimmedHistory.forEach { entry ->
                val jsonObject = JSONObject().apply {
                    put("id", entry.id)
                    put("url", entry.url)
                    put("title", entry.title)
                    put("uploader", entry.uploader)
                    put("quality", entry.quality)
                    put("filePath", entry.filePath ?: "")
                    put("fileSize", entry.fileSize)
                    put("success", entry.success)
                    put("error", entry.error ?: "")
                    put("timestamp", entry.timestamp)
                }
                jsonArray.put(jsonObject)
            }
            
            prefs.edit()
                .putString(KEY_HISTORY, jsonArray.toString())
                .apply()
            
            Log.d(TAG, "Added download to history: $title")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error adding download to history", e)
        }
    }
    
    /**
     * Get all download history entries
     * @return List of download history entries, most recent first
     */
    fun getDownloadHistory(): List<DownloadHistoryEntry> {
        return try {
            val historyJson = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
            val jsonArray = JSONArray(historyJson)
            val history = mutableListOf<DownloadHistoryEntry>()
            
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val entry = DownloadHistoryEntry(
                    id = jsonObject.getString("id"),
                    url = jsonObject.getString("url"),
                    title = jsonObject.getString("title"),
                    uploader = jsonObject.getString("uploader"),
                    quality = jsonObject.getString("quality"),
                    filePath = jsonObject.optString("filePath").takeIf { it.isNotEmpty() },
                    fileSize = jsonObject.getLong("fileSize"),
                    success = jsonObject.getBoolean("success"),
                    error = jsonObject.optString("error").takeIf { it.isNotEmpty() },
                    timestamp = jsonObject.getLong("timestamp")
                )
                history.add(entry)
            }
            
            history
        } catch (e: Exception) {
            Log.e(TAG, "Error reading download history", e)
            emptyList()
        }
    }
    
    /**
     * Get successful downloads only
     * @return List of successful download entries
     */
    fun getSuccessfulDownloads(): List<DownloadHistoryEntry> {
        return getDownloadHistory().filter { it.success }
    }
    
    /**
     * Get failed downloads only
     * @return List of failed download entries
     */
    fun getFailedDownloads(): List<DownloadHistoryEntry> {
        return getDownloadHistory().filter { !it.success }
    }
    
    /**
     * Check if a URL was already successfully downloaded
     * @param url URL to check
     * @return DownloadHistoryEntry if found and file still exists, null otherwise
     */
    fun findSuccessfulDownload(url: String): DownloadHistoryEntry? {
        Log.d(TAG, "Searching for existing download of URL: $url")
        val successfulDownloads = getSuccessfulDownloads()
        Log.d(TAG, "Total successful downloads in history: ${successfulDownloads.size}")
        
        successfulDownloads.forEach { entry ->
            Log.d(TAG, "Checking entry: URL=${entry.url}, Title=${entry.title}, FilePath=${entry.filePath}")
        }
        
        val matchingEntry = successfulDownloads.firstOrNull { it.url == url }
        if (matchingEntry != null) {
            Log.d(TAG, "Found matching URL entry: ${matchingEntry.title}")
            if (matchingEntry.filePath != null) {
                val fileExists = checkFileExists(matchingEntry.filePath)
                Log.d(TAG, "File path: ${matchingEntry.filePath}, exists: $fileExists")
                if (fileExists) {
                    Log.d(TAG, "Returning existing download entry")
                    return matchingEntry
                } else {
                    Log.d(TAG, "File no longer exists, ignoring entry")
                }
            } else {
                Log.d(TAG, "File path is null, ignoring entry")
            }
        } else {
            Log.d(TAG, "No matching URL found in history")
        }
        
        return null
    }
    
    /**
     * Check if a file or content URI exists
     * Handles both regular file paths and content:// URIs from MediaStore
     */
    private fun checkFileExists(filePath: String): Boolean {
        return try {
            if (filePath.startsWith("content://")) {
                // Handle content:// URIs by checking if we can query the content resolver
                val uri = android.net.Uri.parse(filePath)
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    cursor.moveToFirst()
                } ?: false
            } else {
                // Handle regular file paths
                java.io.File(filePath).exists()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking file existence: ${e.message}")
            false
        }
    }
    
    /**
     * Remove a download from history
     * @param id Download ID
     * @return true if removed successfully, false otherwise
     */
    fun removeDownload(id: String): Boolean {
        return try {
            val history = getDownloadHistory().toMutableList()
            val removed = history.removeAll { it.id == id }
            
            if (removed) {
                val jsonArray = JSONArray()
                history.forEach { entry ->
                    val jsonObject = JSONObject().apply {
                        put("id", entry.id)
                        put("url", entry.url)
                        put("title", entry.title)
                        put("uploader", entry.uploader)
                        put("quality", entry.quality)
                        put("filePath", entry.filePath ?: "")
                        put("fileSize", entry.fileSize)
                        put("success", entry.success)
                        put("error", entry.error ?: "")
                        put("timestamp", entry.timestamp)
                    }
                    jsonArray.put(jsonObject)
                }
                
                prefs.edit()
                    .putString(KEY_HISTORY, jsonArray.toString())
                    .apply()
                
                Log.d(TAG, "Removed download from history: $id")
            }
            
            removed
        } catch (e: Exception) {
            Log.e(TAG, "Error removing download from history", e)
            false
        }
    }
    
    /**
     * Clear all download history
     */
    fun clearHistory() {
        prefs.edit()
            .remove(KEY_HISTORY)
            .apply()
        Log.d(TAG, "Cleared all download history")
    }
    
    /**
     * Get download statistics
     * @return DownloadStats object with statistics
     */
    fun getDownloadStats(): DownloadStats {
        val history = getDownloadHistory()
        val successful = history.count { it.success }
        val failed = history.count { !it.success }
        val totalSize = history.filter { it.success }.sumOf { it.fileSize }
        
        return DownloadStats(
            totalDownloads = history.size,
            successfulDownloads = successful,
            failedDownloads = failed,
            totalSizeBytes = totalSize
        )
    }
    
    /**
     * Data class for download history entries
     */
    data class DownloadHistoryEntry(
        val id: String,
        val url: String,
        val title: String,
        val uploader: String,
        val quality: String,
        val filePath: String?,
        val fileSize: Long,
        val success: Boolean,
        val error: String?,
        val timestamp: Long
    ) {
        fun getFormattedDate(): String {
            return java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
                .format(Date(timestamp))
        }
        
        fun getFormattedFileSize(): String {
            val kb = fileSize / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            
            return when {
                gb >= 1 -> "%.1f GB".format(gb)
                mb >= 1 -> "%.1f MB".format(mb)
                kb >= 1 -> "%.1f KB".format(kb)
                else -> "$fileSize B"
            }
        }
    }
    
    /**
     * Data class for download statistics
     */
    data class DownloadStats(
        val totalDownloads: Int,
        val successfulDownloads: Int,
        val failedDownloads: Int,
        val totalSizeBytes: Long
    ) {
        fun getFormattedTotalSize(): String {
            val kb = totalSizeBytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            
            return when {
                gb >= 1 -> "%.1f GB".format(gb)
                mb >= 1 -> "%.1f MB".format(mb)
                kb >= 1 -> "%.1f KB".format(kb)
                else -> "$totalSizeBytes B"
            }
        }
    }
}
