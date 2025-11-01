package org.gnosco.share2archivetoday.download

import android.app.Activity
import android.app.AlertDialog
import org.gnosco.share2archivetoday.media.VideoFormatSelector
import org.gnosco.share2archivetoday.utils.FileUtils

/**
 * Base class for video download dialog management
 * Consolidates common dialog logic from VideoDownloadDialogManager and VideoDownloadActivityDialogManager
 */
abstract class BaseVideoDialogManager(protected val activity: Activity) {
    
    /**
     * Show quality selection dialog
     */
    fun showQualitySelectionDialog(
        title: String,
        qualityOptions: List<VideoFormatSelector.QualityOption>,
        onQualitySelected: (VideoFormatSelector.QualityOption) -> Unit
    ) {
        val displayOptions = qualityOptions.map { it.displayName }.toTypedArray()
        
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setItems(displayOptions) { _, which ->
                onQualitySelected(qualityOptions[which])
            }
            .setNegativeButton("Cancel") { _, _ ->
                handleCancel()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Show data usage warning dialog
     */
    fun showDataUsageWarning(
        qualityOption: VideoFormatSelector.QualityOption,
        onContinue: () -> Unit
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
                handleCancel()
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
     * Show error dialog
     */
    fun showErrorDialog(title: String, message: String) {
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { _, _ ->
                handleCancel()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Show permission rationale dialog
     */
    fun showPermissionRationale(onGrant: () -> Unit) {
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
                handlePermissionCancel()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Handle cancel action - to be overridden by subclasses
     * Default implementation does nothing, but subclasses can provide specific behavior
     */
    protected abstract fun handleCancel()
    
    /**
     * Handle permission cancel action - to be overridden by subclasses
     * Default implementation delegates to handleCancel, but subclasses can override
     */
    protected open fun handlePermissionCancel() {
        handleCancel()
    }
}

