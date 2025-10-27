package org.gnosco.share2archivetoday.download

import org.gnosco.share2archivetoday.PythonVideoDownloader
import org.gnosco.share2archivetoday.media.AudioRemuxer
import org.gnosco.share2archivetoday.MemoryManager
import org.gnosco.share2archivetoday.network.*
import org.gnosco.share2archivetoday.utils.*
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import java.io.File

/**
 * Background service for handling video downloads
 * This service runs in the foreground to ensure downloads continue even when the app is not visible
 * Refactored to use helper classes for better modularity
 */
class BackgroundDownloadService : Service() {

    companion object {
        private const val TAG = "BackgroundDownloadService"

        // Intent extras
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_UPLOADER = "uploader"
        const val EXTRA_QUALITY = "quality"
        const val EXTRA_QUALITY_LABEL = "quality_label"

        // Actions
        const val ACTION_START_DOWNLOAD = "start_download"
        const val ACTION_CANCEL_DOWNLOAD = "cancel_download"

        fun startDownload(
            context: Context,
            url: String,
            title: String = "Unknown",
            uploader: String = "Unknown",
            quality: String = "best",
            formatId: String? = null,
            qualityLabel: String? = null
        ) {
            val intent = Intent(context, BackgroundDownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_UPLOADER, uploader)
                putExtra(EXTRA_QUALITY, quality)
                if (formatId != null) {
                    putExtra("EXTRA_FORMAT_ID", formatId)
                }
                if (qualityLabel != null) {
                    putExtra(EXTRA_QUALITY_LABEL, qualityLabel)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Core managers
    private lateinit var pythonDownloader: PythonVideoDownloader
    private lateinit var downloadHistoryManager: DownloadHistoryManager
    private lateinit var downloadResumptionManager: DownloadResumptionManager
    private lateinit var networkMonitor: NetworkMonitor
    
    // Helper classes
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var fileProcessor: FileProcessor
    private lateinit var mediaStoreHelper: MediaStoreHelper
    private lateinit var storageHelper: StorageHelper
    
    // Coordinators
    private lateinit var downloadOrchestrator: DownloadOrchestrator
    private lateinit var prerequisitesChecker: DownloadPrerequisitesChecker
    private lateinit var resultHandler: DownloadResultHandler
    private lateinit var progressHandler: ProgressHandler
    
    private var currentDownloadJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        
        // Initialize core managers
        pythonDownloader = PythonVideoDownloader(applicationContext)
        downloadHistoryManager = DownloadHistoryManager(applicationContext)
        downloadResumptionManager = DownloadResumptionManager(applicationContext)
        networkMonitor = NetworkMonitor(applicationContext)
        
        // Initialize helpers
        notificationHelper = NotificationHelper(this)
        fileProcessor = FileProcessor(applicationContext)
        mediaStoreHelper = MediaStoreHelper(applicationContext)
        storageHelper = StorageHelper(applicationContext)
        
        // Initialize coordinators
        downloadOrchestrator = DownloadOrchestrator(
            applicationContext, pythonDownloader, downloadHistoryManager,
            downloadResumptionManager, notificationHelper, fileProcessor,
            mediaStoreHelper, storageHelper
        )
        prerequisitesChecker = DownloadPrerequisitesChecker(
            applicationContext, networkMonitor, storageHelper,
            notificationHelper, downloadResumptionManager, downloadHistoryManager
        )
        resultHandler = DownloadResultHandler(
            applicationContext, downloadOrchestrator, notificationHelper,
            networkMonitor, storageHelper
        )
        progressHandler = ProgressHandler(
            applicationContext, notificationHelper, downloadResumptionManager,
            networkMonitor, pythonDownloader
        )
        
        notificationHelper.createNotificationChannel()
        networkMonitor.startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Unknown"
                val uploader = intent.getStringExtra(EXTRA_UPLOADER) ?: "Unknown"
                val quality = intent.getStringExtra(EXTRA_QUALITY) ?: "best"
                val formatId = intent.getStringExtra("EXTRA_FORMAT_ID")
                val qualityLabel = intent.getStringExtra(EXTRA_QUALITY_LABEL)

                startForeground(
                    NotificationHelper.NOTIFICATION_ID,
                    notificationHelper.createNotification("Starting download...", title)
                )
                startDownload(url, title, uploader, quality, formatId, qualityLabel)
            }
            ACTION_CANCEL_DOWNLOAD -> {
                Log.d(TAG, "Received cancellation request")
                cancelCurrentDownload()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service being destroyed")
        serviceScope.cancel()
        currentDownloadJob?.cancel()
        pythonDownloader.cancelDownload()
        pythonDownloader.cleanup()
        networkMonitor.stopMonitoring()
    }

    private fun startDownload(
        url: String,
        title: String,
        uploader: String,
        quality: String,
        formatId: String? = null,
        qualityLabel: String? = null
    ) {
        val downloadId = "${url.hashCode()}_${System.currentTimeMillis()}"
        pythonDownloader.resetCancellation()

        currentDownloadJob = serviceScope.launch {
            try {
                notificationHelper.updateNotification("Getting video info...", title)
                pythonDownloader.currentDownloadJob = currentDownloadJob

                // Get video info and determine display title
                val videoInfo = try {
                    pythonDownloader.getVideoInfo(url)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get video info: ${e.message}")
                    null
                }

                val displayTitle = downloadOrchestrator.determineDisplayTitle(videoInfo, title, url)
                val displayUploader = videoInfo?.uploader ?: uploader
                val displayQuality = qualityLabel ?: quality

                Log.d(TAG, "Download starting - Title: '$displayTitle', Uploader: '$displayUploader'")
                notificationHelper.updateNotification("Starting download...", displayTitle)

                // Check for cancellation
                if (pythonDownloader.isCancelled()) {
                    Log.d(TAG, "Download cancelled before starting")
                    notificationHelper.showCancelledNotification(displayTitle)
                    stopSelf()
                    return@launch
                }

                // Check network and storage prerequisites
                val prereqResult = prerequisitesChecker.checkPrerequisites(
                    displayTitle, displayUploader, displayQuality, url, downloadId
                )
                if (!prereqResult.passed) {
                    stopSelf()
                    return@launch
                }

                val downloadDir = storageHelper.getDownloadDirectory()
                val estimatedSizeMB = 100L

                // Start tracking download
                downloadResumptionManager.startDownload(downloadId, url, displayTitle, displayQuality, downloadDir)

                // Execute download
                val result = downloadOrchestrator.executeDownload(
                    url, downloadDir, quality, displayTitle, downloadId, 
                    estimatedSizeMB, formatId
                ) { progressInfo ->
                    progressHandler.handleProgressUpdate(progressInfo, displayTitle, downloadId)
                }

                // Process result
                if (result.success) {
                    resultHandler.handleSuccessfulDownload(
                        result, displayTitle, displayUploader, displayQuality, 
                        url, downloadId, quality
                    )
                } else {
                    resultHandler.handleFailedDownload(
                        result, displayTitle, displayUploader, displayQuality, 
                        url, downloadId, quality
                    )
                }

            } catch (e: Exception) {
                resultHandler.handleException(e, title)
            } finally {
                delay(3000)
                stopSelf()
            }
        }
    }


    private fun cancelCurrentDownload() {
        Log.d(TAG, "Cancelling current download")
        pythonDownloader.cancelDownload()
        currentDownloadJob?.cancel()
        stopSelf()
    }
}
