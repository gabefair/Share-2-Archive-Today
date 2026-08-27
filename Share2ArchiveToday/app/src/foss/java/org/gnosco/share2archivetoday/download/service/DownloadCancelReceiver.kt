package org.gnosco.share2archivetoday.download.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles the Cancel action on the download notification. */
class DownloadCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != DownloadNotifications.ACTION_CANCEL) return
        val id = intent.getStringExtra(DownloadNotifications.EXTRA_DOWNLOAD_ID)
        DownloadScheduler.cancel(context, id)
    }
}
