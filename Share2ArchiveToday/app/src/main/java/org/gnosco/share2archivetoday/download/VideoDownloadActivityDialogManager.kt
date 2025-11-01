package org.gnosco.share2archivetoday.download

import android.app.Activity
import android.app.AlertDialog
import android.util.Log
import android.widget.Toast
import org.gnosco.share2archivetoday.media.VideoFormatSelector
import org.gnosco.share2archivetoday.utils.FileUtils

/**
 * Manages all dialogs for VideoDownloadActivity
 * Extends BaseVideoDialogManager for common dialog functionality
 */
class VideoDownloadActivityDialogManager(
    activity: Activity,
    private val videoFileHandler: VideoDownloadFileHandler
) : BaseVideoDialogManager(activity) {
    companion object {
        private const val TAG = "VideoDownloadActivityDialogManager"
    }
    
    /**
     * Handle cancel by finishing the activity
     */
    override fun handleCancel() {
        activity.finish()
    }
    
    /**
     * Handle permission cancel with toast and finish
     */
    override fun handlePermissionCancel() {
        Toast.makeText(
            activity,
            "This feature requires accepted permissions.",
            Toast.LENGTH_LONG
        ).show()
        activity.finish()
    }
    
    /**
     * Show already downloaded dialog
     */
    fun showAlreadyDownloadedDialog(
        url: String,
        existingDownload: DownloadHistoryManager.DownloadHistoryEntry,
        onDownloadAgain: () -> Unit
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
                videoFileHandler.shareVideoFile(existingDownload.filePath!!, existingDownload.title)
            }
            .setNeutralButton("Download Again") { _, _ ->
                onDownloadAgain()
            }
            .setNegativeButton("Cancel") { _, _ ->
                activity.finish()
            }
            .setCancelable(false)
            .show()
    }
}

