package org.gnosco.share2archivetoday.download.service

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * Live snapshot of the download currently holding the Python interpreter.
 * The share/probe UI reads this so a busy wait can show real progress.
 */
object ActiveDownloadStatus {

    data class Snapshot(
        val downloadId: String? = null,
        val title: String? = null,
        val status: String? = null,
        val downloaded: Long = 0L,
        val total: Long = 0L,
    ) {
        val active: Boolean get() = !downloadId.isNullOrBlank()

        fun progressLabel(): String? {
            if (total > 0L) {
                val pct = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                return "$pct%"
            }
            return status
        }
    }

    private val current = AtomicReference(Snapshot())
    private val listeners = CopyOnWriteArrayList<(Snapshot) -> Unit>()

    fun snapshot(): Snapshot = current.get()

    fun begin(downloadId: String, title: String) {
        publish(Snapshot(downloadId = downloadId, title = title, status = "Starting…"))
    }

    fun progress(downloadId: String, title: String, downloaded: Long, total: Long) {
        val cur = current.get()
        if (cur.downloadId != null && cur.downloadId != downloadId) return
        publish(
            Snapshot(
                downloadId = downloadId,
                title = title,
                status = cur.status,
                downloaded = downloaded,
                total = total,
            ),
        )
    }

    fun status(downloadId: String, title: String, message: String) {
        val cur = current.get()
        if (cur.downloadId != null && cur.downloadId != downloadId) return
        publish(
            Snapshot(
                downloadId = downloadId,
                title = title,
                status = message,
                downloaded = cur.downloaded,
                total = cur.total,
            ),
        )
    }

    fun end(downloadId: String?) {
        val cur = current.get()
        if (downloadId != null && cur.downloadId != null && cur.downloadId != downloadId) return
        publish(Snapshot())
    }

    fun addListener(listener: (Snapshot) -> Unit): () -> Unit {
        listeners.add(listener)
        listener(snapshot())
        return { listeners.remove(listener) }
    }

    private fun publish(snapshot: Snapshot) {
        current.set(snapshot)
        listeners.forEach { runCatching { it(snapshot) } }
    }
}
