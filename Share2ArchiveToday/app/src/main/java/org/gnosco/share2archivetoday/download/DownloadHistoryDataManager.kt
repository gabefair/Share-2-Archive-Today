package org.gnosco.share2archivetoday.download

import android.content.Context
import android.util.Log
import android.widget.Toast

/**
 * Manages download history data operations
 */
class DownloadHistoryDataManager(
    private val context: Context,
    private val downloadHistoryManager: DownloadHistoryManager,
    private val downloadResumptionManager: DownloadResumptionManager
) {
    
    companion object {
        private const val TAG = "DownloadHistoryDataManager"
    }
    
    /**
     * Load and merge download history from multiple sources
     */
    fun loadDownloadHistory(): List<DownloadHistoryItem> {
        try {
            val completedDownloads = downloadHistoryManager.getDownloadHistory()
            val activeDownloads = downloadResumptionManager.getActiveDownloads()
            
            // Create a map to deduplicate downloads by URL
            val downloadMap = mutableMapOf<String, DownloadHistoryItem>()
            
            // Add completed downloads from history manager
            completedDownloads.forEach { download: DownloadHistoryManager.DownloadHistoryEntry ->
                if (download.success) {  // Only show successful downloads
                    downloadMap[download.url] = DownloadHistoryItem(
                        title = download.title,
                        url = download.url,
                        uploader = download.uploader,
                        quality = download.quality,
                        filePath = download.filePath,
                        fileSize = download.fileSize,
                        success = true,
                        error = null,
                        timestamp = download.timestamp,
                        status = "COMPLETED"
                    )
                }
            }
            
            // Add completed downloads from resumption manager (only if not already in history)
            activeDownloads.forEach { download ->
                if (download.status == DownloadResumptionManager.DownloadStatus.COMPLETED && 
                    !downloadMap.containsKey(download.url)) {
                    downloadMap[download.url] = DownloadHistoryItem(
                        title = download.title,
                        url = download.url,
                        uploader = "Unknown",
                        quality = download.quality,
                        filePath = download.partialFilePath,
                        fileSize = download.totalBytes,
                        success = true,
                        error = null,
                        timestamp = download.startTime,
                        status = "COMPLETED"
                    )
                }
            }
            
            // Convert map to list and sort by timestamp (newest first)
            return downloadMap.values.sortedByDescending { it.timestamp }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading download history", e)
            Toast.makeText(context, "Error loading download history", Toast.LENGTH_SHORT).show()
            return emptyList()
        }
    }
    
    /**
     * Clear all download history
     */
    fun clearAllHistory() {
        downloadHistoryManager.clearHistory()
        downloadResumptionManager.cleanupOldDownloads(0) // Clear all
        Toast.makeText(context, "Download history cleared", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Delete a single history item
     */
    fun deleteHistoryItem(item: DownloadHistoryItem): Boolean {
        return try {
            // Remove from history manager by URL
            val history = downloadHistoryManager.getDownloadHistory().toMutableList()
            val removed = history.removeAll { it.url == item.url && it.timestamp == item.timestamp }
            
            if (removed) {
                // Save updated history
                downloadHistoryManager.clearHistory()
                history.forEach { entry ->
                    downloadHistoryManager.addDownload(
                        url = entry.url,
                        title = entry.title,
                        uploader = entry.uploader,
                        quality = entry.quality,
                        filePath = entry.filePath,
                        fileSize = entry.fileSize,
                        success = entry.success,
                        error = entry.error
                    )
                }
                
                Toast.makeText(context, "Removed from history", Toast.LENGTH_SHORT).show()
                true
            } else {
                Toast.makeText(context, "Item not found in history", Toast.LENGTH_SHORT).show()
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting history item", e)
            Toast.makeText(context, "Error removing item from history", Toast.LENGTH_SHORT).show()
            false
        }
    }
}

