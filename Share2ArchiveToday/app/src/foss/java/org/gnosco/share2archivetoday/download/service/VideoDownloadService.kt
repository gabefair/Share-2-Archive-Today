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
import java.io.File
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
import org.gnosco.share2archivetoday.ytdlp.Media3Muxer
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
        val audioFormatId = intent.getStringExtra(EXTRA_AUDIO_FORMAT_ID)
        val combinedFormatId = intent.getStringExtra(EXTRA_COMBINED_FORMAT_ID)
        val needsMux = intent.getBooleanExtra(EXTRA_NEEDS_MUX, false)
        val audioOnly = intent.getBooleanExtra(EXTRA_AUDIO_ONLY, false)
        val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID) ?: UUID.randomUUID().toString()

        val notification = buildNotification("Starting download…", title, indeterminate = true)
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

        scope.launch {
            try {
                runDownload(
                    downloadId, url, title, videoFormatId, audioFormatId,
                    combinedFormatId, needsMux, audioOnly,
                )
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

    private fun runDownload(
        downloadId: String,
        url: String,
        title: String,
        videoFormatId: String?,
        audioFormatId: String?,
        combinedFormatId: String?,
        needsMux: Boolean,
        audioOnly: Boolean,
    ) {
        val bridge = YtDlpBridge.get(this)
        val work = partials.workDir(downloadId)
        updateNotification("Downloading…", title, 0, 100)

        val finalFile: File
        val mime: String

        when {
            audioOnly && audioFormatId != null && !needsMux -> {
                val result = bridge.download(url, audioFormatId, work.absolutePath, continuedl = true) {
                    updateFromProgress(title, it.downloaded, it.total)
                }
                finalFile = File(result.filepath)
                partials.considerPartial(downloadId, finalFile)
                mime = guessAudioMime(finalFile)
            }
            combinedFormatId != null && !needsMux -> {
                val result = bridge.download(url, combinedFormatId, work.absolutePath, continuedl = true) {
                    updateFromProgress(title, it.downloaded, it.total)
                }
                finalFile = File(result.filepath)
                partials.considerPartial(downloadId, finalFile)
                mime = if (audioOnly) guessAudioMime(finalFile) else "video/mp4"
            }
            videoFormatId != null && audioFormatId != null && needsMux -> {
                val video = bridge.download(
                    url, videoFormatId, work.absolutePath,
                    outTemplate = "video.%(ext)s", continuedl = true,
                ) { updateFromProgress(title, it.downloaded, it.total) }
                val videoFile = File(video.filepath).also { partials.considerPartial(downloadId, it) }

                val audioIds = listOf(audioFormatId) // caller already chose best; service can extend later
                var audioFile: File? = null
                var lastError: Throwable? = null
                for (aid in audioIds) {
                    try {
                        val audio = bridge.download(
                            url, aid, work.absolutePath,
                            outTemplate = "audio.%(ext)s", continuedl = true,
                        ) { updateFromProgress(title, it.downloaded, it.total) }
                        audioFile = File(audio.filepath)
                        break
                    } catch (t: Throwable) {
                        lastError = t
                    }
                }
                val audio = audioFile ?: throw lastError ?: IllegalStateException("No audio track")
                updateNotification("Merging…", title, indeterminate = true)
                val out = File(work, "merged.mp4")
                finalFile = Media3Muxer(this).muxBlocking(videoFile, audio, out)
                partials.considerPartial(downloadId, finalFile)
                mime = "video/mp4"
            }
            else -> error("Invalid download parameters")
        }

        val best = partials.bestFile(downloadId) ?: finalFile
        val publishSource = if (best.length() >= finalFile.length()) best else finalFile
        val displayName = sanitizeName(title) + if (mime.startsWith("audio")) ".m4a" else ".mp4"
        val uri = DownloadsPublisher.publish(this, publishSource, displayName, mime)

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
            val open = Intent(Intent.ACTION_VIEW).setDataAndType(uri, contentResolver.getType(uri) ?: "*/*")
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
            NotificationChannel(CHANNEL_ID, getString(R.string.download_channel_name), NotificationManager.IMPORTANCE_LOW)
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

    private fun guessAudioMime(file: File): String = when (file.extension.lowercase()) {
        "mp3" -> "audio/mpeg"
        "m4a", "mp4" -> "audio/mp4"
        "webm" -> "audio/webm"
        "opus" -> "audio/opus"
        else -> "audio/*"
    }

    companion object {
        const val CHANNEL_ID = "video_downloads"
        const val NOTIFICATION_ID = 42
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_VIDEO_FORMAT_ID = "video_format_id"
        const val EXTRA_AUDIO_FORMAT_ID = "audio_format_id"
        const val EXTRA_COMBINED_FORMAT_ID = "combined_format_id"
        const val EXTRA_NEEDS_MUX = "needs_mux"
        const val EXTRA_AUDIO_ONLY = "audio_only"
        const val EXTRA_DOWNLOAD_ID = "download_id"

        fun start(
            context: Context,
            url: String,
            title: String,
            videoFormatId: String? = null,
            audioFormatId: String? = null,
            combinedFormatId: String? = null,
            needsMux: Boolean = false,
            audioOnly: Boolean = false,
        ) {
            val i = Intent(context, VideoDownloadService::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_VIDEO_FORMAT_ID, videoFormatId)
                putExtra(EXTRA_AUDIO_FORMAT_ID, audioFormatId)
                putExtra(EXTRA_COMBINED_FORMAT_ID, combinedFormatId)
                putExtra(EXTRA_NEEDS_MUX, needsMux)
                putExtra(EXTRA_AUDIO_ONLY, audioOnly)
                putExtra(EXTRA_DOWNLOAD_ID, UUID.randomUUID().toString())
            }
            context.startForegroundService(i)
        }
    }
}
