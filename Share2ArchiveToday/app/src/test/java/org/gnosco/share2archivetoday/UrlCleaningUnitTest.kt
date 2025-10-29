package org.gnosco.share2archivetoday

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for URL cleaning methods
 * These tests focus on the specific methods that were fixed for Issue #1 and Issue #2
 */
class UrlCleaningUnitTest {

    /**
     * Unit tests for processArchiveUrl method (Issue #1 fix)
     */
    @Test
    fun testProcessArchiveUrl_PreservesQueryParameters() {
        val mainActivity = MainActivity()
        
        // Test that query parameters are preserved in archive.ph URLs
        val inputUrl = "https://archive.ph/o/hZbes/https://twitter.com/nexta_tv/status/1969390541759521046?ref_src=twsrc^tfw|twcamp^tweetembed|twterm^1969390541759521046|twgr^b51ef9819dc31f7a2d3c5295a2bcf37ba68f3d3d|twcon^s1_&ref_url=https://www.redditmedia.com/mediaembed/liveupdate/18hnzysb1elcs/LiveUpdate_571533b4-9637-11f0-b411-ce2b58824be7/0"
        
        val result = mainActivity.processArchiveUrl(inputUrl)
        
        // Should extract the Twitter URL with all its query parameters
        assertTrue("Result should contain the Twitter URL", result.contains("twitter.com/nexta_tv/status/1969390541759521046"))
        assertTrue("Result should contain ref_src parameter", result.contains("ref_src=twsrc"))
        assertTrue("Result should contain ref_url parameter", result.contains("ref_url="))
        assertTrue("Result should start with https://", result.startsWith("https://"))
    }

    @Test
    fun testProcessArchiveUrl_RemovesFragments() {
        val mainActivity = MainActivity()
        
        // Test that fragments are removed from archive.ph URLs
        val inputUrl = "https://archive.ph/o/test123/https://example.com/page?param=value#fragment"
        
        val result = mainActivity.processArchiveUrl(inputUrl)
        
        assertEquals("Fragments should be removed", "https://example.com/page?param=value", result)
    }

    @Test
    fun testProcessArchiveUrl_HandlesDecoding() {
        val mainActivity = MainActivity()
        
        // Test that URL decoding works correctly
        val inputUrl = "https://archive.ph/o/test123/https%3A//example.com/page%3Fparam%3Dvalue"
        
        val result = mainActivity.processArchiveUrl(inputUrl)
        
        assertEquals("URL should be properly decoded", "https://example.com/page?param=value", result)
    }

    /**
     * Unit tests for Google URL handling in applyPlatformSpecificOptimizations (Issue #2 fix)
     */
    @Test
    fun testGoogleUrlHandler_ExtractsTargetUrl() {
        val mainActivity = MainActivity()
        
        // Test that Google URLs extract the target URL correctly
        val inputUrl = "https://www.google.com/url?sa=t&source=web&rct=j&opi=89978449&url=https://amp.miamiherald.com/news/nation-world/world/americas/cuba/article312212357.html&ved=2ahUKEwiVvabWme-PAxX3EmIAHS6qFPsQ0PADKAB6BQilARAB&usg=AOvVaw2Doxr5AlAMZuiMilVbTOHV"
        
        val result = mainActivity.applyPlatformSpecificOptimizations(inputUrl)
        
        // Should return the cleaned target URL with Google tracking parameters
        assertTrue("Result should contain the target URL", result.contains("amp.miamiherald.com/news/nation-world/world/americas/cuba/article312212357.html"))
        assertTrue("Result should contain ved parameter", result.contains("ved=2ahUKEwiVvabWme-PAxX3EmIAHS6qFPsQ0PADKAB6BQilARAB"))
        assertTrue("Result should contain usg parameter", result.contains("usg=AOvVaw2Doxr5AlAMZuiMilVbTOHV"))
        assertTrue("Result should start with https://", result.startsWith("https://"))
    }

    @Test
    fun testGoogleUrlHandler_CleansTargetUrlTracking() {
        val mainActivity = MainActivity()
        
        // Test that Google URLs clean tracking parameters from the target URL
        val inputUrl = "https://www.google.com/url?url=https://example.com/page?utm_source=google&utm_medium=cpc&fbclid=123&other=keep&ved=google123&usg=google456"
        
        val result = mainActivity.applyPlatformSpecificOptimizations(inputUrl)
        
        // Should clean tracking parameters from target URL and add Google tracking params
        assertTrue("Result should contain the base URL", result.contains("https://example.com/page"))
        assertTrue("Result should contain non-tracking parameter", result.contains("other=keep"))
        assertTrue("Result should contain Google ved parameter", result.contains("ved=google123"))
        assertTrue("Result should contain Google usg parameter", result.contains("usg=google456"))
        assertFalse("Should not contain utm_source from target URL", result.contains("utm_source"))
        assertFalse("Should not contain utm_medium from target URL", result.contains("utm_medium"))
        assertFalse("Should not contain fbclid from target URL", result.contains("fbclid"))
    }

    @Test
    fun testGoogleUrlHandler_AddsQuestionMark() {
        val mainActivity = MainActivity()
        
        // Test that the ? character is properly added before query parameters
        val inputUrl = "https://www.google.com/url?url=https://example.com/page&ved=test123&usg=test456"
        
        val result = mainActivity.applyPlatformSpecificOptimizations(inputUrl)
        
        // Should have ? before the query parameters
        assertTrue("Result should contain ? before query parameters", result.contains("?"))
        assertTrue("Result should have proper format with ?&", result.contains("?&ved="))
    }

    @Test
    fun testGoogleUrlHandler_HandlesMissingTrackingParams() {
        val mainActivity = MainActivity()
        
        // Test Google URL without ved or usg parameters
        val inputUrl = "https://www.google.com/url?url=https://example.com/page&other=param"
        
        val result = mainActivity.applyPlatformSpecificOptimizations(inputUrl)
        
        assertEquals("Should return target URL with empty parameter", "https://example.com/page?", result)
    }

    @Test
    fun testGoogleUrlHandler_OnlyVedParameter() {
        val mainActivity = MainActivity()
        
        // Test Google URL with only ved parameter
        val inputUrl = "https://www.google.com/url?url=https://example.com/page&ved=abc123"
        
        val result = mainActivity.applyPlatformSpecificOptimizations(inputUrl)
        
        assertEquals("Should return target URL with ved parameter", "https://example.com/page?&ved=abc123", result)
    }

    @Test
    fun testGoogleUrlHandler_OnlyUsgParameter() {
        val mainActivity = MainActivity()
        
        // Test Google URL with only usg parameter
        val inputUrl = "https://www.google.com/url?url=https://example.com/page&usg=def456"
        
        val result = mainActivity.applyPlatformSpecificOptimizations(inputUrl)
        
        assertEquals("Should return target URL with usg parameter", "https://example.com/page?&usg=def456", result)
    }

    /**
     * Unit tests for cleanUrl method (Issue #2 fix)
     */
    @Test
    fun testCleanUrl_PreservesQuestionMarkWithQueryParams() {
        val mainActivity = MainActivity()
        
        // Test that ? is preserved when query parameters exist
        val inputUrl = "https://example.com/page?param=value"
        
        val result = mainActivity.cleanUrl(inputUrl)
        
        assertTrue("Question mark should be preserved when query parameters exist", result.contains("?"))
        assertEquals("URL should remain unchanged", inputUrl, result)
    }

    @Test
    fun testCleanUrl_RemovesQuestionMarkWithoutQueryParams() {
        val mainActivity = MainActivity()
        
        // Test that ? is removed when no query parameters exist
        val inputUrl = "https://example.com/page?"
        
        val result = mainActivity.cleanUrl(inputUrl)
        
        assertFalse("Question mark should be removed when no query parameters exist", result.contains("?"))
        assertEquals("URL should have ? removed", "https://example.com/page", result)
    }

    @Test
    fun testCleanUrl_HandlesMalformedUrls() {
        val mainActivity = MainActivity()
        
        // Test that malformed URLs are handled gracefully
        val inputUrl = "not-a-valid-url"
        
        val result = mainActivity.cleanUrl(inputUrl)
        
        // Should not crash and should return some form of the input
        assertNotNull("Result should not be null", result)
        assertTrue("Result should contain the input", result.contains("not-a-valid-url"))
    }

    /**
     * Integration tests for the complete URL cleaning flow
     */
    @Test
    fun testCompleteFlow_ArchiveUrlToCleanUrl() {
        val mainActivity = MainActivity()
        
        // Test the complete flow: archive.ph URL -> processArchiveUrl -> cleanUrl
        val inputUrl = "https://archive.ph/o/test123/https://example.com/page?param=value"
        
        val processedUrl = mainActivity.processArchiveUrl(inputUrl)
        val cleanedUrl = mainActivity.cleanUrl(processedUrl)
        
        assertEquals("Complete flow should preserve query parameters", "https://example.com/page?param=value", cleanedUrl)
    }

    @Test
    fun testCompleteFlow_GoogleUrlToCleanUrl() {
        val mainActivity = MainActivity()
        
        // Test the complete flow: Google URL -> applyPlatformSpecificOptimizations -> cleanUrl
        val inputUrl = "https://www.google.com/url?url=https://example.com/page&ved=test123"
        
        val processedUrl = mainActivity.applyPlatformSpecificOptimizations(inputUrl)
        val cleanedUrl = mainActivity.cleanUrl(processedUrl)
        
        assertEquals("Complete flow should preserve Google tracking parameters", "https://example.com/page?&ved=test123", cleanedUrl)
    }

    /**
     * Edge case tests
     */
    @Test
    fun testProcessArchiveUrl_EmptyPathSegments() {
        val mainActivity = MainActivity()
        
        // Test archive.ph URL with insufficient path segments
        val inputUrl = "https://archive.ph/o/"
        
        val result = mainActivity.processArchiveUrl(inputUrl)
        
        assertEquals("Should return original URL when path segments are insufficient", inputUrl, result)
    }

    @Test
    fun testGoogleUrlHandler_WrongHost() {
        val mainActivity = MainActivity()
        
        // Test non-Google host
        val inputUrl = "https://example.com/url?url=https://target.com/page&ved=test123"
        
        val result = mainActivity.applyPlatformSpecificOptimizations(inputUrl)
        
        assertEquals("Should return original URL for non-Google host", inputUrl, result)
    }

    @Test
    fun testGoogleUrlHandler_WrongPath() {
        val mainActivity = MainActivity()
        
        // Test Google host but wrong path
        val inputUrl = "https://www.google.com/search?url=https://target.com/page&ved=test123"
        
        val result = mainActivity.applyPlatformSpecificOptimizations(inputUrl)
        
        assertEquals("Should return original URL for wrong path", inputUrl, result)
    }
}
