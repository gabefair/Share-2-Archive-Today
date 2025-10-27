package org.gnosco.share2archivetoday.download

import org.gnosco.share2archivetoday.PythonVideoDownloader
import android.app.Activity
import android.app.AlertDialog
import org.gnosco.share2archivetoday.media.VideoFormatSelector
import org.gnosco.share2archivetoday.network.NetworkMonitor
import org.gnosco.share2archivetoday.utils.ErrorMessageParser
import org.gnosco.share2archivetoday.utils.FileUtils

/**
 * Manages dialog creation and display for video downloads
 */
class VideoDownloadDialogManager(
    private val activity: Activity,
    private val networkMonitor: NetworkMonitor
) {
    
    /**
     * Show quality selection dialog
     */
    fun showQualitySelectionDialog(
        videoInfo: PythonVideoDownloader.VideoInfo,
        qualityOptions: List<VideoFormatSelector.QualityOption>,
        onQualitySelected: (VideoFormatSelector.QualityOption) -> Unit,
        onCancel: () -> Unit
    ) {
        val networkRecommendation = getNetworkBasedRecommendation()
        
        val dialogTitle = buildString {
            append("Select Download Quality")
            append("\nDuration: ${FileUtils.formatDuration(videoInfo.duration.toLong())}")
            append("\nUploader: ${videoInfo.uploader}")
            append("\nExtractor: ${videoInfo.extractor ?: "unknown"}")
            if (networkRecommendation.isNotEmpty()) {
                append("\nNetwork: $networkRecommendation")
            }
        }
        
        val displayOptions = qualityOptions.map { it.displayName }.toTypedArray()
        
        AlertDialog.Builder(activity)
            .setTitle(dialogTitle)
            .setItems(displayOptions) { _, which ->
                onQualitySelected(qualityOptions[which])
            }
            .setNegativeButton("Cancel") { _, _ ->
                onCancel()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Show data usage warning dialog
     */
    fun showDataUsageWarning(
        qualityOption: VideoFormatSelector.QualityOption,
        onContinue: () -> Unit,
        onCancel: () -> Unit
    ) {
        val estimatedSize = FileUtils.formatFileSize(qualityOption.estimatedSize)
        
        AlertDialog.Builder(activity)
            .setTitle("Mobile Data Usage Warning")
            .setMessage(
                "You're using mobile data. This download may use approximately $estimatedSize.\n\n" +
                        "The video will be downloaded in its original format and quality.\n" +
                        "Consider using WiFi for large downloads to avoid data charges.\n\n" +
                        "Do you want to continue?"
            )
            .setPositiveButton("Continue") { _, _ ->
                onContinue()
            }
            .setNegativeButton("Cancel") { _, _ ->
                onCancel()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Show already downloaded dialog
     */
    fun showAlreadyDownloadedDialog(
        existingDownload: DownloadHistoryManager.DownloadHistoryEntry,
        onOpen: () -> Unit,
        onDownloadAgain: () -> Unit,
        onCancel: () -> Unit
    ) {
        val message = buildString {
            append("This video was already downloaded\n\n")
            append("Title: ${existingDownload.title}\n")
            append("Quality: ${existingDownload.quality}\n")
            append("Size: ${existingDownload.getFormattedFileSize()}\n")
            append("Downloaded: ${existingDownload.getFormattedDate()}")
        }
        
        AlertDialog.Builder(activity)
            .setTitle("Already Downloaded")
            .setMessage(message)
            .setPositiveButton("Open Video") { _, _ ->
                onOpen()
            }
            .setNeutralButton("Download Again") { _, _ ->
                onDownloadAgain()
            }
            .setNegativeButton("Cancel") { _, _ ->
                onCancel()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Show error dialog
     */
    fun showErrorDialog(title: String, message: String, onDismiss: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { _, _ ->
                onDismiss()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Show loading dialog
     */
    fun showLoadingDialog(title: String, message: String): AlertDialog {
        return AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .create()
            .also { it.show() }
    }
    
    /**
     * Show permission rationale dialog
     */
    fun showPermissionRationale(
        onGrant: () -> Unit,
        onCancel: () -> Unit
    ) {
        AlertDialog.Builder(activity)
            .setTitle("Permissions Required")
            .setMessage(
                "To archive videos, this app needs:\n\n" +
                        "• Network Access: Required to archive videos from the internet\n\n" +
                        "• Storage Access: Required to save archived videos to your Downloads folder\n\n" +
                        "• Notifications: To show archive progress and completion\n\n" +
                        "Your privacy is important: This app only archives the files you explicitly request " +
                        "and does no other network traffic."
            )
            .setPositiveButton("Grant Permissions") { _, _ ->
                onGrant()
            }
            .setNegativeButton("Cancel") { _, _ ->
                onCancel()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Get network-based quality recommendation
     */
    private fun getNetworkBasedRecommendation(): String {
        return when {
            networkMonitor.isWiFiConnected() -> "WiFi - Good for original quality"
            networkMonitor.isConnected() -> "Mobile Data - Consider lower resolution"
            else -> "No connection"
        }
    }
}

