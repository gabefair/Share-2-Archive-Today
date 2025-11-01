package org.gnosco.share2archivetoday.download

import org.gnosco.share2archivetoday.download.PythonVideoDownloader
import android.app.Activity
import android.app.AlertDialog
import org.gnosco.share2archivetoday.media.VideoFormatSelector
import org.gnosco.share2archivetoday.network.NetworkMonitor
import org.gnosco.share2archivetoday.utils.ErrorMessageParser
import org.gnosco.share2archivetoday.utils.FileUtils

/**
 * Manages dialog creation and display for video downloads
 * Extends BaseVideoDialogManager for common dialog functionality
 */
class VideoDownloadDialogManager(
    activity: Activity,
    private val networkMonitor: NetworkMonitor
) : BaseVideoDialogManager(activity) {
    
    private var currentCancelCallback: (() -> Unit)? = null
    
    /**
     * Handle cancel by calling the current callback if set
     */
    override fun handleCancel() {
        currentCancelCallback?.invoke()
    }
    
    /**
     * Show quality selection dialog with video info
     */
    fun showQualitySelectionDialog(
        videoInfo: PythonVideoDownloader.VideoInfo,
        qualityOptions: List<VideoFormatSelector.QualityOption>,
        onQualitySelected: (VideoFormatSelector.QualityOption) -> Unit,
        onCancel: () -> Unit
    ) {
        currentCancelCallback = onCancel
        
        val networkRecommendation = networkMonitor.getRecommendationText()
        
        val dialogTitle = buildString {
            append("Select Download Quality")
            append("\nDuration: ${FileUtils.formatDuration(videoInfo.duration.toLong())}")
            append("\nUploader: ${videoInfo.uploader}")
            append("\nExtractor: ${videoInfo.extractor ?: "unknown"}")
            if (networkRecommendation.isNotEmpty()) {
                append("\nNetwork: $networkRecommendation")
            }
        }
        
        // Use base class method with formatted title
        showQualitySelectionDialog(dialogTitle, qualityOptions, onQualitySelected)
    }
    
    /**
     * Show data usage warning dialog with cancel callback
     */
    fun showDataUsageWarning(
        qualityOption: VideoFormatSelector.QualityOption,
        onContinue: () -> Unit,
        onCancel: () -> Unit
    ) {
        currentCancelCallback = onCancel
        // Use base class method
        showDataUsageWarning(qualityOption, onContinue)
    }
    
    /**
     * Show already downloaded dialog
     */
    fun showAlreadyDownloadedDialog(
        existingDownload: DownloadHistoryManager.DownloadHistoryEntry,
        onShare: () -> Unit,
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
            .setPositiveButton("Share Video") { _, _ ->
                onShare()
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
     * Show error dialog with custom dismiss callback
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
     * Show permission rationale dialog with custom cancel callback
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
}

