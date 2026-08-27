package org.gnosco.share2archivetoday.download.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import org.gnosco.share2archivetoday.R
import org.gnosco.share2archivetoday.download.OpenDownloadedMedia
import org.gnosco.share2archivetoday.download.history.DownloadHistoryStore
import org.gnosco.share2archivetoday.download.history.HistoryEntry

/**
 * Floating history panel — opened from notifications or the share sheet.
 *
 * Swipe left on a row → open the file.
 * Swipe right → remove from history (with undo).
 * Tap → open when possible, otherwise show actions.
 * Long-press → full action sheet.
 */
class DownloadHistoryActivity : Activity() {

    private lateinit var store: DownloadHistoryStore
    private lateinit var listView: ListView
    private lateinit var subtitle: TextView
    private lateinit var swipeHint: TextView
    private lateinit var clearButton: Button
    private lateinit var emptyView: LinearLayout
    private lateinit var undoBar: View
    private lateinit var swipeController: HistorySwipeController
    private val mainHandler = Handler(Looper.getMainLooper())
    private var entries: List<HistoryEntry> = emptyList()
    private var pendingUndo: HistoryEntry? = null
    private var undoHideRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = DownloadHistoryStore(this)
        setContentView(R.layout.activity_download_history)

        subtitle = findViewById(R.id.history_subtitle)
        swipeHint = findViewById(R.id.history_swipe_hint)
        listView = findViewById(R.id.history_list)
        clearButton = findViewById(R.id.history_clear)
        emptyView = findViewById(R.id.history_empty)
        undoBar = findViewById(R.id.history_undo_bar)
        findViewById<Button>(R.id.history_close).setOnClickListener { finish() }
        findViewById<Button>(R.id.history_undo).setOnClickListener { undoDelete() }
        clearButton.setOnClickListener { confirmClear() }

        listView.adapter = HistoryAdapter()
        listView.setOnItemClickListener { _, _, position, _ ->
            onRowTap(entries[position])
        }
        listView.setOnItemLongClickListener { _, _, position, _ ->
            showEntryActions(entries[position])
            true
        }
        swipeController = HistorySwipeController(
            listView = listView,
            onOpen = { position ->
                if (position in entries.indices) openEntry(entries[position])
            },
            onDelete = { position ->
                if (position in entries.indices) deleteEntry(entries[position], offerUndo = true)
            },
        )
        swipeController.attach()

        reload()
    }

    override fun onResume() {
        super.onResume()
        if (::store.isInitialized) reload()
    }

    override fun onDestroy() {
        undoHideRunnable?.let(mainHandler::removeCallbacks)
        super.onDestroy()
    }

    private fun onRowTap(entry: HistoryEntry) {
        if (entry.success && store.uriStillValid(this, entry)) {
            openEntry(entry)
        } else {
            showEntryActions(entry)
        }
    }

    private fun openEntry(entry: HistoryEntry) {
        val canOpen = entry.success && store.uriStillValid(this, entry)
        if (!canOpen) {
            Toast.makeText(this, R.string.download_file_missing, Toast.LENGTH_SHORT).show()
            return
        }
        OpenDownloadedMedia.open(this, Uri.parse(entry.mediaStoreUri!!))
    }

    private fun deleteEntry(entry: HistoryEntry, offerUndo: Boolean) {
        store.remove(entry.id)
        if (offerUndo) {
            showUndo(entry)
        } else {
            hideUndo()
        }
        reload()
    }

    private fun showUndo(entry: HistoryEntry) {
        undoHideRunnable?.let(mainHandler::removeCallbacks)
        pendingUndo = entry
        undoBar.visibility = View.VISIBLE
        undoHideRunnable = Runnable {
            pendingUndo = null
            undoBar.visibility = View.GONE
        }
        mainHandler.postDelayed(undoHideRunnable!!, UNDO_MS)
    }

    private fun hideUndo() {
        undoHideRunnable?.let(mainHandler::removeCallbacks)
        undoHideRunnable = null
        pendingUndo = null
        undoBar.visibility = View.GONE
    }

    private fun undoDelete() {
        val entry = pendingUndo ?: return
        store.add(entry)
        hideUndo()
        reload()
    }

    private fun reload() {
        entries = store.all()
        val hasEntries = entries.isNotEmpty()
        listView.visibility = if (hasEntries) View.VISIBLE else View.GONE
        emptyView.visibility = if (hasEntries) View.GONE else View.VISIBLE
        swipeHint.visibility = if (hasEntries) View.VISIBLE else View.GONE
        clearButton.isEnabled = hasEntries

        if (hasEntries) {
            subtitle.text = getString(R.string.download_history_count, entries.size)
            (listView.adapter as HistoryAdapter).notifyDataSetChanged()
        } else {
            subtitle.text = getString(R.string.download_history_empty)
        }
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle(R.string.download_history_clear)
            .setMessage(R.string.download_history_clear_confirm)
            .setPositiveButton(R.string.download_history_clear) { _, _ ->
                hideUndo()
                store.clear()
                Toast.makeText(this, R.string.download_history_cleared, Toast.LENGTH_SHORT).show()
                reload()
            }
            .setNegativeButton(R.string.download_cancel, null)
            .show()
    }

    private fun showEntryActions(entry: HistoryEntry) {
        val canOpen = entry.success && store.uriStillValid(this, entry)
        val detailText = buildEntryDetailText(entry, canOpen)
        val items = mutableListOf<String>()
        if (canOpen) items.add(getString(R.string.download_open))
        items.add(getString(R.string.download_share))
        items.add(getString(R.string.download_archive_page))
        items.add(getString(R.string.download_again))
        items.add(getString(R.string.download_copy_url))
        items.add(getString(R.string.download_delete_entry))
        items.add(getString(R.string.download_cancel))

        AlertDialog.Builder(this)
            .setTitle(entry.title)
            .setMessage(detailText)
            .setItems(items.toTypedArray()) { _, which ->
                when (items[which]) {
                    getString(R.string.download_open) -> openEntry(entry)
                    getString(R.string.download_share) -> shareEntry(entry, detailText, canOpen)
                    getString(R.string.download_archive_page) -> archivePage(entry)
                    getString(R.string.download_again) -> {
                        startActivity(
                            Intent(this, DownloadVideoActivity::class.java).apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, entry.url)
                            },
                        )
                        finish()
                    }
                    getString(R.string.download_copy_url) -> {
                        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("url", entry.url))
                        Toast.makeText(this, R.string.download_url_copied, Toast.LENGTH_SHORT).show()
                    }
                    getString(R.string.download_delete_entry) -> deleteEntry(entry, offerUndo = true)
                }
            }
            .show()
    }

    /** Snapshot the page the media came from, so the download keeps its context. */
    private fun archivePage(entry: HistoryEntry) {
        val intent = Intent(
            Intent.ACTION_VIEW,
            android.net.Uri.parse(
                org.gnosco.share2archivetoday.ArchiveToday.submissionUrl(entry.url),
            ),
        )
        runCatching { startActivity(intent) }
            .onFailure { Toast.makeText(this, R.string.download_no_browser, Toast.LENGTH_LONG).show() }
    }

    private fun buildEntryDetailText(entry: HistoryEntry, canOpen: Boolean): String =
        buildString {
            append(entry.title)
            append("\n\n")
            append(formatRelativeTime(entry.timestamp))
            entry.estimatedBytes?.takeIf { it > 0 }?.let {
                append(" · ")
                append(formatSize(it))
            }
            urlHost(entry.url)?.let {
                append("\n")
                append(it)
            }
            if (!entry.success) {
                append("\n\n")
                append(entry.error?.take(240) ?: getString(R.string.download_status_fail))
            } else if (!canOpen) {
                append("\n\n")
                append(getString(R.string.download_file_missing))
            }
            append("\n\n")
            append(entry.url)
        }

    private fun shareEntry(entry: HistoryEntry, detailText: String, canOpen: Boolean) {
        val shareText = detailText
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_SUBJECT, entry.title)
            putExtra(Intent.EXTRA_TEXT, shareText)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (canOpen && !entry.mediaStoreUri.isNullOrBlank()) {
            val uri = Uri.parse(entry.mediaStoreUri)
            val mime = contentResolver.getType(uri)?.takeIf { it.isNotBlank() } ?: "video/*"
            intent.type = mime
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.clipData = ClipData.newUri(contentResolver, entry.title, uri)
        } else {
            intent.type = "text/plain"
        }
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.download_share_via)))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.download_share_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun formatRelativeTime(timestamp: Long): String =
        DateUtils.getRelativeDateTimeString(
            this,
            timestamp,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.WEEK_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE,
        ).toString()

    private fun urlHost(url: String): String? =
        runCatching { Uri.parse(url).host?.removePrefix("www.") }.getOrNull()

    private fun formatSize(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1) {
            String.format(Locale.US, "%.1f MB", mb)
        } else {
            String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        }
    }

    private inner class HistoryAdapter : BaseAdapter() {
        override fun getCount(): Int = entries.size
        override fun getItem(position: Int): HistoryEntry = entries[position]
        override fun getItemId(position: Int): Long = entries[position].id.hashCode().toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.item_download_history, parent, false)
            if (::swipeController.isInitialized) swipeController.resetRow(view)

            val entry = entries[position]
            val status = view.findViewById<TextView>(R.id.row_status)
            val meta = view.findViewById<TextView>(R.id.row_meta)
            val title = view.findViewById<TextView>(R.id.row_title)
            val detail = view.findViewById<TextView>(R.id.row_detail)
            val chevron = view.findViewById<TextView>(R.id.row_chevron)

            title.text = entry.title
            meta.text = formatRelativeTime(entry.timestamp)

            val canOpen = entry.success && store.uriStillValid(this@DownloadHistoryActivity, entry)
            chevron.visibility = if (canOpen) View.VISIBLE else View.GONE

            when {
                entry.success && canOpen -> {
                    status.text = getString(R.string.download_status_ok)
                    status.setTextColor(0xFF1B5E20.toInt())
                    status.setBackgroundResource(R.drawable.bg_history_status)
                    bindDetail(detail, entry, showSize = true)
                }
                entry.success -> {
                    status.text = getString(R.string.download_status_missing)
                    status.setTextColor(0xFFE65100.toInt())
                    status.setBackgroundResource(R.drawable.bg_history_status_missing)
                    bindDetail(detail, entry, showSize = false, fallback = getString(R.string.download_file_missing))
                }
                else -> {
                    status.text = getString(R.string.download_status_fail)
                    status.setTextColor(0xFFB71C1C.toInt())
                    status.setBackgroundResource(R.drawable.bg_history_status_fail)
                    val err = entry.error?.take(120)
                    bindDetail(detail, entry, showSize = false, fallback = err)
                }
            }
            return view
        }

        private fun bindDetail(
            detail: TextView,
            entry: HistoryEntry,
            showSize: Boolean,
            fallback: String? = null,
        ) {
            val host = urlHost(entry.url)
            val size = entry.estimatedBytes?.takeIf { it > 0 && showSize }?.let { formatSize(it) }
            val text = when {
                host != null && size != null -> "$host · $size"
                host != null -> host
                size != null -> size
                !fallback.isNullOrBlank() -> fallback
                else -> null
            }
            if (text.isNullOrBlank()) {
                detail.visibility = View.GONE
            } else {
                detail.visibility = View.VISIBLE
                detail.text = text
            }
        }
    }

    companion object {
        private const val UNDO_MS = 5_000L
    }
}
