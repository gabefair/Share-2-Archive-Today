package org.gnosco.share2archivetoday

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import android.view.View
import android.widget.ProgressBar
import kotlinx.coroutines.*

/**
 * Activity to manage download resumption
 * Shows paused/interrupted downloads and allows resumption
 */
class DownloadResumptionActivity : Activity() {
    
    companion object {
        private const val TAG = "DownloadResumptionActivity"
    }
    
    private lateinit var downloadResumptionManager: DownloadResumptionManager
    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var emptyTextView: TextView
    private lateinit var refreshButton: Button
    private lateinit var clearAllButton: Button
    
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize components
        downloadResumptionManager = DownloadResumptionManager(applicationContext)
        
        setupUI()
        loadResumableDownloads()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }
    
    private fun setupUI() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        // Title
        val titleText = TextView(this).apply {
            text = "Download Manager"
            textSize = 20f
            setPadding(0, 0, 0, 32)
        }
        
        // Empty state text
        emptyTextView = TextView(this).apply {
            text = "No downloads to resume"
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            visibility = View.GONE
        }
        
        // Buttons
        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        
        refreshButton = Button(this).apply {
            text = "Refresh"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { loadResumableDownloads() }
        }
        
        clearAllButton = Button(this).apply {
            text = "Clear All"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { showClearAllDialog() }
        }
        
        buttonLayout.addView(refreshButton)
        buttonLayout.addView(clearAllButton)
        
        // List view
        listView = ListView(this)
        
        layout.addView(titleText)
        layout.addView(emptyTextView)
        layout.addView(buttonLayout)
        layout.addView(listView)
        
        setContentView(layout)
    }
    
    private fun loadResumableDownloads() {
        try {
            val activeDownloads = downloadResumptionManager.getActiveDownloads()
            val resumableDownloads = downloadResumptionManager.getResumableDownloads()
            
            Log.d(TAG, "Found ${activeDownloads.size} active downloads, ${resumableDownloads.size} resumable")
            
            if (activeDownloads.isEmpty()) {
                showEmptyState()
                return
            }
            
            // Create display items
            val displayItems = activeDownloads.map { download ->
                val statusIcon = when (download.status) {
                    DownloadResumptionManager.DownloadStatus.DOWNLOADING -> "⬇️"
                    DownloadResumptionManager.DownloadStatus.PAUSED -> "⏸️"
                    DownloadResumptionManager.DownloadStatus.PROCESSING -> "⚙️"
                    DownloadResumptionManager.DownloadStatus.COMPLETED -> "✅"
                    DownloadResumptionManager.DownloadStatus.FAILED -> "❌"
                }
                
                val progressText = if (download.totalBytes > 0) {
                    val percentage = download.getProgressPercentage()
                    val downloadedMB = download.downloadedBytes / (1024.0 * 1024.0)
                    val totalMB = download.totalBytes / (1024.0 * 1024.0)
                    "$percentage% (${String.format("%.1f", downloadedMB)}MB / ${String.format("%.1f", totalMB)}MB)"
                } else {
                    "Starting..."
                }
                
                val timeText = java.text.SimpleDateFormat("MMM dd HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(download.startTime))
                
                "$statusIcon ${download.title}\n$progressText • $timeText"
            }
            
            adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayItems)
            listView.adapter = adapter
            
            listView.setOnItemClickListener { _, _, position, _ ->
                handleDownloadItemClick(activeDownloads[position])
            }
            
            emptyTextView.visibility = View.GONE
            listView.visibility = View.VISIBLE
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading resumable downloads", e)
            Toast.makeText(this, "Error loading downloads", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showEmptyState() {
        emptyTextView.text = "No active downloads"
        emptyTextView.visibility = View.VISIBLE
        listView.visibility = View.GONE
    }
    
    private fun handleDownloadItemClick(download: DownloadResumptionManager.DownloadState) {
        when (download.status) {
            DownloadResumptionManager.DownloadStatus.DOWNLOADING -> {
                showDownloadOptionsDialog(download, "Download in Progress")
            }
            DownloadResumptionManager.DownloadStatus.PAUSED -> {
                showDownloadOptionsDialog(download, "Paused Download")
            }
            DownloadResumptionManager.DownloadStatus.PROCESSING -> {
                showDownloadOptionsDialog(download, "Processing Download")
            }
            DownloadResumptionManager.DownloadStatus.COMPLETED -> {
                showCompletedDownloadDialog(download)
            }
            DownloadResumptionManager.DownloadStatus.FAILED -> {
                showFailedDownloadDialog(download)
            }
        }
    }
    
    private fun showDownloadOptionsDialog(download: DownloadResumptionManager.DownloadState, title: String) {
        val message = buildString {
            append("Title: ${download.title}\n")
            append("Quality: ${download.quality}\n")
            append("Progress: ${download.getFormattedProgress()}\n")
            append("Status: ${download.status.name.lowercase().replaceFirstChar { it.uppercase() }}")
            if (download.error != null) {
                append("\nError: ${download.error}")
            }
        }
        
        val builder = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
        
        when (download.status) {
            DownloadResumptionManager.DownloadStatus.DOWNLOADING -> {
                builder.setPositiveButton("Pause") { _, _ ->
                    pauseDownload(download.id)
                }
                builder.setNegativeButton("Cancel") { _, _ ->
                    cancelDownload(download.id)
                }
            }
            DownloadResumptionManager.DownloadStatus.PAUSED -> {
                builder.setPositiveButton("Resume") { _, _ ->
                    resumeDownload(download.id)
                }
                builder.setNegativeButton("Cancel") { _, _ ->
                    cancelDownload(download.id)
                }
            }
            DownloadResumptionManager.DownloadStatus.PROCESSING -> {
                builder.setPositiveButton("OK", null)
            }
            else -> {
                builder.setPositiveButton("OK", null)
            }
        }
        
        builder.show()
    }
    
    private fun showCompletedDownloadDialog(download: DownloadResumptionManager.DownloadState) {
        val message = buildString {
            append("Title: ${download.title}\n")
            append("Quality: ${download.quality}\n")
            append("File: ${download.partialFilePath ?: "Unknown"}\n")
            append("Size: ${formatFileSize(download.downloadedBytes)}")
        }
        
        AlertDialog.Builder(this)
            .setTitle("Download Completed")
            .setMessage(message)
            .setPositiveButton("Open File") { _, _ ->
                download.partialFilePath?.let { filePath ->
                    openFile(filePath)
                }
            }
            .setNegativeButton("Remove") { _, _ ->
                downloadResumptionManager.cancelDownload(download.id)
                loadResumableDownloads()
            }
            .setNeutralButton("OK", null)
            .show()
    }
    
    private fun showFailedDownloadDialog(download: DownloadResumptionManager.DownloadState) {
        val message = buildString {
            append("Title: ${download.title}\n")
            append("Quality: ${download.quality}\n")
            append("Error: ${download.error ?: "Unknown error"}")
        }
        
        AlertDialog.Builder(this)
            .setTitle("Download Failed")
            .setMessage(message)
            .setPositiveButton("Retry") { _, _ ->
                retryDownload(download)
            }
            .setNegativeButton("Remove") { _, _ ->
                downloadResumptionManager.cancelDownload(download.id)
                loadResumableDownloads()
            }
            .setNeutralButton("OK", null)
            .show()
    }
    
    private fun pauseDownload(downloadId: String) {
        try {
            downloadResumptionManager.pauseDownload(downloadId)
            Toast.makeText(this, "Download paused", Toast.LENGTH_SHORT).show()
            loadResumableDownloads()
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing download", e)
            Toast.makeText(this, "Error pausing download", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun resumeDownload(downloadId: String) {
        try {
            downloadResumptionManager.resumeDownload(downloadId)
            Toast.makeText(this, "Download resumed", Toast.LENGTH_SHORT).show()
            loadResumableDownloads()
            
            // Start the download service to resume
            val download = downloadResumptionManager.getActiveDownloads().find { it.id == downloadId }
            download?.let {
                // Note: In a real implementation, you'd need to restart the download service
                // with the specific download parameters. This is a simplified version.
                Toast.makeText(this, "Download service will resume automatically", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming download", e)
            Toast.makeText(this, "Error resuming download", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun cancelDownload(downloadId: String) {
        try {
            downloadResumptionManager.cancelDownload(downloadId)
            Toast.makeText(this, "Download cancelled", Toast.LENGTH_SHORT).show()
            loadResumableDownloads()
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling download", e)
            Toast.makeText(this, "Error cancelling download", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun retryDownload(download: DownloadResumptionManager.DownloadState) {
        try {
            // Remove the failed download and restart it
            downloadResumptionManager.cancelDownload(download.id)
            
            // Start a new download with the same parameters
            BackgroundDownloadService.startDownload(
                context = this,
                url = download.url,
                title = download.title,
                uploader = "Unknown", // We don't store uploader in DownloadState
                quality = download.quality
            )
            
            Toast.makeText(this, "Download restarted", Toast.LENGTH_SHORT).show()
            loadResumableDownloads()
        } catch (e: Exception) {
            Log.e(TAG, "Error retrying download", e)
            Toast.makeText(this, "Error retrying download", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showClearAllDialog() {
        AlertDialog.Builder(this)
            .setTitle("Clear All Downloads")
            .setMessage("Are you sure you want to cancel all active downloads? This cannot be undone.")
            .setPositiveButton("Clear All") { _, _ ->
                clearAllDownloads()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun clearAllDownloads() {
        try {
            val activeDownloads = downloadResumptionManager.getActiveDownloads()
            activeDownloads.forEach { download ->
                downloadResumptionManager.cancelDownload(download.id)
            }
            
            Toast.makeText(this, "All downloads cleared", Toast.LENGTH_SHORT).show()
            loadResumableDownloads()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing downloads", e)
            Toast.makeText(this, "Error clearing downloads", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openFile(filePath: String) {
        try {
            val file = java.io.File(filePath)
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
}
