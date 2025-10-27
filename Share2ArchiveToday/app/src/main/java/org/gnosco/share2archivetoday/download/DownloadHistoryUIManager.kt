package org.gnosco.share2archivetoday.download

import android.app.Activity
import android.util.TypedValue
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/**
 * Manages UI creation and display for Download History
 */
class DownloadHistoryUIManager(private val activity: Activity) {
    
    lateinit var listView: ListView
        private set
    lateinit var adapter: ArrayAdapter<String>
        private set
    lateinit var clearHistoryButton: Button
        private set
    lateinit var openFolderButton: Button
        private set
    lateinit var emptyStateText: TextView
        private set
    
    /**
     * Create and setup the main layout
     */
    fun createLayout(): View {
        // Convert dp to pixels for consistent sizing across devices
        val dp16 = dpToPx(16f)
        val dp12 = dpToPx(12f)
        val dp8 = dpToPx(8f)
        val dp48 = dpToPx(48f)  // Standard Android button height
        
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp16, dp16, dp16, dp16)
        }
        
        // Title
        val titleText = TextView(activity).apply {
            text = "Download History"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setPadding(0, 0, 0, dp16)
        }
        layout.addView(titleText)
        
        // Clear History button
        clearHistoryButton = Button(activity).apply {
            text = "CLEAR HISTORY"
            isAllCaps = true
            minHeight = dp48
            setPadding(dp16, dp12, dp16, dp12)
        }
        
        val clearButtonParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 0, dp8)
        }
        layout.addView(clearHistoryButton, clearButtonParams)
        
        // Open Downloads Folder button
        openFolderButton = Button(activity).apply {
            text = "OPEN DOWNLOADS FOLDER"
            isAllCaps = true
            minHeight = dp48
            setPadding(dp16, dp12, dp16, dp12)
        }
        
        val openButtonParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 0, dp16)
        }
        layout.addView(openFolderButton, openButtonParams)
        
        // Empty state text
        emptyStateText = TextView(activity).apply {
            text = "No downloads yet"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(0, dp8, 0, 0)
            visibility = View.GONE
        }
        layout.addView(emptyStateText)
        
        // List view
        listView = ListView(activity).apply {
            setPadding(0, 0, 0, 0)
        }
        
        val listParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f  // Take remaining space with weight
        )
        layout.addView(listView, listParams)
        
        return layout
    }
    
    /**
     * Update the download list display
     */
    fun updateDownloadList(
        downloads: List<DownloadHistoryItem>,
        onItemClick: (DownloadHistoryItem) -> Unit
    ) {
        if (downloads.isEmpty()) {
            emptyStateText.visibility = View.VISIBLE
            listView.visibility = View.GONE
        } else {
            emptyStateText.visibility = View.GONE
            listView.visibility = View.VISIBLE
            
            // Create simple string list for ListView
            val displayItems = downloads.map { item ->
                val statusIcon = if (item.success) "✅" else "❌"
                val fileSizeText = if (item.fileSize > 0) " (${formatFileSize(item.fileSize)})" else ""
                "$statusIcon ${item.title} - ${item.status}$fileSizeText"
            }
            
            adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, displayItems)
            listView.adapter = adapter
            
            listView.setOnItemClickListener { _, _, position, _ ->
                onItemClick(downloads[position])
            }
        }
    }
    
    /**
     * Convert density-independent pixels (dp) to actual pixels based on screen density
     */
    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            activity.resources.displayMetrics
        ).toInt()
    }
    
    /**
     * Format file size for display
     */
    fun formatFileSize(bytes: Long): String {
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

