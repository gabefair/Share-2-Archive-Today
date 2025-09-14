package org.gnosco.share2archivetoday

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Helper class for managing notifications and providing reliable user feedback
 * This replaces unreliable toast messages with proper notifications
 */
class NotificationHelper(private val context: Context) {
    
    companion object {
        const val CHANNEL_ID_GENERAL = "general_notifications"
        const val CHANNEL_ID_DOWNLOAD = "download_notifications"
        const val NOTIFICATION_ID_GENERAL = 1001
        const val NOTIFICATION_ID_DOWNLOAD = 1002
    }
    
    init {
        createNotificationChannels()
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // General notifications channel
            val generalChannel = NotificationChannel(
                CHANNEL_ID_GENERAL,
                "General",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "General app notifications"
                setShowBadge(false)
            }
            
            // Download notifications channel
            val downloadChannel = NotificationChannel(
                CHANNEL_ID_DOWNLOAD,
                "Downloads",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Video download progress and status"
                setShowBadge(false)
            }
            
            notificationManager.createNotificationChannels(listOf(generalChannel, downloadChannel))
        }
    }
    
    /**
     * Show a general notification instead of a toast
     */
    fun showGeneralNotification(title: String, message: String, autoDismiss: Boolean = true) {
        try {
            val builder = NotificationCompat.Builder(context, CHANNEL_ID_GENERAL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(autoDismiss)
                .setOngoing(!autoDismiss)
            
            if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_GENERAL, builder.build())
            }
        } catch (e: Exception) {
            // Fallback to toast if notification fails
            android.widget.Toast.makeText(context, "$title: $message", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Show download progress notification
     */
    fun showDownloadNotification(title: String, message: String, progress: Int = -1, indeterminate: Boolean = false) {
        try {
            val builder = NotificationCompat.Builder(context, CHANNEL_ID_DOWNLOAD)
                .setSmallIcon(android.R.drawable.ic_menu_upload)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(false)
                .setOngoing(true)
            
            if (progress >= 0 && !indeterminate) {
                builder.setProgress(100, progress, false)
            } else if (indeterminate) {
                builder.setProgress(0, 0, true)
            }
            
            if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_DOWNLOAD, builder.build())
            }
        } catch (e: Exception) {
            // Fallback to toast if notification fails
            android.widget.Toast.makeText(context, "$title: $message", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Update download notification with new progress
     */
    fun updateDownloadNotification(title: String, message: String, progress: Int) {
        showDownloadNotification(title, message, progress, false)
    }
    
    /**
     * Complete download notification
     */
    fun completeDownloadNotification(title: String, message: String) {
        try {
            val builder = NotificationCompat.Builder(context, CHANNEL_ID_DOWNLOAD)
                .setSmallIcon(android.R.drawable.ic_menu_send)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setOngoing(false)
            
            if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_DOWNLOAD, builder.build())
            }
        } catch (e: Exception) {
            // Fallback to toast if notification fails
            android.widget.Toast.makeText(context, "$title: $message", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * Cancel all notifications
     */
    fun cancelAllNotifications() {
        try {
            NotificationManagerCompat.from(context).cancelAll()
        } catch (e: Exception) {
            // Ignore errors when canceling
        }
    }
    
    /**
     * Check if notifications are enabled
     */
    fun areNotificationsEnabled(): Boolean {
        return try {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        } catch (e: Exception) {
            false
        }
    }
}
