package org.gnosco.share2archivetoday.utils

import org.gnosco.share2archivetoday.utils.*

import android.annotation.TargetApi
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File

/**
 * Helper class for MediaStore operations
 * Handles moving files to MediaStore Downloads and legacy storage
 */
class MediaStoreHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "MediaStoreHelper"
    }
    
    private val contentResolver: ContentResolver = context.contentResolver
    
    /**
     * Move downloaded file from app-specific dir to MediaStore Downloads
     * Supports both modern scoped storage (API 29+) and legacy storage (API 28-)
     */
    fun moveToMediaStore(filePath: String, title: String): Uri? {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "File does not exist: $filePath")
                return null
            }

            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                moveToMediaStoreModern(file, title)
            } else {
                moveToMediaStoreLegacy(file, title)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error moving to MediaStore", e)
            return null
        }
    }
    
    /**
     * Get actual file path from MediaStore URI
     */
    fun getFilePathFromMediaStoreUri(uri: Uri): String? {
        try {
            val projection = arrayOf(MediaStore.MediaColumns.DATA)
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                    val path = cursor.getString(columnIndex)
                    Log.d(TAG, "Resolved MediaStore URI $uri to path: $path")
                    return path
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file path from MediaStore URI", e)
        }
        return null
    }
    
    @TargetApi(Build.VERSION_CODES.Q)
    private fun moveToMediaStoreModern(file: File, title: String): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, FileUtils.getMimeType(file.absolutePath))
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/Share2ArchiveToday")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            put(MediaStore.MediaColumns.TITLE, title)
            put(MediaStore.MediaColumns.DATE_ADDED, System.currentTimeMillis() / 1000)
        }

        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let { contentUri ->
            // Copy file to MediaStore
            contentResolver.openOutputStream(contentUri)?.use { outputStream ->
                file.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            // Mark as complete
            val updateValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            contentResolver.update(contentUri, updateValues, null, null)

            // Delete staging file
            file.delete()

            Log.d(TAG, "File moved to MediaStore (modern): $contentUri")
            return contentUri
        }

        return null
    }
    
    private fun moveToMediaStoreLegacy(file: File, title: String): Uri? {
        // For SDK 28 and below, copy to public Downloads directory
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val appDir = File(downloadsDir, "Share2ArchiveToday")

        // Create directory if it doesn't exist
        if (!appDir.exists()) {
            appDir.mkdirs()
        }

        val destFile = File(appDir, file.name)

        // Copy file to public Downloads
        return try {
            file.copyTo(destFile, overwrite = true)
            file.delete() // Remove staging file

            Log.d(TAG, "File moved to public Downloads (legacy): ${destFile.absolutePath}")

            // Add to MediaStore for gallery scanning
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DATA, destFile.absolutePath)
                put(MediaStore.MediaColumns.DISPLAY_NAME, destFile.name)
                put(MediaStore.MediaColumns.MIME_TYPE, FileUtils.getMimeType(destFile.absolutePath))
                put(MediaStore.MediaColumns.TITLE, title)
                put(MediaStore.MediaColumns.DATE_ADDED, System.currentTimeMillis() / 1000)
                put(MediaStore.MediaColumns.SIZE, destFile.length())
            }

            // Use the general Files URI for SDK 28
            val uri = contentResolver.insert(
                MediaStore.Files.getContentUri("external"),
                contentValues
            )

            // Trigger media scan for immediate visibility
            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            mediaScanIntent.data = Uri.fromFile(destFile)
            context.sendBroadcast(mediaScanIntent)

            uri ?: Uri.fromFile(destFile)

        } catch (e: Exception) {
            Log.e(TAG, "Error copying file to public Downloads", e)
            null
        }
    }
}

