package org.gnosco.share2archivetoday

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import android.net.Uri
import android.util.Log
import androidx.core.util.PatternsCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedText ->
                Log.d("MainActivity", "Shared text: $sharedText")
                val url = extractUrl(sharedText)
                if (url != null) {
                    val processedUrl = processArchiveUrl(url)
                    val cleanedUrl = cleanTrackingParamsFromUrl(processedUrl)
                    openInBrowser("https://archive.is/?run=1&url=${Uri.encode(cleanedUrl)}")
                }
            }
        }
        finish()
    }

    private fun processArchiveUrl(url: String): String {
        val uri = Uri.parse(url)
        val pattern = Regex("archive\\.[a-z]+/o/[a-zA-Z0-9]+/(.+)")
        val matchResult = pattern.find(uri.toString())

        return if (matchResult != null) {
            matchResult.groupValues[1]
        } else {
            url
        }
    }

    private fun cleanTrackingParamsFromUrl(url: String): String {
        val uri = Uri.parse(url)
        if (uri.queryParameterNames.isEmpty()) {
            return url
        }

        // List of common tracking parameters to be removed
        val trackingParams = setOf(
            "utm_source", "utm_medium", "utm_campaign", "utm_content", "utm_term",
            "fbclid", "gclid", "dclid", "gbraid", "wbraid", "msclkid", "tclid",
            "aff_id", "affiliate_id", "ref", "referer", "campaign_id", "ad_id",
            "adgroup_id", "adset_id", "creativetype", "placement", "network",
            "mc_eid", "mc_cid", "s", "icid", "_ga", "_gid", "scid", "click_id",
            "trk", "track", "trk_sid", "sid"
        )

        val newUriBuilder = uri.buildUpon().clearQuery()
        uri.queryParameterNames.forEach { param ->
            // Skip adding tracking parameters
            if (param !in trackingParams) {
                newUriBuilder.appendQueryParameter(param, uri.getQueryParameter(param))
            }
        }

        // Additional handling for YouTube URLs
        if (uri.host?.contains("youtube.com") == true || uri.host?.contains("youtu.be") == true) {
            val nestedQueryParams = uri.getQueryParameter("q")
            if (nestedQueryParams != null) {
                val nestedUri = Uri.parse(nestedQueryParams)
                val newNestedUriBuilder = nestedUri.buildUpon().clearQuery()

                nestedUri.queryParameterNames.forEach { nestedParam ->
                    newNestedUriBuilder.appendQueryParameter(nestedParam, nestedUri.getQueryParameter(nestedParam))
                }

                newUriBuilder.appendQueryParameter("q", newNestedUriBuilder.build().toString())
            }

            newUriBuilder.path(uri.path?.replace("/shorts/", "/v/") ?: uri.path)
        }

        return newUriBuilder.build().toString()
    }

    private fun extractUrl(text: String): String? {
        val matcher = PatternsCompat.WEB_URL.matcher(text)
        return if (matcher.find()) {
            matcher.group(0)
        } else {
            null
        }
    }

    private fun openInBrowser(url: String) {
        Log.d("MainActivity", "Opening URL: $url")
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(browserIntent)
        finish()
    }
}
