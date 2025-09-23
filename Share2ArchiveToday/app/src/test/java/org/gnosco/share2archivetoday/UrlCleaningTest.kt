package org.gnosco.share2archivetoday

import org.junit.Test
import org.junit.Assert.*

/**
 * Test cases for URL cleaning functionality
 * These tests verify the fixes for Issue #1 and Issue #2
 */
class UrlCleaningTest {

    /**
     * Test cases for Issue #1: Archive.ph URL processing
     * Verifies that processArchiveUrl correctly extracts the target URL with query parameters
     */
    @Test
    fun testProcessArchiveUrl_Issue1() {
        val mainActivity = MainActivity()
        
        // Test case from Issue #1
        val inputUrl = "https://archive.ph/o/hZbes/https://twitter.com/nexta_tv/status/1969390541759521046?ref_src=twsrc^tfw|twcamp^tweetembed|twterm^1969390541759521046|twgr^b51ef9819dc31f7a2d3c5295a2bcf37ba68f3d3d|twcon^s1_&ref_url=https://www.redditmedia.com/mediaembed/liveupdate/18hnzysb1elcs/LiveUpdate_571533b4-9637-11f0-b411-ce2b58824be7/0"
        val expectedOutput = "https://twitter.com/nexta_tv/status/1969390541759521046?ref_src=twsrc^tfw|twcamp^tweetembed|twterm^1969390541759521046|twgr^b51ef9819dc31f7a2d3c5295a2bcf37ba68f3d3d|twcon^s1_&ref_url=https://www.redditmedia.com/mediaembed/liveupdate/18hnzysb1elcs/LiveUpdate_571533b4-9637-11f0-b411-ce2b58824be7/0"
        
        val actualOutput = mainActivity.processArchiveUrl(inputUrl)
        
        assertEquals("Archive.ph URL should extract target URL with query parameters", expectedOutput, actualOutput)
    }

    @Test
    fun testProcessArchiveUrl_SimpleArchiveUrl() {
        val mainActivity = MainActivity()
        
        // Simple archive.ph URL without complex query parameters
        val inputUrl = "https://archive.ph/o/abc123/https://example.com/page?param=value"
        val expectedOutput = "https://example.com/page?param=value"
        
        val actualOutput = mainActivity.processArchiveUrl(inputUrl)
        
        assertEquals("Simple archive.ph URL should extract target URL correctly", expectedOutput, actualOutput)
    }

    @Test
    fun testProcessArchiveUrl_NoQueryParams() {
        val mainActivity = MainActivity()
        
        // Archive.ph URL with target URL that has no query parameters
        val inputUrl = "https://archive.ph/o/xyz789/https://example.com/simple-page"
        val expectedOutput = "https://example.com/simple-page"
        
        val actualOutput = mainActivity.processArchiveUrl(inputUrl)
        
        assertEquals("Archive.ph URL with no query params should extract target URL correctly", expectedOutput, actualOutput)
    }

    @Test
    fun testProcessArchiveUrl_NonArchiveUrl() {
        val mainActivity = MainActivity()
        
        // Non-archive.ph URL should be returned as-is
        val inputUrl = "https://example.com/regular-url"
        val expectedOutput = "https://example.com/regular-url"
        
        val actualOutput = mainActivity.processArchiveUrl(inputUrl)
        
        assertEquals("Non-archive.ph URL should be returned unchanged", expectedOutput, actualOutput)
    }

    /**
     * Test cases for Issue #2: Google search result URL processing
     * Verifies that Google URLs are handled correctly and produce properly formatted output
     */
    @Test
    fun testApplyPlatformSpecificOptimizations_GoogleUrl_Issue2() {
        val mainActivity = MainActivity()
        
        // Test case from Issue #2
        val inputUrl = "https://www.google.com/url?sa=t&source=web&rct=j&opi=89978449&url=https://amp.miamiherald.com/news/nation-world/world/americas/cuba/article312212357.html&ved=2ahUKEwiVvabWme-PAxX3EmIAHS6qFPsQ0PADKAB6BQilARAB&usg=AOvVaw2Doxr5AlAMZuiMilVbTOHV"
        val expectedOutput = "https://amp.miamiherald.com/news/nation-world/world/americas/cuba/article312212357.html?&ved=2ahUKEwiVvabWme-PAxX3EmIAHS6qFPsQ0PADKAB6BQilARAB&usg=AOvVaw2Doxr5AlAMZuiMilVbTOHV"
        
        val actualOutput = mainActivity.applyPlatformSpecificOptimizations(inputUrl)
        
        assertEquals("Google URL should extract target URL with proper formatting", expectedOutput, actualOutput)
    }

    @Test
    fun testApplyPlatformSpecificOptimizations_GoogleUrl_Simple() {
        val mainActivity = MainActivity()
        
        // Simple Google URL with minimal parameters
        val inputUrl = "https://www.google.com/url?url=https://example.com/page&ved=abc123"
        val expectedOutput = "https://example.com/page?&ved=abc123"
        
        val actualOutput = mainActivity.applyPlatformSpecificOptimizations(inputUrl)
        
        assertEquals("Simple Google URL should extract target URL correctly", expectedOutput, actualOutput)
    }

    @Test
    fun testApplyPlatformSpecificOptimizations_GoogleUrl_OnlyVed() {
        val mainActivity = MainActivity()
        
        // Google URL with only ved parameter
        val inputUrl = "https://www.google.com/url?url=https://example.com/page&ved=xyz789"
        val expectedOutput = "https://example.com/page?&ved=xyz789"
        
        val actualOutput = mainActivity.applyPlatformSpecificOptimizations(inputUrl)
        
        assertEquals("Google URL with only ved should work correctly", expectedOutput, actualOutput)
    }

    @Test
    fun testApplyPlatformSpecificOptimizations_GoogleUrl_OnlyUsg() {
        val mainActivity = MainActivity()
        
        // Google URL with only usg parameter
        val inputUrl = "https://www.google.com/url?url=https://example.com/page&usg=def456"
        val expectedOutput = "https://example.com/page?&usg=def456"
        
        val actualOutput = mainActivity.applyPlatformSpecificOptimizations(inputUrl)
        
        assertEquals("Google URL with only usg should work correctly", expectedOutput, actualOutput)
    }

    @Test
    fun testApplyPlatformSpecificOptimizations_GoogleUrl_NoTrackingParams() {
        val mainActivity = MainActivity()
        
        // Google URL without ved or usg parameters
        val inputUrl = "https://www.google.com/url?url=https://example.com/page&other=param"
        val expectedOutput = "https://example.com/page?"
        
        val actualOutput = mainActivity.applyPlatformSpecificOptimizations(inputUrl)
        
        assertEquals("Google URL without tracking params should still add ?", expectedOutput, actualOutput)
    }

    @Test
    fun testApplyPlatformSpecificOptimizations_NonGoogleUrl() {
        val mainActivity = MainActivity()
        
        // Non-Google URL should not be processed by Google handler
        val inputUrl = "https://example.com/regular-url"
        val expectedOutput = "https://example.com/regular-url"
        
        val actualOutput = mainActivity.applyPlatformSpecificOptimizations(inputUrl)
        
        assertEquals("Non-Google URL should be returned unchanged", expectedOutput, actualOutput)
    }

    @Test
    fun testApplyPlatformSpecificOptimizations_GoogleUrl_WrongPath() {
        val mainActivity = MainActivity()
        
        // Google URL but not the /url path
        val inputUrl = "https://www.google.com/search?q=test"
        val expectedOutput = "https://www.google.com/search?q=test"
        
        val actualOutput = mainActivity.applyPlatformSpecificOptimizations(inputUrl)
        
        assertEquals("Google URL with wrong path should not be processed", expectedOutput, actualOutput)
    }

    /**
     * Test cases for the complete URL handling flow
     * Tests the threeSteps method which combines processArchiveUrl and handleURL
     */
    @Test
    fun testThreeSteps_ArchiveUrl() {
        val mainActivity = MainActivity()
        
        // Test the complete flow with an archive.ph URL
        val inputUrl = "https://archive.ph/o/test123/https://twitter.com/user/status/123456789?ref_src=twsrc"
        val processedUrl = mainActivity.processArchiveUrl(inputUrl)
        val expectedProcessed = "https://twitter.com/user/status/123456789?ref_src=twsrc"
        
        assertEquals("processArchiveUrl should extract target URL correctly", expectedProcessed, processedUrl)
        
        // Note: We can't test the full threeSteps method without mocking the browser opening
        // But we can verify the URL processing steps work correctly
    }

    @Test
    fun testThreeSteps_GoogleUrl() {
        val mainActivity = MainActivity()
        
        // Test the complete flow with a Google URL
        val inputUrl = "https://www.google.com/url?url=https://example.com/page&ved=test123"
        val processedUrl = mainActivity.applyPlatformSpecificOptimizations(inputUrl)
        val expectedProcessed = "https://example.com/page?&ved=test123"
        
        assertEquals("Google URL processing should work correctly", expectedProcessed, processedUrl)
    }

    /**
     * Edge case tests
     */
    @Test
    fun testProcessArchiveUrl_MalformedUrl() {
        val mainActivity = MainActivity()
        
        // Malformed archive.ph URL
        val inputUrl = "https://archive.ph/o/"
        val expectedOutput = "https://archive.ph/o/"
        
        val actualOutput = mainActivity.processArchiveUrl(inputUrl)
        
        assertEquals("Malformed archive.ph URL should be returned as-is", expectedOutput, actualOutput)
    }

    @Test
    fun testApplyPlatformSpecificOptimizations_GoogleUrl_MissingUrlParam() {
        val mainActivity = MainActivity()
        
        // Google URL without the url parameter
        val inputUrl = "https://www.google.com/url?ved=test123&usg=test456"
        val expectedOutput = "https://www.google.com/url?ved=test123&usg=test456"
        
        val actualOutput = mainActivity.applyPlatformSpecificOptimizations(inputUrl)
        
        assertEquals("Google URL without url param should be returned unchanged", expectedOutput, actualOutput)
    }

    @Test
    fun testApplyPlatformSpecificOptimizations_GoogleUrl_InvalidTargetUrl() {
        val mainActivity = MainActivity()
        
        // Google URL with invalid target URL
        val inputUrl = "https://www.google.com/url?url=not-a-valid-url&ved=test123"
        val expectedOutput = "https://www.google.com/url?url=not-a-valid-url&ved=test123"
        
        val actualOutput = mainActivity.applyPlatformSpecificOptimizations(inputUrl)
        
        assertEquals("Google URL with invalid target URL should be returned unchanged", expectedOutput, actualOutput)
    }
}
