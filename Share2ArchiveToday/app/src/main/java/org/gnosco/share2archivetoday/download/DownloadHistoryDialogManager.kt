package org.gnosco.share2archivetoday.download

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import org.gnosco.share2archivetoday.utils.FileUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages all dialogs for Download History
 */
class DownloadHistoryDialogManager(
    private val activity: Activity,
    private val fileManager: DownloadHistoryFileManager
) {
    
    companion object {
        private const val TAG = "DownloadHistoryDialogManager"
    }
    
    /**
     * Show action dialog for a downloaded item
     */
    fun showDownloadActionDialog(
        item: DownloadHistoryItem,
        onDelete: () -> Unit
    ) {
        val actions = mutableListOf<String>()
        val actionCallbacks = mutableListOf<() -> Unit>()
        
        // Open video file
        actions.add("Open Video")
        actionCallbacks.add { fileManager.openFile(item.filePath!!) }
        
        // Copy source URL
        actions.add("Copy Source URL")
        actionCallbacks.add { copyUrlToClipboard(item.url) }
        
        // Share video
        actions.add("Share Video")
        actionCallbacks.add { fileManager.shareVideo(item) }
        
        // Show download details
        actions.add("View Details")
        actionCallbacks.add { showDownloadDetails(item) }
        
        // Delete this download from history
        actions.add("Delete from History")
        actionCallbacks.add { showDeleteItemDialog(item, onDelete) }
        
        AlertDialog.Builder(activity)
            .setTitle(item.title)
            .setItems(actions.toTypedArray()) { _, which ->
                actionCallbacks[which]()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Show download details dialog
     */
    fun showDownloadDetails(item: DownloadHistoryItem) {
        val message = buildString {
            append("Title: ${item.title}\n\n")
            append("Status: ${if (item.success) "Completed" else "Failed"}\n\n")
            append("Quality: ${item.quality}\n\n")
            
            if (item.fileSize > 0) {
                append("Size: ${FileUtils.formatFileSize(item.fileSize)}\n\n")
            }
            
            if (item.uploader.isNotEmpty() && item.uploader != "Unknown") {
                append("Uploader: ${item.uploader}\n\n")
            }
            
            // Format the download date
            val dateStr = try {
                SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
                    .format(Date(item.timestamp))
            } catch (e: Exception) {
                "Unknown"
            }
            append("Downloaded: $dateStr\n\n")
            
            if (item.filePath != null) {
                val locationText = fileManager.getFriendlyLocationText(item.filePath)
                append("Location: $locationText\n\n")
            }
            
            append("URL: ${item.url}")
        }
        
        AlertDialog.Builder(activity)
            .setTitle("Download Details")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setNeutralButton("Copy URL") { _, _ ->
                copyUrlToClipboard(item.url)
            }
            .show()
    }
    
    /**
     * Show error details dialog
     */
    fun showErrorDetails(item: DownloadHistoryItem) {
        val message = buildString {
            append("Title: ${item.title}\n")
            append("Error: ${item.error ?: "Unknown error"}\n")
            append("URL: ${item.url}")
        }
        
        AlertDialog.Builder(activity)
            .setTitle("Download Failed")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    
    /**
     * Show clear history confirmation dialog
     */
    fun showClearHistoryDialog(onConfirm: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle("Clear Download History")
            .setMessage("Are you sure you want to clear all download history? This will remove all entries from the list.\n\nNote: Downloaded files will NOT be deleted.")
            .setPositiveButton("Clear All") { _, _ ->
                onConfirm()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Show delete item confirmation dialog
     */
    fun showDeleteItemDialog(item: DownloadHistoryItem, onConfirm: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle("Delete from History")
            .setMessage("Remove \"${item.title}\" from download history?\n\nNote: The downloaded file will NOT be deleted.")
            .setPositiveButton("Delete") { _, _ ->
                onConfirm()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Show open downloads folder explanation dialog
     */
    fun showOpenFolderDialog(onConfirm: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle("Open Downloads Folder")
            .setMessage(
                "This will open your device's Downloads folder where your downloaded videos are stored.\n\n" +
                "You may see an 'Open with' dialog asking you to choose which app to use. " +
                "Select your preferred file manager (like Files, Downloads, or your default file browser) and choose 'Always' if you want to set it as default.\n\n" +
                "This will help you easily access your downloaded videos."
            )
            .setPositiveButton("Open Downloads") { _, _ ->
                onConfirm()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Copy URL to clipboard
     */
    private fun copyUrlToClipboard(url: String) {
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Video URL", url)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(activity, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}

