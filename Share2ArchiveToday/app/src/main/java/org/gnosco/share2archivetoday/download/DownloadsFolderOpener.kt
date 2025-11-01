package org.gnosco.share2archivetoday.download

import android.app.Activity
import android.content.Intent
import android.util.Log
import android.widget.Toast

/**
 * Handles opening the Downloads folder with multiple fallback strategies
 */
class DownloadsFolderOpener(private val activity: Activity) {
    
    companion object {
        private const val TAG = "DownloadsFolderOpener"
    }
    
    /**
     * Open the downloads folder using multiple approaches
     */
    fun openDownloadsFolder() {
        try {
            // Try multiple approaches to open Downloads folder
            
            // Approach 1: Try to open Downloads app directly
            val downloadsAppIntent = Intent().apply {
                action = Intent.ACTION_VIEW
                setDataAndType(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, "*/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            if (downloadsAppIntent.resolveActivity(activity.packageManager) != null) {
                activity.startActivity(downloadsAppIntent)
                return
            }
            
            // Approach 2: Try to open Files app with Downloads folder
            val filesAppIntent = Intent().apply {
                action = Intent.ACTION_VIEW
                setDataAndType(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, "resource/folder")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            if (filesAppIntent.resolveActivity(activity.packageManager) != null) {
                activity.startActivity(filesAppIntent)
                return
            }
            
            // Approach 3: Try generic file manager
            val fileManagerIntent = Intent(Intent.ACTION_VIEW).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("video/*", "audio/*", "image/*", "application/*"))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            if (fileManagerIntent.resolveActivity(activity.packageManager) != null) {
                activity.startActivity(Intent.createChooser(fileManagerIntent, "Open Downloads"))
                return
            }
            
            // Approach 4: Try to open Downloads via Settings
            val settingsIntent = Intent(android.provider.Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            if (settingsIntent.resolveActivity(activity.packageManager) != null) {
                activity.startActivity(settingsIntent)
                Toast.makeText(activity, "Please navigate to Downloads in Settings", Toast.LENGTH_LONG).show()
                return
            }
            
            // Last resort: Show helpful message
            Toast.makeText(
                activity,
                "Please open your device's Downloads app or Files app to view downloaded files",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error opening downloads folder", e)
            Toast.makeText(
                activity,
                "Please open your device's Downloads app to view files",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

