package org.gnosco.share2archivetoday

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.File
import android.annotation.TargetApi
import android.content.ContentResolver

/**
 * Background service for handling video downloads
 * This service runs in the foreground to ensure downloads continue even when the app is not visible
 */
class BackgroundDownloadService : Service() {

    companion object {
        private const val TAG = "BackgroundDownloadService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "video_download_channel"
        private const val CHANNEL_NAME = "Video Downloads"

        // Intent extras
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_UPLOADER = "uploader"
        const val EXTRA_QUALITY = "quality"
        const val EXTRA_QUALITY_LABEL = "quality_label"

        // Actions
        const val ACTION_START_DOWNLOAD = "start_download"
        const val ACTION_CANCEL_DOWNLOAD = "cancel_download"

        fun startDownload(context: Context, url: String, title: String = "Unknown", uploader: String = "Unknown", quality: String = "best", formatId: String? = null, qualityLabel: String? = null) {
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

            // Use the Activity context when possible to avoid background launch restrictions on Android 12+
            val isActivityContext = context is android.app.Activity
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (isActivityContext) {
                    context.startForegroundService(intent)
                } else {
                    // Fallback: best effort start; caller should prefer Activity context
                    context.startForegroundService(intent)
                }
            } else {
                context.startService(intent)
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var pythonDownloader: PythonVideoDownloader
    private lateinit var downloadHistoryManager: DownloadHistoryManager
    private lateinit var downloadResumptionManager: DownloadResumptionManager
    private lateinit var networkMonitor: NetworkMonitor
    private var currentDownloadJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        pythonDownloader = PythonVideoDownloader(applicationContext)
        downloadHistoryManager = DownloadHistoryManager(applicationContext)
        downloadResumptionManager = DownloadResumptionManager(applicationContext)
        networkMonitor = NetworkMonitor(applicationContext)
        createNotificationChannel()

        // Start network monitoring
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

                startForeground(NOTIFICATION_ID, createNotification("Starting download...", title))
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for video downloads"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String, title: String, showProgress: Boolean = false, progress: Int = 0): Notification {
        val cancelIntent = Intent(this, BackgroundDownloadService::class.java).apply {
            action = ACTION_CANCEL_DOWNLOAD
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, 0, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading: $title")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                cancelPendingIntent
            )

        // Add progress bar if requested
        if (showProgress) {
            builder.setProgress(100, progress, false)
        }

        return builder.build()
    }

    private fun updateNotification(contentText: String, title: String, showProgress: Boolean = false, progress: Int = 0) {
        val notification = createNotification(contentText, title, showProgress, progress)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun startDownload(url: String, title: String, uploader: String, quality: String, formatId: String? = null, qualityLabel: String? = null) {
        val downloadId = "${url.hashCode()}_${System.currentTimeMillis()}"

        // Reset cancellation flag from any previous downloads
        pythonDownloader.resetCancellation()

        currentDownloadJob = serviceScope.launch {
            try {
                updateNotification("Getting video info...", title)

                // Store download job in PythonVideoDownloader for cancellation
                pythonDownloader.currentDownloadJob = currentDownloadJob

                // Get video info first - this will get the actual video title from yt-dlp
                val videoInfo = try {
                    pythonDownloader.getVideoInfo(url)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get video info: ${e.message}")
                    null
                }

                // Use actual video title if available, otherwise construct from URL
                val displayTitle = when {
                    // Best case: we have actual video title from yt-dlp
                    !videoInfo?.title.isNullOrBlank() && videoInfo?.title != "Unknown" -> {
                        videoInfo!!.title
                    }
                    // Second best: use the title parameter if it's meaningful
                    !title.isNullOrBlank() && title != "Unknown" && title != "Unknown Video" -> {
                        title
                    }
                    // Last resort: extract domain from URL
                    else -> {
                        try {
                            val uri = android.net.Uri.parse(url)
                            val domain = uri.host?.removePrefix("www.")?.removePrefix("m.") ?: "unknown"
                            "Video from $domain"
                        } catch (e: Exception) {
                            "Downloaded Video"
                        }
                    }
                }

                // Also get uploader info if available
                val displayUploader = videoInfo?.uploader ?: uploader

                // Use quality label for display if available, otherwise fall back to quality parameter
                val displayQuality = qualityLabel ?: quality

                Log.d(TAG, "Download starting - Title: '$displayTitle', Uploader: '$displayUploader'")

                updateNotification("Starting download...", displayTitle)

                // Check if cancelled
                if (pythonDownloader.isCancelled()) {
                    Log.d(TAG, "Download cancelled before starting")
                    showCancelledNotification(displayTitle)
                    stopSelf()
                    return@launch
                }

                // Check network connectivity
                if (!networkMonitor.isConnected()) {
                    updateNotification("No internet connection", displayTitle)
                    showErrorNotification(displayTitle, "No internet connection. Please check your network settings.")

                    // Mark download as failed due to network
                    downloadResumptionManager.failDownload(downloadId, "No internet connection")

                    // Add failed download to history
                    downloadHistoryManager.addDownload(
                        url = url,
                        title = displayTitle,
                        uploader = displayUploader,
                        quality = displayQuality,
                        filePath = null,
                        fileSize = 0,
                        success = false,
                        error = "No internet connection"
                    )

                    stopSelf()
                    return@launch
                }

                // Warn about data usage if not on WiFi
                if (networkMonitor.shouldWarnAboutDataUsage()) {
                    updateNotification("Warning: Using mobile data. Large downloads may consume data.", displayTitle)
                }

                // Create download directory
                val downloadDir = getDownloadDirectory()

                // Check storage space before starting download
                val availableSpaceMB = getAvailableStorageSpace(downloadDir)
                if (availableSpaceMB < 50) {  // Need at least 50MB
                    updateNotification("Insufficient storage space. Available: ${availableSpaceMB}MB", displayTitle)
                    showErrorNotification(displayTitle, "Insufficient storage space. Available: ${availableSpaceMB}MB")
                    stopSelf()
                    return@launch
                }

                // Get estimated file size for memory planning (use default since VideoInfo doesn't have fileSize)
                val estimatedSizeMB = 100L  // Default estimate: 100MB

                // Start tracking this download - use quality label for display if available
                downloadResumptionManager.startDownload(downloadId, url, displayTitle, displayQuality, downloadDir)

                // Download video with selected quality and real-time progress tracking
                val result = when {
                    quality.startsWith("audio_") -> {
                        val audioFormat = if (quality == "audio_mp3") "mp3" else "aac"
                        pythonDownloader.downloadAudio(
                            url = url,
                            outputDir = downloadDir,
                            audioFormat = audioFormat,
                            progressCallback = { progressInfo ->
                                handleProgressUpdate(progressInfo, displayTitle, downloadId)
                            }
                        )
                    }
                    else -> {
                        pythonDownloader.downloadVideo(
                            url = url,
                            outputDir = downloadDir,
                            quality = quality, // Use quality preference instead of specific format ID
                            progressCallback = { progressInfo ->
                                handleProgressUpdate(progressInfo, displayTitle, downloadId)
                            },
                            estimatedSizeMB = estimatedSizeMB,
                            formatId = null // Deprecated - let Python side handle quality selection
                        )
                    }
                }

                // Process the result - don't check cancellation here
                // If the download completed (success or failure), the result tells us what happened
                // The cancellation check during download is handled by the progress callback

                if (result.success) {
                    updateNotification("Processing completed file...", displayTitle)

                    // Handle separate A/V streams if needed
                    val finalResult = if (result.separateAv && result.videoPath != null && result.audioPath != null) {
                        Log.d(TAG, "Merging separate video and audio streams")
                        val videoPath = result.videoPath
                        val audioPath = result.audioPath
                        val mergedPath = mergeVideoAudio(videoPath, audioPath, displayTitle)
                        if (mergedPath != null) {
                            result.copy(filePath = mergedPath, separateAv = false)
                        } else {
                            Log.w(TAG, "Failed to merge streams, keeping separate files")
                            result
                        }
                    } else if (result.separateAv && result.filePath != null) {
                        // Extract audio from video file
                        Log.d(TAG, "Extracting audio from video file")
                        updateNotification("Extracting audio...", displayTitle)
                        val audioFormat = if (quality == "audio_mp3") "mp3" else "aac"
                        val videoFilePath = result.filePath
                        val extractedPath = extractAudioFromVideo(videoFilePath, displayTitle, audioFormat)
                        if (extractedPath != null) {
                            // Delete the original video file since we only wanted audio
                            try {
                                File(videoFilePath).delete()
                                Log.d(TAG, "Deleted original video file after audio extraction")
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to delete original video file: ${e.message}")
                            }
                            result.copy(filePath = extractedPath, separateAv = false)
                        } else {
                            Log.w(TAG, "Failed to extract audio from video, keeping video file")
                            result
                        }
                    } else {
                        result
                    }

                    // Process audio files that may need re-muxing (DASH m4a -> standard m4a)
                    val processedResult = if (quality.startsWith("audio_")) {
                        // For audio-only downloads, check if conversion is needed
                        val filePath = finalResult.filePath
                        if (filePath != null) {
                            val file = File(filePath)
                            if (AudioRemuxer.needsRemuxing(file, isAudioOnlyDownload = true)) {
                                val processedPath = processAudioFile(filePath, displayTitle, true)
                                finalResult.copy(filePath = processedPath)
                            } else {
                                finalResult
                            }
                        } else {
                            finalResult
                        }
                    } else {
                        // For video downloads, only process separate audio files if needed
                        if (finalResult.separateAv && finalResult.audioPath != null) {
                            val audioFile = File(finalResult.audioPath)
                            if (AudioRemuxer.needsRemuxing(audioFile, isAudioOnlyDownload = false)) {
                                val processedAudioPath = processAudioFile(finalResult.audioPath, displayTitle, false)
                                finalResult.copy(audioPath = processedAudioPath)
                            } else {
                                finalResult
                            }
                        } else {
                            finalResult
                        }
                    }

                    val processedFilePath = finalResult.filePath ?: processedResult.filePath

                    // Move file to MediaStore Downloads
                    val mediaStoreUri = processedFilePath?.let { filePath ->
                        moveToMediaStore(filePath, displayTitle)
                    }


                    // Store the MediaStore URI as a string for history
                    // Modern Android uses content:// URIs, not file paths
                    val uriString = mediaStoreUri?.toString() ?: processedResult.filePath

                    // Mark download as completed
                    downloadResumptionManager.completeDownload(downloadId, uriString ?: "", processedResult.fileSize)

                    // Add to download history
                    downloadHistoryManager.addDownload(
                        url = url,
                        title = displayTitle,
                        uploader = displayUploader,
                        quality = displayQuality,
                        filePath = uriString,
                        fileSize = processedResult.fileSize,
                        success = true
                    )

                    // Log successful download for analytics/monitoring
                    Log.i(TAG, "Download completed successfully: $displayTitle ($quality) - ${processedResult.fileSize} bytes")

                    // Show completion notification
                    showCompletionNotification(displayTitle, mediaStoreUri, processedResult.filePath)
                } else {
                    Log.e(TAG, "Download failed: ${result.error}")
                    Log.e(TAG, "Download failure details:")
                    Log.e(TAG, "  URL: $url")
                    Log.e(TAG, "  Title: $displayTitle")
                    Log.e(TAG, "  Quality: $quality")
                    Log.e(TAG, "  Network connected: ${networkMonitor.isConnected()}")
                    Log.e(TAG, "  On WiFi: ${networkMonitor.isWiFiConnected()}")
                    Log.e(TAG, "  Free space MB: ${getAvailableStorageSpace(getDownloadDirectory())}")
                    Log.e(TAG, "  Error: ${result.error}")

                    // Convert technical error to user-friendly message
                    val userFriendlyError = convertToUserFriendlyError(result.error ?: "Unknown error")

                    updateNotification("Download failed: $userFriendlyError", displayTitle)

                    // Mark download as failed
                    downloadResumptionManager.failDownload(downloadId, userFriendlyError)

                    // Add failed download to history
                    downloadHistoryManager.addDownload(
                        url = url,
                        title = displayTitle,
                        uploader = displayUploader,
                        quality = displayQuality,
                        filePath = null,
                        fileSize = 0,
                        success = false,
                        error = userFriendlyError
                    )

                    showErrorNotification(displayTitle, userFriendlyError)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error in background download", e)
                val friendlyError = convertToUserFriendlyError(e.message ?: "Unknown error")
                updateNotification("Download error: $friendlyError", title)
                showErrorNotification(title, friendlyError)
            } finally {
                // Stop the service after a delay to show the final notification
                delay(3000)
                stopSelf()
            }
        }
    }

    private fun cancelCurrentDownload() {
        Log.d(TAG, "Cancelling current download")
        pythonDownloader.cancelDownload()
        currentDownloadJob?.cancel()
        // Service will stop itself after cleanup
        stopSelf()
    }

    private fun showCancelledNotification(title: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Download Cancelled")
            .setContentText("$title download was cancelled")
            .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID + 3, notification)
    }

    private fun cleanupPartialDownload(downloadDir: String, title: String) {
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

    /**
     * Handle real-time progress updates from Python downloader
     */
    private fun handleProgressUpdate(
        progressInfo: PythonVideoDownloader.ProgressInfo,
        title: String,
        downloadId: String
    ) {
        when (progressInfo) {
            is PythonVideoDownloader.ProgressInfo.Starting -> {
                updateNotification("Starting download...", title)
                Log.d(TAG, "Starting: ${progressInfo.title}")

                // Update download state
                downloadResumptionManager.updateProgress(
                    downloadId = downloadId,
                    downloadedBytes = 0L,
                    totalBytes = 0L,
                    partialFilePath = null
                )
            }

            is PythonVideoDownloader.ProgressInfo.Retrying -> {
                updateNotification(progressInfo.message, title)
                Log.d(TAG, "Retrying: ${progressInfo.title} - ${progressInfo.message}")

                // Update retry status
                downloadResumptionManager.updateRetryStatus(downloadId, progressInfo.message)
            }

            is PythonVideoDownloader.ProgressInfo.Downloading -> {
                // Check for memory pressure during download
                monitorMemoryDuringDownload()

                // Update download state with progress
                downloadResumptionManager.updateProgress(
                    downloadId = downloadId,
                    downloadedBytes = progressInfo.downloadedBytes,
                    totalBytes = progressInfo.totalBytes,
                    partialFilePath = progressInfo.filename
                )

                // Create detailed progress text
                val progressText = buildString {
                    append("${progressInfo.percentage}%")

                    // Show download speed
                    if (progressInfo.speedBytesPerSec > 0) {
                        append(" • ${progressInfo.getFormattedSpeed()}")
                    }

                    // Show downloaded/total
                    if (progressInfo.totalBytes > 0) {
                        append(" • ${progressInfo.getFormattedDownloaded()} / ${progressInfo.getFormattedTotal()}")
                    }

                    // Show ETA
                    if (progressInfo.etaSeconds > 0) {
                        append(" • ETA: ${progressInfo.getFormattedEta()}")
                    }

                    // Show network status
                    if (networkMonitor.shouldWarnAboutDataUsage()) {
                        append(" • Mobile Data")
                    }
                }

                updateNotificationWithProgress(progressText, title, progressInfo.percentage)
                Log.d(TAG, "Progress: $progressText")
            }

            is PythonVideoDownloader.ProgressInfo.Finished -> {
                updateNotification("Processing completed file...", title)
                Log.d(TAG, "Finished: ${progressInfo.filename}")

                // Mark as processing - this will be updated to COMPLETED when the file is moved to MediaStore
                downloadResumptionManager.updateStatus(downloadId, DownloadResumptionManager.DownloadStatus.PROCESSING)
            }

            is PythonVideoDownloader.ProgressInfo.Error -> {
                Log.e(TAG, "Download error: ${progressInfo.error}")

                // Enhanced error recovery
                val shouldRetry = shouldRetryDownload(progressInfo.error)
                if (shouldRetry) {
                    Log.d(TAG, "Attempting automatic retry for error: ${progressInfo.error}")
                    downloadResumptionManager.updateRetryStatus(downloadId, "Retrying after error: ${progressInfo.error}")
                    // The Python side will handle the retry automatically
                } else {
                    downloadResumptionManager.failDownload(downloadId, progressInfo.error)
                }
            }

            is PythonVideoDownloader.ProgressInfo.Unknown -> {
                Log.w(TAG, "Unknown progress status")
            }
        }
    }

    /**
     * Monitor memory pressure during downloads
     */
    private fun monitorMemoryDuringDownload() {
        val memoryStatus = pythonDownloader.getMemoryManager().checkMemoryStatus()
        if (memoryStatus.level == MemoryManager.MemoryLevel.CRITICAL) {
            Log.w(TAG, "Critical memory pressure detected during download")
            updateNotification("Low memory - optimizing...", "Download in progress")

            // Try to free memory
            pythonDownloader.getMemoryManager().tryFreeMemory()
            pythonDownloader.getMemoryManager().cleanCacheIfNeeded()
        }
    }

    /**
     * Determine if a download error should trigger a retry
     */
    private fun shouldRetryDownload(error: String): Boolean {
        val retryableErrors = listOf(
            "network",
            "connection",
            "timeout",
            "temporary",
            "server error",
            "rate limit",
            "HTTP 500", "502", "503", "504"
        )

        val errorLower = error.lowercase()
        return retryableErrors.any { retryableError ->
            errorLower.contains(retryableError)
        }
    }

    /**
     * Update notification with progress bar
     */
    private fun updateNotificationWithProgress(text: String, title: String, percentage: Int) {
        val notification = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading: $title")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, percentage, false)  // Real progress, not indeterminate!
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        val notificationManager = getSystemService(android.app.NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun getDownloadDirectory(): String {
        // Stage into app-specific external files; we'll move to MediaStore Downloads afterward
        val staging = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val appDownloadsDir = File(staging, "Share2ArchiveToday")
        if (!appDownloadsDir.exists()) appDownloadsDir.mkdirs()
        return appDownloadsDir.absolutePath
    }

    private fun getAvailableStorageSpace(downloadDir: String): Long {
        val stat = android.os.StatFs(downloadDir)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        return availableBytes / 1048576L  // Convert to MB
    }


    private fun getMimeType(filePath: String): String {
        return try {
            // First, try to get MIME type from file extension
            val extension = filePath.substringAfterLast('.', "").lowercase()
            val mimeFromExtension = when (extension) {
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

            // If we have a specific MIME type from extension, use it
            if (mimeFromExtension != null) {
                return mimeFromExtension
            }

            // Try Android's built-in MIME type detection
            val file = File(filePath)
            if (file.exists()) {
                val mimeType = android.webkit.MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(extension)
                if (!mimeType.isNullOrEmpty()) {
                    return mimeType
                }
            }

            // Fallback based on file content analysis
            if (filePath.contains("_audio") || extension in listOf("mp3", "aac", "m4a", "wav", "ogg", "opus")) {
                "audio/mpeg" // Generic audio fallback
            } else {
                "video/mp4" // Default to MP4 for video files instead of generic video/*
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error determining MIME type for: $filePath", e)
            "video/mp4" // Safe fallback
        }
    }

    private fun showCompletionNotification(title: String, contentUri: android.net.Uri?, filePath: String?) {
        val intent = when {
            contentUri != null -> {
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(contentUri, getMimeType(filePath ?: ""))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            filePath != null -> {
                val file = File(filePath)
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    this, applicationContext.packageName + ".fileprovider", file
                )
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, getMimeType(filePath))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            else -> {
                Intent(Intent.ACTION_VIEW).apply {
                    setData(android.net.Uri.parse(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath))
                }
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create history intent
        val historyIntent = Intent(this, DownloadHistoryActivity::class.java)
        val historyPendingIntent = PendingIntent.getActivity(
            this, 2, historyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Download Complete")
            .setContentText("$title has been downloaded")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_recent_history,
                "View History",
                historyPendingIntent
            )
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun showErrorNotification(title: String, error: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Download Failed")
            .setContentText("Failed to download $title: $error")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID + 2, notification)
    }

    /**
     * Merge separate video and audio streams using Android MediaMuxer
     */
    private fun mergeVideoAudio(videoPath: String, audioPath: String, title: String): String? {
        return try {
            val ffmpegWrapper = FFmpegWrapper(applicationContext)
            val outputPath = "${getDownloadDirectory()}/${title}_merged.mp4"

            val success = ffmpegWrapper.mergeAudioVideo(
                videoPath = videoPath,
                audioPath = audioPath,
                outputPath = outputPath,
                outputFormat = android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )

            if (success) {
                // Clean up separate files
                File(videoPath).delete()
                File(audioPath).delete()
                Log.d(TAG, "Successfully merged video and audio streams")
                outputPath
            } else {
                Log.e(TAG, "Failed to merge video and audio streams")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error merging video and audio streams", e)
            null
        }
    }

    /**
     * Extract audio from video file using MediaExtractor and MediaMuxer
     */
    private fun extractAudioFromVideo(videoPath: String, title: String, requestedAudioFormat: String): String? {
        return try {
            Log.d(TAG, "Extracting audio from video: $videoPath (requested format: $requestedAudioFormat)")

            val extractor = android.media.MediaExtractor()
            extractor.setDataSource(videoPath)

            // Find audio track
            var audioTrackIndex = -1
            var trackFormat: android.media.MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    trackFormat = format
                    Log.d(TAG, "Found audio track $i with mime type: $mime")
                    break
                }
            }

            if (audioTrackIndex == -1 || trackFormat == null) {
                Log.e(TAG, "No audio track found in video file")
                extractor.release()
                return null
            }

            // Determine output file extension based on audio format
            val mime = trackFormat.getString(android.media.MediaFormat.KEY_MIME)
            val (outputExt, muxerFormat) = when {
                mime?.contains("mp4a") == true || mime?.contains("aac") == true ->
                    "m4a" to android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                mime?.contains("opus") == true ->
                    "webm" to android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
                else ->
                    "m4a" to android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            }

            val outputPath = "${getDownloadDirectory()}/${title}_audio.${outputExt}"
            Log.d(TAG, "Output audio path: $outputPath")

            // Setup muxer
            val muxer = android.media.MediaMuxer(outputPath, muxerFormat)
            extractor.selectTrack(audioTrackIndex)
            val muxerTrackIndex = muxer.addTrack(trackFormat)
            muxer.start()

            // Copy audio data
            val bufferInfo = android.media.MediaCodec.BufferInfo()
            val buffer = java.nio.ByteBuffer.allocate(1024 * 1024) // 1MB buffer
            var extractedFrames = 0

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) {
                    bufferInfo.presentationTimeUs = 0
                    bufferInfo.flags = android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    bufferInfo.size = 0
                    bufferInfo.offset = 0
                    muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                    Log.d(TAG, "End of stream reached")
                    break
                }

                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = when {
                    extractor.sampleFlags and android.media.MediaExtractor.SAMPLE_FLAG_SYNC != 0 ->
                        android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME
                    extractor.sampleFlags and android.media.MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0 ->
                        android.media.MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
                    else -> 0
                }
                bufferInfo.size = sampleSize
                bufferInfo.offset = 0

                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                extractor.advance()
                extractedFrames++

                // Log progress every 100 frames
                if (extractedFrames % 100 == 0) {
                    Log.d(TAG, "Extracted $extractedFrames audio frames")
                }
            }

            Log.d(TAG, "Total extracted frames: $extractedFrames")

            // Cleanup
            muxer.stop()
            muxer.release()
            extractor.release()

            Log.d(TAG, "Successfully extracted audio to: $outputPath")
            outputPath

        } catch (e: Exception) {
            Log.e(TAG, "Error extracting audio from video", e)
            null
        }
    }

    /**
     * Move downloaded file from app-specific dir to MediaStore Downloads
     * Returns the content URI that can be used to open the file
     */
    /**
     * Get actual file path from MediaStore URI
     */
    private fun getFilePathFromMediaStoreUri(uri: android.net.Uri): String? {
        try {
            val projection = arrayOf(android.provider.MediaStore.MediaColumns.DATA)
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATA)
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

    /**
     * Process audio files that may need conversion (DASH m4a -> MP3)
     * @param filePath The original file path
     * @param title The file title for logging
     * @return The processed file path (original if no processing needed or failed)
     */
    private fun processAudioFile(filePath: String, @Suppress("UNUSED_PARAMETER") title: String, isAudioOnlyDownload: Boolean = true): String? {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "File does not exist for processing: $filePath")
                return filePath
            }

            // Check if this is an audio file that needs re-muxing
            if (!AudioRemuxer.needsRemuxing(file, isAudioOnlyDownload)) {
                Log.d(TAG, "File does not need re-muxing: ${file.name}")
                return filePath
            }

            Log.d(TAG, "Processing audio file for MP3 conversion: ${file.name}")

            // Create temporary file for re-muxed output
            val tempFile = AudioRemuxer.createTempFile(file)

            // Perform re-muxing
            val success = AudioRemuxer.remuxAudioFile(file, tempFile)

            if (success && tempFile.exists()) {
                Log.d(TAG, "Audio conversion successful: ${tempFile.name}")

                // Create new M4A file with original name but .m4a extension
                val m4aFile = File(file.parent, "${file.nameWithoutExtension}.m4a")

                // Replace original file with converted M4A version
                if (file.delete()) {
                    if (tempFile.renameTo(m4aFile)) {
                        Log.d(TAG, "Replaced original file with M4A version: ${m4aFile.name}")
                        return m4aFile.absolutePath
                    } else {
                        Log.e(TAG, "Failed to rename converted file to M4A name")
                        // Restore original file if rename failed
                        tempFile.renameTo(file)
                    }
                } else {
                    Log.e(TAG, "Failed to delete original file for replacement")
                }
            } else {
                Log.e(TAG, "Audio conversion failed for: ${file.name}")
                // Clean up temp file if it exists
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing audio file: $filePath", e)
        }

        // Return original file path if processing failed
        return filePath
    }

    /**
     * Move downloaded file from app-specific dir to MediaStore Downloads
     * Returns the content URI that can be used to open the file
     * Supports both modern scoped storage (API 29+) and legacy storage (API 28-)
     */
    private fun moveToMediaStore(filePath: String, title: String): android.net.Uri? {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "File does not exist: $filePath")
                return null
            }

            val resolver = contentResolver

            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ (API 29+) - Use scoped storage with MediaStore.Downloads
                moveToMediaStoreModern(file, title, resolver)
            } else {
                // Android 9 and below (API 28-) - Use legacy approach
                moveToMediaStoreLegacy(file, title, resolver)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error moving to MediaStore", e)
            return null
        }
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private fun moveToMediaStoreModern(file: File, title: String, resolver: ContentResolver): android.net.Uri? {
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, getMimeType(file.absolutePath))
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download/Share2ArchiveToday")
            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
            put(android.provider.MediaStore.MediaColumns.TITLE, title)
            put(android.provider.MediaStore.MediaColumns.DATE_ADDED, System.currentTimeMillis() / 1000)
        }

        val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let { contentUri ->
            // Copy file to MediaStore
            resolver.openOutputStream(contentUri)?.use { outputStream ->
                file.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            // Mark as complete
            val updateValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(contentUri, updateValues, null, null)

            // Delete staging file
            file.delete()

            Log.d(TAG, "File moved to MediaStore (modern): $contentUri")
            return contentUri
        }

        return null
    }

    private fun moveToMediaStoreLegacy(file: File, title: String, resolver: ContentResolver): android.net.Uri? {
        // For SDK 28 and below, copy to public Downloads directory
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val appDir = File(downloadsDir, "Share2ArchiveToday")

        // Create directory if it doesn't exist
        if (!appDir.exists()) {
            appDir.mkdirs()
        }

        val destFile = File(appDir, file.name)

        // Copy file to public Downloads
        try {
            file.copyTo(destFile, overwrite = true)
            file.delete() // Remove staging file

            Log.d(TAG, "File moved to public Downloads (legacy): ${destFile.absolutePath}")

            // Add to MediaStore for gallery scanning
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DATA, destFile.absolutePath)
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, destFile.name)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, getMimeType(destFile.absolutePath))
                put(android.provider.MediaStore.MediaColumns.TITLE, title)
                put(android.provider.MediaStore.MediaColumns.DATE_ADDED, System.currentTimeMillis() / 1000)
                put(android.provider.MediaStore.MediaColumns.SIZE, destFile.length())
            }

            // Use the general Files URI for SDK 28
            val uri = resolver.insert(
                android.provider.MediaStore.Files.getContentUri("external"),
                contentValues
            )

            // Trigger media scan for immediate visibility
            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            mediaScanIntent.data = android.net.Uri.fromFile(destFile)
            sendBroadcast(mediaScanIntent)

            return uri ?: android.net.Uri.fromFile(destFile)

        } catch (e: Exception) {
            Log.e(TAG, "Error copying file to public Downloads", e)
            return null
        }
    }

    /**
     * Convert technical error messages to user-friendly messages
     */
    private fun convertToUserFriendlyError(error: String): String {
        return when {
            // Rate limiting (check first to catch rate-limit errors early)
            error.contains("rate-limit", ignoreCase = true) || error.contains("rate limit", ignoreCase = true) ->
                "Your IP address has been rate-limited. Switch to a different network (WiFi/mobile data), add this app to your VPN's split tunneling, or wait a few hours."

            // Login/authentication required
            error.contains("login required", ignoreCase = true) || error.contains("sign in", ignoreCase = true) ->
                "This content requires an account to access. The app cannot download private or login-restricted content."
            error.contains("cookies", ignoreCase = true) && error.contains("login", ignoreCase = true) ->
                "This video requires login or special access that cannot be provided."

            // Format issues
            error.contains("format is not available", ignoreCase = true) ->
                "This video format is not available. Try a different quality setting."
            error.contains("requested format", ignoreCase = true) ->
                "The selected video quality is not available for this video."
            error.contains("no video formats", ignoreCase = true) || error.contains("no formats found", ignoreCase = true) ->
                "No downloadable formats found. The video may be live, DRM-protected, or use an unsupported format."

            // Network errors
            error.contains("network", ignoreCase = true) || error.contains("connection", ignoreCase = true) ->
                "Network connection problem. Please check your internet connection."
            error.contains("timeout", ignoreCase = true) ->
                "Download timed out. Please try again."
            error.contains("ssl", ignoreCase = true) || error.contains("certificate", ignoreCase = true) ->
                "Secure connection error. This may be a temporary issue with the website."

            // Too many requests (general, different from rate-limit)
            error.contains("too many requests", ignoreCase = true) ->
                "Too many requests. Please wait a few hours before trying again."

            // Permissions and storage
            error.contains("permission", ignoreCase = true) ->
                "Permission denied. Please check app permissions."
            error.contains("storage", ignoreCase = true) || error.contains("space", ignoreCase = true) ->
                "Not enough storage space available."

            // Content availability
            error.contains("not found", ignoreCase = true) || error.contains("404", ignoreCase = true) ->
                "Video not found. The link may be invalid or the video may have been removed."
            error.contains("private", ignoreCase = true) || error.contains("unavailable", ignoreCase = true) ->
                "This video is private or unavailable."
            error.contains("removed", ignoreCase = true) && error.contains("violation", ignoreCase = true) ->
                "This content has been removed due to violations."

            // Access restrictions
            error.contains("age", ignoreCase = true) && (error.contains("restrict", ignoreCase = true) || error.contains("verification", ignoreCase = true)) ->
                "This video has age restrictions and cannot be downloaded."
            error.contains("blocked", ignoreCase = true) || error.contains("forbidden", ignoreCase = true) ->
                "This video is blocked or restricted in your region."
            error.contains("geo", ignoreCase = true) && error.contains("block", ignoreCase = true) ->
                "This content is not available in your region."
            error.contains("region", ignoreCase = true) || (error.contains("not available", ignoreCase = true) && error.contains("country", ignoreCase = true)) ->
                "This video is not available in your region."

            // Copyright
            error.contains("copyright", ignoreCase = true) || error.contains("dmca", ignoreCase = true) ->
                "This video cannot be downloaded due to copyright restrictions."

            // Server issues
            error.contains("server error", ignoreCase = true) || error.contains("http 5", ignoreCase = true) ->
                "Server error occurred. Please try again later."

            // URL and extractor issues
            error.contains("invalid url", ignoreCase = true) || error.contains("malformed", ignoreCase = true) ->
                "Invalid video URL. Please check the link and try again."
            error.contains("extractor", ignoreCase = true) || error.contains("unsupported url", ignoreCase = true) ->
                "This video site is not supported or the video format is incompatible."
            error.contains("no suitable extractor", ignoreCase = true) ->
                "This website is not supported for video downloads."

            // Content type issues
            error.contains("live", ignoreCase = true) || error.contains("streaming", ignoreCase = true) ->
                "Live streams cannot be downloaded. Please try with a regular video."
            error.contains("playlist", ignoreCase = true) ->
                "Playlist downloads are not supported. Please try with individual video links."

            // Captcha
            error.contains("captcha", ignoreCase = true) ->
                "Human verification required. The website is requesting verification that cannot be completed automatically."

            else -> {
                // Log the novel error for debugging while showing a generic message
                Log.w(TAG, "Novel error encountered: $error")
                "Download failed. This may be due to website restrictions, temporary server issues, or changes to the video platform. Please try again later."
            }
        }
    }
}


