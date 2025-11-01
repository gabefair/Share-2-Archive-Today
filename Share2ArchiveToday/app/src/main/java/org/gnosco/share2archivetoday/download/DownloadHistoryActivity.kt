package org.gnosco.share2archivetoday.download

import android.app.Activity
import android.os.Bundle
import org.gnosco.share2archivetoday.download.*

/**
 * Activity to display download history
 * Shows completed, failed, and active downloads
 */
class DownloadHistoryActivity : Activity() {
    
    private lateinit var downloadHistoryManager: DownloadHistoryManager
    private lateinit var downloadResumptionManager: DownloadResumptionManager
    private lateinit var uiManager: DownloadHistoryUIManager
    private lateinit var fileManager: DownloadHistoryFileManager
    private lateinit var dialogManager: DownloadHistoryDialogManager
    private lateinit var dataManager: DownloadHistoryDataManager
    private lateinit var folderOpener: DownloadsFolderOpener
    
    private var allDownloads = mutableListOf<DownloadHistoryItem>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize managers
        downloadHistoryManager = DownloadHistoryManager(applicationContext)
        downloadResumptionManager = DownloadResumptionManager(applicationContext)
        
        uiManager = DownloadHistoryUIManager(this)
        fileManager = DownloadHistoryFileManager(this)
        dialogManager = DownloadHistoryDialogManager(this, fileManager)
        dataManager = DownloadHistoryDataManager(
            applicationContext,
            downloadHistoryManager,
            downloadResumptionManager
        )
        folderOpener = DownloadsFolderOpener(this)
        
        // Create layout with buttons
        val layout = uiManager.createLayout()
        setContentView(layout)
        
        // Setup button listeners
        setupButtonListeners()
        
        // Load and display download history
        loadDownloadHistory()
    }
    
    private fun setupButtonListeners() {
        uiManager.clearHistoryButton.setOnClickListener {
            dialogManager.showClearHistoryDialog {
                dataManager.clearAllHistory()
                loadDownloadHistory()
            }
        }
        
        uiManager.openFolderButton.setOnClickListener {
            dialogManager.showOpenFolderDialog {
                folderOpener.openDownloadsFolder()
            }
        }
    }
    
    private fun loadDownloadHistory() {
        allDownloads.clear()
        allDownloads.addAll(dataManager.loadDownloadHistory())
        
        uiManager.updateDownloadList(allDownloads) { item ->
            handleDownloadItemClick(item)
        }
    }
    
    private fun handleDownloadItemClick(item: DownloadHistoryItem) {
        when {
            item.success && item.filePath != null -> {
                // Show action dialog for successful downloads
                dialogManager.showDownloadActionDialog(item) {
                    if (dataManager.deleteHistoryItem(item)) {
                        loadDownloadHistory()
                    }
                }
            }
            item.status == "DOWNLOADING" || item.status == "PAUSED" -> {
                // Show download details
                dialogManager.showDownloadDetails(item)
            }
            else -> {
                // Show error details
                dialogManager.showErrorDetails(item)
            }
        }
    }
}

