package org.gnosco.share2archivetoday.utils

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Consolidated helper class for video file operations (sharing and opening)
 * Centralizes duplicate logic from VideoDownloadCoordinator, VideoDownloadFileHandler, 
 * and DownloadHistoryFileManager
 */
class VideoFileOperationsHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "VideoFileOpsHelper"
    }
    
    /**
     * Share a video file with optional activity exclusion
     * @param filePath The file path or content URI
     * @param title The title to include in the share
     * @param excludeActivity Optional activity class to exclude from the chooser (e.g., VideoDownloadActivity)
     * @param onComplete Optional callback after share intent is launched
     * @param onError Optional callback if an error occurs
     */
    fun shareVideo(
        filePath: String,
        title: String,
        excludeActivity: Class<*>? = null,
        onComplete: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        try {
            val uri = parseFilePathToUri(filePath)
            
            if (uri == null || !checkUriExists(uri)) {
                val errorMsg = "Video file no longer exists"
                Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                onError?.invoke(errorMsg)
                return
            }
            
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "Shared video: $title")
                putExtra(Intent.EXTRA_SUBJECT, title)
                type = FileUtils.getMimeType(filePath)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                
                // Use ClipData to provide better metadata for sharing
                val clipData = android.content.ClipData.newUri(context.contentResolver, title, uri)
                setClipData(clipData)
            }
            
            // Create chooser intent with optional activity exclusion
            val chooserIntent = Intent.createChooser(shareIntent, "Share Video")
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            // Exclude specified activity from the share chooser (if provided and API level supports it)
            if (excludeActivity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val componentToExclude = ComponentName(context, excludeActivity)
                chooserIntent.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(componentToExclude))
            }
            
            if (chooserIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(chooserIntent)
                onComplete?.invoke()
            } else {
                val errorMsg = "No app found to share this file"
                Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                onError?.invoke(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing video", e)
            val errorMsg = "Error sharing video: ${e.message}"
            Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
            onError?.invoke(errorMsg)
        }
    }
    
    /**
     * Open a video file with the default video player
     * @param filePath The file path or content URI
     * @param onComplete Optional callback after video player is launched
     * @param onError Optional callback if an error occurs
     */
    fun openVideo(
        filePath: String,
        onComplete: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        try {
            val uri = parseFilePathToUri(filePath)
            
            if (uri == null || !checkUriExists(uri)) {
                val errorMsg = "Video file no longer exists"
                Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                onError?.invoke(errorMsg)
                return
            }
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, FileUtils.getMimeType(filePath))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            try {
                context.startActivity(intent)
                Toast.makeText(context, "Opening video...", Toast.LENGTH_SHORT).show()
                onComplete?.invoke()
            } catch (e: android.content.ActivityNotFoundException) {
                val errorMsg = "No video player app found"
                Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                onError?.invoke(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening video file", e)
            val errorMsg = "Error opening video: ${e.message}"
            Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
            onError?.invoke(errorMsg)
        }
    }
    
    /**
     * Parse file path or URI string into a proper Uri object
     * Handles content://, file://, and regular file paths
     * @return Uri if valid, null if file doesn't exist
     */
    fun parseFilePathToUri(pathOrUri: String): Uri? {
        return when {
            pathOrUri.startsWith("content://") -> Uri.parse(pathOrUri)
            pathOrUri.startsWith("file://") -> Uri.parse(pathOrUri)
            else -> {
                // Legacy file path - convert to FileProvider URI
                val file = File(pathOrUri)
                if (file.exists()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                    } else {
                        Uri.fromFile(file)
                    }
                } else {
                    null
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
                    context.contentResolver.openInputStream(uri)?.use { true } ?: false
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
}
