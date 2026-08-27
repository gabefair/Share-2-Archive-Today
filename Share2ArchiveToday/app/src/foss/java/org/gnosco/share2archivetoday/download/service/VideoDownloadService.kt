package org.gnosco.share2archivetoday.download.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.gnosco.share2archivetoday.R
import org.gnosco.share2archivetoday.download.DownloadErrorMessages
import org.gnosco.share2archivetoday.download.history.BestPartialStore
import org.gnosco.share2archivetoday.download.history.DownloadHistoryStore
import org.gnosco.share2archivetoday.download.history.HistoryEntry
import org.gnosco.share2archivetoday.download.ui.DownloadHistoryActivity
import org.gnosco.share2archivetoday.ytdlp.DownloadsPublisher
import org.gnosco.share2archivetoday.ytdlp.YtDlpBridge
import org.gnosco.share2archivetoday.ytdlp.YtDlpFailureClassifier

class VideoDownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var history: DownloadHistoryStore
    private lateinit var partials: BestPartialStore

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        history = DownloadHistoryStore(this)
        partials = BestPartialStore(this)
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL) ?: return stopAndFinish()
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "video"
        val videoFormatId = intent.getStringExtra(EXTRA_VIDEO_FORMAT_ID)
        val audioFormatIds = intent.getStringArrayListExtra(EXTRA_AUDIO_FORMAT_IDS).orEmpty()
        val combinedFormatId = intent.getStringExtra(EXTRA_COMBINED_FORMAT_ID)
        val needsMux = intent.getBooleanExtra(EXTRA_NEEDS_MUX, false)
        val audioOnly = intent.getBooleanExtra(EXTRA_AUDIO_ONLY, false)
        val requiresVideoExtract = intent.getBooleanExtra(EXTRA_REQUIRES_VIDEO_EXTRACT, false)
        val archiveMetadata = intent.getBooleanExtra(EXTRA_ARCHIVE_METADATA, false)
        val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID) ?: UUID.randomUUID().toString()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification("Starting download…", title, indeterminate = true),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )

        scope.launch {
            try {
                val executor = DownloadExecutor(
                    context = this@VideoDownloadService,
                    bridge = YtDlpBridge.get(this@VideoDownloadService),
                    partials = partials,
                    onProgress = { done, total -> updateFromProgress(title, done, total) },
                    onStatus = { msg -> updateStatus(msg, title) },
                )
                val result = executor.execute(
                    downloadId = downloadId,
                    url = url,
                    videoFormatId = videoFormatId,
                    audioFormatIds = audioFormatIds,
                    combinedFormatId = combinedFormatId,
                    needsMux = needsMux,
                    audioOnly = audioOnly,
                    requiresVideoExtract = requiresVideoExtract,
                    archiveMetadata = archiveMetadata,
                )

                val best = partials.bestFile(downloadId) ?: result.file
                val publishSource =
                    if (best.length() >= result.file.length()) best else result.file
                val ext = if (result.mimeType.startsWith("audio")) ".m4a" else ".mp4"
                val baseName = sanitizeName(title)
                val displayName = baseName + ext
                val uri = DownloadsPublisher.publish(
                    this@VideoDownloadService, publishSource, displayName, result.mimeType,
                )
                publishSidecars(baseName, result.sidecars)

                history.add(
                    HistoryEntry(
                        id = downloadId,
                        url = url,
                        title = title,
                        mediaStoreUri = uri.toString(),
                        success = true,
                        error = null,
                        timestamp = System.currentTimeMillis(),
                        estimatedBytes = publishSource.length(),
                    )
                )
                partials.clear(downloadId)
                notifyFinished(true, title, uri, null)
            } catch (t: Throwable) {
                val friendly = DownloadErrorMessages.message(this@VideoDownloadService, t)
                Log.e(TAG, "Download failed kind=${YtDlpFailureClassifier.classify(t)} for $url", t)
                history.add(
                    HistoryEntry(
                        id = downloadId,
                        url = url,
                        title = title,
                        mediaStoreUri = null,
                        success = false,
                        error = friendly,
                        timestamp = System.currentTimeMillis(),
                        estimatedBytes = null,
                    )
                )
                notifyFinished(false, title, null, friendly)
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private var lastNotifyAtMs = 0L
    private var lastNotifyPct = -1
    private var lastStatusText: String? = null

    private fun updateFromProgress(title: String, downloaded: Long, total: Long) {
        val now = System.currentTimeMillis()
        if (total > 0) {
            val pct = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
            // Android sheds package notifies around ~5/s; keep well under that.
            if (pct != 100 && pct == lastNotifyPct) return
            if (pct != 100 && now - lastNotifyAtMs < NOTIFY_MIN_INTERVAL_MS) return
            if (pct != 100 && pct - lastNotifyPct < NOTIFY_MIN_PCT_STEP) return
            lastNotifyPct = pct
            lastNotifyAtMs = now
            lastStatusText = null
            publishOngoing("Downloading… $pct%", title, pct, 100, indeterminate = false)
        } else if (now - lastNotifyAtMs >= NOTIFY_MIN_INTERVAL_MS) {
            lastNotifyAtMs = now
            publishOngoing("Downloading…", title, indeterminate = true)
        }
    }

    private fun updateStatus(content: String, title: String) {
        val now = System.currentTimeMillis()
        if (content == lastStatusText) return
        // Status changes (metadata → merging) should show, but not reset the rate budget to zero.
        if (now - lastNotifyAtMs < NOTIFY_STATUS_MIN_INTERVAL_MS) return
        lastStatusText = content
        lastNotifyAtMs = now
        publishOngoing(content, title, indeterminate = true)
    }

    /**
     * Update the FGS notification via [startForeground] instead of [NotificationManager.notify]
     * so progress updates are less likely to be shed by the system enqueue rate limiter.
     */
    private fun publishOngoing(
        content: String,
        title: String,
        progress: Int = 0,
        max: Int = 0,
        indeterminate: Boolean = true,
    ) {
        val notification = buildNotification(content, title, progress, max, indeterminate)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    private fun notifyFinished(success: Boolean, title: String, uri: Uri?, error: String?) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        stopForeground(STOP_FOREGROUND_REMOVE)
        nm.cancel(NOTIFICATION_ID)
        val text = if (success) "Download complete" else (error ?: "Download failed")
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "History", historyPendingIntent())
        if (success && uri != null) {
            val open = org.gnosco.share2archivetoday.download.OpenDownloadedMedia
                .viewIntent(this, uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            builder.setContentIntent(
                PendingIntent.getActivity(
                    this, 2, open,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
        }
        nm.notify(NOTIFICATION_DONE_ID, builder.build())
    }

    private fun buildNotification(
        content: String,
        title: String,
        progress: Int = 0,
        max: Int = 0,
        indeterminate: Boolean = true,
    ): Notification {
        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(content)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(0, "History", historyPendingIntent())
        if (indeterminate) b.setProgress(0, 0, true) else b.setProgress(max, progress, false)
        return b.build()
    }

    private fun historyPendingIntent(): PendingIntent {
        val i = Intent(this, DownloadHistoryActivity::class.java)
        return PendingIntent.getActivity(
            this, 1, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    private fun stopAndFinish(): Int {
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun publishSidecars(baseName: String, sidecars: List<org.gnosco.share2archivetoday.ytdlp.DownloadSidecar>) {
        val used = mutableSetOf<String>()
        for (side in sidecars) {
            val src = java.io.File(side.path)
            if (!src.isFile || !used.add(src.absolutePath)) continue
            val (suffix, mime) = when (side.kind) {
                "infojson" -> ".info.json" to "application/json"
                "description" -> ".description.txt" to "text/plain"
                "thumbnail" -> {
                    val ext = src.extension.ifBlank { "jpg" }
                    ".$ext" to when (ext.lowercase()) {
                        "png" -> "image/png"
                        "webp" -> "image/webp"
                        else -> "image/jpeg"
                    }
                }
                else -> ".${src.extension}" to "application/octet-stream"
            }
            runCatching {
                DownloadsPublisher.publish(
                    this,
                    src,
                    baseName + suffix,
                    mime,
                )
            }.onFailure { Log.w(TAG, "Failed to publish sidecar ${src.name}", it) }
        }
    }

    private fun sanitizeName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80).ifBlank { "download" }

    companion object {
        private const val TAG = "VideoDownload"
        const val CHANNEL_ID = "video_downloads"
        const val NOTIFICATION_ID = 42
        const val NOTIFICATION_DONE_ID = 43
        private const val NOTIFY_MIN_INTERVAL_MS = 2_500L
        private const val NOTIFY_STATUS_MIN_INTERVAL_MS = 1_200L
        private const val NOTIFY_MIN_PCT_STEP = 10
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_VIDEO_FORMAT_ID = "video_format_id"
        const val EXTRA_AUDIO_FORMAT_IDS = "audio_format_ids"
        const val EXTRA_COMBINED_FORMAT_ID = "combined_format_id"
        const val EXTRA_NEEDS_MUX = "needs_mux"
        const val EXTRA_AUDIO_ONLY = "audio_only"
        const val EXTRA_REQUIRES_VIDEO_EXTRACT = "requires_video_extract"
        const val EXTRA_ARCHIVE_METADATA = "archive_metadata"
        const val EXTRA_DOWNLOAD_ID = "download_id"

        fun start(
            context: Context,
            url: String,
            title: String,
            videoFormatId: String? = null,
            audioFormatIds: List<String> = emptyList(),
            combinedFormatId: String? = null,
            needsMux: Boolean = false,
            audioOnly: Boolean = false,
            requiresVideoExtract: Boolean = false,
            archiveMetadata: Boolean = false,
        ) {
            val i = Intent(context, VideoDownloadService::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_VIDEO_FORMAT_ID, videoFormatId)
                putStringArrayListExtra(EXTRA_AUDIO_FORMAT_IDS, ArrayList(audioFormatIds))
                putExtra(EXTRA_COMBINED_FORMAT_ID, combinedFormatId)
                putExtra(EXTRA_NEEDS_MUX, needsMux)
                putExtra(EXTRA_AUDIO_ONLY, audioOnly)
                putExtra(EXTRA_REQUIRES_VIDEO_EXTRACT, requiresVideoExtract)
                putExtra(EXTRA_ARCHIVE_METADATA, archiveMetadata)
                putExtra(EXTRA_DOWNLOAD_ID, UUID.randomUUID().toString())
            }
            context.startForegroundService(i)
        }
    }
}
