package org.gnosco.share2archivetoday.utils

import org.gnosco.share2archivetoday.utils.*

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Utility class for opening files with appropriate apps
 */
class FileOpener(private val context: Context) {
    
    companion object {
        private const val TAG = "FileOpener"
    }
    
    /**
     * Open a file with the appropriate app
     */
    fun openFile(filePath: String): Boolean {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show()
                return false
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, FileUtils.getMimeType(filePath))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return true
            } else {
                Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening file", e)
            Toast.makeText(context, "Error opening file: ${e.message}", Toast.LENGTH_SHORT).show()
            return false
        }
    }
}

