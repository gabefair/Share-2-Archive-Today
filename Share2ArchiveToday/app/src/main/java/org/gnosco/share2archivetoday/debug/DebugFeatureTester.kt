package org.gnosco.share2archivetoday.debug

import org.gnosco.share2archivetoday.FeatureFlagDebugActivity
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Utility to test if debug features are properly hidden in release builds
 */
object DebugFeatureTester {
    
    private const val TAG = "DebugFeatureTester"
    
    /**
     * Test if debug activities are accessible
     */
    fun testDebugActivities(context: Context): TestResult {
        val results = mutableListOf<String>()
        var allHidden = true
        
        // Test FeatureFlagDebugActivity
        try {
            val intent = Intent(context, FeatureFlagDebugActivity::class.java)
            val resolveInfo = context.packageManager.resolveActivity(intent, 0)
            
            if (resolveInfo != null) {
                results.add("❌ FeatureFlagDebugActivity is accessible")
                allHidden = false
            } else {
                results.add("✅ FeatureFlagDebugActivity is hidden")
            }
        } catch (e: Exception) {
            results.add("✅ FeatureFlagDebugActivity is hidden (exception: ${e.message})")
        }
        
        // Test VideoDownloadTestActivity
        try {
            val intent = Intent(context, VideoDownloadTestActivity::class.java)
            val resolveInfo = context.packageManager.resolveActivity(intent, 0)
            
            if (resolveInfo != null) {
                results.add("❌ VideoDownloadTestActivity is accessible")
                allHidden = false
            } else {
                results.add("✅ VideoDownloadTestActivity is hidden")
            }
        } catch (e: Exception) {
            results.add("✅ VideoDownloadTestActivity is hidden (exception: ${e.message})")
        }
        
        // Test debug logging
        val debugLogging = Log.isLoggable(TAG, Log.DEBUG)
        if (debugLogging) {
            results.add("❌ Debug logging is enabled")
            allHidden = false
        } else {
            results.add("✅ Debug logging is disabled")
        }
        
        return TestResult(
            allHidden = allHidden,
            results = results,
            buildType = if (allHidden) "RELEASE" else "DEBUG"
        )
    }
    
    /**
     * Log test results
     */
    fun logTestResults(context: Context) {
        val result = testDebugActivities(context)
        Log.i(TAG, "Debug Feature Test Results:")
        Log.i(TAG, "Build Type: ${result.buildType}")
        result.results.forEach { message ->
            Log.i(TAG, message)
        }
    }
    
    data class TestResult(
        val allHidden: Boolean,
        val results: List<String>,
        val buildType: String
    )
}
