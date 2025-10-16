package org.gnosco.share2archivetoday

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Feature flag manager for controlling app features
 * Useful for debugging and gradual feature rollouts
 */
class FeatureFlagManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "FeatureFlagManager"
        private const val PREFS_NAME = "feature_flags"
        private const val DEFAULT_BOOLEAN = false

        @Volatile
        private var instance: FeatureFlagManager? = null

        fun getInstance(context: Context): FeatureFlagManager {
            return instance ?: synchronized(this) {
                instance ?: FeatureFlagManager(context.applicationContext).also { instance = it }
            }
        }

        // Feature flag keys
        const val FALLBACK_HANDLING = "fallback_handling"
        const val DEBUG_LOGGING = "debug_logging"
        const val EXPERIMENTAL_UI = "experimental_ui"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Check if a feature flag is enabled
     */
    fun isEnabled(flag: String, defaultValue: Boolean = DEFAULT_BOOLEAN): Boolean {
        return try {
            prefs.getBoolean(flag, defaultValue)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading feature flag: $flag", e)
            defaultValue
        }
    }

    /**
     * Set a feature flag state
     */
    fun setEnabled(flag: String, enabled: Boolean) {
        try {
            prefs.edit().putBoolean(flag, enabled).apply()
            Log.d(TAG, "Feature flag $flag set to: $enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting feature flag: $flag", e)
        }
    }

    /**
     * Toggle a feature flag (flip current state)
     */
    fun toggle(flag: String): Boolean {
        val currentState = isEnabled(flag)
        val newState = !currentState
        setEnabled(flag, newState)
        return newState
    }

    /**
     * Reset all feature flags to defaults
     */
    fun resetToDefaults() {
        try {
            prefs.edit().clear().apply()
            Log.d(TAG, "All feature flags reset to defaults")
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting feature flags", e)
        }
    }

    /**
     * Get all current feature flag states for debugging
     */
    fun getAllFlags(): Map<String, Boolean> {
        return try {
            val allPrefs = prefs.all
            allPrefs.mapNotNull { (key, value) ->
                if (value is Boolean) key to value else null
            }.toMap()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all feature flags", e)
            emptyMap()
        }
    }

    /**
     * Check if fallback handling is enabled
     * This is a convenience method for the most commonly used flag
     */
    fun isFallbackHandlingEnabled(): Boolean {
        return isEnabled(FALLBACK_HANDLING, defaultValue = false) // Default to disabled for debugging
    }

    /**
     * Set fallback handling state
     */
    fun setFallbackHandlingEnabled(enabled: Boolean) {
        setEnabled(FALLBACK_HANDLING, enabled)
    }
}
