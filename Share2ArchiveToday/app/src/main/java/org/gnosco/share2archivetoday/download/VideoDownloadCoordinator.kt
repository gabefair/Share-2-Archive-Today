package org.gnosco.share2archivetoday.download

import org.gnosco.share2archivetoday.PythonVideoDownloader
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gnosco.share2archivetoday.download.BackgroundDownloadService
import org.gnosco.share2archivetoday.media.VideoFormatSelector
import org.gnosco.share2archivetoday.network.NetworkMonitor
import org.gnosco.share2archivetoday.utils.ErrorMessageParser
import org.gnosco.share2archivetoday.utils.FileUtils
import org.gnosco.share2archivetoday.utils.UrlExtractor

/**
 * Coordinates video download operations
 */
class VideoDownloadCoordinator(
    private val activity: Activity,
    private val pythonDownloader: PythonVideoDownloader,
    private val downloadHistoryManager: DownloadHistoryManager,
    private val networkMonitor: NetworkMonitor,
    private val videoFormatSelector: VideoFormatSelector,
    private val dialogManager: VideoDownloadDialogManager,
    private val downloadScope: CoroutineScope
) {
    companion object {
        private const val TAG = "VideoDownloadCoordinator"
    }
    
    /**
     * Start the download process for a URL
     */
    fun startDownloadProcess(url: String, onSuccess: () -> Unit, onError: () -> Unit) {
        // Block YouTube URLs for Play Store compliance
        if (UrlExtractor.isYouTubeUrl(url)) {
            Toast.makeText(
                activity,
                "YouTube video downloads are not allowed by Play Store.",
                Toast.LENGTH_LONG
            ).show()
            Log.w(TAG, "Blocked YouTube download attempt: $url")
            onError()
            return
        }
        
        checkExistingDownloadAndProceed(url, onSuccess, onError)
    }
    
    /**
     * Check if video was already downloaded
     */
    private fun checkExistingDownloadAndProceed(
        url: String,
        onSuccess: () -> Unit,
        onError: () -> Unit
    ) {
        Log.d(TAG, "Checking if URL was already downloaded: $url")
        val existingDownload = downloadHistoryManager.findSuccessfulDownload(url)
        
        if (existingDownload != null) {
            Log.d(TAG, "Found existing download: ${existingDownload.title}")
            dialogManager.showAlreadyDownloadedDialog(
                existingDownload = existingDownload,
                onOpen = {
                    openVideoFile(existingDownload.filePath!!)
                    onSuccess()
                },
                onDownloadAgain = {
                    showQualitySelection(url, onSuccess, onError)
                },
                onCancel = onError
            )
        } else {
            Log.d(TAG, "No existing download found, proceeding with quality selection")
            showQualitySelection(url, onSuccess, onError)
        }
    }
    
    /**
     * Show quality selection dialog
     */
    private fun showQualitySelection(url: String, onSuccess: () -> Unit, onError: () -> Unit) {
        val loadingDialog = dialogManager.showLoadingDialog(
            "Analyzing Video",
            "Getting available formats and device compatibility..."
        )
        
        downloadScope.launch {
            try {
                Log.d(TAG, "Getting video info for quality selection: $url")
                val videoInfo = pythonDownloader.getVideoInfo(url)
                
                withContext(Dispatchers.Main) {
                    loadingDialog.dismiss()
                    
                    if (videoInfo == null) {
                        Toast.makeText(activity, "Unable to get video information", Toast.LENGTH_SHORT).show()
                        onError()
                        return@withContext
                    }
                    
                    if (videoInfo.formats.isEmpty()) {
                        Toast.makeText(
                            activity,
                            "This site is not supported or blocks downloads. Please email support@gnosco.org if you want us to look at it",
                            Toast.LENGTH_LONG
                        ).show()
                        onError()
                        return@withContext
                    }
                    
                    val qualityOptions = videoFormatSelector.buildUnifiedQualityOptions(videoInfo)
                    
                    if (qualityOptions.isEmpty()) {
                        Toast.makeText(activity, "No suitable formats found", Toast.LENGTH_SHORT).show()
                        onError()
                        return@withContext
                    }
                    
                    dialogManager.showQualitySelectionDialog(
                        videoInfo = videoInfo,
                        qualityOptions = qualityOptions,
                        onQualitySelected = { selectedOption ->
                            if (networkMonitor.shouldWarnAboutDataUsage()) {
                                dialogManager.showDataUsageWarning(
                                    qualityOption = selectedOption,
                                    onContinue = {
                                        executeDownload(url, selectedOption, onSuccess, onError)
                                    },
                                    onCancel = onError
                                )
                            } else {
                                executeDownload(url, selectedOption, onSuccess, onError)
                            }
                        },
                        onCancel = onError
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting video info", e)
                withContext(Dispatchers.Main) {
                    loadingDialog.dismiss()
                    val friendlyError = ErrorMessageParser.parseFriendlyErrorMessage(e)
                    dialogManager.showErrorDialog(
                        title = "Unable to Get Video Information",
                        message = friendlyError,
                        onDismiss = onError
                    )
                }
            }
        }
    }
    
    /**
     * Execute the download with selected quality
     */
    private fun executeDownload(
        url: String,
        qualityOption: VideoFormatSelector.QualityOption,
        onSuccess: () -> Unit,
        onError: () -> Unit
    ) {
        Toast.makeText(activity, "Starting video download...", Toast.LENGTH_SHORT).show()
        
        downloadScope.launch {
            try {
                Log.d(TAG, "Getting video info for download: $url")
                val videoInfo = pythonDownloader.getVideoInfo(url)
                
                val title = when {
                    !videoInfo?.title.isNullOrBlank() && videoInfo?.title != "Unknown" -> videoInfo!!.title
                    else -> {
                        try {
                            val uri = Uri.parse(url)
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
                    Toast.makeText(activity, "Downloading: $title", Toast.LENGTH_LONG).show()
                }
                
                val (quality, formatId) = when (val strategy = qualityOption.downloadStrategy) {
                    is VideoFormatSelector.DownloadStrategy.Combined -> strategy.quality to strategy.formatId
                    is VideoFormatSelector.DownloadStrategy.Separate -> strategy.quality to null
                    is VideoFormatSelector.DownloadStrategy.QualityBased -> strategy.quality to null
                }
                
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
                    onSuccess()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting download", e)
                withContext(Dispatchers.Main) {
                    val friendlyError = ErrorMessageParser.parseFriendlyErrorMessage(e)
                    dialogManager.showErrorDialog(
                        title = "Download Failed",
                        message = friendlyError,
                        onDismiss = onError
                    )
                }
            }
        }
    }
    
    /**
     * Open a video file with the default player
     */
    private fun openVideoFile(filePath: String) {
        try {
            val file = java.io.File(filePath)
            if (!file.exists()) {
                Toast.makeText(activity, "Video file no longer exists", Toast.LENGTH_SHORT).show()
                return
            }
            
            val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                androidx.core.content.FileProvider.getUriForFile(
                    activity,
                    "${activity.applicationContext.packageName}.fileprovider",
                    file
                )
            } else {
                Uri.fromFile(file)
            }
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, FileUtils.getMimeType(filePath))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            try {
                activity.startActivity(intent)
                Toast.makeText(activity, "Opening video...", Toast.LENGTH_SHORT).show()
            } catch (e: android.content.ActivityNotFoundException) {
                Toast.makeText(activity, "No video player app found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening video file", e)
            Toast.makeText(activity, "Error opening video: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

