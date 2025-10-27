package org.gnosco.share2archivetoday.utils

import org.gnosco.share2archivetoday.utils.*

import org.gnosco.share2archivetoday.download.*

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Helper class for creating and managing download notifications
 */
class NotificationHelper(private val service: Service) {
    
    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "video_download_channel"
        const val CHANNEL_NAME = "Video Downloads"
        
        const val NOTIFICATION_ID_COMPLETE = NOTIFICATION_ID + 1
        const val NOTIFICATION_ID_ERROR = NOTIFICATION_ID + 2
        const val NOTIFICATION_ID_CANCELLED = NOTIFICATION_ID + 3
    }
    
    private val notificationManager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    /**
     * Create the notification channel (required for Android O+)
     */
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for video downloads"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Create a basic download notification
     */
    fun createNotification(
        contentText: String,
        title: String,
        showProgress: Boolean = false,
        progress: Int = 0
    ): Notification {
        val cancelIntent = Intent(service, BackgroundDownloadService::class.java).apply {
            action = BackgroundDownloadService.ACTION_CANCEL_DOWNLOAD
        }
        val cancelPendingIntent = PendingIntent.getService(
            service, 0, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(service, CHANNEL_ID)
            .setContentTitle("Downloading: $title")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                cancelPendingIntent
            )

        if (showProgress) {
            builder.setProgress(100, progress, false)
        }

        return builder.build()
    }
    
    /**
     * Update the notification
     */
    fun updateNotification(contentText: String, title: String, showProgress: Boolean = false, progress: Int = 0) {
        val notification = createNotification(contentText, title, showProgress, progress)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * Update notification with progress bar
     */
    fun updateNotificationWithProgress(text: String, title: String, percentage: Int) {
        val notification = NotificationCompat.Builder(service, CHANNEL_ID)
            .setContentTitle("Downloading: $title")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, percentage, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * Show completion notification
     */
    fun showCompletionNotification(title: String, contentUri: android.net.Uri?, filePath: String?) {
        val intent = createOpenIntent(contentUri, filePath)
        
        val pendingIntent = PendingIntent.getActivity(
            service, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val historyIntent = Intent(service, DownloadHistoryActivity::class.java)
        val historyPendingIntent = PendingIntent.getActivity(
            service, 2, historyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(service, CHANNEL_ID)
            .setContentTitle("Download Complete")
            .setContentText("$title has been downloaded")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_recent_history,
                "View History",
                historyPendingIntent
            )
            .build()

        notificationManager.notify(NOTIFICATION_ID_COMPLETE, notification)
    }
    
    /**
     * Show error notification
     */
    fun showErrorNotification(title: String, error: String) {
        val notification = NotificationCompat.Builder(service, CHANNEL_ID)
            .setContentTitle("Download Failed")
            .setContentText("Failed to download $title: $error")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_ERROR, notification)
    }
    
    /**
     * Show cancelled notification
     */
    fun showCancelledNotification(title: String) {
        val notification = NotificationCompat.Builder(service, CHANNEL_ID)
            .setContentTitle("Download Cancelled")
            .setContentText("$title download was cancelled")
            .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_CANCELLED, notification)
    }
    
    private fun createOpenIntent(contentUri: android.net.Uri?, filePath: String?): Intent {
        return when {
            contentUri != null -> {
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(contentUri, FileUtils.getMimeType(filePath ?: ""))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            filePath != null -> {
                val file = java.io.File(filePath)
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    service, service.applicationContext.packageName + ".fileprovider", file
                )
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, FileUtils.getMimeType(filePath))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            else -> {
                Intent(Intent.ACTION_VIEW).apply {
                    setData(android.net.Uri.parse(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).absolutePath))
                }
            }
        }
    }
}

