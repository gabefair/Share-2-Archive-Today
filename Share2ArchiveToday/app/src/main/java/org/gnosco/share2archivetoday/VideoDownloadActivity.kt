package org.gnosco.share2archivetoday

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Activity that extracts and downloads videos from web pages
 */
class VideoDownloadActivity : MainActivity() {
    
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val httpClient = OkHttpClient.Builder().build()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
    
    override fun threeSteps(url: String) {
        val processedUrl = processArchiveUrl(url)
        val cleanedUrl = handleURL(processedUrl)
        extractAndDownloadVideo(cleanedUrl)
    }

    private fun extractAndDownloadVideo(url: String) {
        try {
            // First check if it's a direct video URL
            if (isVideoUrl(url)) {
                downloadVideo(url)
                return
            }
            
            // Try to extract video information from the page
            extractVideoFromPage(url)
            
        } catch (e: Exception) {
            Log.e("VideoDownload", "Failed to process video", e)
            Toast.makeText(this, "Failed to process video: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun extractVideoFromPage(url: String) {
        coroutineScope.launch {
            try {
                // Show initial message
                Toast.makeText(this@VideoDownloadActivity, "Analyzing video page...", Toast.LENGTH_SHORT).show()
                
                // Try to find video elements on the page
                val videoUrls = withContext(Dispatchers.IO) {
                    try {
                        val request = Request.Builder().url(url).build()
                        val response = httpClient.newCall(request).execute()
                        
                        if (response.isSuccessful) {
                            val html = response.body?.string() ?: ""
                            extractVideoUrlsFromHtml(html, url)
                        } else {
                            emptyList()
                        }
                    } catch (e: Exception) {
                        Log.e("VideoDownload", "Error fetching page", e)
                        emptyList()
                    }
                }
                
                if (videoUrls.isNotEmpty()) {
                    // Found video URLs, show options
                    showVideoDownloadOptions(videoUrls, url)
                } else {
                    // No video URLs found, try alternative methods
                    attemptAlternativeVideoExtraction(url)
                }
                
            } catch (e: Exception) {
                Log.e("VideoDownload", "Error extracting video info", e)
                Toast.makeText(this@VideoDownloadActivity, "Could not extract video info. Trying alternative method...", Toast.LENGTH_SHORT).show()
                attemptAlternativeVideoExtraction(url)
            }
        }
    }

    private fun extractVideoUrlsFromHtml(html: String, baseUrl: String): List<VideoInfo> {
        val videoInfos = mutableListOf<VideoInfo>()
        
        // Look for video source tags
        val videoSourcePattern = Regex("<source[^>]*src=[\"']([^\"']*)[\"'][^>]*>")
        val matches = videoSourcePattern.findAll(html)
        
        for (match in matches) {
            val src = match.groupValues[1]
            if (src.isNotEmpty()) {
                val fullUrl = if (src.startsWith("http")) src else "$baseUrl$src"
                videoInfos.add(VideoInfo("Video Source", fullUrl, "mp4"))
            }
        }
        
        // Look for video tags
        val videoTagPattern = Regex("<video[^>]*src=[\"']([^\"']*)[\"'][^>]*>")
        val videoMatches = videoTagPattern.findAll(html)
        
        for (match in videoMatches) {
            val src = match.groupValues[1]
            if (src.isNotEmpty()) {
                val fullUrl = if (src.startsWith("http")) src else "$baseUrl$src"
                videoInfos.add(VideoInfo("Video Element", fullUrl, "mp4"))
            }
        }
        
        // Look for iframe embeds that might contain videos
        val iframePattern = Regex("<iframe[^>]*src=[\"']([^\"']*)[\"'][^>]*>")
        val iframeMatches = iframePattern.findAll(html)
        
        for (match in iframeMatches) {
            val src = match.groupValues[1]
            if (src.isNotEmpty() && (src.contains("youtube.com") || src.contains("vimeo.com") || src.contains("dailymotion.com"))) {
                videoInfos.add(VideoInfo("Embedded Video", src, "embedded"))
            }
        }
        
        // Look for common video URL patterns in the HTML
        val videoUrlPatterns = listOf(
            Regex("https?://[^\\s\"']*\\.(mp4|avi|mov|wmv|flv|webm|mkv|m4v|3gp|ogv)"),
            Regex("https?://[^\\s\"']*/video/[^\\s\"']*"),
            Regex("https?://[^\\s\"']*/media/[^\\s\"']*"),
            Regex("https?://[^\\s\"']*/stream/[^\\s\"']*")
        )
        
        for (pattern in videoUrlPatterns) {
            val urlMatches = pattern.findAll(html)
            for (urlMatch in urlMatches) {
                val videoUrl = urlMatch.value
                val extension = videoUrl.substringAfterLast(".", "mp4")
                videoInfos.add(VideoInfo("Direct Video", videoUrl, extension))
            }
        }
        
        return videoInfos.distinctBy { it.url }
    }

    private fun showVideoDownloadOptions(videoInfos: List<VideoInfo>, originalUrl: String) {
        if (videoInfos.isEmpty()) {
            Toast.makeText(this, "No videos found on this page", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // For now, we'll download the first video found
        // In a full implementation, you'd show a dialog with options
        val firstVideo = videoInfos.first()
        Toast.makeText(this, "Found video: ${firstVideo.title}", Toast.LENGTH_LONG).show()
        
        // Start download
        downloadVideo(firstVideo.url)
    }

    private fun attemptAlternativeVideoExtraction(url: String) {
        // Try to find video URLs using alternative methods
        coroutineScope.launch {
            try {
                // Check if the URL itself might be a video
                if (isVideoUrl(url)) {
                    downloadVideo(url)
                    return@launch
                }
                
                // Try to find video URLs in the page content
                val videoUrls = withContext(Dispatchers.IO) {
                    try {
                        val request = Request.Builder().url(url).build()
                        val response = httpClient.newCall(request).execute()
                        
                        if (response.isSuccessful) {
                            val html = response.body?.string() ?: ""
                            extractVideoUrlsFromHtml(html, url)
                        } else {
                            emptyList()
                        }
                    } catch (e: Exception) {
                        Log.e("VideoDownload", "Error in alternative extraction", e)
                        emptyList()
                    }
                }
                
                if (videoUrls.isNotEmpty()) {
                    showVideoDownloadOptions(videoUrls, url)
                } else {
                    Toast.makeText(this@VideoDownloadActivity, "No videos found. Try the archive option first, then look for download options.", Toast.LENGTH_LONG).show()
                    finish()
                }
                
            } catch (e: Exception) {
                Log.e("VideoDownload", "Error in alternative video extraction", e)
                Toast.makeText(this@VideoDownloadActivity, "Could not extract video. Try the archive option first.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun isVideoUrl(url: String): Boolean {
        val videoExtensions = listOf(".mp4", ".avi", ".mov", ".wmv", ".flv", ".webm", ".mkv", ".m4v", ".3gp", ".ogv")
        
        val uri = Uri.parse(url)
        val path = uri.path ?: ""
        
        // Check if URL has video file extension
        if (videoExtensions.any { path.endsWith(it, ignoreCase = true) }) {
            return true
        }
        
        // Check for common video URL patterns
        val videoPatterns = listOf(
            "/video/", "/videos/", "/media/", "/stream/", "/play/", "/watch/",
            "video.mp4", "video.webm", "video.avi", "stream.mp4", "media.mp4"
        )
        
        if (videoPatterns.any { path.lowercase().contains(it) }) {
            return true
        }
        
        // Check query parameters that might indicate video content
        val videoQueryParams = listOf("video", "media", "stream", "play")
        if (videoQueryParams.any { uri.getQueryParameter(it) != null }) {
            return true
        }
        
        return false
    }

    private fun downloadVideo(url: String) {
        try {
            // Check if we have enough storage space
            if (!hasEnoughStorageSpace()) {
                Toast.makeText(this, "Insufficient storage space for video download", Toast.LENGTH_LONG).show()
                finish()
                return
            }
            
            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val uri = Uri.parse(url)
            
            // Create a filename from the URL
            val fileName = generateFileName(url)
            
            val request = DownloadManager.Request(uri)
                .setTitle("Downloading video")
                .setDescription("Downloading video from: $url")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                .setAllowedOverRoaming(false)
                .setMimeType("video/*")
            
            downloadManager.enqueue(request)
            
            Toast.makeText(this, "Video download started: $fileName", Toast.LENGTH_SHORT).show()
            finish()
            
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to start download: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun hasEnoughStorageSpace(): Boolean {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val freeSpace = downloadsDir.freeSpace
            // Require at least 100MB free space
            return freeSpace > 100 * 1024 * 1024
        } catch (e: Exception) {
            // If we can't check storage, assume it's okay
            return true
        }
    }

    private fun generateFileName(url: String): String {
        val uri = Uri.parse(url)
        val path = uri.path ?: ""
        
        // Try to extract filename from path
        if (path.isNotEmpty()) {
            val segments = path.split("/")
            val lastSegment = segments.lastOrNull()
            if (lastSegment != null && lastSegment.contains(".")) {
                // Clean up the filename and ensure it has a video extension
                val cleanName = lastSegment.split("?")[0].split("#")[0] // Remove query params and fragments
                if (isVideoExtension(cleanName)) {
                    return cleanName
                }
            }
        }
        
        // Fallback: generate filename from host and timestamp
        val host = uri.host?.replace(".", "_") ?: "unknown"
        val timestamp = System.currentTimeMillis()
        return "${host}_${timestamp}.mp4"
    }

    private fun isVideoExtension(filename: String): Boolean {
        val videoExtensions = listOf(".mp4", ".avi", ".mov", ".wmv", ".flv", ".webm", ".mkv", ".m4v", ".3gp", ".ogv")
        return videoExtensions.any { filename.lowercase().endsWith(it) }
    }

    override fun openInBrowser(url: String) {
        // Override to prevent browser opening - this should never be called in video download mode
        extractAndDownloadVideo(url)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}

/**
 * Data class to hold video information
 */
data class VideoInfo(
    val title: String,
    val url: String,
    val format: String
)
