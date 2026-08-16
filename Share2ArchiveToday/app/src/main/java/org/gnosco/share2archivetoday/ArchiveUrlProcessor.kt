package org.gnosco.share2archivetoday

import android.net.Uri
import android.util.Log

/**
 * Handles processing of archive.today URLs to extract the embedded target URL
 */
class ArchiveUrlProcessor {

    private val nonArchivablePatterns = listOf(
        Pair(Regex("^https?://news\\.google\\.com/read/.*", RegexOption.IGNORE_CASE), "news.google.com/read URLs cannot be archived yet"),
        Pair(Regex("^https?://share\\.google\\.com/.*", RegexOption.IGNORE_CASE), "share.google.com URLs cannot be archived anymore"),
        Pair(Regex("^https?://finance\\.yahoo\\.com/.*", RegexOption.IGNORE_CASE), "finance.yahoo.com URLs cannot be archived anymore"),
    )
    
    // Cache for the last checked URL to avoid duplicate searches
    private var lastCheckedUrl: String? = null
    private var lastCheckResult: Pair<Regex, String>? = null
    /**
     * Process archive URLs to extract the embedded target URL
     * Uses regex to strip the archive.ph/o/{hash}/ prefix and extract the base URL
     */
    fun processArchiveUrl(url: String): String {
        // Match archive.today/ph/is URLs with the /o/{hash}/ pattern
        // Example: https://archive.ph/o/vtomj/https://twitter.com/user/status/123?params...
        val archivePattern = Regex("^https?://(?:archive\\.(?:today|ph|is|fo|li|md|vn)/o/[a-zA-Z0-9]+/)(.+)$")
        val match = archivePattern.find(url)
        
        if (match != null) {
            // Extract everything after the archive prefix
            val embeddedUrl = match.groupValues[1]

            try {
                // Parse the embedded URL to properly extract components
                val uri = Uri.parse(embeddedUrl)
                
                // Check if query parameters contain nested URLs (common in tracking parameters)
                var hasNestedUrls = false
                uri.queryParameterNames?.forEach { paramName ->
                    val paramValue = uri.getQueryParameter(paramName)
                    // Check if the parameter value looks like a URL
                    if (paramValue != null && (paramValue.startsWith("http://") || paramValue.startsWith("https://"))) {
                        hasNestedUrls = true
                    }
                }
                
                // If there are nested URLs in query parameters, extract just the base URL
                // Otherwise, preserve query parameters but remove fragments
                val baseUrl = if (hasNestedUrls) {
                    // Extract just scheme + authority + path (no query params)
                    val builder = Uri.Builder()
                        .scheme(uri.scheme)
                        .authority(uri.authority)
                        .path(uri.path)
                    builder.build().toString()
                } else {
                    // Extract URL with query parameters but without fragments
                    val baseUrlPattern = Regex("^(https?://[^#]+)")
                    val baseMatch = baseUrlPattern.find(embeddedUrl)
                    baseMatch?.groupValues?.get(1) ?: embeddedUrl
                }
                
                // Return the processed baseUrl (either with or without query params depending on nested URLs)
                return baseUrl
            } catch (e: Exception) {
                // If parsing fails, return the embedded URL as-is (fallback)
                return embeddedUrl
            }
        }
        
        // If not an archive URL, return the original URL unchanged
        return url
    }
    
    /**
     * Check if a URL can be archived and get the reason if it cannot
     * This method performs the search once and returns both the result and reason
     * 
     * @param url The URL to check
     * @return A message string explaining why the URL cannot be archived, or null if it can be archived
     */
    fun getNonArchivableReason(url: String): String? {
        val matchedPattern = findMatchingPattern(url)
        return matchedPattern?.second
    }
    
    /**
     * Check if a URL matches any non-archivable pattern
     * This method simply calls getNonArchivableReason for convenience
     * 
     * @param url The URL to check
     * @return true if the URL can be archived, false if it matches a non-archivable pattern
     */
    fun canBeArchived(url: String): Boolean {
        return getNonArchivableReason(url) == null
    }
    
    /**
     * Internal method that searches for a matching non-archivable pattern
     * Uses caching to avoid duplicate searches for the same URL
     * 
     * @param url The URL to check
     * @return The matched pattern (Regex, reason) pair, or null if no match
     */
    private fun findMatchingPattern(url: String): Pair<Regex, String>? {
        // If this is the same URL we just checked, return cached result
        if (url == lastCheckedUrl) {
            return lastCheckResult
        }
        
        try {
            // Search through patterns once
            for ((pattern, reason) in nonArchivablePatterns) {
                if (pattern.matches(url)) {
                    Log.d("ArchiveUrlProcessor", "URL matches non-archivable pattern: $url")
                    val result = Pair(pattern, reason)
                    // Cache the result
                    lastCheckedUrl = url
                    lastCheckResult = result
                    return result
                }
            }
            // Cache null result (URL can be archived)
            lastCheckedUrl = url
            lastCheckResult = null
            return null
        } catch (e: Exception) {
            Log.e("ArchiveUrlProcessor", "Error checking if URL can be archived: $url", e)
            // If there's an error, assume it can be archived (fail open)
            lastCheckedUrl = url
            lastCheckResult = null
            return null
        }
    }
}

