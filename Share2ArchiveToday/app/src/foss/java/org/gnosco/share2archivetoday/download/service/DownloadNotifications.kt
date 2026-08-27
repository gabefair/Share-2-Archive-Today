package org.gnosco.share2archivetoday.download.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import org.gnosco.share2archivetoday.ArchiveToday
import org.gnosco.share2archivetoday.R
import org.gnosco.share2archivetoday.download.ui.DownloadHistoryActivity

/** Rate-limited download notifications shared by the WorkManager worker. */
class DownloadNotifications(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastNotifyAtMs = 0L
    private var lastNotifyPct = -1
    private var lastStatusText: String? = null

    init {
        ensureChannel()
    }

    fun ongoing(
        content: String,
        title: String,
        downloadId: String,
        progress: Int = 0,
        max: Int = 0,
        indeterminate: Boolean = true,
    ): Notification {
        val b = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(content)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, context.getString(R.string.download_cancel), cancelPendingIntent(downloadId))
            .addAction(0, context.getString(R.string.download_history_action), historyPendingIntent())
        if (indeterminate) b.setProgress(0, 0, true) else b.setProgress(max, progress, false)
        return b.build()
    }

    fun postProgress(title: String, downloadId: String, downloaded: Long, total: Long, publish: (Notification) -> Unit) {
        mainHandler.post {
            val now = System.currentTimeMillis()
            if (total > 0) {
                val pct = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                if (pct != 100 && pct == lastNotifyPct) return@post
                if (pct != 100 && now - lastNotifyAtMs < NOTIFY_MIN_INTERVAL_MS) return@post
                if (pct != 100 && pct - lastNotifyPct < NOTIFY_MIN_PCT_STEP) return@post
                lastNotifyPct = pct
                lastNotifyAtMs = now
                lastStatusText = null
                publish(ongoing("Downloading… $pct%", title, downloadId, pct, 100, indeterminate = false))
            } else if (now - lastNotifyAtMs >= NOTIFY_MIN_INTERVAL_MS) {
                lastNotifyAtMs = now
                publish(ongoing("Downloading…", title, downloadId, indeterminate = true))
            }
        }
    }

    fun postStatus(content: String, title: String, downloadId: String, publish: (Notification) -> Unit) {
        mainHandler.post {
            val now = System.currentTimeMillis()
            if (content == lastStatusText) return@post
            if (now - lastNotifyAtMs < NOTIFY_STATUS_MIN_INTERVAL_MS) return@post
            lastStatusText = content
            lastNotifyAtMs = now
            publish(ongoing(content, title, downloadId, indeterminate = true))
        }
    }

    /** Updates the ongoing FGS notification without going through WorkManager's Future API. */
    fun notifyOngoing(notification: Notification) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    fun finished(
        success: Boolean,
        title: String,
        uri: Uri?,
        error: String?,
        silent: Boolean = false,
        pageUrl: String? = null,
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
        if (silent) return
        val text = if (success) "Download complete" else (error ?: "Download failed")
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .addAction(0, context.getString(R.string.download_history_action), historyPendingIntent())
        pageUrl?.let {
            builder.addAction(0, context.getString(R.string.download_archive_page), archivePendingIntent(it))
        }
        if (success && uri != null) {
            val open = org.gnosco.share2archivetoday.download.OpenDownloadedMedia
                .viewIntent(context, uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            builder.setContentIntent(
                PendingIntent.getActivity(
                    context, 2, open,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
        }
        nm.notify(NOTIFICATION_DONE_ID, builder.build())
    }

    private fun cancelPendingIntent(downloadId: String): PendingIntent {
        val i = Intent(context, DownloadCancelReceiver::class.java)
            .setAction(ACTION_CANCEL)
            .putExtra(EXTRA_DOWNLOAD_ID, downloadId)
        return PendingIntent.getBroadcast(
            context, downloadId.hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun archivePendingIntent(pageUrl: String): PendingIntent {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(ArchiveToday.submissionUrl(pageUrl)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context, 4, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun historyPendingIntent(): PendingIntent {
        val i = Intent(context, DownloadHistoryActivity::class.java)
        return PendingIntent.getActivity(
            context, 1, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    companion object {
        const val CHANNEL_ID = "video_downloads"
        const val NOTIFICATION_ID = 42
        const val NOTIFICATION_DONE_ID = 43
        const val ACTION_CANCEL = "org.gnosco.share2archivetoday.action.CANCEL_DOWNLOAD"
        const val EXTRA_DOWNLOAD_ID = "download_id"
        private const val NOTIFY_MIN_INTERVAL_MS = 2_500L
        private const val NOTIFY_STATUS_MIN_INTERVAL_MS = 1_200L
        private const val NOTIFY_MIN_PCT_STEP = 10
    }
}
