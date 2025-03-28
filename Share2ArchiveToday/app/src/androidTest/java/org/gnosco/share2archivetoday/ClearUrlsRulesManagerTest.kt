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
    fun testUrlCleaning() {
        val testUrls = mapOf(
            // Amazon raw rule test
            "https://www.amazon.com/dp/B07PXRGG6Z/ref=cm_sw_r_cp_api_glt_i_AB12CD34" to
                    "https://www.amazon.com/dp/B07PXRGG6Z",

            // Facebook with tracking parameters
            "https://www.facebook.com/story.php?story_fbid=12345&id=6789&m_entstream_source=timeline" to
                    "https://www.facebook.com/story.php?story_fbid=12345&id=6789",

            // Google with redirection
            "https://www.google.com/url?sa=t&source=web&url=https%3A%2F%2Fexample.com" to
                    "https://example.com",


        )

        for ((input, expected) in testUrls) {
            val cleaned = clearUrlsManager.cleanTrackingParamsFromUrl(input)
            assertEquals("Failed for URL: $input", expected, cleaned)
        }
    }
}