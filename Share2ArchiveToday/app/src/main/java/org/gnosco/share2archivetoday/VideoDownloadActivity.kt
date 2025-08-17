package org.gnosco.share2archivetoday

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import java.io.File

/**
 * Activity that extracts and downloads videos from web pages instead of archiving them
 */
class VideoDownloadActivity : MainActivity() {
    
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
            
            // Check if it's a known video hosting site that we can handle
            if (isVideoHostingSite(url)) {
                handleVideoHostingSite(url)
                return
            }
            
            // For other URLs, show a message about the current limitations
            Toast.makeText(this, "Video extraction from general web pages requires additional implementation. Try sharing a direct video URL or from supported video sites.", Toast.LENGTH_LONG).show()
            finish()
            
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to process video: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
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

    private fun isVideoHostingSite(url: String): Boolean {
        val videoHosts = listOf(
            "youtube.com", "youtu.be", "vimeo.com", "dailymotion.com", "twitch.tv",
            "facebook.com", "fb.com", "instagram.com", "tiktok.com", "reddit.com",
            "twitter.com", "x.com", "linkedin.com", "pinterest.com"
        )
        val uri = Uri.parse(url)
        return videoHosts.any { uri.host?.contains(it) == true }
    }

    private fun handleVideoHostingSite(url: String) {
        val uri = Uri.parse(url)
        val host = uri.host ?: ""
        
        when {
            host.contains("youtube.com") || host.contains("youtu.be") -> {
                Toast.makeText(this, "YouTube videos cannot be directly downloaded due to terms of service. Consider using the archive option instead.", Toast.LENGTH_LONG).show()
                finish()
            }
            host.contains("vimeo.com") -> {
                Toast.makeText(this, "Vimeo videos have download restrictions. Try the archive option or check if the video has a download button.", Toast.LENGTH_LONG).show()
                finish()
            }
            host.contains("dailymotion.com") -> {
                Toast.makeText(this, "Dailymotion videos have download restrictions. Try the archive option instead.", Toast.LENGTH_LONG).show()
                finish()
            }
            host.contains("twitch.tv") -> {
                Toast.makeText(this, "Twitch videos cannot be downloaded due to platform restrictions. Try the archive option instead.", Toast.LENGTH_LONG).show()
                finish()
            }
            host.contains("facebook.com") || host.contains("fb.com") -> {
                Toast.makeText(this, "Facebook videos have download restrictions. Try the archive option first.", Toast.LENGTH_LONG).show()
                finish()
            }
            host.contains("instagram.com") -> {
                Toast.makeText(this, "Instagram videos have download restrictions. Try the archive option first.", Toast.LENGTH_LONG).show()
                finish()
            }
            host.contains("tiktok.com") -> {
                Toast.makeText(this, "TikTok videos have download restrictions. Try the archive option first.", Toast.LENGTH_LONG).show()
                finish()
            }
            host.contains("reddit.com") -> {
                Toast.makeText(this, "Reddit videos may have download restrictions. Try the archive option first.", Toast.LENGTH_LONG).show()
                finish()
            }
            host.contains("twitter.com") || host.contains("x.com") -> {
                Toast.makeText(this, "Twitter/X videos have download restrictions. Try the archive option first.", Toast.LENGTH_LONG).show()
                finish()
            }
            host.contains("linkedin.com") -> {
                Toast.makeText(this, "LinkedIn videos have download restrictions. Try the archive option first.", Toast.LENGTH_LONG).show()
                finish()
            }
            host.contains("pinterest.com") -> {
                Toast.makeText(this, "Pinterest videos have download restrictions. Try the archive option first.", Toast.LENGTH_LONG).show()
                finish()
            }
            else -> {
                Toast.makeText(this, "Video hosting site detected. Try the archive option first, then look for download options on the archived page.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
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
}
