package org.gnosco.share2archivetoday.utils

import org.gnosco.share2archivetoday.network.WebURLMatcher
import android.net.Uri
import android.util.Log

/**
 * Utility class for extracting and validating URLs from text
 */
class UrlExtractor {
    
    companion object {
        private const val TAG = "UrlExtractor"
        
        /**
         * Extract URL from text using multiple strategies
         */
        fun extractUrl(text: String): String? {
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
         * Prioritizes archive URLs, then uses the FIRST valid URL found (not the last)
         */
        private fun extractUrlSimple(text: String): String? {
            // First, check for archive URLs specifically as they should be prioritized
            val archivePattern = Regex("https?://(?:archive\\.(?:today|ph|is|fo|li|md|vn))[^\\s]*")
            val archiveMatch = archivePattern.find(text)
            if (archiveMatch != null) {
                val url = cleanArchiveUrl(archiveMatch.value)
                if (isValidExtractedUrl(url)) {
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
                if (host.contains("'") || host.contains('"') || host.contains("Â")) return false
                return true
            } catch (e: Exception) {
                return false
            }
        }

        /**
         * Clean URL by removing trailing punctuation and fixing protocol issues
         */
        private fun cleanUrl(url: String): String {
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

        /**
         * Check if a URL is a YouTube URL
         * CRITICAL for Play Store compliance - must block YouTube downloads
         */
        fun isYouTubeUrl(url: String): Boolean {
            val uri = Uri.parse(url.lowercase())
            val host = uri.host ?: return false

            return host.contains("youtube.com") ||
                    host.contains("youtu.be") ||
                    host.contains("youtube-nocookie.com") ||
                    host.contains("m.youtube.com") ||
                    host.contains("music.youtube.com") ||
                    host.contains("gaming.youtube.com") ||
                    host.contains("shorts.youtube.com") ||
                    host.contains("googlevideo.com") ||
                    host.contains("youtube.googleapis.com") ||
                    host.contains("ytimg.com") ||
                    host.contains("googleusercontent.com") ||
                    host.contains("yt3.ggpht.com") ||
                    host.contains("ytstatic.com") ||
                    host.contains("youtubei.googleapis.com")
        }
    }
}

