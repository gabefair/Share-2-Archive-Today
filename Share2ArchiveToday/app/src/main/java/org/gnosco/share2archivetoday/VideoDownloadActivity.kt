package org.gnosco.share2archivetoday

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
// import com.yausername.ffmpeg.FFmpeg
import kotlinx.coroutines.*
import java.io.File
import kotlinx.coroutines.delay

/**
 * Activity that downloads videos using youtubedl-android library
 */
class VideoDownloadActivity : MainActivity() {
    
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentProcessId: String? = null
    private var isDownloadInProgress = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d("VideoDownload", "VideoDownloadActivity onCreate started")
        Log.d("VideoDownload", "Network status: ${if (isNetworkAvailable()) "Available" else "Not Available"}")
        
        // Initialize parent class components manually to avoid calling handleShareIntent
        clearUrlsRulesManager = ClearUrlsRulesManager(applicationContext)
        qrCodeScanner = QRCodeScanner(applicationContext)
        notificationHelper = NotificationHelper(applicationContext)
        
        // Initialize youtubedl-android with FFmpeg support
        try {
            Log.d("VideoDownload", "Initializing YoutubeDL...")
            YoutubeDL.getInstance().init(this)
            Log.d("VideoDownload", "YoutubeDL initialized successfully")
            
            // Initialize FFmpeg for audio extraction and format conversion
            try {
                Log.d("VideoDownload", "Initializing FFmpeg...")
                // FFmpeg.getInstance().init(this) // Commented out as FFmpeg AAR is not available
                Log.d("VideoDownload", "FFmpeg initialization skipped (FFmpeg AAR not available)")
            } catch (e: Exception) {
                Log.w("VideoDownload", "FFmpeg not available, continuing without it", e)
            }
            Log.d("VideoDownload", "youtubedl-android initialized successfully")
        } catch (e: YoutubeDLException) {
            Log.e("VideoDownload", "Failed to initialize youtubedl-android", e)
            Log.e("VideoDownload", "Exception type: ${e.javaClass.simpleName}")
            Log.e("VideoDownload", "Exception message: ${e.message}")
            e.printStackTrace()
            Toast.makeText(this, "Failed to initialize video downloader", Toast.LENGTH_LONG).show()
            notificationHelper.showGeneralNotification(
                "Video Download",
                "Failed to initialize video downloader"
            )
            finish()
            return
        }
        
        Log.d("VideoDownload", "VideoDownloadActivity onCreate completed successfully")
        
        // Handle the share intent for video downloads
        handleVideoShareIntent(intent)
    }
    
    /**
     * Override handleShareIntent to prevent immediate finish() for video downloads
     */
    override fun handleShareIntent(intent: Intent?) {
        // This method should not be called directly from onCreate in VideoDownloadActivity
        // It's overridden to prevent the parent class from finishing the activity immediately
        Log.d("VideoDownload", "handleShareIntent called - this should not happen in VideoDownloadActivity")
    }
    
    /**
     * Handle video share intent specifically for this activity
     */
    private fun handleVideoShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedText ->
                Log.d("VideoDownload", "Processing video share intent: $sharedText")
                val url = extractUrl(sharedText)
                
                if (url != null) {
                    // Start the download process
                    threeSteps(url)
                } else {
                    Log.d("VideoDownload", "No URL found in shared text")
                    Toast.makeText(this, "No URL found in shared text", Toast.LENGTH_SHORT).show()
                    notificationHelper.showGeneralNotification(
                        "Video Download",
                        "No URL found in shared text"
                    )
                    cleanupCoroutineScope()
                    finish()
                }
            }
        } else {
            // Not a text share, finish immediately
            cleanupCoroutineScope()
            finish()
        }
    }
    
    /**
     * Handle new intents that come in while the activity is running
     */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d("VideoDownload", "onNewIntent called with: ${intent?.action}")
        
        // If this is a new share intent, handle it
        if (intent?.action == Intent.ACTION_SEND) {
            handleVideoShareIntent(intent)
        }
    }
    
    override fun threeSteps(url: String) {
        // Check if activity is still valid before proceeding
        if (isFinishing || isDestroyed) {
            Log.d("VideoDownload", "Activity destroyed, skipping threeSteps")
            cleanupCoroutineScope()
            return
        }
        
        val processedUrl = processArchiveUrl(url)
        val cleanedUrl = handleURL(processedUrl)
        
        // Check if the URL is likely to contain a video
        if (isLikelyVideoUrl(cleanedUrl)) {
            checkNetworkAndProceed(cleanedUrl)
        } else {
            Toast.makeText(this, "This URL doesn't appear to contain a video. Trying anyway...", Toast.LENGTH_LONG).show()
            notificationHelper.showGeneralNotification(
                "Video Download",
                "This URL doesn't appear to contain a video. Trying anyway..."
            )
            checkNetworkAndProceed(cleanedUrl)
        }
    }
    
    private fun isLikelyVideoUrl(url: String): Boolean {
        val videoHosts = listOf(
            "youtube.com", "youtu.be", "vimeo.com", "dailymotion.com", "twitch.tv",
            "facebook.com", "instagram.com", "twitter.com", "tiktok.com", "reddit.com",
            "tumblr.com", "pinterest.com", "linkedin.com", "snapchat.com", "discord.com"
        )
        
        val lowerUrl = url.lowercase()
        return videoHosts.any { host -> lowerUrl.contains(host) }
    }
    
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }
    
    private fun checkNetworkAndProceed(url: String) {
        // Check if activity is still valid before proceeding
        if (isFinishing || isDestroyed) {
            Log.d("VideoDownload", "Activity destroyed, skipping network check")
            cleanupCoroutineScope()
            return
        }
        
        if (!isNetworkAvailable()) {
            Log.e("VideoDownload", "No network connection available")
            Toast.makeText(this, "No internet connection available", Toast.LENGTH_LONG).show()
            cleanupCoroutineScope()
            finish()
            return
        }
        
        Log.d("VideoDownload", "Network connection available, proceeding with download")
        
        // Set download in progress flag BEFORE starting the network test
        // This prevents the coroutine scope from being cancelled in onDestroy
        isDownloadInProgress = true
        Log.d("VideoDownload", "Setting isDownloadInProgress = true before network test")
        
        // Start the network connectivity test
        testNetworkConnectivity { isConnected ->
            // This callback runs after the activity is finished, so just handle the result
            if (isConnected) {
                // Start the download process
                downloadVideo(url)
            } else {
                Log.e("VideoDownload", "Network connectivity test failed")
                notificationHelper.showGeneralNotification(
                    "Video Download",
                    "Network connectivity test failed"
                )
                // Reset flag since download failed
                isDownloadInProgress = false
                cleanupCoroutineScope()
            }
        }
        
        // Since this is a NoDisplay theme activity, we must finish after starting the download process
        // The download will continue in the background via coroutines
        Log.d("VideoDownload", "Download process started, finishing activity")
        finish()
    }
    
    private fun testNetworkConnectivity(callback: (Boolean) -> Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                Log.d("VideoDownload", "Starting network connectivity test...")
                Log.d("VideoDownload", "Current thread: ${Thread.currentThread().name}")
                
                // Check if the coroutine is still active before proceeding
                if (!isActive) {
                    Log.d("VideoDownload", "Coroutine cancelled before network test, aborting")
                    cleanupCoroutineScope()
                    return@launch
                }
                
                // Try to connect to a reliable service
                val url = java.net.URL("https://www.google.com")
                Log.d("VideoDownload", "Testing connection to: ${url}")
                
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.requestMethod = "HEAD"
                
                Log.d("VideoDownload", "Attempting to connect...")
                val responseCode = connection.responseCode
                Log.d("VideoDownload", "Network test response code: $responseCode")
                
                connection.disconnect()
                
                val isConnected = responseCode in 200..399
                Log.d("VideoDownload", "Network connectivity test result: $isConnected")
                
                // Check if still active before calling callback
                if (isActive) {
                    withContext(Dispatchers.Main) {
                        // Don't check activity state - we want the download to continue even after activity is destroyed
                        callback(isConnected)
                    }
                } else {
                    Log.d("VideoDownload", "Coroutine cancelled after network test, skipping callback")
                    cleanupCoroutineScope()
                }
            } catch (e: Exception) {
                Log.e("VideoDownload", "Network connectivity test failed", e)
                Log.e("VideoDownload", "Exception type: ${e.javaClass.simpleName}")
                Log.e("VideoDownload", "Exception message: ${e.message}")
                Log.e("VideoDownload", "Current thread: ${Thread.currentThread().name}")
                e.printStackTrace()
                
                // Check if still active before calling callback
                if (isActive) {
                    withContext(Dispatchers.Main) {
                        // Don't check activity state - we want the download to continue even after activity is destroyed
                        callback(false)
                    }
                } else {
                    Log.d("VideoDownload", "Coroutine cancelled during error handling, skipping callback")
                    cleanupCoroutineScope()
                }
            }
        }
    }
    
    private fun formatViewCount(viewCount: Long): String {
        return when {
            viewCount >= 1_000_000_000 -> "${viewCount / 1_000_000_000}B"
            viewCount >= 1_000_000 -> "${viewCount / 1_000_000}M"
            viewCount >= 1_000 -> "${viewCount / 1_000}K"
            else -> viewCount.toString()
        }
    }

    private fun downloadVideo(url: String) {
        coroutineScope.launch {
            try {
                // Check if coroutine is still active before proceeding
                if (!isActive) {
                    Log.d("VideoDownload", "Coroutine cancelled before download process, aborting")
                    isDownloadInProgress = false
                    cleanupCoroutineScope()
                    return@launch
                }
                
                // isDownloadInProgress is already set to true in checkNetworkAndProceed
                Log.d("VideoDownload", "Starting download process for URL: $url")
                Log.d("VideoDownload", "Network status: ${if (isNetworkAvailable()) "Available" else "Not Available"}")
                Log.d("VideoDownload", "Current thread: ${Thread.currentThread().name}")
                
                // Show initial message via notification only (activity may be finished)
                notificationHelper.showDownloadNotification(
                    "Video Download",
                    "Analyzing video...",
                    -1,
                    true
                )
                
                // First, get video info to check if it's downloadable
                val videoInfo = withContext(Dispatchers.IO) {
                    try {
                        Log.d("VideoDownload", "Attempting to get video info from: $url")
                        Log.d("VideoDownload", "YoutubeDL instance: ${YoutubeDL.getInstance()}")
                        Log.d("VideoDownload", "Current thread: ${Thread.currentThread().name}")
                        
                        val info = YoutubeDL.getInstance().getInfo(url)
                        Log.d("VideoDownload", "Successfully retrieved video info: ${info.title}")
                        info
                    } catch (e: Exception) {
                        Log.e("VideoDownload", "Error getting video info from $url", e)
                        Log.e("VideoDownload", "Exception type: ${e.javaClass.simpleName}")
                        Log.e("VideoDownload", "Exception message: ${e.message}")
                        Log.e("VideoDownload", "Current thread: ${Thread.currentThread().name}")
                        e.printStackTrace()
                        null
                    }
                }
                
                // Check if still active before proceeding
                if (!isActive) {
                    Log.d("VideoDownload", "Coroutine cancelled, aborting download")
                    isDownloadInProgress = false
                    cleanupCoroutineScope()
                    return@launch
                }
                
                if (videoInfo == null) {
                    notificationHelper.showGeneralNotification(
                        "Video Download",
                        "Could not analyze video. Trying direct download..."
                    )
                    // Try direct download anyway
                    startDownload(url, "video")
                } else {
                    // Video info found, start download
                    val title = videoInfo.title ?: "video"
                    val format = videoInfo.ext ?: "mp4"
                    val duration = videoInfo.duration
                    val viewCount = videoInfo.viewCount
                    val uploader = videoInfo.uploader
                    
                    val durationText = if (duration != null) {
                        val minutes = duration / 60
                        val seconds = duration % 60
                        " (${minutes}:${String.format("%02d", seconds)})"
                    } else ""
                    
                    val uploaderText = if (uploader != null) " by $uploader" else ""
                    val viewText = if (viewCount != null) " • ${formatViewCount(viewCount.toLongOrNull() ?: 0L)} views" else ""
                    
                    val infoText = "$title$durationText$uploaderText$viewText"
                    notificationHelper.showGeneralNotification(
                        "Video Download",
                        "Found: $infoText"
                    )
                    startDownload(url, title, format)
                }
                
            } catch (e: Exception) {
                Log.e("VideoDownload", "Error in download process", e)
                
                // Reset download in progress flag
                isDownloadInProgress = false
                Log.d("VideoDownload", "Download error, setting isDownloadInProgress = false")
                
                // Clean up coroutine scope since download failed
                cleanupCoroutineScope()
                
                // Show error via notification only (activity may be finished)
                notificationHelper.showGeneralNotification(
                    "Video Download",
                    "Error: ${e.message}"
                )
            }
        }
    }

    private fun startDownload(url: String, title: String, format: String = "mp4") {
        try {
            // Create download directory
            val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Share2Archive")
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }
            
            // Generate filename
            val safeTitle = title.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(100)
            val filename = "${safeTitle}.${format}"
            val outputPath = "${downloadDir.absolutePath}/${filename}"
            
            // Create download request with better format selection
            val request = YoutubeDLRequest(url)
            request.addOption("-o", outputPath)
            // Try to get the best quality available, fallback to best overall
            request.addOption("-f", "bestvideo[ext=${format}]+bestaudio[ext=m4a]/best[ext=${format}]/best")
            request.addOption("--no-mtime")
            request.addOption("--no-playlist")
            request.addOption("--no-warnings")
            request.addOption("--ignore-errors")
            request.addOption("--extract-audio")
            request.addOption("--audio-format", "mp3")
            request.addOption("--audio-quality", "0")
            
            // Generate unique process ID
            currentProcessId = "download_${System.currentTimeMillis()}"
            
            // Start download with progress callback
            try {
                Log.d("VideoDownload", "Starting download execution for: $url")
                Log.d("VideoDownload", "Output path: $outputPath")
                Log.d("VideoDownload", "Process ID: $currentProcessId")
                
                YoutubeDL.getInstance().execute(request, currentProcessId) { progress, etaInSeconds, output ->
                    // Progress callback with more informative messages
                    Log.d("VideoDownload", "Progress callback: $progress%, ETA: ${etaInSeconds}s, Output: $output")
                    
                    val progressText = when {
                        progress < 0f -> "Preparing download..."
                        progress == 0f -> "Starting download..."
                        progress < 10f -> "Downloading: ${progress}% (ETA: ${etaInSeconds}s)"
                        progress < 50f -> "Downloading: ${progress}% (ETA: ${etaInSeconds}s)"
                        progress < 90f -> "Downloading: ${progress}% (ETA: ${etaInSeconds}s)"
                        progress < 100f -> "Finalizing: ${progress}% (ETA: ${etaInSeconds}s)"
                        else -> "Download complete!"
                    }
                    
                    // Update notification directly (no need for runOnUiThread since activity is finished)
                    notificationHelper.updateDownloadNotification(
                        "Video Download",
                        progressText,
                        progress.toInt()
                    )
                    
                    // If download is complete, handle completion
                    if (progress >= 100f) {
                        onDownloadComplete(outputPath, filename)
                    }
                    
                    // Handle download errors (negative progress values)
                    if (progress < -1f) {
                        handleDownloadError(progress.toInt())
                    }
                    
                    // Log progress for debugging
                    Log.d("VideoDownload", "Progress: $progress%, ETA: ${etaInSeconds}s")
                }
                
                notificationHelper.showGeneralNotification(
                    "Video Download",
                    "Download started: $filename"
                )
                
            } catch (e: Exception) {
                Log.e("VideoDownload", "Error during download execution", e)
                Log.e("VideoDownload", "Exception type: ${e.javaClass.simpleName}")
                Log.e("VideoDownload", "Exception message: ${e.message}")
                e.printStackTrace()
                
                // Reset download in progress flag
                isDownloadInProgress = false
                Log.d("VideoDownload", "Download execution error, setting isDownloadInProgress = false")
                
                // Clean up coroutine scope since download failed
                cleanupCoroutineScope()
                
                notificationHelper.showGeneralNotification(
                    "Video Download",
                    "Download execution failed: ${e.message}"
                )
            }
            
        } catch (e: Exception) {
            Log.e("VideoDownload", "Error starting download", e)
            
            // Reset download in progress flag
            isDownloadInProgress = false
            Log.d("VideoDownload", "Download start error, setting isDownloadInProgress = false")
            
            // Clean up coroutine scope since download failed
            cleanupCoroutineScope()
            
            notificationHelper.showGeneralNotification(
                "Video Download",
                "Failed to start download: ${e.message}"
            )
        }
    }

    private fun onDownloadComplete(filePath: String, filename: String) {
        try {
            // Reset download in progress flag
            isDownloadInProgress = false
            Log.d("VideoDownload", "Download completed, setting isDownloadInProgress = false")
            
            val file = File(filePath)
            if (file.exists() && file.length() > 0) {
                val fileSizeMB = file.length() / (1024 * 1024)
                val fileSizeText = if (fileSizeMB > 0) " (${fileSizeMB}MB)" else ""
                notificationHelper.completeDownloadNotification(
                    "Video Download",
                    "Download complete!$fileSizeText Sharing video..."
                )
                
                // Small delay to ensure file is fully written
                coroutineScope.launch {
                    delay(1000)
                    shareVideo(file)
                }
                
                // Clean up coroutine scope since download is complete
                cleanupCoroutineScope()
            } else {
                notificationHelper.showGeneralNotification(
                    "Video Download",
                    "Download failed or file is empty"
                )
                cleanupCoroutineScope()
            }
        } catch (e: Exception) {
            Log.e("VideoDownload", "Error handling download completion", e)
            
            // Reset download in progress flag
            isDownloadInProgress = false
            Log.d("VideoDownload", "Download completion error, setting isDownloadInProgress = false")
            
            // Clean up coroutine scope since download failed
            cleanupCoroutineScope()
            
            // Show error via notification only (activity may be finished)
            notificationHelper.showGeneralNotification(
                "Video Download",
                "Error sharing video: ${e.message}"
            )
        }
    }

    private fun shareVideo(videoFile: File) {
        try {
            val videoUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                // Use FileProvider for Android 7+ (API 24+)
                val authority = "${applicationContext.packageName}.fileprovider"
                val contentUri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    authority,
                    videoFile
                )
                contentUri
            } else {
                // Fallback for older Android versions
                Uri.fromFile(videoFile)
            }
            
            // Determine the MIME type based on file extension
            val mimeType = when (videoFile.extension.lowercase()) {
                "mp4" -> "video/mp4"
                "webm" -> "video/webm"
                "mkv" -> "video/x-matroska"
                "avi" -> "video/x-msvideo"
                "mov" -> "video/quicktime"
                "mp3" -> "audio/mpeg"
                "m4a" -> "audio/mp4"
                "wav" -> "audio/wav"
                else -> "video/*"
            }
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, videoUri)
                putExtra(Intent.EXTRA_SUBJECT, "Media downloaded with Share2Archive")
                putExtra(Intent.EXTRA_TEXT, "Check out this media I downloaded!")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            // Start the share activity with new task flag since this activity is finished
            applicationContext.startActivity(Intent.createChooser(shareIntent, "Share Media"))
            
            // Activity is already finished, no need to call finish() again
            
        } catch (e: Exception) {
            Log.e("VideoDownload", "Error sharing video", e)
            
            // Show error via notification only (activity is already finished)
            notificationHelper.showGeneralNotification(
                "Video Download",
                "Error sharing video: ${e.message}"
            )
            
            // Clean up coroutine scope since sharing failed
            cleanupCoroutineScope()
        }
    }

    private fun handleDownloadError(errorCode: Int) {
        val errorMessage = when (errorCode) {
            -2 -> "Download failed: Video not available"
            -3 -> "Download failed: Network error"
            -4 -> "Download failed: Format not supported"
            -5 -> "Download failed: Access denied"
            else -> "Download failed: Unknown error (code: $errorCode)"
        }
        
        // Reset download in progress flag
        isDownloadInProgress = false
        Log.d("VideoDownload", "Download error, setting isDownloadInProgress = false")
        
        // Clean up coroutine scope since download failed
        cleanupCoroutineScope()
        
        // Show error via notification only (activity is already finished)
        notificationHelper.showGeneralNotification(
            "Video Download",
            "Download error: $errorMessage"
        )
        Log.e("VideoDownload", "Download error: $errorMessage")
    }

    override fun openInBrowser(url: String) {
        // Override to prevent browser opening - this should never be called in video download mode
        // Since the activity is already finished, just start the download process
        downloadVideo(url)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        Log.d("VideoDownload", "onDestroy called, download in progress: $isDownloadInProgress")
        
        // Cancel any ongoing download
        currentProcessId?.let { processId ->
            try {
                YoutubeDL.getInstance().destroyProcessById(processId)
                Log.d("VideoDownload", "Download process cancelled: $processId")
            } catch (e: Exception) {
                Log.e("VideoDownload", "Error cancelling download process", e)
            }
        }
        
        // Clean up notifications
        notificationHelper.cancelAllNotifications()
        
        // Only cancel coroutines if no download is in progress
        if (!isDownloadInProgress) {
            Log.d("VideoDownload", "No download in progress, cancelling coroutine scope")
            coroutineScope.cancel("Activity destroyed")
        } else {
            Log.d("VideoDownload", "Download in progress, keeping coroutine scope alive")
        }
    }
    
    /**
     * Clean up coroutine scope when download is really finished
     */
    private fun cleanupCoroutineScope() {
        if (!isDownloadInProgress) {
            Log.d("VideoDownload", "Cleaning up coroutine scope")
            coroutineScope.cancel("Download finished")
        }
    }
}

