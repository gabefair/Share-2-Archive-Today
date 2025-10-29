package org.gnosco.share2archivetoday.download

import org.gnosco.share2archivetoday.PythonVideoDownloader
import org.gnosco.share2archivetoday.MemoryManager
import org.gnosco.share2archivetoday.network.NetworkMonitor
import org.gnosco.share2archivetoday.utils.NotificationHelper
import android.content.Context
import android.util.Log

/**
 * Handles download progress updates and notifications
 */
class ProgressHandler(
    private val context: Context,
    private val notificationHelper: NotificationHelper,
    private val downloadResumptionManager: DownloadResumptionManager,
    private val networkMonitor: NetworkMonitor,
    private val pythonDownloader: PythonVideoDownloader
) {
    companion object {
        private const val TAG = "ProgressHandler"
    }

    /**
     * Handle different types of progress updates
     */
    fun handleProgressUpdate(
        progressInfo: PythonVideoDownloader.ProgressInfo,
        title: String,
        downloadId: String
    ) {
        when (progressInfo) {
            is PythonVideoDownloader.ProgressInfo.Starting -> {
                handleStarting(progressInfo, title, downloadId)
            }

            is PythonVideoDownloader.ProgressInfo.Retrying -> {
                handleRetrying(progressInfo, title, downloadId)
            }

            is PythonVideoDownloader.ProgressInfo.Downloading -> {
                handleDownloading(progressInfo, title, downloadId)
            }

            is PythonVideoDownloader.ProgressInfo.Finished -> {
                handleFinished(progressInfo, title, downloadId)
            }

            is PythonVideoDownloader.ProgressInfo.Error -> {
                handleError(progressInfo, downloadId)
            }

            is PythonVideoDownloader.ProgressInfo.Unknown -> {
                Log.w(TAG, "Unknown progress status")
            }
        }
    }

    /**
     * Handle starting phase
     */
    private fun handleStarting(
        progressInfo: PythonVideoDownloader.ProgressInfo.Starting,
        title: String,
        downloadId: String
    ) {
        notificationHelper.updateNotification("Starting download...", title)
        Log.d(TAG, "Starting: ${progressInfo.title}")
        downloadResumptionManager.updateProgress(downloadId, 0L, 0L, null)
    }

    /**
     * Handle retry attempts
     */
    private fun handleRetrying(
        progressInfo: PythonVideoDownloader.ProgressInfo.Retrying,
        title: String,
        downloadId: String
    ) {
        notificationHelper.updateNotification(progressInfo.message, title)
        Log.d(TAG, "Retrying: ${progressInfo.title} - ${progressInfo.message}")
        downloadResumptionManager.updateRetryStatus(downloadId, progressInfo.message)
    }

    /**
     * Handle active downloading phase
     */
    private fun handleDownloading(
        progressInfo: PythonVideoDownloader.ProgressInfo.Downloading,
        title: String,
        downloadId: String
    ) {
        monitorMemoryDuringDownload()
        
        downloadResumptionManager.updateProgress(
            downloadId, 
            progressInfo.downloadedBytes,
            progressInfo.totalBytes, 
            progressInfo.filename
        )

        val progressText = buildProgressText(progressInfo)
        notificationHelper.updateNotificationWithProgress(progressText, title, progressInfo.percentage)
        Log.d(TAG, "Progress: $progressText")
    }

    /**
     * Handle completion phase
     */
    private fun handleFinished(
        progressInfo: PythonVideoDownloader.ProgressInfo.Finished,
        title: String,
        downloadId: String
    ) {
        notificationHelper.updateNotification("Processing completed file...", title)
        Log.d(TAG, "Finished: ${progressInfo.filename}")
        downloadResumptionManager.updateStatus(downloadId, DownloadResumptionManager.DownloadStatus.PROCESSING)
    }

    /**
     * Handle error during download
     */
    private fun handleError(
        progressInfo: PythonVideoDownloader.ProgressInfo.Error,
        downloadId: String
    ) {
        Log.e(TAG, "Download error: ${progressInfo.error}")
        if (DownloadErrorHandler.shouldRetryDownload(progressInfo.error)) {
            Log.d(TAG, "Attempting automatic retry for error: ${progressInfo.error}")
            downloadResumptionManager.updateRetryStatus(downloadId, "Retrying after error: ${progressInfo.error}")
        } else {
            downloadResumptionManager.failDownload(downloadId, progressInfo.error)
        }
    }

    /**
     * Build progress text with download statistics
     */
    private fun buildProgressText(progressInfo: PythonVideoDownloader.ProgressInfo.Downloading): String {
        return buildString {
            append("${progressInfo.percentage}%")

            if (progressInfo.speedBytesPerSec > 0) {
                append(" • ${progressInfo.getFormattedSpeed()}")
            }

            if (progressInfo.totalBytes > 0) {
                append(" • ${progressInfo.getFormattedDownloaded()} / ${progressInfo.getFormattedTotal()}")
            }

            if (progressInfo.etaSeconds > 0) {
                append(" • ETA: ${progressInfo.getFormattedEta()}")
            }

            if (networkMonitor.shouldWarnAboutDataUsage()) {
                append(" • Mobile Data")
            }
        }
    }

    /**
     * Monitor memory usage during download and clean up if necessary
     */
    private fun monitorMemoryDuringDownload() {
        val memoryStatus = pythonDownloader.getMemoryManager().checkMemoryStatus()
        if (memoryStatus.level == MemoryManager.MemoryLevel.CRITICAL) {
            Log.w(TAG, "Critical memory pressure detected during download")
            notificationHelper.updateNotification("Low memory - optimizing...", "Download in progress")
            pythonDownloader.getMemoryManager().tryFreeMemory()
            pythonDownloader.getMemoryManager().cleanCacheIfNeeded()
        }
    }
}

