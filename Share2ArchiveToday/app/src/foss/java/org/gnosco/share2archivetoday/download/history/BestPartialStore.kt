package org.gnosco.share2archivetoday.download.history

import android.content.Context
import java.io.File

/**
 * Owns the per-download scratch directory and keeps the largest partial for each stream
 * across retries.
 *
 * "Largest wins" is only meaningful when comparing retries of the *same* stream, so
 * every candidate is filed under a role ("video", "audio", "merged"). Comparing across
 * roles previously let a muxed or audio-extracted output lose to the larger file it was
 * derived from, and the wrong file got published.
 */
class BestPartialStore(context: Context) {

    private val root =
        File(context.applicationContext.filesDir, "download_partials").also { it.mkdirs() }
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun workDir(downloadId: String): File =
        File(root, downloadId).also { it.mkdirs() }

    fun recordedBytes(downloadId: String, role: String): Long =
        prefs.getLong(bytesKey(downloadId, role), 0L)

    /** Keep [candidate] only if it is strictly larger than the best so far for [role]. */
    fun considerPartial(downloadId: String, role: String, candidate: File): File {
        if (!candidate.exists()) return bestFile(downloadId, role) ?: candidate
        val bestPath = prefs.getString(pathKey(downloadId, role), null)
        val bestFile = bestPath?.let(::File)
        val bestSize = bestFile?.takeIf { it.exists() }?.length()
            ?: recordedBytes(downloadId, role)
        val candidateSize = candidate.length()
        return if (candidateSize > bestSize) {
            if (bestFile != null &&
                bestFile.absolutePath != candidate.absolutePath &&
                bestFile.exists()
            ) {
                bestFile.delete()
            }
            prefs.edit()
                .putString(pathKey(downloadId, role), candidate.absolutePath)
                .putLong(bytesKey(downloadId, role), candidateSize)
                .apply()
            candidate
        } else {
            bestFile?.takeIf { it.exists() } ?: candidate
        }
    }

    fun bestFile(downloadId: String, role: String): File? =
        prefs.getString(pathKey(downloadId, role), null)
            ?.let(::File)
            ?.takeIf { it.exists() }

    fun clear(downloadId: String) {
        workDir(downloadId).deleteRecursively()
        val editor = prefs.edit()
        for (key in prefs.all.keys) {
            if (key.endsWith("_$downloadId") || key.contains("_${downloadId}_")) {
                editor.remove(key)
            }
        }
        editor.apply()
    }

    /**
     * Delete scratch directories left behind by downloads that failed and were never
     * resumed. Without this they accumulate in internal storage indefinitely.
     */
    fun gc(maxAgeMs: Long = DEFAULT_MAX_AGE_MS, keepIds: Set<String> = emptySet()): Int {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        var removed = 0
        val dirs = root.listFiles() ?: return 0
        for (dir in dirs) {
            if (!dir.isDirectory || dir.name in keepIds) continue
            val touched = newestTimestamp(dir)
            if (touched > cutoff) continue
            if (dir.deleteRecursively()) removed++
            val editor = prefs.edit()
            for (key in prefs.all.keys) {
                if (key.endsWith("_${dir.name}") || key.contains("_${dir.name}_")) {
                    editor.remove(key)
                }
            }
            editor.apply()
        }
        return removed
    }

    private fun newestTimestamp(dir: File): Long {
        val children = dir.listFiles() ?: return dir.lastModified()
        return (children.map { it.lastModified() } + dir.lastModified()).max()
    }

    private fun pathKey(id: String, role: String) = "path_${role}_$id"
    private fun bytesKey(id: String, role: String) = "bytes_${role}_$id"

    companion object {
        private const val PREFS = "download_best_partial"
        private const val DEFAULT_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    }
}
