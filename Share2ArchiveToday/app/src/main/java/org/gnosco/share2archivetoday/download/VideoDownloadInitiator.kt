package org.gnosco.share2archivetoday.download

import android.app.Activity
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gnosco.share2archivetoday.media.VideoFormatSelector
import org.gnosco.share2archivetoday.network.NetworkMonitor
import org.gnosco.share2archivetoday.utils.ErrorMessageParser
import org.gnosco.share2archivetoday.utils.FileUtils
import org.gnosco.share2archivetoday.utils.UrlExtractor

/**
 * Handles the video download initiation flow:
 * - Checking for duplicates
 * - Getting video info
 * - Showing quality selection
 * - Starting background download
 */
class VideoDownloadInitiator(
    private val activity: Activity,
    private val pythonDownloader: PythonVideoDownloader,
    private val downloadHistoryManager: DownloadHistoryManager,
    private val videoFormatSelector: VideoFormatSelector,
    private val networkMonitor: NetworkMonitor,
    private val dialogManager: VideoDownloadActivityDialogManager,
    private val downloadScope: CoroutineScope
) {
    companion object {
        private const val TAG = "VideoDownloadInitiator"
    }
    
    /**
     * Start the download process for a URL
     */
    fun initiateDownload(url: String) {
        // Block YouTube URLs for Play Store compliance
        if (UrlExtractor.isYouTubeUrl(url)) {
            Toast.makeText(
                activity,
                "YouTube video downloads are not allowed by Play Store.",
                Toast.LENGTH_LONG
            ).show()
            Log.w(TAG, "Blocked YouTube download attempt: $url")
            activity.finish()
            return
        }
        
        checkDuplicateAndProceed(url)
    }
    
    /**
     * Check if video was already downloaded
     */
    private fun checkDuplicateAndProceed(url: String) {
        Log.d(TAG, "Checking if URL was already downloaded: $url")
        val existingDownload = downloadHistoryManager.findSuccessfulDownload(url)
        
        if (existingDownload != null) {
            Log.d(TAG, "Found existing download: ${existingDownload.title}, file: ${existingDownload.filePath}")
            dialogManager.showAlreadyDownloadedDialog(
                url = url,
                existingDownload = existingDownload,
                onDownloadAgain = { showQualitySelection(url) }
            )
        } else {
            Log.d(TAG, "No existing download found, proceeding with quality selection")
            showQualitySelection(url)
        }
    }
    
    /**
     * Show quality selection dialog
     */
    private fun showQualitySelection(url: String) {
        val loadingDialog = dialogManager.showLoadingDialog(
            "Analyzing Video",
            "Getting available formats and device compatibility..."
        )
        
        downloadScope.launch {
            try {
                // Get video info and available formats
                Log.d(TAG, "Getting video info for quality selection: $url")
                val videoInfo = pythonDownloader.getVideoInfo(url)
                
                withContext(Dispatchers.Main) {
                    loadingDialog.dismiss()
                    
                    if (videoInfo == null) {
                        Toast.makeText(
                            activity,
                            "Unable to get video information",
                            Toast.LENGTH_SHORT
                        ).show()
                        activity.finish()
                        return@withContext
                    }
                    
                    if (videoInfo.formats.isEmpty()) {
                        Toast.makeText(
                            activity,
                            "This site is not supported or blocks downloads. Please email support@gnosco.org if you want us to look at it",
                            Toast.LENGTH_LONG
                        ).show()
                        activity.finish()
                        return@withContext
                    }
                    
                    // Build unified quality options
                    val qualityOptions = videoFormatSelector.buildUnifiedQualityOptions(videoInfo)
                    
                    if (qualityOptions.isEmpty()) {
                        Toast.makeText(
                            activity,
                            "No suitable formats found",
                            Toast.LENGTH_SHORT
                        ).show()
                        activity.finish()
                        return@withContext
                    }
                    
                    // Get network recommendation
                    val networkRecommendation = networkMonitor.getRecommendationText()
                    
                    val dialogTitle = buildString {
                        append("Select Download Quality")
                        append("\nDuration: ${FileUtils.formatDuration(videoInfo.duration.toLong())}")
                        append("\nUploader: ${videoInfo.uploader}")
                        append("\nExtractor: ${videoInfo.extractor ?: "unknown"}")
                        if (networkRecommendation.isNotEmpty()) {
                            append("\nNetwork: $networkRecommendation")
                        }
                    }
                    
                    dialogManager.showQualitySelectionDialog(
                        title = dialogTitle,
                        qualityOptions = qualityOptions,
                        onQualitySelected = { selectedOption ->
                            // Show data usage warning if needed
                            if (networkMonitor.shouldWarnAboutDataUsage()) {
                                dialogManager.showDataUsageWarning(
                                    qualityOption = selectedOption,
                                    onContinue = { startDownloadWithQualityOption(url, selectedOption) }
                                )
                            } else {
                                startDownloadWithQualityOption(url, selectedOption)
                            }
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting video info", e)
                withContext(Dispatchers.Main) {
                    loadingDialog.dismiss()
                    val friendlyError = ErrorMessageParser.parseFriendlyErrorMessage(e)
                    dialogManager.showErrorDialog(
                        title = "Unable to Get Video Information",
                        message = friendlyError
                    )
                }
            }
        }
    }
    
    /**
     * Start download with selected quality option
     */
    private fun startDownloadWithQualityOption(url: String, qualityOption: VideoFormatSelector.QualityOption) {
        Toast.makeText(activity, "Starting video download...", Toast.LENGTH_SHORT).show()
        
        downloadScope.launch {
            try {
                // Get video info first
                Log.d(TAG, "Getting video info for download: $url")
                val videoInfo = pythonDownloader.getVideoInfo(url)
                
                // Determine the best title to use
                val title = when {
                    !videoInfo?.title.isNullOrBlank() && videoInfo?.title != "Unknown" -> {
                        videoInfo!!.title
                    }
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
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        activity,
                        "Downloading: $title",
                        Toast.LENGTH_LONG
                    ).show()
                }
                
                // Map quality option to download parameters
                val (quality, formatId) = when (val strategy = qualityOption.downloadStrategy) {
                    is VideoFormatSelector.DownloadStrategy.Combined -> strategy.quality to strategy.formatId
                    is VideoFormatSelector.DownloadStrategy.Separate -> strategy.quality to null
                    is VideoFormatSelector.DownloadStrategy.QualityBased -> strategy.quality to null
                }
                
                // Start background download service
                BackgroundDownloadService.startDownload(
                    context = activity,
                    url = url,
                    title = title,
                    uploader = uploader,
                    quality = quality,
                    formatId = formatId,
                    qualityLabel = qualityOption.displayName
                )
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        activity,
                        "Download started in background. You'll be notified when complete.",
                        Toast.LENGTH_LONG
                    ).show()
                    activity.finish()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting download", e)
                withContext(Dispatchers.Main) {
                    val friendlyError = ErrorMessageParser.parseFriendlyErrorMessage(e)
                    dialogManager.showErrorDialog(
                        title = "Download Failed",
                        message = friendlyError
                    )
                }
            }
        }
    }
}

