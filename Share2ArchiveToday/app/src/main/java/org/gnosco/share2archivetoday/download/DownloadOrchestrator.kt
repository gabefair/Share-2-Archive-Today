package org.gnosco.share2archivetoday.download

import org.gnosco.share2archivetoday.download.PythonVideoDownloader
import org.gnosco.share2archivetoday.media.*
import org.gnosco.share2archivetoday.utils.*
import android.content.Context
import android.util.Log
import java.io.File

/**
 * Orchestrates the download process, coordinating between various helpers
 */
class DownloadOrchestrator(
    private val context: Context,
    private val pythonDownloader: PythonVideoDownloader,
    private val downloadHistoryManager: DownloadHistoryManager,
    private val downloadResumptionManager: DownloadResumptionManager,
    private val notificationHelper: NotificationHelper,
    private val fileProcessor: FileProcessor,
    private val mediaStoreHelper: MediaStoreHelper,
    private val storageHelper: StorageHelper
) {
    companion object {
        private const val TAG = "DownloadOrchestrator"
    }

    /**
     * Determine the best display title for the download
     */
    fun determineDisplayTitle(videoInfo: PythonVideoDownloader.VideoInfo?, title: String, url: String): String {
        return when {
            !videoInfo?.title.isNullOrBlank() && videoInfo?.title != "Unknown" -> videoInfo!!.title
            !title.isNullOrBlank() && title != "Unknown" && title != "Unknown Video" -> title
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
    }

    /**
     * Execute the actual download (video or audio)
     */
    suspend fun executeDownload(
        url: String,
        downloadDir: String,
        quality: String,
        displayTitle: String,
        downloadId: String,
        estimatedSizeMB: Long,
        formatId: String?,
        progressCallback: (PythonVideoDownloader.ProgressInfo) -> Unit
    ): PythonVideoDownloader.DownloadResult {
        return when {
            quality.startsWith("audio_") -> {
                val audioFormat = if (quality == "audio_mp3") "mp3" else "aac"
                pythonDownloader.downloadAudio(
                    url = url,
                    outputDir = downloadDir,
                    audioFormat = audioFormat,
                    progressCallback = progressCallback
                )
            }
            else -> {
                pythonDownloader.downloadVideo(
                    url = url,
                    outputDir = downloadDir,
                    quality = quality,
                    progressCallback = progressCallback,
                    estimatedSizeMB = estimatedSizeMB,
                    formatId = formatId
                )
            }
        }
    }

    /**
     * Process audio files if needed (remuxing, format conversion)
     */
    fun processAudioIfNeeded(
        result: PythonVideoDownloader.DownloadResult,
        quality: String,
        displayTitle: String
    ): PythonVideoDownloader.DownloadResult {
        if (quality.startsWith("audio_")) {
            val filePath = result.filePath
            if (filePath != null) {
                val file = File(filePath)
                if (AudioRemuxer.needsRemuxing(file, isAudioOnlyDownload = true)) {
                    val processedPath = fileProcessor.processAudioFile(filePath, displayTitle, true)
                    return result.copy(filePath = processedPath)
                }
            }
        } else if (result.separateAv && result.audioPath != null) {
            val audioFile = File(result.audioPath)
            if (AudioRemuxer.needsRemuxing(audioFile, isAudioOnlyDownload = false)) {
                val processedAudioPath = fileProcessor.processAudioFile(result.audioPath, displayTitle, false)
                return result.copy(audioPath = processedAudioPath)
            }
        }
        return result
    }

    /**
     * Handle separate audio/video streams (merge or extract)
     */
    suspend fun processSeparateStreams(
        result: PythonVideoDownloader.DownloadResult,
        displayTitle: String,
        quality: String
    ): PythonVideoDownloader.DownloadResult {
        if (result.separateAv && result.videoPath != null && result.audioPath != null) {
            Log.d(TAG, "Merging separate video and audio streams")
            notificationHelper.updateNotification("Merging video and audio...", displayTitle)
            val mergedPath = fileProcessor.mergeVideoAudio(
                result.videoPath,
                result.audioPath,
                "${storageHelper.getDownloadDirectory()}/${displayTitle}_merged.mp4"
            )
            if (mergedPath != null) {
                return result.copy(filePath = mergedPath, separateAv = false)
            }
        } else if (result.separateAv && result.filePath != null) {
            // Extract audio from video file
            notificationHelper.updateNotification("Extracting audio...", displayTitle)
            val audioFormat = if (quality == "audio_mp3") "mp3" else "aac"
            val extractedPath = fileProcessor.extractAudioFromVideo(result.filePath, displayTitle, audioFormat)
            if (extractedPath != null) {
                File(result.filePath).delete()
                return result.copy(filePath = extractedPath, separateAv = false)
            }
        }
        return result
    }

    /**
     * Move file to MediaStore and get URI
     */
    suspend fun moveToMediaStore(filePath: String?, displayTitle: String): String? {
        return filePath?.let { 
            mediaStoreHelper.moveToMediaStore(it, displayTitle)?.toString()
        }
    }

    /**
     * Complete download and add to history
     */
    fun finalizeSuccessfulDownload(
        url: String,
        displayTitle: String,
        displayUploader: String,
        displayQuality: String,
        downloadId: String,
        uriString: String?,
        fileSize: Long
    ) {
        // Mark download as completed
        downloadResumptionManager.completeDownload(downloadId, uriString ?: "", fileSize)

        // Add to history
        downloadHistoryManager.addDownload(
            url = url,
            title = displayTitle,
            uploader = displayUploader,
            quality = displayQuality,
            filePath = uriString,
            fileSize = fileSize,
            success = true
        )

        Log.i(TAG, "Download completed successfully: $displayTitle - $fileSize bytes")
    }

    /**
     * Handle failed download (logging and history)
     */
    fun finalizeFailedDownload(
        url: String,
        displayTitle: String,
        displayUploader: String,
        displayQuality: String,
        downloadId: String,
        error: String
    ) {
        downloadResumptionManager.failDownload(downloadId, error)
        
        downloadHistoryManager.addDownload(
            url = url,
            title = displayTitle,
            uploader = displayUploader,
            quality = displayQuality,
            filePath = null,
            fileSize = 0,
            success = false,
            error = error
        )
    }
}

