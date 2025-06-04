package org.gnosco.share2archivetoday

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast

/**
 * Activity that cleans URLs and copies them to clipboard instead of opening archive.today
 */
class ClipboardActivity : MainActivity() {
    override fun threeSteps(url: String) {
        val processedUrl = processArchiveUrl(url)
        val cleanedUrl = handleURL(processedUrl)
        copyToClipboard(cleanedUrl)
    }

    private fun copyToClipboard(url: String) {
        try {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Cleaned URL", url)
            clipboard.setPrimaryClip(clip)

            Toast.makeText(this, "Clean URL copied to clipboard", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to copy URL", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    override fun openInBrowser(url: String) {
        // Override to prevent browser opening - this should never be called in clipboard mode
        copyToClipboard(url)
    }
}