package org.gnosco.share2archivetoday

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ArrayAdapter
import java.io.File

/**
 * Activity to display download history
 * Shows completed, failed, and active downloads
 */
class DownloadHistoryActivity : Activity() {
    
    companion object {
        private const val TAG = "DownloadHistoryActivity"
    }
    
    private lateinit var downloadHistoryManager: DownloadHistoryManager
    private lateinit var downloadResumptionManager: DownloadResumptionManager
    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize components
        downloadHistoryManager = DownloadHistoryManager(applicationContext)
        downloadResumptionManager = DownloadResumptionManager(applicationContext)
        
        // Create simple layout
        listView = ListView(this)
        setContentView(listView)
        
        // Load and display download history
        loadDownloadHistory()
    }
    
    private fun loadDownloadHistory() {
        try {
            val completedDownloads = downloadHistoryManager.getDownloadHistory()
            val activeDownloads = downloadResumptionManager.getActiveDownloads()
            
            val allDownloads = mutableListOf<DownloadHistoryItem>()
            
            // Add active downloads
            activeDownloads.forEach { download ->
                allDownloads.add(
                    DownloadHistoryItem(
                        title = download.title,
                        url = download.url,
                        uploader = "Unknown",
                        quality = download.quality,
                        filePath = download.partialFilePath,
                        fileSize = download.totalBytes,
                        success = download.status == DownloadResumptionManager.DownloadStatus.COMPLETED,
                        error = download.error,
                        timestamp = download.startTime,
                        status = download.status.name
                    )
                )
            }
            
            // Add completed downloads
            completedDownloads.forEach { download: DownloadHistoryManager.DownloadHistoryEntry ->
                allDownloads.add(
                    DownloadHistoryItem(
                        title = download.title,
                        url = download.url,
                        uploader = download.uploader,
                        quality = download.quality,
                        filePath = download.filePath,
                        fileSize = download.fileSize,
                        success = download.success,
                        error = download.error,
                        timestamp = download.timestamp,
                        status = if (download.success) "COMPLETED" else "FAILED"
                    )
                )
            }
            
            // Sort by timestamp (newest first)
            allDownloads.sortByDescending { it.timestamp }
            
            // Create simple string list for ListView
            val displayItems = allDownloads.map { item ->
                "${item.title} - ${item.status} (${item.quality})"
            }
            
            adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayItems)
            listView.adapter = adapter
            
            listView.setOnItemClickListener { _, _, position, _ ->
                handleDownloadItemClick(allDownloads[position])
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading download history", e)
            Toast.makeText(this, "Error loading download history", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun handleDownloadItemClick(item: DownloadHistoryItem) {
        when {
            item.success && item.filePath != null -> {
                // Open downloaded file
                openFile(item.filePath)
            }
            item.status == "DOWNLOADING" || item.status == "PAUSED" -> {
                // Show download details
                showDownloadDetails(item)
            }
            else -> {
                // Show error details
                showErrorDetails(item)
            }
        }
    }
    
    private fun openFile(filePath: String) {
        try {
            val file = File(filePath)
            if (file.exists()) {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    this, packageName + ".fileprovider", file
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, getMimeType(filePath))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "No app found to open this file", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening file", e)
            Toast.makeText(this, "Error opening file", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showDownloadDetails(item: DownloadHistoryItem) {
        val message = buildString {
            append("Title: ${item.title}\n")
            append("Status: ${item.status}\n")
            append("Quality: ${item.quality}\n")
            if (item.fileSize > 0) {
                append("Size: ${formatFileSize(item.fileSize)}\n")
            }
            append("URL: ${item.url}")
        }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Download Details")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun showErrorDetails(item: DownloadHistoryItem) {
        val message = buildString {
            append("Title: ${item.title}\n")
            append("Error: ${item.error ?: "Unknown error"}\n")
            append("URL: ${item.url}")
        }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Download Failed")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun getMimeType(filePath: String): String {
        val extension = filePath.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mp3" -> "audio/mpeg"
            "aac" -> "audio/aac"
            "m4a" -> "audio/mp4"
            else -> "video/*"
        }
    }
    
    private fun formatFileSize(bytes: Long): String {
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
    
    /**
     * Data class for download history items
     */
    data class DownloadHistoryItem(
        val title: String,
        val url: String,
        val uploader: String,
        val quality: String,
        val filePath: String?,
        val fileSize: Long,
        val success: Boolean,
        val error: String?,
        val timestamp: Long,
        val status: String
    )
}

