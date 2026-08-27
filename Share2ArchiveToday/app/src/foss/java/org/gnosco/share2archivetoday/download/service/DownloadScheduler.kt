package org.gnosco.share2archivetoday.download.service

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import org.gnosco.share2archivetoday.download.history.HistoryEntry
import org.gnosco.share2archivetoday.ytdlp.DownloadNaming

/** Enqueues and cancels video downloads via WorkManager. */
object DownloadScheduler {

    private const val TAG = "DownloadScheduler"
    private const val UNIQUE_PREFIX = "video-download-"
    private const val WORK_TAG = "video-download"

    fun enqueue(context: Context, request: DownloadRequest) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (request.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED,
            )
            .build()

        val work = OneTimeWorkRequestBuilder<VideoDownloadWorker>()
            .setInputData(VideoDownloadWorker.inputData(request))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(WORK_TAG)
            .addTag(UNIQUE_PREFIX + request.downloadId)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_PREFIX + request.downloadId,
            ExistingWorkPolicy.KEEP,
            work,
        )
        Log.i(TAG, "Enqueued download ${request.downloadId}")
    }

    fun cancel(context: Context, downloadId: String?) {
        VideoDownloadWorker.signalCancel(downloadId)
        val wm = WorkManager.getInstance(context)
        if (downloadId != null) {
            wm.cancelUniqueWork(UNIQUE_PREFIX + downloadId)
        } else {
            wm.cancelAllWorkByTag(WORK_TAG)
        }
    }

    fun retry(context: Context, entry: HistoryEntry): Boolean {
        val retry = entry.retry ?: return false
        val request = DownloadRequest.fromRetry(
            downloadId = entry.id,
            title = entry.title,
            retry = retry,
            videoId = entry.videoId,
            webpageUrl = entry.webpageUrl,
        )
        // REPLACE so a failed unique work can run again with the same id (resumes partials).
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (request.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED,
            )
            .build()
        val work = OneTimeWorkRequestBuilder<VideoDownloadWorker>()
            .setInputData(VideoDownloadWorker.inputData(request))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(WORK_TAG)
            .addTag(UNIQUE_PREFIX + request.downloadId)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_PREFIX + request.downloadId,
            ExistingWorkPolicy.REPLACE,
            work,
        )
        return true
    }

    fun start(
        context: Context,
        url: String,
        title: String,
        originalUrl: String = url,
        videoFormatId: String? = null,
        audioFormatIds: List<String> = emptyList(),
        combinedFormatId: String? = null,
        needsMux: Boolean = false,
        audioOnly: Boolean = false,
        requiresVideoExtract: Boolean = false,
        archiveMetadata: Boolean = false,
        includeComments: Boolean = false,
        estimatedBytes: Long? = null,
        webpageUrl: String? = null,
        videoId: String? = null,
        wifiOnly: Boolean = false,
    ) {
        val downloadId = DownloadNaming.downloadId(
            url, videoFormatId, audioFormatIds, combinedFormatId,
        )
        enqueue(
            context,
            DownloadRequest(
                downloadId = downloadId,
                url = url,
                originalUrl = originalUrl,
                title = title,
                videoFormatId = videoFormatId,
                audioFormatIds = audioFormatIds,
                combinedFormatId = combinedFormatId,
                needsMux = needsMux,
                audioOnly = audioOnly,
                requiresVideoExtract = requiresVideoExtract,
                archiveMetadata = archiveMetadata,
                includeComments = includeComments,
                estimatedBytes = estimatedBytes,
                webpageUrl = webpageUrl,
                videoId = videoId,
                wifiOnly = wifiOnly,
            ),
        )
    }
}
