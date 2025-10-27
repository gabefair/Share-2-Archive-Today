package org.gnosco.share2archivetoday

import org.gnosco.share2archivetoday.media.*

import org.gnosco.share2archivetoday.utils.*
import org.gnosco.share2archivetoday.network.NetworkMonitor
import org.gnosco.share2archivetoday.download.DownloadHistoryManager
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
import org.gnosco.share2archivetoday.download.BackgroundDownloadService
import org.gnosco.share2archivetoday.network.WebURLMatcher

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
    private lateinit var videoFormatSelector: VideoFormatSelector
    private val downloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize components
        pythonDownloader = PythonVideoDownloader(applicationContext)
        permissionManager = PermissionManager(applicationContext)
        networkMonitor = NetworkMonitor(applicationContext)
        downloadHistoryManager = DownloadHistoryManager(applicationContext)
        videoFormatSelector = VideoFormatSelector()

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
                        val url = UrlExtractor.extractUrl(sharedText)

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
        if (UrlExtractor.isYouTubeUrl(url)) {
            Toast.makeText(
                this,
                "YouTube video downloads are not allowed by Play Store. ",
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
            .setTitle("Analyzing Video")
            .setMessage("Getting available formats and device compatibility...")
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
                            "Unable to get video information",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                        return@withContext
                    }

                    if (videoInfo.formats.isEmpty()) {
                        Toast.makeText(
                            this@VideoDownloadActivity,
                            "This site is not supported or blocks downloads. Please email support@gnosco.org if you want us to look at it",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                        return@withContext
                    }

                    // Build unified quality options
                    val qualityOptions = videoFormatSelector.buildUnifiedQualityOptions(videoInfo)

                    if (qualityOptions.isEmpty()) {
                        Toast.makeText(
                            this@VideoDownloadActivity,
                            "No suitable formats found",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                        return@withContext
                    }

                    // Get network recommendation
                    val networkRecommendation = getNetworkBasedRecommendation()

                    val dialogTitle = buildString {
                        append("Select Download Quality")
                        append("\nDuration: ${FileUtils.formatDuration(videoInfo.duration.toLong())}")
                        append("\nUploader: ${videoInfo.uploader}")
                        append("\nExtractor: ${videoInfo.extractor ?: "unknown"}")
                        if (networkRecommendation.isNotEmpty()) {
                            append("\nNetwork: $networkRecommendation")
                        }
                    }

                    // Create display options for dialog
                    val displayOptions = qualityOptions.map { it.displayName }.toTypedArray()

                    AlertDialog.Builder(this@VideoDownloadActivity)
                        .setTitle(dialogTitle)
                        .setItems(displayOptions) { _, which ->
                            val selectedOption = qualityOptions[which]

                            // Show data usage warning if needed
                            if (networkMonitor.shouldWarnAboutDataUsage()) {
                                showDataUsageWarning(url, selectedOption)
                            } else {
                                startDownloadWithQualityOption(url, selectedOption)
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
                    val friendlyError = ErrorMessageParser.parseFriendlyErrorMessage(e)
                    showErrorDialog(
                        title = "Unable to Get Video Information",
                        message = friendlyError
                    )
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
                setDataAndType(uri, FileUtils.getMimeType(filePath))
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
     * Show data usage warning for mobile users - updated to use QualityOption
     */
    private fun showDataUsageWarning(url: String, qualityOption: VideoFormatSelector.QualityOption) {
        val estimatedSize = FileUtils.formatFileSize(qualityOption.estimatedSize)

        AlertDialog.Builder(this)
            .setTitle("Mobile Data Usage Warning")
            .setMessage(
                "You're using mobile data. This download may use approximately $estimatedSize.\n\n" +
                        "The video will be downloaded in its original format and quality.\n" +
                        "Consider using WiFi for large downloads to avoid data charges.\n\n" +
                        "Do you want to continue?"
            )
            .setPositiveButton("Continue") { _, _ ->
                startDownloadWithQualityOption(url, qualityOption)
            }
            .setNegativeButton("Cancel") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }


    private fun startDownloadWithQualityOption(url: String, qualityOption: VideoFormatSelector.QualityOption) {
        // Show initial toast
        Toast.makeText(this, "Starting video download...", Toast.LENGTH_SHORT).show()

        downloadScope.launch {
            try {
                // Get video info first
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
                Log.d(TAG, "Selected quality option: ${qualityOption.displayName}")

                // Show video info to user
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@VideoDownloadActivity,
                        "Downloading: $title",
                        Toast.LENGTH_LONG
                    ).show()
                }

                // Map quality option to download parameters
                val (quality, formatId) = when (val strategy = qualityOption.downloadStrategy) {
                    is VideoFormatSelector.DownloadStrategy.Combined -> strategy.quality to strategy.formatId
                    is VideoFormatSelector.DownloadStrategy.Separate -> {
                        // For separate streams, use quality and let the Python side handle format selection
                        // You could extend this to pass both format IDs if needed
                        strategy.quality to null
                    }
                    is VideoFormatSelector.DownloadStrategy.QualityBased -> strategy.quality to null
                }

                // Start background download service with quality preference
                BackgroundDownloadService.startDownload(
                    context = this@VideoDownloadActivity,
                    url = url,
                    title = title,
                    uploader = uploader,
                    quality = quality,
                    formatId = formatId,
                    qualityLabel = qualityOption.displayName
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
                    val friendlyError = ErrorMessageParser.parseFriendlyErrorMessage(e)
                    showErrorDialog(
                        title = "Download Failed",
                        message = friendlyError
                    )
                }
            }
        }
    }

    private fun handleImageShare(imageUri: Uri) {
        try {
            val qrCodeScanner = QRCodeScanner(applicationContext)
            val qrCodeText = qrCodeScanner.extractQRCodeFromImage(imageUri)
            val qrUrl = UrlExtractor.extractUrl(qrCodeText)

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
                "To archive videos , this app needs:\n\n" +
                        "• Network Access: Required to archive videos from the internet\n\n" +
                        "• Storage Access: Required to save archived videos to your Downloads folder\n\n" +
                        "• Notifications: To show archive progress and completion\n\n" +
                        "Your privacy is important: This app only archives the files you explicitly request " +
                        "and does no other netowrk traffic."
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

    /**
     * Show a user-friendly error dialog
     */
    private fun showErrorDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

}