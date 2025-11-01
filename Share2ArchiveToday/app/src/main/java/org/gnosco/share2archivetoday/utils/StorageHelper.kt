package org.gnosco.share2archivetoday.utils

import org.gnosco.share2archivetoday.utils.*

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import java.io.File

/**
 * Helper class for storage operations
 * Handles download directories and storage space checks
 */
class StorageHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "StorageHelper"
        private const val APP_DIR_NAME = "Share2ArchiveToday"
    }
    
    /**
     * Get the download directory for staging files
     */
    fun getDownloadDirectory(): String {
        // Stage into app-specific external files; we'll move to MediaStore Downloads afterward
        val staging = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val appDownloadsDir = File(staging, APP_DIR_NAME)
        if (!appDownloadsDir.exists()) {
            appDownloadsDir.mkdirs()
        }
        return appDownloadsDir.absolutePath
    }
    
    /**
     * Get available storage space in MB
     */
    fun getAvailableStorageSpace(downloadDir: String): Long {
        return try {
            val stat = StatFs(downloadDir)
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            availableBytes / 1048576L  // Convert to MB
        } catch (e: Exception) {
            Log.e(TAG, "Error checking available storage", e)
            0L
        }
    }
    
    /**
     * Check if there's enough storage space
     */
    fun hasEnoughSpace(downloadDir: String, requiredMB: Long = 50): Boolean {
        val availableMB = getAvailableStorageSpace(downloadDir)
        return availableMB >= requiredMB
    }
    
    /**
     * Clean up partial download files
     */
    fun cleanupPartialDownload(downloadDir: String, title: String) {
        try {
            val dir = File(downloadDir)
            // Clean up .part files and temporary files
            dir.listFiles()?.forEach { file ->
                if (file.name.contains(title, ignoreCase = true) &&
                    (file.name.endsWith(".part") || file.name.endsWith(".ytdl") || file.name.endsWith(".tmp"))) {
                    file.delete()
                    Log.d(TAG, "Deleted partial file: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up partial download", e)
        }
    }
}

