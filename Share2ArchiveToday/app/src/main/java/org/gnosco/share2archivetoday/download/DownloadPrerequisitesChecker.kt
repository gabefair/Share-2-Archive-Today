package org.gnosco.share2archivetoday.download

import org.gnosco.share2archivetoday.network.*

import org.gnosco.share2archivetoday.utils.*

import org.gnosco.share2archivetoday.download.*

import android.content.Context

/**
 * Checks prerequisites before starting a download (network, storage, etc.)
 */
class DownloadPrerequisitesChecker(
    private val context: Context,
    private val networkMonitor: NetworkMonitor,
    private val storageHelper: StorageHelper,
    private val notificationHelper: NotificationHelper,
    private val downloadResumptionManager: DownloadResumptionManager,
    private val downloadHistoryManager: DownloadHistoryManager
) {
    companion object {
        private const val TAG = "PrerequisitesChecker"
        private const val MIN_STORAGE_SPACE_MB = 50
    }

    data class PrerequisiteCheckResult(
        val passed: Boolean,
        val error: String? = null
    )

    /**
     * Check all prerequisites before starting download
     */
    suspend fun checkPrerequisites(
        displayTitle: String,
        displayUploader: String,
        displayQuality: String,
        url: String,
        downloadId: String
    ): PrerequisiteCheckResult {
        // Check network
        val networkCheck = checkNetwork(displayTitle, displayUploader, displayQuality, url, downloadId)
        if (!networkCheck.passed) {
            return networkCheck
        }

        // Check storage space
        val storageCheck = checkStorage(displayTitle)
        if (!storageCheck.passed) {
            return storageCheck
        }

        return PrerequisiteCheckResult(passed = true)
    }

    /**
     * Check network connectivity
     */
    private suspend fun checkNetwork(
        displayTitle: String,
        displayUploader: String,
        displayQuality: String,
        url: String,
        downloadId: String
    ): PrerequisiteCheckResult {
        if (!networkMonitor.isConnected()) {
            val error = "No internet connection. Please check your network settings."
            notificationHelper.updateNotification("No internet connection", displayTitle)
            notificationHelper.showErrorNotification(displayTitle, error)
            
            downloadResumptionManager.failDownload(downloadId, "No internet connection")
            addFailedDownloadToHistory(url, displayTitle, displayUploader, displayQuality, "No internet connection")
            
            return PrerequisiteCheckResult(passed = false, error = error)
        }

        // Warn about data usage if on mobile network
        if (networkMonitor.shouldWarnAboutDataUsage()) {
            notificationHelper.updateNotification(
                "Warning: Using mobile data. Large downloads may consume data.", 
                displayTitle
            )
        }

        return PrerequisiteCheckResult(passed = true)
    }

    /**
     * Check available storage space
     */
    private fun checkStorage(displayTitle: String): PrerequisiteCheckResult {
        val downloadDir = storageHelper.getDownloadDirectory()
        val availableSpaceMB = storageHelper.getAvailableStorageSpace(downloadDir)
        
        if (availableSpaceMB < MIN_STORAGE_SPACE_MB) {
            val error = "Insufficient storage space. Available: ${availableSpaceMB}MB"
            notificationHelper.updateNotification(error, displayTitle)
            notificationHelper.showErrorNotification(displayTitle, error)
            return PrerequisiteCheckResult(passed = false, error = error)
        }

        return PrerequisiteCheckResult(passed = true)
    }

    /**
     * Helper method to add failed download to history
     */
    private fun addFailedDownloadToHistory(
        url: String,
        displayTitle: String,
        displayUploader: String,
        displayQuality: String,
        error: String
    ) {
        downloadHistoryManager.addDownload(
            url = url,
            title = displayTitle,
            uploader = displayUploader,
            quality = displayQuality,
            filePath = null,
            fileSize = 0,
            success = false,
            error = error
        )
    }
}

