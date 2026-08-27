package org.gnosco.share2archivetoday.download

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import org.gnosco.share2archivetoday.R

/** Builds / launches VIEW intents for saved MediaStore files. */
object OpenDownloadedMedia {

    private const val TAG = "OpenDownloadedMedia"

    fun viewIntent(
        context: Context,
        uri: Uri,
        fallbackMime: String = "video/mp4",
    ): Intent {
        val mime = context.contentResolver.getType(uri)?.takeIf { it.isNotBlank() } ?: fallbackMime
        return Intent(Intent.ACTION_VIEW).apply {
            // Mime + URI; do not set component — let the system / chooser resolve.
            setDataAndType(uri, mime)
            clipData = ClipData.newUri(context.contentResolver, "media", uri)
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun open(activity: Activity, uri: Uri, fallbackMime: String = "video/mp4") {
        val intent = viewIntent(activity, uri, fallbackMime)
        // queryIntentActivities(content://media/…) often returns empty on Android 11+
        // even when players exist; probe by mime only for a useful error, then always
        // hand the real URI to the system chooser.
        if (!hasAnyViewer(activity, intent.type ?: fallbackMime)) {
            Log.w(TAG, "No mime viewers for type=${intent.type} uri=$uri")
            Toast.makeText(activity, R.string.download_no_player, Toast.LENGTH_LONG).show()
            return
        }
        try {
            val chooser = Intent.createChooser(
                intent,
                activity.getString(R.string.download_open_with),
            ).apply {
                // Chooser must carry the grant or targets can't read the MediaStore URI.
                clipData = intent.clipData
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(chooser)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "VIEW failed for $uri", e)
            Toast.makeText(activity, R.string.download_no_player, Toast.LENGTH_LONG).show()
        }
    }

    private fun hasAnyViewer(context: Context, mime: String): Boolean {
        val probe = Intent(Intent.ACTION_VIEW).apply {
            type = mime
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        val flags = PackageManager.MATCH_DEFAULT_ONLY
        return context.packageManager.queryIntentActivities(probe, flags).isNotEmpty()
    }
}
