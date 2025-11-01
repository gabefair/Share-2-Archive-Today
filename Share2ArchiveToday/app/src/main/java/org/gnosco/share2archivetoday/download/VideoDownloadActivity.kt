package org.gnosco.share2archivetoday.download

import org.gnosco.share2archivetoday.media.*
import org.gnosco.share2archivetoday.network.NetworkMonitor
import org.gnosco.share2archivetoday.PermissionManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.*

/**
 * Activity for downloading videos and media using yt-dlp via Chaquopy
 * This is a lightweight coordinator that delegates to helper classes
 */
class VideoDownloadActivity : Activity() {

    companion object {
        private const val TAG = "VideoDownloadActivity"
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    // Core components
    private lateinit var pythonDownloader: PythonVideoDownloader
    private lateinit var permissionManager: PermissionManager
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var downloadHistoryManager: DownloadHistoryManager
    private lateinit var videoFormatSelector: VideoFormatSelector
    
    // Helper classes for specific concerns
    private lateinit var intentHandler: VideoDownloadIntentHandler
    private lateinit var fileHandler: VideoDownloadFileHandler
    private lateinit var dialogManager: VideoDownloadActivityDialogManager
    private lateinit var downloadInitiator: VideoDownloadInitiator
    
    private val downloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize core components
        pythonDownloader = PythonVideoDownloader(applicationContext)
        permissionManager = PermissionManager(applicationContext)
        networkMonitor = NetworkMonitor(applicationContext)
        downloadHistoryManager = DownloadHistoryManager(applicationContext)
        videoFormatSelector = VideoFormatSelector()
        
        // Initialize helper classes
        intentHandler = VideoDownloadIntentHandler(this)
        fileHandler = VideoDownloadFileHandler(this)
        dialogManager = VideoDownloadActivityDialogManager(this, fileHandler)
        downloadInitiator = VideoDownloadInitiator(
            activity = this,
            pythonDownloader = pythonDownloader,
            downloadHistoryManager = downloadHistoryManager,
            videoFormatSelector = videoFormatSelector,
            networkMonitor = networkMonitor,
            dialogManager = dialogManager,
            downloadScope = downloadScope
        )

        // Test Python functionality
        if (!pythonDownloader.testPythonFunctionality()) {
            Toast.makeText(
                this, 
                "Video download feature not available. Python/yt-dlp initialization failed.", 
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        downloadScope.cancel()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (permissionManager.handlePermissionResult(requestCode, permissions, grantResults)) {
                // Permissions granted, proceed with download
                proceedWithPendingDownload()
            } else {
                // Permissions denied
                Toast.makeText(
                    this,
                    "Permissions required for video downloads. Please grant access to continue.",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    /**
     * Handle share intent
     */
    private fun handleShareIntent(intent: Intent?) {
        val result = intentHandler.processIntent(intent)
        
        when (result) {
            is VideoDownloadIntentHandler.IntentResult.Success -> {
                startDownloadProcess(result.url)
            }
            is VideoDownloadIntentHandler.IntentResult.NoIntent,
            is VideoDownloadIntentHandler.IntentResult.UnsupportedType -> {
                finish()
            }
            else -> {
                result.getUserMessage()?.let { message ->
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
                finish()
            }
        }
    }

    /**
     * Start the download process for a URL
     */
    private fun startDownloadProcess(url: String) {
        // Check permissions first
        if (permissionManager.hasAllPermissions()) {
            downloadInitiator.initiateDownload(url)
        } else {
            // Request permissions
            if (permissionManager.requestPermissionsIfNeeded(this)) {
                downloadInitiator.initiateDownload(url)
            } else {
                // Show rationale if needed
                if (permissionManager.shouldShowRationale(this)) {
                    dialogManager.showPermissionRationale {
                        permissionManager.requestPermissionsIfNeeded(this)
                    }
                }
                // Store URL for later use after permission grant
                storePendingDownload(url)
            }
        }
    }

    /**
     * Proceed with download after permissions granted
     */
    private fun proceedWithPendingDownload() {
        val downloadUrl = getPendingDownload()
        if (downloadUrl != null) {
            downloadInitiator.initiateDownload(downloadUrl)
        } else {
            Toast.makeText(this, "No URL to download", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    /**
     * Store URL while waiting for permissions
     */
    private fun storePendingDownload(url: String) {
        val prefs = getSharedPreferences("video_download_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("pending_download_url", url).apply()
    }

    /**
     * Get pending URL after permissions granted
     */
    private fun getPendingDownload(): String? {
        val prefs = getSharedPreferences("video_download_prefs", Context.MODE_PRIVATE)
        val url = prefs.getString("pending_download_url", null)
        prefs.edit().remove("pending_download_url").apply()
        return url
    }
}

