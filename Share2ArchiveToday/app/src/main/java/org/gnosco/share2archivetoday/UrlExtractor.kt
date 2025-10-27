package org.gnosco.share2archivetoday

import WebURLMatcher
import android.net.Uri
import android.util.Log

/**
 * Handles URL extraction from text and validation
 */
class UrlExtractor {
    /**
     * Extract URL from text using multiple strategies
     */
    fun extractUrl(text: String): String? {
        Log.d("UrlExtractor", "Extracting URL from text: $text")
        // First, try simple protocol-based extraction for better reliability
        val simpleUrl = extractUrlSimple(text)
        if (simpleUrl != null) {
            Log.d("UrlExtractor", "Extracted URL (simple): $simpleUrl")
            return simpleUrl
        }

        // Fallback to the existing WebURLMatcher approach
        val protocolMatcher = WebURLMatcher.matcher(text)
        if (protocolMatcher.find()) {
            val foundUrl = protocolMatcher.group(0)
            // Validate that the found URL looks reasonable
            if (foundUrl != null && isValidExtractedUrl(foundUrl)) {
                return foundUrl
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
            // Add https:// prefix
            return "https://$bareUrl"
        }

        return null
    }

    /**
     * Simple URL extraction that looks for http:// or https:// and extracts to the next boundary
     * Prioritizes archive URLs, then uses the FIRST valid URL found (not the last)
     */
    private fun extractUrlSimple(text: String): String? {
        // First, check for archive URLs specifically as they should be prioritized
        val archivePattern = Regex("https?://(?:archive\\.(?:today|ph|is|fo|li|md|vn))[^\\s]*")
        val archiveMatch = archivePattern.find(text)
        if (archiveMatch != null) {
            Log.d("UrlExtractor", "Found archive URL: ${archiveMatch.value}")
            val url = cleanArchiveUrl(archiveMatch.value)
            if (isValidExtractedUrl(url)) {
                Log.d("UrlExtractor", "Archive URL is valid: $url")
                return url
            }
        }
        
        // Use indexOf instead of lastIndexOf to get the FIRST URL
        val httpIndex = text.indexOf("http://")
        val httpsIndex = text.indexOf("https://")

        val startIndex = when {
            httpIndex == -1 && httpsIndex == -1 -> return null
            httpIndex == -1 -> httpsIndex
            httpsIndex == -1 -> httpIndex
            else -> minOf(httpIndex, httpsIndex) // Get the first one
        }

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
     * Clean up archive URLs by removing trailing punctuation that might be captured
     */
    private fun cleanArchiveUrl(url: String): String {
        return url.trimEnd { it in setOf('?', '&', '#', '.', ',', ';', ')', '\'', '"') }
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
}

