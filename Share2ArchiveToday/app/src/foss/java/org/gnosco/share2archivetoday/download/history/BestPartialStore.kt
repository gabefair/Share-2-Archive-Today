package org.gnosco.share2archivetoday.download.history

import android.content.Context
import java.io.File

/**
 * Tracks in-progress temps and keeps the best partial across retries (most bytes wins).
 */
class BestPartialStore(context: Context) {

    private val root = File(context.applicationContext.filesDir, "download_partials").also { it.mkdirs() }
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun workDir(downloadId: String): File =
        File(root, downloadId).also { it.mkdirs() }

    fun recordedBytes(downloadId: String): Long = prefs.getLong(bytesKey(downloadId), 0L)

    /** Keep [candidate] only if it is strictly larger than the best so far. */
    fun considerPartial(downloadId: String, candidate: File): File {
        if (!candidate.exists()) return bestFile(downloadId) ?: candidate
        val bestPath = prefs.getString(pathKey(downloadId), null)
        val bestFile = bestPath?.let(::File)
        val bestSize = bestFile?.takeIf { it.exists() }?.length() ?: recordedBytes(downloadId)
        val candidateSize = candidate.length()
        return if (candidateSize > bestSize) {
            if (bestFile != null && bestFile.absolutePath != candidate.absolutePath && bestFile.exists()) {
                bestFile.delete()
            }
            prefs.edit()
                .putString(pathKey(downloadId), candidate.absolutePath)
                .putLong(bytesKey(downloadId), candidateSize)
                .apply()
            candidate
        } else {
            bestFile?.takeIf { it.exists() } ?: candidate
        }
    }

    fun bestFile(downloadId: String): File? =
        prefs.getString(pathKey(downloadId), null)?.let(::File)?.takeIf { it.exists() }

    fun clear(downloadId: String) {
        bestFile(downloadId)?.delete()
        workDir(downloadId).deleteRecursively()
        prefs.edit().remove(pathKey(downloadId)).remove(bytesKey(downloadId)).apply()
    }

    private fun pathKey(id: String) = "path_$id"
    private fun bytesKey(id: String) = "bytes_$id"

    companion object {
        private const val PREFS = "download_best_partial"
    }
}
