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
        val cleanedUrl = if (!hasHttpProtocol(url)) {
            val lastValidUrlIndex = findLastHttpProtocolStart(url)

                // Extract the portion from the last valid protocol and clean any remaining %09 sequences
            if (lastValidUrlIndex != null) {
                url.substring(lastValidUrlIndex).replace(Regex("%09+"), "")
            } else {
                // If no valid protocol is found, add https:// and clean %09 sequences
                "https://${url.replace(Regex("%09+"), "")}"
            }
        } else {
            // URL already starts with a protocol, just clean %09 sequences
            normalizeUrlProtocol(url).replace(Regex("%09+"), "")
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
     * Remove anchors and text fragments from URLs.
     * Fragments that contain '=' (e.g. #q=trump) are treated as hash-based
     * parameters and preserved. Chrome text fragments (#:~:text=...) are always removed.
     */
    fun removeAnchorsAndTextFragments(url: String): String {
        try {
            val uri = Uri.parse(url)
            val fragment = uri.fragment
            
            // If no fragment, return URL as-is
            if (fragment.isNullOrEmpty()) {
                return url
            }
            
            // Chrome text fragments always strip, even though they contain '='
            if (fragment.startsWith(":~:text=") || fragment.contains(":~:text=")) {
                val builder = uri.buildUpon()
                builder.fragment(null)
                return builder.build().toString()
            }

            // Hash used as parameters (e.g. #q=trump) — keep the fragment
            if (fragment.contains('=')) {
                return url
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

        val hashIndex = cleanedUrl.indexOf('#')
        if (hashIndex == -1) return cleanedUrl

        val fragment = cleanedUrl.substring(hashIndex + 1)
        // Preserve hash-based parameters (contain '='); strip plain anchors
        if (fragment.contains('=')) {
            return cleanedUrl
        }

        return cleanedUrl.substring(0, hashIndex)
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

