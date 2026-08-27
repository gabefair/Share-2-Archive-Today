package org.gnosco.share2archivetoday.download.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.gnosco.share2archivetoday.R
import org.gnosco.share2archivetoday.download.ui.DownloadVideoActivity
import org.json.JSONArray

/**
 * URLs shared while a download held the Python interpreter.
 * When the interpreter frees up, a notification invites the user back to pick quality.
 */
object PendingShareQueue {

    private const val PREFS = "pending_share_queue"
    private const val KEY = "urls"
    private const val MAX = 8

    fun enqueue(context: Context, url: String) {
        val cleaned = url.trim()
        if (cleaned.isEmpty()) return
        val list = all(context).toMutableList()
        list.removeAll { it == cleaned }
        list.add(cleaned)
        persist(context, list.takeLast(MAX))
    }

    fun peek(context: Context): String? = all(context).firstOrNull()

    fun remove(context: Context, url: String) {
        persist(context, all(context).filterNot { it == url })
    }

    fun all(context: Context): List<String> {
        val raw = prefs(context).getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) add(arr.getString(i))
        }
    }

    /**
     * If something is queued and Python is free, post a one-shot "continue" notification.
     * Called when a download finishes (success, fail, or cancel).
     */
    fun notifyIfReady(context: Context) {
        val url = peek(context) ?: return
        val app = context.applicationContext
        DownloadNotifications(app).ensureChannel()
        val open = Intent(app, DownloadVideoActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pi = PendingIntent.getActivity(
            app,
            url.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val remaining = all(app).size
        val text = if (remaining > 1) {
            app.getString(R.string.download_queued_ready_many, remaining)
        } else {
            app.getString(R.string.download_queued_ready)
        }
        val n = NotificationCompat.Builder(app, DownloadNotifications.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(app.getString(R.string.download_queued_ready_title))
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(app).notify(NOTIFICATION_QUEUED_ID, n)
        }
    }

    private fun persist(context: Context, urls: List<String>) {
        val arr = JSONArray()
        urls.forEach { arr.put(it) }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    const val NOTIFICATION_QUEUED_ID = 44
}
