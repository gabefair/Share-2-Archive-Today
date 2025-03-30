package org.gnosco.share2archivetoday

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class ClearUrlsRulesManagerTest {

    private lateinit var clearUrlsManager: ClearUrlsRulesManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        clearUrlsManager = ClearUrlsRulesManager(context)
    }

    @Test
    fun testRulesLoaded() {
        // Verify that rules were loaded successfully
        assertTrue("ClearURLs rules should be loaded", clearUrlsManager.areRulesLoaded())
    }

    @Test
    fun testUrlCleaning() {
        // Skip test if rules aren't loaded
        if (!clearUrlsManager.areRulesLoaded()) {
            println("Skipping test as rules aren't loaded")
            return
        }

        val testUrls = mapOf(
            // Google tracking parameters
            "https://www.google.com/search?q=test&ved=123&ei=abc123" to
                    "https://www.google.com/search?q=test",

            // Facebook with tracking parameters
            "https://www.facebook.com/story.php?story_fbid=12345&id=6789&fbclid=abc123" to
                    "https://www.facebook.com/story.php?story_fbid=12345&id=6789",

            // YouTube features
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&feature=youtu.be" to
                    "https://www.youtube.com/watch?v=dQw4w9WgXcQ",

            // Twitter with tracking
            "https://twitter.com/username/status/123456?s=20&t=abc123" to
                    "https://twitter.com/username/status/123456",

            // UTM parameters
            "https://example.com/page?utm_source=newsletter&utm_medium=email&utm_campaign=spring2023" to
                    "https://example.com/page",

            // Multiple tracking parameters
            "https://shop.example.com/product?id=123&fbclid=abc&gclid=xyz&utm_source=facebook" to
                    "https://shop.example.com/product?id=123",

            // Substack with tracking
            "https://writer.substack.com/p/article-title?utm_source=substack&utm_medium=email" to
                    "https://writer.substack.com/p/article-title"
        )

        for ((input, expected) in testUrls) {
            val cleaned = clearUrlsManager.clearUrl(input)
            assertEquals("Failed for URL: $input", expected, cleaned)
        }
    }

    @Test
    fun testSpecialCases() {
        // Test URLs that should remain unchanged or have special handling
        val testUrls = mapOf(
            // No tracking parameters
            "https://example.com/clean-url" to "https://example.com/clean-url",

            // URL with legitimate query parameters that should be preserved
            "https://api.example.com/data?id=123&format=json" to "https://api.example.com/data?id=123&format=json",

            // URL with hash fragment
            "https://docs.example.com/guide#section-3" to "https://docs.example.com/guide#section-3"
        )

        for ((input, expected) in testUrls) {
            val cleaned = clearUrlsManager.clearUrl(input)
            assertEquals("Failed for URL: $input", expected, cleaned)
        }
    }
}