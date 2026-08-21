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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.gnosco.share2archivetoday.R
import org.gnosco.share2archivetoday.download.history.BestPartialStore
import org.gnosco.share2archivetoday.download.history.DownloadHistoryStore
import org.gnosco.share2archivetoday.download.history.HistoryEntry
import org.gnosco.share2archivetoday.download.ui.DownloadHistoryActivity
import org.gnosco.share2archivetoday.ytdlp.DownloadsPublisher
import org.gnosco.share2archivetoday.ytdlp.YtDlpBridge

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
                    onStatus = { msg -> updateNotification(msg, title, indeterminate = true) },
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
                )

                val best = partials.bestFile(downloadId) ?: result.file
                val publishSource =
                    if (best.length() >= result.file.length()) best else result.file
                val ext = if (result.mimeType.startsWith("audio")) ".m4a" else ".mp4"
                val displayName = sanitizeName(title) + ext
                val uri = DownloadsPublisher.publish(
                    this@VideoDownloadService, publishSource, displayName, result.mimeType,
                )

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
                history.add(
                    HistoryEntry(
                        id = downloadId,
                        url = url,
                        title = title,
                        mediaStoreUri = null,
                        success = false,
                        error = t.message,
                        timestamp = System.currentTimeMillis(),
                        estimatedBytes = null,
                    )
                )
                notifyFinished(false, title, null, t.message)
            } finally {
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun updateFromProgress(title: String, downloaded: Long, total: Long) {
        if (total > 0) {
            val pct = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
            updateNotification("Downloading… $pct%", title, pct, 100)
        } else {
            updateNotification("Downloading…", title, indeterminate = true)
        }
    }

    private fun updateNotification(
        content: String,
        title: String,
        progress: Int = 0,
        max: Int = 0,
        indeterminate: Boolean = false,
    ) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(content, title, progress, max, indeterminate))
    }

    private fun notifyFinished(success: Boolean, title: String, uri: Uri?, error: String?) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val text = if (success) "Download complete" else (error ?: "Download failed")
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .addAction(0, "History", historyPendingIntent())
        if (success && uri != null) {
            val open = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, contentResolver.getType(uri) ?: "*/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            builder.setContentIntent(
                PendingIntent.getActivity(
                    this, 2, open,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
        }
        nm.notify(NOTIFICATION_ID + 1, builder.build())
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

    private fun sanitizeName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80).ifBlank { "download" }

    companion object {
        const val CHANNEL_ID = "video_downloads"
        const val NOTIFICATION_ID = 42
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_VIDEO_FORMAT_ID = "video_format_id"
        const val EXTRA_AUDIO_FORMAT_IDS = "audio_format_ids"
        const val EXTRA_COMBINED_FORMAT_ID = "combined_format_id"
        const val EXTRA_NEEDS_MUX = "needs_mux"
        const val EXTRA_AUDIO_ONLY = "audio_only"
        const val EXTRA_REQUIRES_VIDEO_EXTRACT = "requires_video_extract"
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
                putExtra(EXTRA_DOWNLOAD_ID, UUID.randomUUID().toString())
            }
            context.startForegroundService(i)
        }
    }
}
