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
            "https://www.amazon.com/dp/B07PXRGG6Z/ref=cm_sw_r_cp_api_glt_i_AB12CD34" to
                    "https://www.amazon.com/dp/B07PXRGG6Z",

            "https://www.google.com/url?sa=t&source=web&rct=j&url=https%3A%2F%2Fexample.com&usg=AOvVaw123456" to
                    "https://example.com",

            // Add more test cases with expected results
        )

        for ((input, expected) in testUrls) {
            val cleaned = clearUrlsManager.cleanTrackingParamsFromUrl(input)
            assertEquals("Failed for URL: $input", expected, cleaned)
        }
    }
}