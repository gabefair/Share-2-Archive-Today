package org.gnosco.share2archivetoday.download

import org.gnosco.share2archivetoday.PythonVideoDownloader

import org.gnosco.share2archivetoday.network.*

import org.gnosco.share2archivetoday.utils.*

import org.gnosco.share2archivetoday.download.*

import android.content.Context
import android.util.Log

/**
 * Handles the results of download operations (success or failure)
 */
class DownloadResultHandler(
    private val context: Context,
    private val orchestrator: DownloadOrchestrator,
    private val notificationHelper: NotificationHelper,
    private val networkMonitor: NetworkMonitor,
    private val storageHelper: StorageHelper
) {
    companion object {
        private const val TAG = "DownloadResultHandler"
    }

    /**
     * Handle a successful download result
     */
    suspend fun handleSuccessfulDownload(
        result: PythonVideoDownloader.DownloadResult,
        displayTitle: String,
        displayUploader: String,
        displayQuality: String,
        url: String,
        downloadId: String,
        quality: String
    ) {
        notificationHelper.updateNotification("Processing completed file...", displayTitle)

        // Handle separate A/V streams (merge or extract)
        var finalResult = orchestrator.processSeparateStreams(result, displayTitle, quality)

        // Process audio files if needed
        val processedResult = orchestrator.processAudioIfNeeded(finalResult, quality, displayTitle)
        val processedFilePath = processedResult.filePath ?: finalResult.filePath

        // Move to MediaStore
        val uriString = orchestrator.moveToMediaStore(processedFilePath, displayTitle) ?: processedResult.filePath

        // Finalize and add to history
        orchestrator.finalizeSuccessfulDownload(
            url, displayTitle, displayUploader, displayQuality,
            downloadId, uriString, processedResult.fileSize
        )

        // Show completion notification
        val mediaStoreUri = uriString?.let { 
            if (it.startsWith("content://")) android.net.Uri.parse(it) else null 
        }
        notificationHelper.showCompletionNotification(displayTitle, mediaStoreUri, processedResult.filePath)
        
        Log.i(TAG, "Download completed successfully: $displayTitle ($quality) - ${processedResult.fileSize} bytes")
    }

    /**
     * Handle a failed download result
     */
    suspend fun handleFailedDownload(
        result: PythonVideoDownloader.DownloadResult,
        displayTitle: String,
        displayUploader: String,
        displayQuality: String,
        url: String,
        downloadId: String,
        quality: String
    ) {
        Log.e(TAG, "Download failed: ${result.error}")
        logFailureDetails(url, displayTitle, quality, result.error)

        val userFriendlyError = DownloadErrorHandler.convertToUserFriendlyError(result.error ?: "Unknown error")
        notificationHelper.updateNotification("Download failed: $userFriendlyError", displayTitle)

        orchestrator.finalizeFailedDownload(
            url, displayTitle, displayUploader, displayQuality,
            downloadId, userFriendlyError
        )
        
        notificationHelper.showErrorNotification(displayTitle, userFriendlyError)
    }

    /**
     * Handle exceptions during download
     */
    suspend fun handleException(e: Exception, title: String) {
        Log.e(TAG, "Error in background download", e)
        val friendlyError = DownloadErrorHandler.convertToUserFriendlyError(e.message ?: "Unknown error")
        notificationHelper.updateNotification("Download error: $friendlyError", title)
        notificationHelper.showErrorNotification(title, friendlyError)
    }

    /**
     * Log detailed failure information for debugging
     */
    private fun logFailureDetails(url: String, displayTitle: String, quality: String, error: String?) {
        Log.e(TAG, "Download failure details:")
        Log.e(TAG, "  URL: $url")
        Log.e(TAG, "  Title: $displayTitle")
        Log.e(TAG, "  Quality: $quality")
        Log.e(TAG, "  Network connected: ${networkMonitor.isConnected()}")
        Log.e(TAG, "  On WiFi: ${networkMonitor.isWiFiConnected()}")
        Log.e(TAG, "  Free space MB: ${storageHelper.getAvailableStorageSpace(storageHelper.getDownloadDirectory())}")
        Log.e(TAG, "  Error: $error")
    }
}

