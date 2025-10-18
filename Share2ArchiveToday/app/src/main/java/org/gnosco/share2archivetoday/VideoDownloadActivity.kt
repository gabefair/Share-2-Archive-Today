package org.gnosco.share2archivetoday

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.*
import java.io.File
import java.net.URL
import org.gnosco.share2archivetoday.BackgroundDownloadService
import org.gnosco.share2archivetoday.WebURLMatcher

/**
 * Activity for downloading videos and media using yt-dlp via Chaquopy
 * This activity handles permissions and starts background downloads
 */
class VideoDownloadActivity : Activity() {
    
    companion object {
        private const val TAG = "VideoDownloadActivity"
        private const val PERMISSION_REQUEST_CODE = 1001
    }
    
    private lateinit var pythonDownloader: PythonVideoDownloader
    private lateinit var permissionManager: PermissionManager
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var downloadHistoryManager: DownloadHistoryManager
    private val downloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize components
        pythonDownloader = PythonVideoDownloader(applicationContext)
        permissionManager = PermissionManager(applicationContext)
        networkMonitor = NetworkMonitor(applicationContext)
        downloadHistoryManager = DownloadHistoryManager(applicationContext)
        
        // Test Python functionality
        if (!pythonDownloader.testPythonFunctionality()) {
            Toast.makeText(this, "Video download feature not available. Python/yt-dlp initialization failed.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        handleShareIntent(intent)
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        downloadScope.cancel()
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (permissionManager.handlePermissionResult(requestCode, permissions, grantResults)) {
                // Permissions granted, proceed with download
                proceedWithDownload()
            } else {
                // Permissions denied
                Toast.makeText(
                    this,
                    "Permissions required for video downloads. Please grant access to continue.",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }
    
    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            when (intent.type) {
                "text/plain" -> {
                    intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedText ->
                        Log.d(TAG, "Shared text for video download: $sharedText")
                        val url = extractUrl(sharedText)
                        
                        if (url != null) {
                            startDownloadProcess(url)
                        } else {
                            Toast.makeText(this, "No URL found in shared text", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                }
                else -> {
                    // Handle image shares (QR codes)
                    if (intent.type?.startsWith("image/") == true) {
                        try {
                            val imageUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                            }
                            
                            imageUri?.let {
                                handleImageShare(it)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error handling image share", e)
                            Toast.makeText(this, "Error processing QR code", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                }
            }
        } else {
            finish()
        }
    }
    
    private fun startDownloadProcess(url: String) {
        // Block YouTube URLs for Play Store compliance
        if (isYouTubeUrl(url)) {
            Toast.makeText(
                this,
                "YouTube video downloads are not allowed by Play Store. " +
                "This app only supports non-YouTube video downloads.",
                Toast.LENGTH_LONG
            ).show()
            Log.w(TAG, "Blocked YouTube download attempt: $url")
            finish()
            return
        }
        
        // Check permissions first
        if (permissionManager.hasAllPermissions()) {
            proceedWithDownload(url)
        } else {
            // Request permissions
            if (permissionManager.requestPermissionsIfNeeded(this)) {
                proceedWithDownload(url)
            } else {
                // Show rationale if needed
                if (permissionManager.shouldShowRationale(this)) {
                    showPermissionRationale()
                }
                // Store URL for later use after permission grant
                storePendingDownload(url)
            }
        }
    }
    
    private fun proceedWithDownload(url: String? = null) {
        val downloadUrl = url ?: getPendingDownload()
        if (downloadUrl != null) {
            downloadVideo(downloadUrl)
        } else {
            Toast.makeText(this, "No URL to download", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    private fun downloadVideo(url: String) {
        // Check if already downloaded
        Log.d(TAG, "Checking if URL was already downloaded: $url")
        val existingDownload = downloadHistoryManager.findSuccessfulDownload(url)
        if (existingDownload != null) {
            Log.d(TAG, "Found existing download: ${existingDownload.title}, file: ${existingDownload.filePath}")
            showAlreadyDownloadedDialog(url, existingDownload)
        } else {
            Log.d(TAG, "No existing download found, proceeding with quality selection")
            // Show quality selection dialog
            showQualitySelectionDialog(url)
        }
    }
    
    private fun showQualitySelectionDialog(url: String) {
        // Show loading dialog while getting video info
        val loadingDialog = AlertDialog.Builder(this)
            .setTitle("Loading")
            .setMessage("Getting available formats...")
            .setCancelable(false)
            .create()
        loadingDialog.show()
        
        downloadScope.launch {
            try {
                // Get video info and available formats
                Log.d(TAG, "Getting video info for quality selection: $url")
                val videoInfo = pythonDownloader.getVideoInfo(url)
                
                withContext(Dispatchers.Main) {
                    loadingDialog.dismiss()
                    
                    if (videoInfo == null) {
                        Toast.makeText(
                            this@VideoDownloadActivity,
                            "Failed to get video information",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                        return@withContext
                    }
                    
                    // Get network recommendation
                    val networkRecommendation = getNetworkBasedRecommendation()
                    
                    val dialogTitle = buildString {
                        append("Select Download Quality")
                        append("\nDuration: ${formatDuration(videoInfo.duration.toLong())}")
                        append("\nUploader: ${videoInfo.uploader}")
                        if (networkRecommendation.isNotEmpty()) {
                            append("\nNetwork: $networkRecommendation")
                        }
                    }
                    
                    // Always add audio-only option at the end
                    val qualityOptions = mutableListOf<String>()
                    val qualityValues = mutableListOf<String>()
                    
                    // Add "best" option
                    qualityOptions.add("Best Available Quality")
                    qualityValues.add("best")
                    
                    // Add audio-only option
                    qualityOptions.add("Audio Only (MP3)")
                    qualityValues.add("audio_mp3")
                    
                    AlertDialog.Builder(this@VideoDownloadActivity)
                        .setTitle(dialogTitle)
                        .setItems(qualityOptions.toTypedArray()) { _, which ->
                            val selectedQuality = qualityValues[which]
                            
                            // Show data usage warning if needed
                            if (networkMonitor.shouldWarnAboutDataUsage() && !selectedQuality.startsWith("audio_")) {
                                showDataUsageWarning(url, selectedQuality, null)
                            } else {
                                startDownloadWithQuality(url, selectedQuality, null)
                            }
                        }
                        .setNegativeButton("Cancel") { _, _ ->
                            finish()
                        }
                        .setCancelable(false)
                        .show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting video info", e)
                withContext(Dispatchers.Main) {
                    loadingDialog.dismiss()
                    Toast.makeText(
                        this@VideoDownloadActivity,
                        "Error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            }
        }
    }
    
    /**
     * Show dialog when video was already downloaded
     */
    private fun showAlreadyDownloadedDialog(url: String, existingDownload: DownloadHistoryManager.DownloadHistoryEntry) {
        val message = buildString {
            append("This video was already downloaded\n\n")
            append("Title: ${existingDownload.title}\n")
            append("Quality: ${existingDownload.quality}\n")
            append("Size: ${existingDownload.getFormattedFileSize()}\n")
            append("Downloaded: ${existingDownload.getFormattedDate()}")
        }
        
        AlertDialog.Builder(this)
            .setTitle("Already Downloaded")
            .setMessage(message)
            .setPositiveButton("Open Video") { _, _ ->
                openVideoFile(existingDownload.filePath!!)
            }
            .setNeutralButton("Download Again") { _, _ ->
                // Proceed with download
                showQualitySelectionDialog(url)
            }
            .setNegativeButton("Cancel") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Open a video file with the default video player
     */
    private fun openVideoFile(filePath: String) {
        try {
            val file = java.io.File(filePath)
            if (!file.exists()) {
                Toast.makeText(this, "Video file no longer exists", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            
            val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                androidx.core.content.FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.fileprovider",
                    file
                )
            } else {
                android.net.Uri.fromFile(file)
            }
            
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, getMimeType(filePath))
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            try {
                startActivity(intent)
                Toast.makeText(this, "Opening video...", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: android.content.ActivityNotFoundException) {
                Toast.makeText(this, "No video player app found", Toast.LENGTH_SHORT).show()
                finish()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening video file", e)
            Toast.makeText(this, "Error opening video: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    /**
     * Get network-based quality recommendation
     */
    private fun getNetworkBasedRecommendation(): String {
        return when {
            networkMonitor.isWiFiConnected() -> "WiFi - Good for original quality"
            networkMonitor.isConnected() -> "Mobile Data - Consider lower resolution"
            else -> "No connection"
        }
    }
    
    /**
     * Show data usage warning for mobile users
     */
    private fun showDataUsageWarning(url: String, quality: String, formatId: String?) {
        val estimatedSize = estimateDownloadSize(quality)
        
        AlertDialog.Builder(this)
            .setTitle("Mobile Data Usage Warning")
            .setMessage(
                "You're using mobile data. This download may use approximately $estimatedSize.\n\n" +
                "The video will be downloaded in its original format and quality.\n" +
                "Consider using WiFi for large downloads to avoid data charges.\n\n" +
                "Do you want to continue?"
            )
            .setPositiveButton("Continue") { _, _ ->
                startDownloadWithQuality(url, quality, formatId)
            }
            .setNegativeButton("Cancel") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Estimate download size based on quality
     */
    private fun estimateDownloadSize(quality: String): String {
        return when (quality) {
            "best" -> "50-200 MB"
            "720p" -> "30-100 MB"
            "480p" -> "15-50 MB"
            "360p" -> "8-25 MB"
            "audio_mp3", "audio_aac" -> "3-10 MB"
            else -> "Unknown"
        }
    }
    
    /**
     * Determine optimal quality based on video duration
     * Always prioritize original format, but limit resolution for longer videos
     * Videos ≤6 minutes: original quality in original format
     * Videos >6 minutes: 720p in original format
     */
    private fun determineOptimalQuality(videoInfo: PythonVideoDownloader.VideoInfo): String {
        val durationMinutes = videoInfo.duration / 60.0
        return when {
            durationMinutes <= 6.0 -> "best"  // Original quality and format for short videos
            else -> "720p"  // 720p in original format for longer videos
        }
    }
    
    /**
     * Format duration in a readable format
     */
    private fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        
        return when {
            hours > 0 -> "${hours}h ${minutes}m ${secs}s"
            minutes > 0 -> "${minutes}m ${secs}s"
            else -> "${secs}s"
        }
    }
    
    private fun startDownloadWithQuality(url: String, quality: String, formatId: String?) {
        // Show initial toast
        Toast.makeText(this, "Starting video download...", Toast.LENGTH_SHORT).show()
        
        downloadScope.launch {
            try {
                // Get video info first (this will log all available formats)
                Log.d(TAG, "Getting video info and logging available formats for: $url")
                val videoInfo = pythonDownloader.getVideoInfo(url)
                
                // Determine the best title to use
                val title = when {
                    // Best case: we have actual video title from yt-dlp
                    !videoInfo?.title.isNullOrBlank() && videoInfo?.title != "Unknown" -> {
                        videoInfo!!.title
                    }
                    // Second best: extract domain from URL for a meaningful title
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
                
                val uploader = videoInfo?.uploader ?: "Unknown"
                
                Log.d(TAG, "Video info: $title by $uploader")
                Log.d(TAG, "Selected quality: $quality")
                if (formatId != null) {
                    Log.d(TAG, "Selected format ID: $formatId")
                }
                
                // Show video info to user
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@VideoDownloadActivity,
                        "Downloading: $title",
                        Toast.LENGTH_LONG
                    ).show()
                }
                
                // Start background download service with quality
                BackgroundDownloadService.startDownload(
                    context = this@VideoDownloadActivity,
                    url = url,
                    title = title,
                    uploader = uploader,
                    quality = quality,
                    formatId = formatId
                )
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@VideoDownloadActivity,
                        "Download started in background. You'll be notified when complete.",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting download", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@VideoDownloadActivity,
                        "Download error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            }
        }
    }
    
    private fun handleImageShare(imageUri: Uri) {
        try {
            val qrCodeScanner = QRCodeScanner(applicationContext)
            val qrCodeText = qrCodeScanner.extractQRCodeFromImage(imageUri)
            val qrUrl = extractUrl(qrCodeText)
            
            if (qrUrl != null) {
                startDownloadProcess(qrUrl)
                Toast.makeText(this, "URL found in QR code", Toast.LENGTH_SHORT).show()
            } else {
                Log.d(TAG, "No QR code found in image")
                Toast.makeText(this, "No URL found in QR code image", Toast.LENGTH_SHORT).show()
                finish()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing QR code", e)
            Toast.makeText(this, "Error processing QR code", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    private fun showPermissionRationale() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage(
                "To download videos in their original format, this app needs:\n\n" +
                "• Network Access: Required to download videos from the internet\n\n" +
                "• Storage Access: Required to save downloaded videos to your Downloads folder\n\n" +
                "• Notifications: To show download progress and completion\n\n" +
                "Your privacy is important: This app only downloads the videos you explicitly request " +
                "in their original format and quality, and does not access any other files on your device.\n\n" +
                "Downloads are saved to your Downloads/Share2ArchiveToday folder."
            )
            .setPositiveButton("Grant Permissions") { _, _ ->
                permissionManager.requestPermissionsIfNeeded(this)
            }
            .setNegativeButton("Cancel") { _, _ ->
                Toast.makeText(
                    this,
                    "This feature requires accepted permissions.",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun storePendingDownload(url: String) {
        val prefs = getSharedPreferences("video_download_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("pending_download_url", url).apply()
    }
    
    private fun getPendingDownload(): String? {
        val prefs = getSharedPreferences("video_download_prefs", Context.MODE_PRIVATE)
        val url = prefs.getString("pending_download_url", null)
        prefs.edit().remove("pending_download_url").apply()
        return url
    }
    
    private fun getDownloadDirectory(): String {
        // Use app-specific external dir for staging; we'll move to MediaStore after
        val staging = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val appDownloadsDir = File(staging, "Share2ArchiveToday")
        if (!appDownloadsDir.exists()) appDownloadsDir.mkdirs()
        return appDownloadsDir.absolutePath
    }
    
    private fun getMimeType(filePath: String): String {
        val extension = filePath.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "aac" -> "audio/aac"
            "m4a" -> "audio/mp4"
            "wav" -> "audio/wav"
            else -> "video/*"
        }
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
    
    // Reuse URL extraction logic from MainActivity
    private fun extractUrl(text: String): String? {
        // First, try simple protocol-based extraction for better reliability
        val simpleUrl = extractUrlSimple(text)
        if (simpleUrl != null) {
            return cleanUrl(simpleUrl)
        }
        
        // Fallback to the existing WebURLMatcher approach
        val protocolMatcher = WebURLMatcher.matcher(text)
        if (protocolMatcher.find()) {
            val foundUrl = protocolMatcher.group(0)
            // Validate that the found URL looks reasonable
            if (foundUrl != null && isValidExtractedUrl(foundUrl)) {
                return cleanUrl(foundUrl)
            }
        }
        
        // If no URL with protocol is found, look for potential bare domains
        val domainPattern = Regex(
            "(?:^|\\s+)(" +  // Start of string or whitespace
                    "(?:[a-zA-Z0-9][a-zA-Z0-9-]*\\.)+?" + // Subdomains and domain name
                    "[a-zA-Z]{2,}" +  // TLD
                    "(?:/[^\\s]*)?" + // Optional path
                    ")(?:\\s+|\$)"    // End of string or whitespace
        )
        
        val domainMatch = domainPattern.find(text)
        if (domainMatch != null) {
            val bareUrl = domainMatch.groupValues[1].trim()
            // Add https:// prefix and clean the URL
            return cleanUrl("https://$bareUrl")
        }
        
        return null
    }
    
    /**
     * Simple URL extraction that looks for http:// or https:// and extracts to the next boundary
     */
    private fun extractUrlSimple(text: String): String? {
        val httpIndex = text.lastIndexOf("http://")
        val httpsIndex = text.lastIndexOf("https://")
        
        val startIndex = maxOf(httpIndex, httpsIndex)
        if (startIndex == -1) return null
        
        // Find the end of the URL - look for whitespace, newline, or certain punctuation
        var endIndex = text.length
        for (i in startIndex until text.length) {
            val char = text[i]
            if (char.isWhitespace() || char == '\n' || char == '\r') {
                endIndex = i
                break
            }
            // Stop at certain punctuation that's likely not part of the URL
            if (i > startIndex + 10) { // Only check after we have a reasonable URL length
                if (char in setOf(',', ';', ')', '"', '\'') &&
                    (i == text.length - 1 || text[i + 1].isWhitespace())) {
                    endIndex = i
                    break
                }
            }
        }
        
        val extractedUrl = text.substring(startIndex, endIndex)
        return if (isValidExtractedUrl(extractedUrl)) extractedUrl else null
    }
    
    /**
     * Validate that an extracted URL looks reasonable
     */
    private fun isValidExtractedUrl(url: String): Boolean {
        if (url.length < 10) return false // Too short to be a real URL
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        
        try {
            val uri = Uri.parse(url)
            val host = uri.host
            
            // Must have a valid host
            if (host.isNullOrEmpty()) return false
            
            // Host should contain at least one dot (domain.tld)
            if (!host.contains(".")) return false
            
            // Host shouldn't have weird characters that suggest parsing error
            if (host.contains("'") || host.contains('"') || host.contains("â€")) return false
            return true
        } catch (e: Exception) {
            return false
        }
    }
    
    private fun cleanUrl(url: String): String {
        val cleanedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            val lastHttpsIndex = url.lastIndexOf("https://")
            val lastHttpIndex = url.lastIndexOf("http://")
            val lastValidUrlIndex = maxOf(lastHttpsIndex, lastHttpIndex)
            
            if (lastValidUrlIndex != -1) {
                // Extract the portion from the last valid protocol and clean any remaining %09 sequences
                url.substring(lastValidUrlIndex).replace(Regex("%09+"), "")
            } else {
                // If no valid protocol is found, add https:// and clean %09 sequences
                "https://${url.replace(Regex("%09+"), "")}"
            }
        } else {
            // URL already starts with a protocol, just clean %09 sequences
            url.replace(Regex("%09+"), "")
        }
        
        return cleanedUrl
            .removeSuffix("?")
            .removeSuffix("&")
            .removeSuffix("#")
            .removeSuffix(".")
            .removeSuffix(",")
            .removeSuffix(";")
            .removeSuffix(")")
            .removeSuffix("'")
            .removeSuffix("\"")
    }
    
    /**
     * Check if a URL is a YouTube URL
     * CRITICAL for Play Store compliance - must block YouTube downloads
     */
    private fun isYouTubeUrl(url: String): Boolean {
        val uri = android.net.Uri.parse(url.lowercase())
        val host = uri.host ?: return false
        
        return host.contains("youtube.com") || 
               host.contains("youtu.be") || 
               host.contains("youtube-nocookie.com") ||
               host.contains("m.youtube.com") ||
               host.contains("music.youtube.com") ||
               host.contains("gaming.youtube.com") ||
               host.contains("shorts.youtube.com") ||
               host.contains("googlevideo.com") ||
               host.contains("youtube.googleapis.com") ||
               host.contains("ytimg.com") ||
               host.contains("googleusercontent.com") ||
               host.contains("yt3.ggpht.com") ||
               host.contains("ytstatic.com") ||
               host.contains("youtubei.googleapis.com")
    }
}
