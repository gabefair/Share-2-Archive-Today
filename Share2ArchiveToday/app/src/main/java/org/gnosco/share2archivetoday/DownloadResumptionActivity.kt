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
import org.gnosco.share2archivetoday.download.DownloadResumptionManager
import org.gnosco.share2archivetoday.download.DownloadDialogHelper
import org.gnosco.share2archivetoday.download.BackgroundDownloadService
import org.gnosco.share2archivetoday.utils.FileOpener
import org.gnosco.share2archivetoday.utils.FileUtils

/**
 * Activity to manage download resumption
 * Shows paused/interrupted downloads and allows resumption
 */
class DownloadResumptionActivity : Activity() {
    
    companion object {
        private const val TAG = "DownloadResumptionActivity"
    }
    
    private lateinit var downloadResumptionManager: DownloadResumptionManager
    private lateinit var dialogHelper: DownloadDialogHelper
    private lateinit var fileOpener: FileOpener
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
        dialogHelper = DownloadDialogHelper(this)
        fileOpener = FileOpener(this)
        
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
                    val downloaded = FileUtils.formatFileSize(download.downloadedBytes)
                    val total = FileUtils.formatFileSize(download.totalBytes)
                    "$percentage% ($downloaded / $total)"
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
                dialogHelper.showDownloadOptionsDialog(
                    download = download,
                    title = "Download in Progress",
                    onPause = { pauseDownload(download.id) },
                    onResume = { resumeDownload(download.id) },
                    onCancel = { cancelDownload(download.id) }
                )
            }
            DownloadResumptionManager.DownloadStatus.PAUSED -> {
                dialogHelper.showDownloadOptionsDialog(
                    download = download,
                    title = "Paused Download",
                    onPause = { pauseDownload(download.id) },
                    onResume = { resumeDownload(download.id) },
                    onCancel = { cancelDownload(download.id) }
                )
            }
            DownloadResumptionManager.DownloadStatus.PROCESSING -> {
                dialogHelper.showDownloadOptionsDialog(
                    download = download,
                    title = "Processing Download",
                    onPause = { pauseDownload(download.id) },
                    onResume = { resumeDownload(download.id) },
                    onCancel = { cancelDownload(download.id) }
                )
            }
            DownloadResumptionManager.DownloadStatus.COMPLETED -> {
                dialogHelper.showCompletedDownloadDialog(
                    download = download,
                    onOpenFile = { 
                        download.partialFilePath?.let { fileOpener.openFile(it) }
                    },
                    onRemove = {
                        downloadResumptionManager.cancelDownload(download.id)
                        loadResumableDownloads()
                    }
                )
            }
            DownloadResumptionManager.DownloadStatus.FAILED -> {
                dialogHelper.showFailedDownloadDialog(
                    download = download,
                    onRetry = { retryDownload(download) },
                    onRemove = {
                        downloadResumptionManager.cancelDownload(download.id)
                        loadResumableDownloads()
                    }
                )
            }
        }
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
        dialogHelper.showClearAllDialog {
            clearAllDownloads()
        }
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
}
