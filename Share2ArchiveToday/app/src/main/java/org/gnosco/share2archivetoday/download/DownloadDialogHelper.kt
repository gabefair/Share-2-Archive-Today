package org.gnosco.share2archivetoday.download

import org.gnosco.share2archivetoday.utils.*

import org.gnosco.share2archivetoday.download.*

import android.app.AlertDialog
import android.content.Context

/**
 * Helper class for creating download-related dialogs
 */
class DownloadDialogHelper(private val context: Context) {
    
    /**
     * Show dialog for downloads in progress or paused
     */
    fun showDownloadOptionsDialog(
        download: DownloadResumptionManager.DownloadState,
        title: String,
        onPause: () -> Unit,
        onResume: () -> Unit,
        onCancel: () -> Unit
    ) {
        val message = buildDownloadMessage(download)
        
        val builder = AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
        
        when (download.status) {
            DownloadResumptionManager.DownloadStatus.DOWNLOADING -> {
                builder.setPositiveButton("Pause") { _, _ -> onPause() }
                builder.setNegativeButton("Cancel") { _, _ -> onCancel() }
            }
            DownloadResumptionManager.DownloadStatus.PAUSED -> {
                builder.setPositiveButton("Resume") { _, _ -> onResume() }
                builder.setNegativeButton("Cancel") { _, _ -> onCancel() }
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
    
    /**
     * Show dialog for completed downloads
     */
    fun showCompletedDownloadDialog(
        download: DownloadResumptionManager.DownloadState,
        onOpenFile: () -> Unit,
        onRemove: () -> Unit
    ) {
        val message = buildString {
            append("Title: ${download.title}\n")
            append("Quality: ${download.quality}\n")
            append("File: ${download.partialFilePath ?: "Unknown"}\n")
            append("Size: ${FileUtils.formatFileSize(download.downloadedBytes)}")
        }
        
        AlertDialog.Builder(context)
            .setTitle("Download Completed")
            .setMessage(message)
            .setPositiveButton("Open File") { _, _ -> onOpenFile() }
            .setNegativeButton("Remove") { _, _ -> onRemove() }
            .setNeutralButton("OK", null)
            .show()
    }
    
    /**
     * Show dialog for failed downloads
     */
    fun showFailedDownloadDialog(
        download: DownloadResumptionManager.DownloadState,
        onRetry: () -> Unit,
        onRemove: () -> Unit
    ) {
        val message = buildString {
            append("Title: ${download.title}\n")
            append("Quality: ${download.quality}\n")
            append("Error: ${download.error ?: "Unknown error"}")
        }
        
        AlertDialog.Builder(context)
            .setTitle("Download Failed")
            .setMessage(message)
            .setPositiveButton("Retry") { _, _ -> onRetry() }
            .setNegativeButton("Remove") { _, _ -> onRemove() }
            .setNeutralButton("OK", null)
            .show()
    }
    
    /**
     * Show confirmation dialog for clearing all downloads
     */
    fun showClearAllDialog(onConfirm: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle("Clear All Downloads")
            .setMessage("Are you sure you want to cancel all active downloads? This cannot be undone.")
            .setPositiveButton("Clear All") { _, _ -> onConfirm() }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Build message for download info
     */
    private fun buildDownloadMessage(download: DownloadResumptionManager.DownloadState): String {
        return buildString {
            append("Title: ${download.title}\n")
            append("Quality: ${download.quality}\n")
            append("Progress: ${download.getFormattedProgress()}\n")
            append("Status: ${download.status.name.lowercase().replaceFirstChar { it.uppercase() }}")
            if (download.error != null) {
                append("\nError: ${download.error}")
            }
        }
    }
}

