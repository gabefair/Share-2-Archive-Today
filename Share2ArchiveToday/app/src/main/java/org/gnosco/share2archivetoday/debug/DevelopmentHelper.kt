package org.gnosco.share2archivetoday.debug

import org.gnosco.share2archivetoday.BuildConfig
import org.gnosco.share2archivetoday.FeatureFlagManager
import android.content.Context
import android.util.Log
import android.widget.Toast

/**
 * Development helper for toggling feature flags
 * This should be removed or disabled in production builds
 */
object DevelopmentHelper {
    
    private const val TAG = "DevelopmentHelper"
    
    /**
     * Toggle YouTube blocking for development testing
     * Call this from a debug menu or long-press gesture
     */
    fun toggleYouTubeBlocking(context: Context) {
        val featureFlagManager = FeatureFlagManager.getInstance(context)
        val currentState = featureFlagManager.isEnabled(FeatureFlagManager.DISABLE_YOUTUBE_BLOCKING, false)
        val newState = !currentState
        
        featureFlagManager.setEnabled(FeatureFlagManager.DISABLE_YOUTUBE_BLOCKING, newState)
        
        val message = if (newState) {
            "YouTube blocking DISABLED for development"
        } else {
            "YouTube blocking ENABLED (Play Store compliant)"
        }
        
        Log.w(TAG, message)
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
    
    /**
     * Check if we're in development mode
     * In production, this should always return false
     */
    fun isDevelopmentMode(): Boolean {
        // In production builds, this should be false
        // You can use BuildConfig.DEBUG or a custom build variant flag
        return BuildConfig.DEBUG // Use BuildConfig.DEBUG for proper build variant detection
    }
    
    /**
     * Log development information
     */
    fun logDevelopmentInfo(context: Context) {
        if (!isDevelopmentMode()) return
        
        val featureFlagManager = FeatureFlagManager.getInstance(context)
        val youtubeBlockingDisabled = featureFlagManager.isEnabled(FeatureFlagManager.DISABLE_YOUTUBE_BLOCKING, false)
        
        Log.d(TAG, "=== DEVELOPMENT INFO ===")
        Log.d(TAG, "YouTube blocking disabled: $youtubeBlockingDisabled")
        Log.d(TAG, "Debug logging enabled: ${featureFlagManager.isEnabled(FeatureFlagManager.DEBUG_LOGGING, false)}")
        Log.d(TAG, "========================")
    }
}
