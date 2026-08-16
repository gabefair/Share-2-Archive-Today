package org.gnosco.share2archivetoday

import WebURLMatcher
import android.net.Uri

/**
 * Handles URL extraction from text and validation
 */
class UrlExtractor {
    private val archiveUrlPattern = Regex(
        """https?://(?:archive\.(?:today|ph|is|fo|li|md|vn))[^\s]*""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Extract URL from text using multiple strategies
     */
    fun extractUrl(text: String): String? {
        // First, try simple protocol-based extraction for better reliability
        val simpleUrl = extractUrlSimple(text)
        if (simpleUrl != null) {
            return simpleUrl
        }

        // Fallback to the existing WebURLMatcher approach
        val protocolMatcher = WebURLMatcher.matcher(text)
        if (protocolMatcher.find()) {
            val foundUrl = protocolMatcher.group(0)
            // Validate that the found URL looks reasonable
            if (foundUrl != null && isValidExtractedUrl(foundUrl)) {
                return normalizeUrlProtocol(foundUrl)
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
            return "http://$bareUrl"
        }

        return null
    }

    /**
     * Simple URL extraction that looks for http:// or https:// and extracts to the next boundary
     * Prioritizes archive URLs, then uses the FIRST valid URL found (not the last)
     */
    private fun extractUrlSimple(text: String): String? {
        val archiveMatch = archiveUrlPattern.find(text)
        if (archiveMatch != null) {
            val url = normalizeUrlProtocol(cleanArchiveUrl(archiveMatch.value))
            if (isValidExtractedUrl(url)) {
                return url
            }
        }

        val startIndex = findFirstHttpProtocolStart(text) ?: return null

        var endIndex = text.length
        for (i in startIndex until text.length) {
            val char = text[i]
            if (char.isWhitespace() || char == '\n' || char == '\r') {
                endIndex = i
                break
            }
            if (i > startIndex + 10) {
                if (char in setOf(',', ';', ')', '"', '\'') &&
                    (i == text.length - 1 || text[i + 1].isWhitespace())) {
                    endIndex = i
                    break
                }
            }
        }

        val extractedUrl = normalizeUrlProtocol(text.substring(startIndex, endIndex))
        return if (isValidExtractedUrl(extractedUrl)) extractedUrl else null
    }

    private fun cleanArchiveUrl(url: String): String {
        return url.trimEnd { it in setOf('?', '&', '#', '.', ',', ';', ')', '\'', '"') }
    }

    private fun isValidExtractedUrl(url: String): Boolean {
        if (url.length < 10) return false
        if (!hasHttpProtocol(url)) return false

        try {
            val uri = Uri.parse(normalizeUrlProtocol(url))
            val host = uri.host

            if (host.isNullOrEmpty()) return false
            if (!host.contains(".")) return false
            if (host.contains("'") || host.contains('"') || host.contains("â€")) return false
            return true
        } catch (e: Exception) {
            return false
        }
    }
}
