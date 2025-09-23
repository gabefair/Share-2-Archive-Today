package org.gnosco.share2archivetoday

/**
 * Test configuration and helper methods for URL cleaning tests
 * 
 * This file contains test utilities and configuration that can be used
 * across different test classes for URL cleaning functionality.
 */
object TestConfiguration {
    
    /**
     * Test data for Issue #1 (Archive.ph URL processing)
     */
    object Issue1TestData {
        const val COMPLEX_ARCHIVE_URL = "https://archive.ph/o/hZbes/https://twitter.com/nexta_tv/status/1969390541759521046?ref_src=twsrc^tfw|twcamp^tweetembed|twterm^1969390541759521046|twgr^b51ef9819dc31f7a2d3c5295a2bcf37ba68f3d3d|twcon^s1_&ref_url=https://www.redditmedia.com/mediaembed/liveupdate/18hnzysb1elcs/LiveUpdate_571533b4-9637-11f0-b411-ce2b58824be7/0"
        const val EXPECTED_TWITTER_URL = "https://twitter.com/nexta_tv/status/1969390541759521046?ref_src=twsrc^tfw|twcamp^tweetembed|twterm^1969390541759521046|twgr^b51ef9819dc31f7a2d3c5295a2bcf37ba68f3d3d|twcon^s1_&ref_url=https://www.redditmedia.com/mediaembed/liveupdate/18hnzysb1elcs/LiveUpdate_571533b4-9637-11f0-b411-ce2b58824be7/0"
        
        const val SIMPLE_ARCHIVE_URL = "https://archive.ph/o/abc123/https://example.com/page?param=value"
        const val EXPECTED_SIMPLE_URL = "https://example.com/page?param=value"
        
        const val NO_QUERY_ARCHIVE_URL = "https://archive.ph/o/xyz789/https://example.com/simple-page"
        const val EXPECTED_NO_QUERY_URL = "https://example.com/simple-page"
    }
    
    /**
     * Test data for Issue #2 (Google URL processing)
     */
    object Issue2TestData {
        const val COMPLEX_GOOGLE_URL = "https://www.google.com/url?sa=t&source=web&rct=j&opi=89978449&url=https://amp.miamiherald.com/news/nation-world/world/americas/cuba/article312212357.html&ved=2ahUKEwiVvabWme-PAxX3EmIAHS6qFPsQ0PADKAB6BQilARAB&usg=AOvVaw2Doxr5AlAMZuiMilVbTOHV"
        const val EXPECTED_MIAMI_HERALD_URL = "https://amp.miamiherald.com/news/nation-world/world/americas/cuba/article312212357.html?&ved=2ahUKEwiVvabWme-PAxX3EmIAHS6qFPsQ0PADKAB6BQilARAB&usg=AOvVaw2Doxr5AlAMZuiMilVbTOHV"
        
        const val SIMPLE_GOOGLE_URL = "https://www.google.com/url?url=https://example.com/page&ved=abc123"
        const val EXPECTED_SIMPLE_GOOGLE_URL = "https://example.com/page?&ved=abc123"
        
        const val GOOGLE_URL_ONLY_VED = "https://www.google.com/url?url=https://example.com/page&ved=xyz789"
        const val EXPECTED_GOOGLE_URL_ONLY_VED = "https://example.com/page?&ved=xyz789"
        
        const val GOOGLE_URL_ONLY_USG = "https://www.google.com/url?url=https://example.com/page&usg=def456"
        const val EXPECTED_GOOGLE_URL_ONLY_USG = "https://example.com/page?&usg=def456"
    }
    
    /**
     * Helper method to validate URL structure
     */
    fun isValidUrl(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }
    
    /**
     * Helper method to check if URL has query parameters
     */
    fun hasQueryParameters(url: String): Boolean {
        return url.contains("?") && url.split("?").size > 1 && url.split("?")[1].isNotEmpty()
    }
    
    /**
     * Helper method to extract domain from URL
     */
    fun extractDomain(url: String): String? {
        return try {
            val uri = java.net.URI(url)
            uri.host
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Helper method to count query parameters in URL
     */
    fun countQueryParameters(url: String): Int {
        return if (hasQueryParameters(url)) {
            url.split("?")[1].split("&").size
        } else {
            0
        }
    }
    
    /**
     * Test cases that should be run to verify the fixes
     */
    val CRITICAL_TEST_CASES = listOf(
        "Issue1_ArchivePhUrlProcessing",
        "Issue1_PreservesQueryParameters", 
        "Issue1_RemovesFragments",
        "Issue2_GoogleUrlProcessing",
        "Issue2_AddsQuestionMark",
        "Issue2_ExtractsTargetUrl",
        "Issue2_PreservesTrackingParameters"
    )
    
    /**
     * Expected behavior documentation for the fixes
     */
    object ExpectedBehavior {
        const val ISSUE1_DESCRIPTION = """
            Issue #1 Fix: Archive.ph URL Processing
            - Should extract the target URL from archive.ph/o/.../target-url format
            - Should preserve all query parameters from the target URL
            - Should remove fragments (#...) from the target URL
            - Should handle URL decoding properly
            - Should return original URL if not an archive.ph URL
        """
        
        const val ISSUE2_DESCRIPTION = """
            Issue #2 Fix: Google URL Processing  
            - Should detect Google search result URLs (www.google.com/url)
            - Should extract the target URL from the 'url' parameter
            - Should add Google tracking parameters (ved, usg) to the target URL
            - Should ensure proper formatting with ? before query parameters
            - Should handle URLs without tracking parameters gracefully
            - Should return original URL if not a Google redirect URL
        """
    }
}
