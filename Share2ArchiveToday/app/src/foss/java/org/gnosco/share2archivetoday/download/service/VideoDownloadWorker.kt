package org.gnosco.share2archivetoday.download.service

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gnosco.share2archivetoday.ytdlp.DownloadNaming

/**
 * Long-running download worker.
 *
 * Uses [setForeground] so the job survives process death better than a bare FGS, gets
 * WorkManager retry/backoff for network failures, and unique-work deduping by download id.
 * Cancel is signalled via [DownloadScheduler.cancel] (notification action or history).
 */
class VideoDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val notifications = DownloadNotifications(appContext)
    private val cancelFlag = AtomicBoolean(false)

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val title = inputData.getString(KEY_TITLE) ?: "video"
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: "download"
        return foregroundInfo(
            notifications.ongoing("Starting download…", title, downloadId, indeterminate = true)
        )
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val request = parseRequest(inputData) ?: return@withContext Result.failure()
        registerCancel(request.downloadId, cancelFlag)
        ActiveDownloadStatus.begin(request.downloadId, request.title)

        val title = request.title
        try {
            setForeground(getForegroundInfo())
        } catch (t: Throwable) {
            Log.w(TAG, "Could not promote to foreground", t)
        }

        val pipeline = DownloadPipeline(
            context = applicationContext,
            onProgress = { done, total ->
                if (isStopped) cancelFlag.set(true)
                ActiveDownloadStatus.progress(request.downloadId, title, done, total)
                notifications.postProgress(title, request.downloadId, done, total) { n ->
                    notifications.notifyOngoing(n)
                }
            },
            onStatus = { msg ->
                if (isStopped) cancelFlag.set(true)
                ActiveDownloadStatus.status(request.downloadId, title, msg)
                notifications.postStatus(msg, title, request.downloadId) { n ->
                    notifications.notifyOngoing(n)
                }
            },
        )

        // Poll WorkManager stop into the same flag the Python progress hook reads.
        val stopWatcher = Thread({
            while (!cancelFlag.get()) {
                if (isStopped) {
                    cancelFlag.set(true)
                    break
                }
                try {
                    Thread.sleep(250)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }, "download-stop-watch").apply { isDaemon = true; start() }

        val outcome = try {
            pipeline.run(request, cancelFlag)
        } finally {
            cancelFlag.set(true)
            unregisterCancel(request.downloadId)
            stopWatcher.interrupt()
            ActiveDownloadStatus.end(request.downloadId)
            PendingShareQueue.notifyIfReady(applicationContext)
        }

        when (outcome) {
            is DownloadOutcome.Success -> {
                notifications.finished(
                    success = true,
                    title = title,
                    uri = outcome.uri,
                    error = null,
                    pageUrl = request.originalUrl,
                )
                Result.success()
            }
            is DownloadOutcome.Cancelled -> {
                notifications.finished(
                    success = false,
                    title = title,
                    uri = null,
                    error = null,
                    silent = true,
                )
                Result.failure()
            }
            is DownloadOutcome.Failed -> {
                notifications.finished(
                    success = false,
                    title = title,
                    uri = null,
                    error = outcome.message,
                )
                if (outcome.retryable && runAttemptCount < MAX_RETRIES) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        }
    }

    private fun foregroundInfo(notification: android.app.Notification): ForegroundInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                DownloadNotifications.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(DownloadNotifications.NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val TAG = "VideoDownloadWorker"
        private const val MAX_RETRIES = 3

        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_URL = "url"
        const val KEY_ORIGINAL_URL = "original_url"
        const val KEY_TITLE = "title"
        const val KEY_VIDEO_FORMAT_ID = "video_format_id"
        const val KEY_AUDIO_FORMAT_IDS = "audio_format_ids"
        const val KEY_COMBINED_FORMAT_ID = "combined_format_id"
        const val KEY_NEEDS_MUX = "needs_mux"
        const val KEY_AUDIO_ONLY = "audio_only"
        const val KEY_REQUIRES_VIDEO_EXTRACT = "requires_video_extract"
        const val KEY_ARCHIVE_METADATA = "archive_metadata"
        const val KEY_INCLUDE_COMMENTS = "include_comments"
        const val KEY_ESTIMATED_BYTES = "estimated_bytes"
        const val KEY_WEBPAGE_URL = "webpage_url"
        const val KEY_VIDEO_ID = "video_id"
        const val KEY_WIFI_ONLY = "wifi_only"

        private val activeCancels =
            java.util.concurrent.ConcurrentHashMap<String, AtomicBoolean>()

        fun signalCancel(downloadId: String?) {
            if (downloadId != null) {
                activeCancels[downloadId]?.set(true)
            } else {
                activeCancels.values.forEach { it.set(true) }
            }
        }

        private fun registerCancel(id: String, flag: AtomicBoolean) {
            activeCancels[id] = flag
        }

        private fun unregisterCancel(id: String) {
            activeCancels.remove(id)
        }

        fun inputData(request: DownloadRequest): Data =
            Data.Builder()
                .putString(KEY_DOWNLOAD_ID, request.downloadId)
                .putString(KEY_URL, request.url)
                .putString(KEY_ORIGINAL_URL, request.originalUrl)
                .putString(KEY_TITLE, request.title)
                .putString(KEY_VIDEO_FORMAT_ID, request.videoFormatId)
                .putStringArray(KEY_AUDIO_FORMAT_IDS, request.audioFormatIds.toTypedArray())
                .putString(KEY_COMBINED_FORMAT_ID, request.combinedFormatId)
                .putBoolean(KEY_NEEDS_MUX, request.needsMux)
                .putBoolean(KEY_AUDIO_ONLY, request.audioOnly)
                .putBoolean(KEY_REQUIRES_VIDEO_EXTRACT, request.requiresVideoExtract)
                .putBoolean(KEY_ARCHIVE_METADATA, request.archiveMetadata)
                .putBoolean(KEY_INCLUDE_COMMENTS, request.includeComments)
                .putLong(KEY_ESTIMATED_BYTES, request.estimatedBytes ?: 0L)
                .putString(KEY_WEBPAGE_URL, request.webpageUrl)
                .putString(KEY_VIDEO_ID, request.videoId)
                .putBoolean(KEY_WIFI_ONLY, request.wifiOnly)
                .build()

        fun parseRequest(data: Data): DownloadRequest? {
            val url = data.getString(KEY_URL) ?: return null
            val title = data.getString(KEY_TITLE) ?: "video"
            val videoFormatId = data.getString(KEY_VIDEO_FORMAT_ID)
            val audioIds = data.getStringArray(KEY_AUDIO_FORMAT_IDS)?.toList().orEmpty()
            val combined = data.getString(KEY_COMBINED_FORMAT_ID)
            val downloadId = data.getString(KEY_DOWNLOAD_ID)
                ?: DownloadNaming.downloadId(url, videoFormatId, audioIds, combined)
            return DownloadRequest(
                downloadId = downloadId,
                url = url,
                originalUrl = data.getString(KEY_ORIGINAL_URL) ?: url,
                title = title,
                videoFormatId = videoFormatId,
                audioFormatIds = audioIds,
                combinedFormatId = combined,
                needsMux = data.getBoolean(KEY_NEEDS_MUX, false),
                audioOnly = data.getBoolean(KEY_AUDIO_ONLY, false),
                requiresVideoExtract = data.getBoolean(KEY_REQUIRES_VIDEO_EXTRACT, false),
                archiveMetadata = data.getBoolean(KEY_ARCHIVE_METADATA, false),
                includeComments = data.getBoolean(KEY_INCLUDE_COMMENTS, false),
                estimatedBytes = data.getLong(KEY_ESTIMATED_BYTES, 0L).takeIf { it > 0 },
                webpageUrl = data.getString(KEY_WEBPAGE_URL),
                videoId = data.getString(KEY_VIDEO_ID),
                wifiOnly = data.getBoolean(KEY_WIFI_ONLY, false),
            )
        }
    }
}
