package org.gnosco.share2archivetoday.download

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import org.gnosco.share2archivetoday.utils.VideoFileOperationsHelper
import java.io.File

/**
 * Manages file operations for Download History
 */
class DownloadHistoryFileManager(private val activity: Activity) {
    
    companion object {
        private const val TAG = "DownloadHistoryFileManager"
    }
    
    private val fileOperationsHelper = VideoFileOperationsHelper(activity)
    
    /**
     * Open a file with the appropriate app
     */
    fun openFile(filePath: String) {
        fileOperationsHelper.openVideo(filePath)
    }
    
    /**
     * Share a video file
     */
    fun shareVideo(item: DownloadHistoryItem) {
        if (item.filePath != null) {
            fileOperationsHelper.shareVideo(
                filePath = item.filePath,
                title = item.title,
                excludeActivity = VideoDownloadActivity::class.java
            )
        } else {
            Toast.makeText(activity, "No file path available", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Parse a file path or URI string into a proper Uri object
     */
    fun parseFilePathOrUri(pathOrUri: String): Uri {
        return when {
            pathOrUri.startsWith("content://") -> Uri.parse(pathOrUri)
            pathOrUri.startsWith("file://") -> Uri.parse(pathOrUri)
            else -> {
                // Legacy file path - convert to FileProvider URI
                val file = File(pathOrUri)
                if (file.exists()) {
                    androidx.core.content.FileProvider.getUriForFile(
                        activity,
                        "${activity.packageName}.fileprovider",
                        file
                    )
                } else {
                    // If file doesn't exist, still try to parse as URI
                    Uri.parse(pathOrUri)
                }
            }
        }
    }
    
    /**
     * Check if a URI points to an existing file
     */
    fun checkUriExists(uri: Uri): Boolean {
        return try {
            when (uri.scheme) {
                "content" -> {
                    // For content URIs, try to open an input stream
                    activity.contentResolver.openInputStream(uri)?.use { true } ?: false
                }
                "file" -> {
                    // For file URIs, check if file exists
                    File(uri.path ?: "").exists()
                }
                else -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if URI exists: $uri", e)
            false
        }
    }
    
    /**
     * Get display name from URI
     */
    fun getDisplayNameFromUri(uri: Uri): String? {
        return try {
            when (uri.scheme) {
                "content" -> {
                    val projection = arrayOf(
                        android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                        android.provider.MediaStore.MediaColumns.TITLE
                    )
                    activity.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val displayNameIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                            val titleIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.TITLE)
                            
                            // Try DISPLAY_NAME first, then TITLE
                            val displayName = if (displayNameIndex >= 0) {
                                cursor.getString(displayNameIndex)
                            } else null
                            
                            val title = if (titleIndex >= 0) {
                                cursor.getString(titleIndex)
                            } else null
                            
                            displayName ?: title
                        } else null
                    }
                }
                "file" -> {
                    // For file URIs, extract filename from path
                    File(uri.path ?: "").name
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting display name from URI: $uri", e)
            null
        }
    }
    
    /**
     * Get MIME type from a URI
     */
    fun getMimeTypeFromUri(uri: Uri): String {
        return try {
            when (uri.scheme) {
                "content" -> {
                    // Query ContentResolver for MIME type
                    val mimeType = activity.contentResolver.getType(uri)
                    if (!mimeType.isNullOrEmpty() && mimeType != "video/*") {
                        mimeType
                    } else {
                        // If MediaStore returned generic video/*, try to get better info
                        getMimeTypeFromMediaStore(uri) ?: guessMimeTypeFromUri(uri)
                    }
                }
                else -> guessMimeTypeFromUri(uri)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting MIME type for URI: $uri", e)
            "video/mp4" // Better fallback than video/*
        }
    }
    
    /**
     * Get MIME type from MediaStore metadata
     */
    private fun getMimeTypeFromMediaStore(uri: Uri): String? {
        return try {
            val projection = arrayOf(
                android.provider.MediaStore.MediaColumns.MIME_TYPE,
                android.provider.MediaStore.MediaColumns.DISPLAY_NAME
            )
            activity.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val mimeTypeIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.MIME_TYPE)
                    val displayNameIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                    
                    // Try MIME_TYPE first
                    if (mimeTypeIndex >= 0) {
                        val mimeType = cursor.getString(mimeTypeIndex)
                        if (!mimeType.isNullOrEmpty() && mimeType != "video/*") {
                            return mimeType
                        }
                    }
                    
                    // Fallback to guessing from display name
                    if (displayNameIndex >= 0) {
                        val displayName = cursor.getString(displayNameIndex)
                        if (!displayName.isNullOrEmpty()) {
                            val extension = displayName.substringAfterLast('.', "").lowercase()
                            return when (extension) {
                                "mp4" -> "video/mp4"
                                "webm" -> "video/webm"
                                "mkv" -> "video/x-matroska"
                                "avi" -> "video/x-msvideo"
                                "mov" -> "video/quicktime"
                                "mp3" -> "audio/mpeg"
                                "aac" -> "audio/aac"
                                "m4a" -> "audio/mp4"
                                "wav" -> "audio/wav"
                                "ogg" -> "audio/ogg"
                                "opus" -> "audio/opus"
                                else -> null
                            }
                        }
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting MIME type from MediaStore: $uri", e)
            null
        }
    }
    
    /**
     * Guess MIME type from URI string
     */
    private fun guessMimeTypeFromUri(uri: Uri): String {
        val uriString = uri.toString()
        val extension = uriString.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mp3" -> "audio/mpeg"
            "aac" -> "audio/aac"
            "m4a" -> "audio/mp4"
            "ogg" -> "audio/ogg"
            "opus" -> "audio/opus"
            else -> "video/mp4" // Better fallback than video/*
        }
    }
    
    /**
     * Get user-friendly location text from file path or URI
     */
    fun getFriendlyLocationText(filePath: String): String {
        return try {
            when {
                filePath.startsWith("content://media/external/downloads/") -> {
                    "Downloads folder"
                }
                filePath.startsWith("content://") -> {
                    "Downloads folder"
                }
                filePath.contains("/Download/") || filePath.contains("/Downloads/") -> {
                    "Downloads folder"
                }
                else -> {
                    // For legacy file paths, try to extract meaningful location
                    val path = filePath.substringAfterLast("/", filePath)
                    if (path.contains("Download")) {
                        "Downloads folder"
                    } else {
                        "Device storage"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting friendly location text for: $filePath", e)
            "Downloads folder"
        }
    }
}

