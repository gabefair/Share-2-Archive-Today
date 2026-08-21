package org.gnosco.share2archivetoday.download.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import org.gnosco.share2archivetoday.R
import org.gnosco.share2archivetoday.download.history.DownloadHistoryStore
import java.text.DateFormat
import java.util.Date

/**
 * History list with no MAIN/LAUNCHER — opened from notifications or share-sheet target.
 */
class DownloadHistoryActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Share-sheet target ignores EXTRA_TEXT; always show history.
        val store = DownloadHistoryStore(this)
        val entries = store.all()
        if (entries.isEmpty()) {
            Toast.makeText(this, R.string.download_history_empty, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        val labels = entries.map { e ->
            val status = if (e.success) "OK" else "FAIL"
            "$status · ${e.title}\n${df.format(Date(e.timestamp))}"
        }

        val list = ListView(this)
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        setContentView(list)

        list.setOnItemClickListener { _, _, position, _ ->
            val entry = entries[position]
            val items = mutableListOf<String>()
            if (entry.success && store.uriStillValid(this, entry)) items.add("Open")
            items.add("Download again")
            items.add("Cancel")
            AlertDialog.Builder(this)
                .setTitle(entry.title)
                .setItems(items.toTypedArray()) { _, which ->
                    when (items[which]) {
                        "Open" -> {
                            startActivity(
                                Intent(Intent.ACTION_VIEW)
                                    .setData(Uri.parse(entry.mediaStoreUri))
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            )
                        }
                        "Download again" -> {
                            // Re-enter the download share flow with this URL.
                            val send = Intent(this, DownloadVideoActivity::class.java).apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, entry.url)
                            }
                            startActivity(send)
                            finish()
                        }
                    }
                }
                .show()
        }
    }
}
