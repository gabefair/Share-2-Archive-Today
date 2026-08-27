package org.gnosco.share2archivetoday

import android.net.Uri

/** Builds archive.today submission links. */
object ArchiveToday {

    /**
     * The URL that asks archive.today to snapshot [cleanedUrl].
     *
     * Opened in the user's browser rather than requested by the app: archive.today
     * gates automated submissions, and a background request would risk getting the
     * user's address blocked.
     */
    fun submissionUrl(cleanedUrl: String): String =
        "https://archive.today/?run=1&url=${Uri.encode(cleanedUrl)}"
}
