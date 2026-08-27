package org.gnosco.share2archivetoday.ytdlp

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import java.io.File

/** Publishes completed media into the public Downloads collection. */
object DownloadsPublisher {

    const val ROOT_DIR = "Share2Archive"
    private const val REL_DOWNLOADS = "Download/$ROOT_DIR"

    /**
     * @param displayName the name MediaStore actually used. It can differ from the one
     *   requested, because MediaStore silently de-duplicates by appending " (1)".
     *   Sidecar names are derived from this so a file and its metadata stay paired.
     */
    data class Published(val uri: Uri, val displayName: String)

    fun publish(
        context: Context,
        sourceFile: File,
        displayName: String,
        mimeType: String,
        subDir: String? = null,
    ): Published {
        require(sourceFile.exists()) { "Missing file: ${sourceFile.absolutePath}" }
        return write(context, displayName, mimeType, subDir) { output ->
            sourceFile.inputStream().use { input -> input.copyTo(output) }
        }
    }

    fun publishBytes(
        context: Context,
        bytes: ByteArray,
        displayName: String,
        mimeType: String,
        subDir: String? = null,
    ): Published = write(context, displayName, mimeType, subDir) { it.write(bytes) }

    private fun write(
        context: Context,
        displayName: String,
        mimeType: String,
        subDir: String?,
        body: (java.io.OutputStream) -> Unit,
    ): Published {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val relative = if (subDir.isNullOrBlank()) REL_DOWNLOADS else "$REL_DOWNLOADS/$subDir"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: error("MediaStore insert failed")
            try {
                resolver.openOutputStream(uri)?.use(body)
                    ?: error("Cannot open MediaStore output stream")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (t: Throwable) {
                // Leaving IS_PENDING set would strand an invisible row for a week.
                runCatching { resolver.delete(uri, null, null) }
                throw t
            }
            Published(uri, actualDisplayName(context, uri) ?: displayName)
        } else {
            @Suppress("DEPRECATION")
            val base = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                ROOT_DIR,
            )
            val dir = if (subDir.isNullOrBlank()) base else File(base, subDir)
            dir.mkdirs()
            val dest = uniqueFile(dir, displayName)
            dest.outputStream().use(body)
            @Suppress("DEPRECATION")
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DATA, dest.absolutePath)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.DISPLAY_NAME, dest.name)
            }
            val uri = context.contentResolver
                .insert(MediaStore.Files.getContentUri("external"), values)
                ?: Uri.fromFile(dest)
            Published(uri, dest.name)
        }
    }

    private fun actualDisplayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun uniqueFile(dir: File, displayName: String): File {
        val direct = File(dir, displayName)
        if (!direct.exists()) return direct
        val dot = displayName.indexOf('.')
        val stem = if (dot > 0) displayName.substring(0, dot) else displayName
        val suffix = if (dot > 0) displayName.substring(dot) else ""
        var n = 1
        while (true) {
            val candidate = File(dir, "$stem ($n)$suffix")
            if (!candidate.exists()) return candidate
            n++
        }
    }

    /** Free space on the volume backing the app's private work directory. */
    fun freeBytesForWork(context: Context): Long = freeBytes(context.filesDir)

    /** Free space on the volume backing public Downloads. */
    fun freeBytesForDownloads(context: Context): Long {
        @Suppress("DEPRECATION")
        val external = Environment.getExternalStorageDirectory()
        return freeBytes(external ?: context.filesDir)
    }

    private fun freeBytes(path: File): Long = runCatching {
        val stat = StatFs(path.absolutePath)
        stat.availableBlocksLong * stat.blockSizeLong
    }.getOrDefault(Long.MAX_VALUE)

    /**
     * Publishing copies rather than moves, so a download needs room for the work file
     * and the published copy at the same time. Checked up front because discovering it
     * mid-copy means throwing away a completed transfer.
     */
    fun hasRoomFor(context: Context, estimatedBytes: Long?, copies: Int = 2): Boolean {
        if (estimatedBytes == null || estimatedBytes <= 0) return true
        val needed = estimatedBytes * copies + SAFETY_MARGIN_BYTES
        return minOf(freeBytesForWork(context), freeBytesForDownloads(context)) >= needed
    }

    private const val SAFETY_MARGIN_BYTES = 64L * 1024 * 1024
}
