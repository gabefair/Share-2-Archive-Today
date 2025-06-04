package org.gnosco.share2archivetoday
// This file is: MainActivity.kt

import WebURLMatcher
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import kotlin.math.max
import android.widget.Toast

open class MainActivity : Activity() {
    private lateinit var clearUrlsRulesManager: ClearUrlsRulesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize ClearURLs rules manager
        clearUrlsRulesManager = ClearUrlsRulesManager(applicationContext)

        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            when (intent.type) {
                "text/plain" -> {
                    intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedText ->
                        Log.d("MainActivity", "Shared text: $sharedText")
                        val url = extractUrl(sharedText)

                        if (url != null) {
                            threeSteps(url)
                        } else {
                            Toast.makeText(this, "No URL found in shared text", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                }
                else -> {
                    // Handle image shares
                    if (intent.type?.startsWith("image/") == true) {
                        try {
                            val imageUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                            }

                            imageUri?.let {
                                handleImageShare(it)
                            }
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error handling image share", e)
                            Toast.makeText(this, "Share 2 Archive did not like that image", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                }
            }
        }
        finish()
    }

    open fun threeSteps(url: String) {
        val processedUrl = processArchiveUrl(url)
        val cleanedUrl = handleURL(processedUrl)
        openInBrowser("https://archive.today/?run=1&url=${Uri.encode(cleanedUrl)}")
    }

    internal fun handleImageShare(imageUri: Uri) {
        try {
            val qrUrl = extractUrl(extractQRCodeFromImage(imageUri))
            if (qrUrl != null) {
                threeSteps(qrUrl)
                Toast.makeText(this, "URL found in QR code", Toast.LENGTH_SHORT).show()
            } else {
                Log.d("MainActivity", "No QR code found in image")
                Toast.makeText(this, "No URL found in QR code image", Toast.LENGTH_SHORT).show()
                finish()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error processing QR code", e)
            Toast.makeText(this, "Error processing QR code", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    /**
     * Main URL handling method that combines ClearURLs rules with platform-specific optimizations
     */
    internal fun handleURL(url: String): String {
        // First clean with ClearURLs rules
        var rulesCleanedUrl = url
        if (clearUrlsRulesManager.areRulesLoaded()) {
            rulesCleanedUrl = clearUrlsRulesManager.clearUrl(url)
        }
        rulesCleanedUrl = cleanTrackingParamsFromUrl(rulesCleanedUrl)

        // Then apply additional platform-specific optimizations that might not be in the rules
        return applyPlatformSpecificOptimizations(rulesCleanedUrl)
    }

    /**
     * Apply platform-specific optimizations that may not be covered by ClearURLs rules
     */
    internal fun applyPlatformSpecificOptimizations(url: String): String {
        val uri = Uri.parse(url)
        val newUriBuilder = uri.buildUpon()
        var changed = false

        // YouTube-specific handling
        if (uri.host?.contains("youtube.com") == true || uri.host?.contains("youtu.be") == true) {
            // Convert shorts to regular videos
            if (uri.path?.contains("/shorts/") == true) {
                newUriBuilder.path(uri.path?.replace("/shorts/", "/v/"))
                changed = true
            }

            // Remove music. prefix
            val modifiedHost = uri.host?.removePrefix("music.")
            if (modifiedHost != uri.host) {
                newUriBuilder.authority(modifiedHost)
                changed = true
            }

            // Handle nested query parameters in YouTube search links
            val nestedQueryParams = uri.getQueryParameter("q")
            if (nestedQueryParams != null && nestedQueryParams.contains("?")) {
                try {
                    val nestedUri = Uri.parse(nestedQueryParams)
                    val newNestedUriBuilder = nestedUri.buildUpon().legacyClearQuery()

                    nestedUri.legacyGetQueryParameterNames().forEach { nestedParam ->
                        if (!isTrackingParam(nestedParam)) {
                            newNestedUriBuilder.appendQueryParameter(nestedParam, nestedUri.getQueryParameter(nestedParam))
                        }
                    }

                    newUriBuilder.appendQueryParameter("q", newNestedUriBuilder.build().toString())
                    changed = true
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error handling nested query params", e)
                }
            }
        }

        // Substack-specific handling
        else if(uri.host?.endsWith(".substack.com") == true) {
            // Add "no_cover=true" parameter for better archive quality
            if (uri.getQueryParameter("no_cover") == null) {
                newUriBuilder.appendQueryParameter("no_cover", "true")
                changed = true
            }
        }

        else if (uri.host?.contains("amazon.com") == true || uri.host?.contains("amazon.") == true) {
            val path = uri.path ?: ""
            // Remove /ref=... tracking from Amazon URLs
            val refPattern = Regex("/ref=[^/]*")
            val cleanedPath = path.replace(refPattern, "")
            if (cleanedPath != path) {
                newUriBuilder.path(cleanedPath)
                changed = true
            }
        }

        //mailchimp-specific handling
        else if (uri.host?.contains("list-manage.com") == true) {
            if (uri.getQueryParameter("e") != null) {
                // Rebuild query parameters without the "e" parameter
                newUriBuilder.legacyClearQuery()
                uri.legacyGetQueryParameterNames().forEach { param ->
                    if (param != "e") {
                        newUriBuilder.appendQueryParameter(param, uri.getQueryParameter(param))
                    }
                }
                changed = true
            }
        }

        else if (uri.host?.equals("t.me", ignoreCase = true) == true) {
            val path = uri.path?.trimStart('/') ?: ""
            if (!path.startsWith("s/") && path.isNotEmpty()) {
                newUriBuilder.path("/s/$path") //This is to archive some parts of the group chat if the web preview feature is enabled, otherwise the about page will be shown by telegram.
                changed = true
            }
        }

        return if (changed) newUriBuilder.build().toString() else url
    }

    internal fun extractQRCodeFromImage(imageUri: Uri): String {
        val inputStream = contentResolver.openInputStream(imageUri) ?: return ""

        // Read image dimensions first
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeStream(inputStream, null, options)
        inputStream.close()

        // Calculate sample size to avoid OOM
        val maxDimension = max(options.outWidth, options.outHeight)
        val sampleSize = max(1, maxDimension / 2048)

        // Read the actual bitmap with sampling
        val scaledOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        contentResolver.openInputStream(imageUri)?.use { stream ->
            val bitmap = BitmapFactory.decodeStream(stream, null, scaledOptions) ?: return ""

            try {
                // Convert to ZXing format
                val width = bitmap.width
                val height = bitmap.height
                val pixels = IntArray(width * height)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

                val source = RGBLuminanceSource(width, height, pixels)
                val binarizer = HybridBinarizer(source)
                val binaryBitmap = BinaryBitmap(binarizer)

                // Try to decode QR code
                val reader = MultiFormatReader()
                val hints = mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to arrayListOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true
                )

                try {
                    val result = reader.decode(binaryBitmap, hints)
                    return result.text
                } catch (e: NotFoundException) {
                    // No QR code found
                    Log.d("MainActivity", "No QR code found in image")
                    return ""
                }
            } finally {
                bitmap.recycle()
            }
        }
        return ""
    }

    internal fun processArchiveUrl(url: String): String {
        val uri = Uri.parse(url)
        val pattern = Regex("archive\\.[a-z]+/o/[a-zA-Z0-9]+/(.+)")
        val matchResult = pattern.find(uri.toString())

        return if (matchResult != null) {
            matchResult.groupValues[1]
        } else {
            url
        }
    }

    // Keep for fallback purposes
    private fun isTrackingParam(param: String): Boolean {
        val trackingParams = setOf(
            "utm_source", "utm_medium", "utm_campaign", "utm_content", "utm_term",
            "fbclid", "gclid", "dclid", "gbraid", "wbraid", "msclkid", "tclid",
            "aff_id", "affiliate_id", "ref", "referer", "campaign_id", "ad_id",
            "adgroup_id", "adset_id", "creativetype", "placement", "network",
            "mc_eid", "mc_cid", "si", "icid", "_ga", "_gid", "scid", "click_id",
            "trk", "track", "trk_sid", "sid", "mibextid", "fb_action_ids",
            "fb_action_types", "twclid", "igshid", "s_kwcid", "sxsrf", "sca_esv",
            "source", "tbo", "sa", "ved", "pi", "fbs", "fbc", "fb_ref", "client", "ei",
            "gs_lp", "sclient", "oq", "uact", "bih", "biw", // sxsrf might be needed on some sites, but google uses it for tracking
            "m_entstream_source", "entstream_source", "fb_source",
            "ref_source", "ref_medium", "ref_campaign", "ref_content", "ref_term", "ref_keyword",
            "ref_type", "ref_campaign_id", "ref_ad_id", "ref_adgroup_id", "ref_adset_id",
            "ref_creativetype", "ref_placement", "ref_network", "ref_sid", "ref_mc_eid",
            "ref_mc_cid", "ref_scid", "ref_click_id", "ref_trk", "ref_track", "ref_trk_sid",
            "ref_sid", "ref_url", "ref_campaign_id", "ref_ad_id", "ref_adgroup_id", "ref_adset_id",
            "chainedPosts" // Reddits new tracker
        )
        return param in trackingParams
    }

    internal fun isUnwantedYoutubeParam(param: String): Boolean {
        val youtubeParams = setOf(
            "feature",
            "ab_channel",
            "t",
            "si"
        )
        return param in youtubeParams
    }
    internal fun isUnwantedSubstackParam(param: String): Boolean {
        val substackParams = setOf(
            "r",
            "showWelcomeOnShare"
        )
        return param in substackParams
    }

    // Keep for fallback and special handling
    internal fun cleanTrackingParamsFromUrl(url: String): String {
        val uri = Uri.parse(url)
        if (uri.legacyGetQueryParameterNames().isEmpty()) {
            return url
        }

        val newUriBuilder = uri.buildUpon().legacyClearQuery()
        var removeYouTubeParams = false
        var removeSubstackParams = false

        // Additional handling for YouTube URLs
        if (uri.host?.contains("youtube.com") == true || uri.host?.contains("youtu.be") == true) {
            removeYouTubeParams = true
            val nestedQueryParams = uri.getQueryParameter("q")
            if (nestedQueryParams != null) {
                val nestedUri = Uri.parse(nestedQueryParams)
                val newNestedUriBuilder = nestedUri.buildUpon().legacyClearQuery()

                nestedUri.legacyGetQueryParameterNames().forEach { nestedParam ->
                    if (!isTrackingParam(nestedParam)) {
                        newNestedUriBuilder.appendQueryParameter(nestedParam, nestedUri.getQueryParameter(nestedParam))
                    }
                }
                newUriBuilder.appendQueryParameter("q", newNestedUriBuilder.build().toString())
            }
        }

        else if(uri.host?.endsWith(".substack.com") == true) {
            removeSubstackParams = true
        }

        uri.legacyGetQueryParameterNames().forEach { param ->
            // Add only non-tracking parameters to the new URL
            if (!isTrackingParam(param) && !(removeYouTubeParams && isUnwantedYoutubeParam(param)) && !(removeSubstackParams && isUnwantedSubstackParam(param))) {
                newUriBuilder.appendQueryParameter(param, uri.getQueryParameter(param))
            }
        }
        return newUriBuilder.build().toString()
    }


    internal fun extractUrl(text: String): String? {
        // First, try simple protocol-based extraction for better reliability
        val simpleUrl = extractUrlSimple(text)
        if (simpleUrl != null) {
            return cleanUrl(simpleUrl)
        }

        // Fallback to the existing WebURLMatcher approach
        val protocolMatcher = WebURLMatcher.matcher(text)
        if (protocolMatcher.find()) {
            val foundUrl = protocolMatcher.group(0)
            // Validate that the found URL looks reasonable
            if (foundUrl != null && isValidExtractedUrl(foundUrl)) {
                return cleanUrl(foundUrl)
            }
        }

        // If no URL with protocol is found, look for potential bare domains
        val domainPattern = Regex(
            "(?:^|\\s+)(" +  // Start of string or whitespace
                    "(?:[a-zA-Z0-9][a-zA-Z0-9-]*\\.)+?" + // Subdomains and domain name
                    "[a-zA-Z]{2,}" +  // TLD
                    "(?:/[^\\s]*)?" + // Optional path
                    ")(?:\\s+|\$)"    // End of string or whitespace
        )

        val domainMatch = domainPattern.find(text)
        if (domainMatch != null) {
            val bareUrl = domainMatch.groupValues[1].trim()
            // Add https:// prefix and clean the URL
            return cleanUrl("https://$bareUrl")
        }

        return null
    }

    /**
     * Simple URL extraction that looks for http:// or https:// and extracts to the next boundary
     */
    private fun extractUrlSimple(text: String): String? {
        val httpIndex = text.lastIndexOf("http://")
        val httpsIndex = text.lastIndexOf("https://")

        val startIndex = maxOf(httpIndex, httpsIndex)
        if (startIndex == -1) return null

        // Find the end of the URL - look for whitespace, newline, or certain punctuation
        var endIndex = text.length
        for (i in startIndex until text.length) {
            val char = text[i]
            if (char.isWhitespace() || char == '\n' || char == '\r') {
                endIndex = i
                break
            }
            // Stop at certain punctuation that's likely not part of the URL
            if (i > startIndex + 10) { // Only check after we have a reasonable URL length
                if (char in setOf(',', ';', ')', '"', '\'') &&
                    (i == text.length - 1 || text[i + 1].isWhitespace())) {
                    endIndex = i
                    break
                }
            }
        }

        val extractedUrl = text.substring(startIndex, endIndex)
        return if (isValidExtractedUrl(extractedUrl)) extractedUrl else null
    }

    /**
     * Validate that an extracted URL looks reasonable
     */
    private fun isValidExtractedUrl(url: String): Boolean {
        if (url.length < 10) return false // Too short to be a real URL
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false

        try {
            val uri = Uri.parse(url)
            val host = uri.host

            // Must have a valid host
            if (host.isNullOrEmpty()) return false

            // Host should contain at least one dot (domain.tld)
            if (!host.contains(".")) return false

            // Host shouldn't have weird characters that suggest parsing error
            if (host.contains("'") || host.contains('"') || host.contains("â€")) return false

            return true
        } catch (e: Exception) {
            return false
        }
    }

    internal fun cleanUrl(url: String): String {
        val cleanedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            val lastHttpsIndex = url.lastIndexOf("https://")
            val lastHttpIndex = url.lastIndexOf("http://")
            val lastValidUrlIndex = maxOf(lastHttpsIndex, lastHttpIndex)

            if (lastValidUrlIndex != -1) {
                // Extract the portion from the last valid protocol and clean any remaining %09 sequences
                url.substring(lastValidUrlIndex).replace(Regex("%09+"), "")
            } else {
                // If no valid protocol is found, add https:// and clean %09 sequences
                "https://${url.replace(Regex("%09+"), "")}"
            }
        } else {
            // URL already starts with a protocol, just clean %09 sequences
            url.replace(Regex("%09+"), "")
        }

        // Remove any trailing punctuation that might have been shared
        return cleanedUrl
            .removeSuffix("?")
            .removeSuffix("&")
            .removeSuffix("#")
            .removeSuffix(".")
            .removeSuffix(",")
            .removeSuffix(";")
            .removeSuffix(")")
            .removeSuffix("'")
            .removeSuffix("\"")
    }


    open fun openInBrowser(url: String) {
        Log.d("MainActivity", "Opening URL: $url")
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(browserIntent)
        finish()
    }
}