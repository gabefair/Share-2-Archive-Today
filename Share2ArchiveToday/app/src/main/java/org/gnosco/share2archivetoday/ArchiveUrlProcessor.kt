package org.gnosco.share2archivetoday

import android.net.Uri
import android.util.Log

/**
 * Handles processing of archive.today URLs to extract the embedded target URL
 */
class ArchiveUrlProcessor {
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
                // Now we need to extract just the base URL (before any ? or # characters)
                // This removes tracking parameters that might contain nested URLs
                val baseUrlPattern = Regex("^(https?://[^?#]+)")
                val baseMatch = baseUrlPattern.find(embeddedUrl)
                
                if (baseMatch != null) {
                    val baseUrl = baseMatch.groupValues[1]
                    return baseUrl
                }
                
                // Fallback: return the embedded URL as-is if we can't extract base
                return embeddedUrl
            } catch (e: Exception) {
                return embeddedUrl
            }
        }
        
        return url
    }
}

