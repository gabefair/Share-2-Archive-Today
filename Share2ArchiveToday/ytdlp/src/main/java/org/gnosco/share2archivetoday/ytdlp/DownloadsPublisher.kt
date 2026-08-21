package org.gnosco.share2archivetoday.ytdlp

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** Publishes completed media into the public Downloads collection. */
object DownloadsPublisher {

    fun publish(
        context: Context,
        sourceFile: File,
        displayName: String,
        mimeType: String,
    ): Uri {
        require(sourceFile.exists()) { "Missing file: ${sourceFile.absolutePath}" }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values)
                ?: error("MediaStore insert failed")
            resolver.openOutputStream(uri)?.use { output ->
                sourceFile.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Cannot open MediaStore output stream")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            val dest = File(dir, displayName)
            sourceFile.copyTo(dest, overwrite = true)
            @Suppress("DEPRECATION")
            val values = android.content.ContentValues().apply {
                put(MediaStore.MediaColumns.DATA, dest.absolutePath)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            }
            context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
                ?: Uri.fromFile(dest)
        }
    }
}
