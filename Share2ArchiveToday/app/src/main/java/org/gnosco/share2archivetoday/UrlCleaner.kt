package org.gnosco.share2archivetoday

import android.net.Uri
import android.util.Log

/**
 * Handles URL cleaning, formatting, and removal of unwanted elements
 */
class UrlCleaner {
    /**
     * Clean a URL by removing trailing punctuation and normalizing protocol
     */
    fun cleanUrl(url: String): String {
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

        // Parse the URL to check if it has query parameters
        val uri = try {
            Uri.parse(cleanedUrl)
        } catch (e: Exception) {
            // If parsing fails, fall back to simple suffix removal
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

        // If the URL has query parameters, don't remove the '?' character
        val hasQueryParams = uri.query != null
        
        return if (hasQueryParams) {
            // Only remove trailing characters that don't affect query parameters
            cleanedUrl
                .removeSuffix("&")
                .removeSuffix("#")
                .removeSuffix(".")
                .removeSuffix(",")
                .removeSuffix(";")
                .removeSuffix(")")
                .removeSuffix("'")
                .removeSuffix("\"")
        } else {
            // No query parameters, safe to remove '?' and other trailing characters
            cleanedUrl
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
    }

    /**
     * Remove anchors and text fragments from URLs
     */
    fun removeAnchorsAndTextFragments(url: String): String {
        try {
            val uri = Uri.parse(url)
            val fragment = uri.fragment
            
            // If no fragment, return URL as-is
            if (fragment.isNullOrEmpty()) {
                return url
            }
            
            // Check if this is a Chrome text fragment (#:~:text=...)
            if (fragment.startsWith(":~:text=") || fragment.contains(":~:text=")) {
                // Remove the entire fragment for text fragments
                val builder = uri.buildUpon()
                builder.fragment(null)
                return builder.build().toString()
            }
            
            val builder = uri.buildUpon()
            builder.fragment(null)
            return builder.build().toString()
            
        } catch (e: Exception) {
            Log.e("UrlCleaner", "Error removing anchors and text fragments from URL: $url", e)
            // If parsing fails, try simple string manipulation as fallback
            return removeAnchorsAndTextFragmentsSimple(url)
        }
    }
    
    /**
     * Fallback method for removing anchors and text fragments
     */
    private fun removeAnchorsAndTextFragmentsSimple(url: String): String {
        // This pattern matches #:~:text= followed by any characters until end of string
        val textFragmentPattern = Regex("#:~:text=.*$")
        var cleanedUrl = url.replace(textFragmentPattern, "")
        
        // Remove regular anchors (#fragment) but preserve query parameters
        // This pattern matches # followed by any characters that are not ? until end of string
        val anchorPattern = Regex("#[^?]*$")
        cleanedUrl = cleanedUrl.replace(anchorPattern, "")
        
        return cleanedUrl
    }

    /**
     * Ensures a URL is properly formatted, particularly fixing missing ? before query parameters
     */
    fun ensureProperUrlFormat(url: String): String {
        try {
            val uri = Uri.parse(url)
            val query = uri.query
            // If the URI has query parameters but the original string doesn't have a ?, fix it
            if (query != null && query.isNotEmpty() && !url.contains("?")) {
                // Find where the query parameters start in the original URL
                val queryStart = url.indexOf("&")
                if (queryStart != -1) {
                    // Replace the first & with ?&
                    return url.substring(0, queryStart) + "?" + url.substring(queryStart + 1)
                }
            }
            return url
        } catch (e: Exception) {
            Log.e("UrlCleaner", "Error formatting URL: $url", e)
            return url
        }
    }
}

