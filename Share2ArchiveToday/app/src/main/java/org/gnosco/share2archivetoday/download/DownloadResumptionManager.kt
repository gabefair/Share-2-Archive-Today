package org.gnosco.share2archivetoday.download

import org.gnosco.share2archivetoday.download.*

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Manages download state for resumable downloads
 * Tracks partial downloads and enables resumption after interruptions
 */
class DownloadResumptionManager(private val context: Context) {
    
    companion object {
        private const val TAG = "DownloadResumptionManager"
        private const val PREFS_NAME = "download_resumption"
        private const val KEY_ACTIVE_DOWNLOADS = "active_downloads"
        private const val KEY_COMPLETED_DOWNLOADS = "completed_downloads"
        private const val MAX_ACTIVE_DOWNLOADS = 5
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Start tracking a new download
     */
    fun startDownload(downloadId: String, url: String, title: String, quality: String, outputDir: String) {
        val downloadState = DownloadState(
            id = downloadId,
            url = url,
            title = title,
            quality = quality,
            outputDir = outputDir,
            status = DownloadStatus.DOWNLOADING,
            startTime = System.currentTimeMillis(),
            lastUpdateTime = System.currentTimeMillis(),
            partialFilePath = null,
            totalBytes = 0L,
            downloadedBytes = 0L,
            error = null
        )
        
        addActiveDownload(downloadState)
        Log.d(TAG, "Started tracking download: $downloadId")
    }
    
    /**
     * Update download progress
     */
    fun updateProgress(downloadId: String, downloadedBytes: Long, totalBytes: Long, partialFilePath: String?) {
        val activeDownloads = getActiveDownloads()
        val downloadIndex = activeDownloads.indexOfFirst { it.id == downloadId }
        
        if (downloadIndex != -1) {
            val download = activeDownloads[downloadIndex]
            val updatedDownload = download.copy(
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                partialFilePath = partialFilePath,
                lastUpdateTime = System.currentTimeMillis()
            )
            
            activeDownloads[downloadIndex] = updatedDownload
            saveActiveDownloads(activeDownloads)
            
            Log.d(TAG, "Updated progress for $downloadId: ${downloadedBytes}/${totalBytes} bytes")
        }
    }
    
    /**
     * Mark download as completed
     */
    fun completeDownload(downloadId: String, finalFilePath: String, fileSize: Long) {
        val activeDownloads = getActiveDownloads()
        val downloadIndex = activeDownloads.indexOfFirst { it.id == downloadId }
        
        if (downloadIndex != -1) {
            val download = activeDownloads[downloadIndex]
            val completedDownload = download.copy(
                status = DownloadStatus.COMPLETED,
                partialFilePath = finalFilePath,
                downloadedBytes = fileSize,
                totalBytes = fileSize,
                lastUpdateTime = System.currentTimeMillis()
            )
            
            // Move to completed downloads
            addCompletedDownload(completedDownload)
            
            // Remove from active downloads
            activeDownloads.removeAt(downloadIndex)
            saveActiveDownloads(activeDownloads)
            
            Log.d(TAG, "Completed download: $downloadId")
        }
    }
    
    /**
     * Mark download as failed
     */
    fun failDownload(downloadId: String, error: String) {
        val activeDownloads = getActiveDownloads()
        val downloadIndex = activeDownloads.indexOfFirst { it.id == downloadId }
        
        if (downloadIndex != -1) {
            val download = activeDownloads[downloadIndex]
            val failedDownload = download.copy(
                status = DownloadStatus.FAILED,
                error = error,
                lastUpdateTime = System.currentTimeMillis()
            )
            
            // Move to completed downloads (for history)
            addCompletedDownload(failedDownload)
            
            // Remove from active downloads
            activeDownloads.removeAt(downloadIndex)
            saveActiveDownloads(activeDownloads)
            
            Log.d(TAG, "Failed download: $downloadId - $error")
        }
    }
    
    /**
     * Pause a download (for manual pause or network issues)
     */
    fun pauseDownload(downloadId: String) {
        val activeDownloads = getActiveDownloads()
        val downloadIndex = activeDownloads.indexOfFirst { it.id == downloadId }
        
        if (downloadIndex != -1) {
            val download = activeDownloads[downloadIndex]
            val pausedDownload = download.copy(
                status = DownloadStatus.PAUSED,
                lastUpdateTime = System.currentTimeMillis()
            )
            
            activeDownloads[downloadIndex] = pausedDownload
            saveActiveDownloads(activeDownloads)
            
            Log.d(TAG, "Paused download: $downloadId")
        }
    }
    
    /**
     * Resume a paused download
     */
    fun resumeDownload(downloadId: String) {
        val activeDownloads = getActiveDownloads()
        val downloadIndex = activeDownloads.indexOfFirst { it.id == downloadId }
        
        if (downloadIndex != -1) {
            val download = activeDownloads[downloadIndex]
            val resumedDownload = download.copy(
                status = DownloadStatus.DOWNLOADING,
                lastUpdateTime = System.currentTimeMillis()
            )
            
            activeDownloads[downloadIndex] = resumedDownload
            saveActiveDownloads(activeDownloads)
            
            Log.d(TAG, "Resumed download: $downloadId")
        }
    }
    
    /**
     * Get all active downloads
     */
    fun getActiveDownloads(): MutableList<DownloadState> {
        val jsonString = prefs.getString(KEY_ACTIVE_DOWNLOADS, "[]") ?: "[]"
        return try {
            val jsonArray = JSONArray(jsonString)
            val downloads = mutableListOf<DownloadState>()
            
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                downloads.add(DownloadState.fromJson(jsonObject))
            }
            
            downloads
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing active downloads", e)
            mutableListOf()
        }
    }
    
    /**
     * Get all completed downloads
     */
    fun getCompletedDownloads(): List<DownloadState> {
        val jsonString = prefs.getString(KEY_COMPLETED_DOWNLOADS, "[]") ?: "[]"
        return try {
            val jsonArray = JSONArray(jsonString)
            val downloads = mutableListOf<DownloadState>()
            
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                downloads.add(DownloadState.fromJson(jsonObject))
            }
            
            downloads
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing completed downloads", e)
            emptyList()
        }
    }
    
    /**
     * Get downloads that can be resumed (paused or interrupted)
     */
    fun getResumableDownloads(): List<DownloadState> {
        return getActiveDownloads().filter { download ->
            download.status == DownloadStatus.PAUSED || 
            (download.status == DownloadStatus.DOWNLOADING && 
             download.lastUpdateTime < System.currentTimeMillis() - 300000) // 5 minutes ago
        }
    }
    
    /**
     * Check if a download can be resumed
     */
    fun canResumeDownload(downloadId: String): Boolean {
        val activeDownloads = getActiveDownloads()
        val download = activeDownloads.find { it.id == downloadId }
        
        return download != null && (
            download.status == DownloadStatus.PAUSED ||
            (download.status == DownloadStatus.DOWNLOADING && 
             download.lastUpdateTime < System.currentTimeMillis() - 300000)
        )
    }
    
    /**
     * Get partial file path for resumption
     */
    fun getPartialFilePath(downloadId: String): String? {
        val activeDownloads = getActiveDownloads()
        val download = activeDownloads.find { it.id == downloadId }
        return download?.partialFilePath
    }
    
    /**
     * Clean up old completed downloads
     */
    fun cleanupOldDownloads(maxAgeDays: Int = 7) {
        val completedDownloads = getCompletedDownloads().toMutableList()
        val cutoffTime = System.currentTimeMillis() - (maxAgeDays * 24 * 60 * 60 * 1000L)
        
        val filteredDownloads = completedDownloads.filter { download ->
            download.lastUpdateTime > cutoffTime
        }
        
        if (filteredDownloads.size != completedDownloads.size) {
            saveCompletedDownloads(filteredDownloads)
            Log.d(TAG, "Cleaned up ${completedDownloads.size - filteredDownloads.size} old downloads")
        }
    }
    
    /**
     * Cancel a download and clean up partial files
     */
    fun cancelDownload(downloadId: String) {
        val activeDownloads = getActiveDownloads()
        val downloadIndex = activeDownloads.indexOfFirst { it.id == downloadId }
        
        if (downloadIndex != -1) {
            val download = activeDownloads[downloadIndex]
            
            // Clean up partial files
            download.partialFilePath?.let { filePath ->
                try {
                    val file = File(filePath)
                    if (file.exists()) {
                        file.delete()
                        Log.d(TAG, "Deleted partial file: $filePath")
                    } else {
                        Log.d(TAG, "Partial file already deleted: $filePath")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting partial file: $filePath", e)
                }
            }
            
            // Remove from active downloads
            activeDownloads.removeAt(downloadIndex)
            saveActiveDownloads(activeDownloads)
            
            Log.d(TAG, "Cancelled download: $downloadId")
        }
    }
    
    private fun addActiveDownload(download: DownloadState) {
        val activeDownloads = getActiveDownloads()
        
        // Remove any existing download with same ID
        activeDownloads.removeAll { it.id == download.id }
        
        // Add new download
        activeDownloads.add(download)
        
        // Limit number of active downloads
        if (activeDownloads.size > MAX_ACTIVE_DOWNLOADS) {
            activeDownloads.removeAt(0) // Remove oldest
        }
        
        saveActiveDownloads(activeDownloads)
    }
    
    private fun addCompletedDownload(download: DownloadState) {
        val completedDownloads = getCompletedDownloads().toMutableList()
        completedDownloads.add(download)
        
        // Limit completed downloads to prevent storage bloat
        if (completedDownloads.size > 100) {
            completedDownloads.sortByDescending { it.lastUpdateTime }
            while (completedDownloads.size > 100) {
                completedDownloads.removeAt(completedDownloads.size - 1)
            }
        }
        
        saveCompletedDownloads(completedDownloads)
    }
    
    private fun saveActiveDownloads(downloads: List<DownloadState>) {
        try {
            val jsonArray = JSONArray()
            downloads.forEach { download ->
                jsonArray.put(download.toJson())
            }
            
            prefs.edit()
                .putString(KEY_ACTIVE_DOWNLOADS, jsonArray.toString())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving active downloads", e)
        }
    }
    
    private fun saveCompletedDownloads(downloads: List<DownloadState>) {
        try {
            val jsonArray = JSONArray()
            downloads.forEach { download ->
                jsonArray.put(download.toJson())
            }
            
            prefs.edit()
                .putString(KEY_COMPLETED_DOWNLOADS, jsonArray.toString())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving completed downloads", e)
        }
    }
    
    /**
     * Data class representing download state
     */
    data class DownloadState(
        val id: String,
        val url: String,
        val title: String,
        val quality: String,
        val outputDir: String,
        val status: DownloadStatus,
        val startTime: Long,
        val lastUpdateTime: Long,
        val partialFilePath: String?,
        val totalBytes: Long,
        val downloadedBytes: Long,
        val error: String?
    ) {
        fun getProgressPercentage(): Int {
            return if (totalBytes > 0) {
                ((downloadedBytes * 100) / totalBytes).toInt()
            } else {
                0
            }
        }
        
        fun getFormattedProgress(): String {
            return if (totalBytes > 0) {
                "${getProgressPercentage()}% (${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)})"
            } else {
                "${getProgressPercentage()}%"
            }
        }
        
        private fun formatBytes(bytes: Long): String {
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            
            return when {
                gb >= 1 -> "%.1f GB".format(gb)
                mb >= 1 -> "%.1f MB".format(mb)
                kb >= 1 -> "%.1f KB".format(kb)
                else -> "$bytes B"
            }
        }
        
        fun toJson(): JSONObject {
            return JSONObject().apply {
                put("id", id)
                put("url", url)
                put("title", title)
                put("quality", quality)
                put("outputDir", outputDir)
                put("status", status.name)
                put("startTime", startTime)
                put("lastUpdateTime", lastUpdateTime)
                put("partialFilePath", partialFilePath ?: "")
                put("totalBytes", totalBytes)
                put("downloadedBytes", downloadedBytes)
                put("error", error ?: "")
            }
        }
        
        companion object {
            fun fromJson(json: JSONObject): DownloadState {
                return DownloadState(
                    id = json.getString("id"),
                    url = json.getString("url"),
                    title = json.getString("title"),
                    quality = json.getString("quality"),
                    outputDir = json.getString("outputDir"),
                    status = DownloadStatus.valueOf(json.getString("status")),
                    startTime = json.getLong("startTime"),
                    lastUpdateTime = json.getLong("lastUpdateTime"),
                    partialFilePath = json.optString("partialFilePath").takeIf { it.isNotEmpty() },
                    totalBytes = json.getLong("totalBytes"),
                    downloadedBytes = json.getLong("downloadedBytes"),
                    error = json.optString("error").takeIf { it.isNotEmpty() }
                )
            }
        }
    }
    
    /**
     * Update retry status for a download
     */
    fun updateRetryStatus(downloadId: String, retryMessage: String) {
        val activeDownloads = getActiveDownloads()
        val downloadIndex = activeDownloads.indexOfFirst { it.id == downloadId }
        
        if (downloadIndex != -1) {
            val download = activeDownloads[downloadIndex]
            val updatedDownload = download.copy(
                lastUpdateTime = System.currentTimeMillis(),
                error = retryMessage
            )
            
            activeDownloads[downloadIndex] = updatedDownload
            saveActiveDownloads(activeDownloads)
            
            Log.d(TAG, "Updated retry status for $downloadId: $retryMessage")
        }
    }
    
    /**
     * Update download status
     */
    fun updateStatus(downloadId: String, status: DownloadStatus) {
        val activeDownloads = getActiveDownloads()
        val downloadIndex = activeDownloads.indexOfFirst { it.id == downloadId }
        
        if (downloadIndex != -1) {
            val download = activeDownloads[downloadIndex]
            val updatedDownload = download.copy(
                status = status,
                lastUpdateTime = System.currentTimeMillis()
            )
            
            activeDownloads[downloadIndex] = updatedDownload
            saveActiveDownloads(activeDownloads)
            
            Log.d(TAG, "Updated status for $downloadId: $status")
        }
    }
    
    /**
     * Download status enum
     */
    enum class DownloadStatus {
        DOWNLOADING,
        PAUSED,
        PROCESSING,
        COMPLETED,
        FAILED
    }
}
