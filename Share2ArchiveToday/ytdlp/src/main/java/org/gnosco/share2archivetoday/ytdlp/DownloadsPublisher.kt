package org.gnosco.share2archivetoday.ytdlp

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** Publishes completed media into the public Downloads collection. */
object DownloadsPublisher {

    private const val REL_DOWNLOADS = "Download/Share2Archive"

    fun publish(
        context: Context,
        sourceFile: File,
        displayName: String,
        mimeType: String,
    ): Uri {
        require(sourceFile.exists()) { "Missing file: ${sourceFile.absolutePath}" }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
                put(MediaStore.MediaColumns.RELATIVE_PATH, REL_DOWNLOADS)
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values)
                ?: error("MediaStore insert failed")
            resolver.openOutputStream(uri)?.use { output ->
                sourceFile.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Cannot open MediaStore output stream")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } else {
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Share2Archive",
            )
            if (!dir.exists()) dir.mkdirs()
            val dest = File(dir, displayName)
            sourceFile.copyTo(dest, overwrite = true)
            @Suppress("DEPRECATION")
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DATA, dest.absolutePath)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            }
            context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
                ?: Uri.fromFile(dest)
        }
    }
}
