package org.gnosco.share2archivetoday

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

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                            val processedUrl = processArchiveUrl(url)
                            val cleanedUrl = cleanTrackingParamsFromUrl(processedUrl)
                            openInBrowser("https://archive.today/?run=1&url=${Uri.encode(cleanedUrl)}")
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
                            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { imageUri ->
                                handleImageShare(imageUri)
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

    private fun handleImageShare(imageUri: Uri) {
        try {
            val qrUrl = extractQRCodeFromImage(imageUri)
            if (qrUrl != null) {
                val processedUrl = processArchiveUrl(qrUrl)
                val cleanedUrl = cleanTrackingParamsFromUrl(processedUrl)
                openInBrowser("https://archive.today/?run=1&url=${Uri.encode(cleanedUrl)}")
            } else {
                Log.d("MainActivity", "No QR code found in image")
                Toast.makeText(this, "No QR code found in image", Toast.LENGTH_SHORT).show()
                finish()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error processing QR code", e)
            Toast.makeText(this, "Error processing QR code", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun extractQRCodeFromImage(imageUri: Uri): String? {
        val inputStream = contentResolver.openInputStream(imageUri) ?: return null

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
            val bitmap = BitmapFactory.decodeStream(stream, null, scaledOptions) ?: return null

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
                    return null
                }
            } finally {
                bitmap.recycle()
            }
        }
        return null
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

    private fun isTrackingParam(param: String): Boolean {
        val trackingParams = setOf(
            "utm_source", "utm_medium", "utm_campaign", "utm_content", "utm_term",
            "fbclid", "gclid", "dclid", "gbraid", "wbraid", "msclkid", "tclid",
            "aff_id", "affiliate_id", "ref", "referer", "campaign_id", "ad_id",
            "adgroup_id", "adset_id", "creativetype", "placement", "network",
            "mc_eid", "mc_cid", "si", "icid", "_ga", "_gid", "scid", "click_id",
            "trk", "track", "trk_sid", "sid", "mibextid", "fb_action_ids",
            "fb_action_types", "twclid", "igshid", "s_kwcid", "sxsrf", "sca_esv",
            "source", "tbo", "sa", "ved", "pi" //sxsrf might be needed on some sites, but google uses it for tracking
        )
        return param in trackingParams
    }

    private fun isUnwantedYoutubeParam(param: String): Boolean {
        val youtubeParams = setOf(
            "feature"
        )
        return param in youtubeParams
    }

    private fun cleanTrackingParamsFromUrl(url: String): String {
        val uri = Uri.parse(url)
        if (uri.legacyGetQueryParameterNames().isEmpty()) {
            return url
        }

        val newUriBuilder = uri.buildUpon().legacyClearQuery()
        var removeYouTubeParams = false

        // Additional handling for YouTube URLs
        if (uri.host?.contains("youtube.com") == true || uri.host?.contains("youtu.be") == true) {
            removeYouTubeParams = true
            val nestedQueryParams = uri.getQueryParameter("q")
            if (nestedQueryParams != null) {
                val nestedUri = Uri.parse(nestedQueryParams)
                val newNestedUriBuilder = nestedUri.buildUpon().legacyClearQuery()

                nestedUri.legacyGetQueryParameterNames().forEach { nestedParam ->
                    newNestedUriBuilder.appendQueryParameter(nestedParam, nestedUri.getQueryParameter(nestedParam))
                }

                newUriBuilder.appendQueryParameter("q", newNestedUriBuilder.build().toString())
            }

            val modifiedHost = uri.host?.removePrefix("music.")
            newUriBuilder.authority(modifiedHost)

            newUriBuilder.path(uri.path?.replace("/shorts/", "/v/") ?: uri.path)
        }

        else if(uri.host?.endsWith(".substack.com") == true) {
            // Add "?no_cover=true" to the URL path
            newUriBuilder.appendQueryParameter("no_cover", "true")
        }

        uri.legacyGetQueryParameterNames().forEach { param ->
            // Add only non-tracking parameters to the new URL
            if (!isTrackingParam(param) && !(removeYouTubeParams && isUnwantedYoutubeParam(param))) {
                newUriBuilder.appendQueryParameter(param, uri.getQueryParameter(param))
            }
        }

        return newUriBuilder.build().toString()
    }

    private fun extractUrl(text: String): String? {
        val matcher = WebURLMatcher.matcher(text)
        return if (matcher.find()) {
            var url = matcher.group(0)
            // Clean the URL by removing erroneous prefixes
            url = cleanUrl(url)
            url
        } else {
            null
        }
    }

    private fun cleanUrl(url: String): String {
        // Find the last occurrence of "https://" in the URL, which should be the start of the valid part
        val lastHttpsIndex = url.lastIndexOf("https://")
        val lastHttpIndex = url.lastIndexOf("http://")
        val lastValidUrlIndex = maxOf(lastHttpsIndex, lastHttpIndex)

        return if (lastValidUrlIndex != -1) {
            // Extract the portion from the last valid "https://" and clean any remaining %09 sequences
            url.substring(lastValidUrlIndex).replace(Regex("%09+"), "")
        } else {
            // If no valid "https://" is found, return the original URL cleaned of %09 sequences
            url.replace(Regex("%09+"), "")
        }
    }



    private fun openInBrowser(url: String) {
        Log.d("MainActivity", "Opening URL: $url")
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(browserIntent)
        finish()
    }
}
