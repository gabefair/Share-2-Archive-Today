package org.gnosco.share2archivetoday

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.util.TypedValue
import android.widget.Toast
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import java.io.File

/**
 * Activity to display download history
 * Shows completed, failed, and active downloads
 */
class DownloadHistoryActivity : Activity() {
    
    companion object {
        private const val TAG = "DownloadHistoryActivity"
    }
    
    private lateinit var downloadHistoryManager: DownloadHistoryManager
    private lateinit var downloadResumptionManager: DownloadResumptionManager
    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var clearHistoryButton: Button
    private lateinit var openFolderButton: Button
    private lateinit var emptyStateText: TextView
    private var allDownloads = mutableListOf<DownloadHistoryItem>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize components
        downloadHistoryManager = DownloadHistoryManager(applicationContext)
        downloadResumptionManager = DownloadResumptionManager(applicationContext)
        
        // Create layout with buttons
        createLayout()
        
        // Load and display download history
        loadDownloadHistory()
    }
    
    private fun createLayout() {
        // Convert dp to pixels for consistent sizing across devices
        val dp16 = dpToPx(16f)
        val dp12 = dpToPx(12f)
        val dp8 = dpToPx(8f)
        val dp48 = dpToPx(48f)  // Standard Android button height
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp16, dp16, dp16, dp16)
        }
        
        // Title
        val titleText = TextView(this).apply {
            text = "Download History"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setPadding(0, 0, 0, dp16)
        }
        layout.addView(titleText)
        
        // Clear History button
        clearHistoryButton = Button(this).apply {
            text = "CLEAR HISTORY"
            isAllCaps = true
            minHeight = dp48
            setPadding(dp16, dp12, dp16, dp12)
            setOnClickListener { showClearHistoryDialog() }
        }
        
        val clearButtonParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 0, dp8)
        }
        layout.addView(clearHistoryButton, clearButtonParams)
        
        // Open Downloads Folder button
        openFolderButton = Button(this).apply {
            text = "OPEN DOWNLOADS FOLDER"
            isAllCaps = true
            minHeight = dp48
            setPadding(dp16, dp12, dp16, dp12)
            setOnClickListener { openDownloadsFolder() }
        }
        
        val openButtonParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 0, dp16)
        }
        layout.addView(openFolderButton, openButtonParams)
        
        // Empty state text
        emptyStateText = TextView(this).apply {
            text = "No downloads yet"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(0, dp8, 0, 0)
            visibility = android.view.View.GONE
        }
        layout.addView(emptyStateText)
        
        // List view
        listView = ListView(this).apply {
            setPadding(0, 0, 0, 0)
        }
        
        val listParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f  // Take remaining space with weight
        )
        layout.addView(listView, listParams)
        
        setContentView(layout)
    }
    
    /**
     * Convert density-independent pixels (dp) to actual pixels based on screen density
     * This ensures consistent sizing across different screen sizes and densities
     */
    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        ).toInt()
    }
    
    private fun loadDownloadHistory() {
        try {
            val completedDownloads = downloadHistoryManager.getDownloadHistory()
            val activeDownloads = downloadResumptionManager.getActiveDownloads()
            
            allDownloads.clear()
            
            // Create a map to deduplicate downloads by URL
            val downloadMap = mutableMapOf<String, DownloadHistoryItem>()
            
            // Add completed downloads from history manager
            completedDownloads.forEach { download: DownloadHistoryManager.DownloadHistoryEntry ->
                if (download.success) {  // Only show successful downloads
                    downloadMap[download.url] = DownloadHistoryItem(
                        title = download.title,
                        url = download.url,
                        uploader = download.uploader,
                        quality = download.quality,
                        filePath = download.filePath,
                        fileSize = download.fileSize,
                        success = true,
                        error = null,
                        timestamp = download.timestamp,
                        status = "COMPLETED"
                    )
                }
            }
            
            // Add completed downloads from resumption manager (only if not already in history)
            activeDownloads.forEach { download ->
                if (download.status == DownloadResumptionManager.DownloadStatus.COMPLETED && 
                    !downloadMap.containsKey(download.url)) {
                    downloadMap[download.url] = DownloadHistoryItem(
                        title = download.title,
                        url = download.url,
                        uploader = "Unknown",
                        quality = download.quality,
                        filePath = download.partialFilePath,
                        fileSize = download.totalBytes,
                        success = true,
                        error = null,
                        timestamp = download.startTime,
                        status = "COMPLETED"
                    )
                }
            }
            
            // Convert map to list and sort by timestamp (newest first)
            allDownloads.addAll(downloadMap.values.sortedByDescending { it.timestamp })
            
            // Show/hide empty state
            if (allDownloads.isEmpty()) {
                emptyStateText.visibility = android.view.View.VISIBLE
                listView.visibility = android.view.View.GONE
            } else {
                emptyStateText.visibility = android.view.View.GONE
                listView.visibility = android.view.View.VISIBLE
                
                // Create simple string list for ListView
                val displayItems = allDownloads.map { item ->
                    val statusIcon = if (item.success) "✅" else "❌"
                    val fileSizeText = if (item.fileSize > 0) " (${formatFileSize(item.fileSize)})" else ""
                    "$statusIcon ${item.title} - ${item.status}$fileSizeText"
                }
                
                adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayItems)
                listView.adapter = adapter
                
                listView.setOnItemClickListener { _, _, position, _ ->
                    handleDownloadItemClick(allDownloads[position])
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading download history", e)
            Toast.makeText(this, "Error loading download history", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun handleDownloadItemClick(item: DownloadHistoryItem) {
        when {
            item.success && item.filePath != null -> {
                // Show action dialog for successful downloads
                showDownloadActionDialog(item)
            }
            item.status == "DOWNLOADING" || item.status == "PAUSED" -> {
                // Show download details
                showDownloadDetails(item)
            }
            else -> {
                // Show error details
                showErrorDetails(item)
            }
        }
    }
    
    private fun showDownloadActionDialog(item: DownloadHistoryItem) {
        val actions = mutableListOf<String>()
        val actionCallbacks = mutableListOf<() -> Unit>()
        
        // Open video file
        actions.add("Open Video")
        actionCallbacks.add { openFile(item.filePath!!) }
        
        // Copy source URL
        actions.add("Copy Source URL")
        actionCallbacks.add { copyUrlToClipboard(item.url) }
        
        // Share video
        actions.add("Share Video")
        actionCallbacks.add { shareVideo(item) }
        
        // Show download details
        actions.add("View Details")
        actionCallbacks.add { showDownloadDetails(item) }
        
        // Delete this download from history
        actions.add("Delete from History")
        actionCallbacks.add { showDeleteItemDialog(item) }
        
        AlertDialog.Builder(this)
            .setTitle(item.title)
            .setItems(actions.toTypedArray()) { _, which ->
                actionCallbacks[which]()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun copyUrlToClipboard(url: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Video URL", url)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
    }
    
    private fun shareVideo(item: DownloadHistoryItem) {
        if (item.filePath != null) {
            try {
                val uri = parseFilePathOrUri(item.filePath)
                
                // Check if the file exists
                if (!checkUriExists(uri)) {
                    Toast.makeText(this, "Video file no longer exists", Toast.LENGTH_SHORT).show()
                    return
                }
                
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, uri)
                    type = getMimeTypeFromUri(uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                if (shareIntent.resolveActivity(packageManager) != null) {
                    startActivity(Intent.createChooser(shareIntent, "Share Video"))
                } else {
                    Toast.makeText(this, "No app found to share this file", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sharing video", e)
                Toast.makeText(this, "Error sharing video: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "No file path available", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showClearHistoryDialog() {
        AlertDialog.Builder(this)
            .setTitle("Clear Download History")
            .setMessage("Are you sure you want to clear all download history? This will remove all entries from the list.\n\nNote: Downloaded files will NOT be deleted.")
            .setPositiveButton("Clear All") { _, _ ->
                downloadHistoryManager.clearHistory()
                downloadResumptionManager.cleanupOldDownloads(0) // Clear all
                loadDownloadHistory() // Refresh the list
                Toast.makeText(this, "Download history cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showDeleteItemDialog(item: DownloadHistoryItem) {
        AlertDialog.Builder(this)
            .setTitle("Delete from History")
            .setMessage("Remove \"${item.title}\" from download history?\n\nNote: The downloaded file will NOT be deleted.")
            .setPositiveButton("Delete") { _, _ ->
                deleteHistoryItem(item)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteHistoryItem(item: DownloadHistoryItem) {
        try {
            // Remove from history manager by URL
            val history = downloadHistoryManager.getDownloadHistory().toMutableList()
            val removed = history.removeAll { it.url == item.url && it.timestamp == item.timestamp }
            
            if (removed) {
                // Save updated history
                downloadHistoryManager.clearHistory()
                history.forEach { entry ->
                    downloadHistoryManager.addDownload(
                        url = entry.url,
                        title = entry.title,
                        uploader = entry.uploader,
                        quality = entry.quality,
                        filePath = entry.filePath,
                        fileSize = entry.fileSize,
                        success = entry.success,
                        error = entry.error
                    )
                }
                
                // Refresh the list
                loadDownloadHistory()
                Toast.makeText(this, "Removed from history", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Item not found in history", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting history item", e)
            Toast.makeText(this, "Error removing item from history", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openDownloadsFolder() {
        try {
            // Use Android's Downloads app/UI to show downloads
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                // Try to open Downloads app via system UI
                data = Uri.parse("package:com.android.documentsui")
            }
            
            // Fallback to generic intent to view downloads
            if (intent.resolveActivity(packageManager) == null) {
                // Use the MediaStore to show downloads
                val downloadsIntent = Intent(Intent.ACTION_VIEW).apply {
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("video/*", "audio/*"))
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                if (downloadsIntent.resolveActivity(packageManager) != null) {
                    startActivity(Intent.createChooser(downloadsIntent, "Open Downloads"))
                    return
                }
            }
            
            // Last resort: Show helpful message
            Toast.makeText(
                this, 
                "Please open your device's Downloads app to view downloaded files",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error opening downloads folder", e)
            Toast.makeText(
                this,
                "Please open your device's Downloads app to view files",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    private fun openFile(filePath: String) {
        try {
            val uri = parseFilePathOrUri(filePath)
            
            // Check if the file exists
            if (!checkUriExists(uri)) {
                Toast.makeText(this, "File not found at: $filePath", Toast.LENGTH_LONG).show()
                Log.e(TAG, "File not found: $filePath")
                return
            }
            
            val mimeType = getMimeTypeFromUri(uri)
            Log.d(TAG, "Opening file: $uri with mime type: $mimeType")
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, "No app found to open this file", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening file: $filePath", e)
            Toast.makeText(this, "Error opening file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Parse a file path or URI string into a proper Uri object
     * Handles both content:// URIs and legacy file paths
     */
    private fun parseFilePathOrUri(pathOrUri: String): Uri {
        return when {
            pathOrUri.startsWith("content://") -> Uri.parse(pathOrUri)
            pathOrUri.startsWith("file://") -> Uri.parse(pathOrUri)
            else -> {
                // Legacy file path - convert to FileProvider URI
                val file = File(pathOrUri)
                if (file.exists()) {
                    androidx.core.content.FileProvider.getUriForFile(
                        this, 
                        "$packageName.fileprovider", 
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
    private fun checkUriExists(uri: Uri): Boolean {
        return try {
            when (uri.scheme) {
                "content" -> {
                    // For content URIs, try to open an input stream
                    contentResolver.openInputStream(uri)?.use { true } ?: false
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
     * Get MIME type from a URI
     */
    private fun getMimeTypeFromUri(uri: Uri): String {
        return try {
            when (uri.scheme) {
                "content" -> {
                    // Query ContentResolver for MIME type
                    contentResolver.getType(uri) ?: guessMimeTypeFromUri(uri)
                }
                else -> guessMimeTypeFromUri(uri)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting MIME type for URI: $uri", e)
            "video/*"
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
            else -> "video/*"
        }
    }
    
    private fun showDownloadDetails(item: DownloadHistoryItem) {
        val message = buildString {
            append("Title: ${item.title}\n\n")
            append("Status: ${item.status}\n\n")
            append("Quality: ${item.quality}\n\n")
            
            if (item.fileSize > 0) {
                append("Size: ${formatFileSize(item.fileSize)}\n\n")
            }
            
            if (item.uploader.isNotEmpty() && item.uploader != "Unknown") {
                append("Uploader: ${item.uploader}\n\n")
            }
            
            // Format the download date
            val dateStr = try {
                java.text.SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", java.util.Locale.getDefault())
                    .format(java.util.Date(item.timestamp))
            } catch (e: Exception) {
                "Unknown"
            }
            append("Downloaded: $dateStr\n\n")
            
            if (item.filePath != null) {
                append("Location: ${item.filePath}\n\n")
            }
            
            append("URL: ${item.url}")
        }
        
        AlertDialog.Builder(this)
            .setTitle("Download Details")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setNeutralButton("Copy URL") { _, _ ->
                copyUrlToClipboard(item.url)
            }
            .show()
    }
    
    private fun showErrorDetails(item: DownloadHistoryItem) {
        val message = buildString {
            append("Title: ${item.title}\n")
            append("Error: ${item.error ?: "Unknown error"}\n")
            append("URL: ${item.url}")
        }
        
        AlertDialog.Builder(this)
            .setTitle("Download Failed")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun formatFileSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        
        return when {
            gb >= 1 -> "%.1f GB".format(gb)
            mb >= 1 -> "%.1f MB".format(mb)
            kb >= 1 -> "%.1f KB".format(kb)
            else -> "$bytes B"
        }
    }
    
    /**
     * Data class for download history items
     */
    data class DownloadHistoryItem(
        val title: String,
        val url: String,
        val uploader: String,
        val quality: String,
        val filePath: String?,
        val fileSize: Long,
        val success: Boolean,
        val error: String?,
        val timestamp: Long,
        val status: String
    )
}

