package org.gnosco.share2archivetoday

import android.app.Activity
import android.content.Intent
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
import com.yausername.ffmpeg.FFmpeg
import kotlinx.coroutines.*
import java.io.File
import kotlinx.coroutines.delay

/**
 * Activity that downloads videos using youtubedl-android library
 */
class VideoDownloadActivity : MainActivity() {
    
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentProcessId: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize youtubedl-android with FFmpeg support
        try {
            YoutubeDL.getInstance().init(this)
            // Initialize FFmpeg for audio extraction and format conversion
            try {
                FFmpeg.getInstance().init(this)
                Log.d("VideoDownload", "FFmpeg initialized successfully")
            } catch (e: Exception) {
                Log.w("VideoDownload", "FFmpeg not available, continuing without it", e)
            }
            Log.d("VideoDownload", "youtubedl-android initialized successfully")
        } catch (e: YoutubeDLException) {
            Log.e("VideoDownload", "Failed to initialize youtubedl-android", e)
            Toast.makeText(this, "Failed to initialize video downloader", Toast.LENGTH_LONG).show()
            finish()
            return
        }
    }
    
    override fun threeSteps(url: String) {
        val processedUrl = processArchiveUrl(url)
        val cleanedUrl = handleURL(processedUrl)
        
        // Check if the URL is likely to contain a video
        if (isLikelyVideoUrl(cleanedUrl)) {
            downloadVideo(cleanedUrl)
        } else {
            Toast.makeText(this, "This URL doesn't appear to contain a video. Trying anyway...", Toast.LENGTH_LONG).show()
            downloadVideo(cleanedUrl)
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
                // Show initial message
                Toast.makeText(this@VideoDownloadActivity, "Analyzing video...", Toast.LENGTH_SHORT).show()
                
                // First, get video info to check if it's downloadable
                val videoInfo = withContext(Dispatchers.IO) {
                    try {
                        YoutubeDL.getInstance().getInfo(url)
                    } catch (e: Exception) {
                        Log.e("VideoDownload", "Error getting video info", e)
                        null
                    }
                }
                
                if (videoInfo == null) {
                    Toast.makeText(this@VideoDownloadActivity, "Could not analyze video. Trying direct download...", Toast.LENGTH_LONG).show()
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
                    Toast.makeText(this@VideoDownloadActivity, "Found: $infoText", Toast.LENGTH_LONG).show()
                    startDownload(url, title, format)
                }
                
            } catch (e: Exception) {
                Log.e("VideoDownload", "Error in download process", e)
                Toast.makeText(this@VideoDownloadActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
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
            YoutubeDL.getInstance().execute(request, currentProcessId) { progress, etaInSeconds, output ->
                // Progress callback with more informative messages
                val progressText = when {
                    progress < 0f -> "Preparing download..."
                    progress == 0f -> "Starting download..."
                    progress < 10f -> "Downloading: ${progress}% (ETA: ${etaInSeconds}s)"
                    progress < 50f -> "Downloading: ${progress}% (ETA: ${etaInSeconds}s)"
                    progress < 90f -> "Downloading: ${progress}% (ETA: ${etaInSeconds}s)"
                    progress < 100f -> "Finalizing: ${progress}% (ETA: ${etaInSeconds}s)"
                    else -> "Download complete!"
                }
                
                runOnUiThread {
                    Toast.makeText(this@VideoDownloadActivity, progressText, Toast.LENGTH_SHORT).show()
                }
                
                // If download is complete, share the file
                if (progress >= 100f) {
                    runOnUiThread {
                        onDownloadComplete(outputPath, filename)
                    }
                }
                
                // Handle download errors (negative progress values)
                if (progress < -1f) {
                    runOnUiThread {
                        handleDownloadError(progress.toInt())
                    }
                }
                
                // Log progress for debugging
                Log.d("VideoDownload", "Progress: $progress%, ETA: ${etaInSeconds}s")
            }
            
            Toast.makeText(this, "Download started: $filename", Toast.LENGTH_LONG).show()
            
        } catch (e: Exception) {
            Log.e("VideoDownload", "Error starting download", e)
            Toast.makeText(this, "Failed to start download: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun onDownloadComplete(filePath: String, filename: String) {
        try {
            val file = File(filePath)
            if (file.exists() && file.length() > 0) {
                val fileSizeMB = file.length() / (1024 * 1024)
                val fileSizeText = if (fileSizeMB > 0) " (${fileSizeMB}MB)" else ""
                Toast.makeText(this, "Download complete!$fileSizeText Sharing video...", Toast.LENGTH_LONG).show()
                
                // Small delay to ensure file is fully written
                coroutineScope.launch {
                    delay(1000)
                    shareVideo(file)
                }
            } else {
                Toast.makeText(this, "Download failed or file is empty", Toast.LENGTH_LONG).show()
                finish()
            }
        } catch (e: Exception) {
            Log.e("VideoDownload", "Error handling download completion", e)
            Toast.makeText(this, "Error sharing video: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
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
            }
            
            // Start the share activity
            startActivity(Intent.createChooser(shareIntent, "Share Media"))
            
            // Finish this activity after sharing
            finish()
            
        } catch (e: Exception) {
            Log.e("VideoDownload", "Error sharing video", e)
            Toast.makeText(this, "Error sharing video: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
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
        
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        Log.e("VideoDownload", "Download error: $errorMessage")
        
        // Wait a bit before finishing to show the error message
        coroutineScope.launch {
            delay(2000)
            finish()
        }
    }

    override fun openInBrowser(url: String) {
        // Override to prevent browser opening - this should never be called in video download mode
        downloadVideo(url)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Cancel any ongoing download
        currentProcessId?.let { processId ->
            try {
                YoutubeDL.getInstance().destroyProcessById(processId)
                Log.d("VideoDownload", "Download process cancelled: $processId")
            } catch (e: Exception) {
                Log.e("VideoDownload", "Error cancelling download process", e)
            }
        }
        
        coroutineScope.cancel()
    }
}
