package org.gnosco.share2archivetoday

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*

/**
 * Debug activity for managing feature flags
 * Allows developers and testers to easily enable/disable features for debugging
 */
class FeatureFlagDebugActivity : Activity() {

    companion object {
        private const val TAG = "FeatureFlagDebug"
    }

    private lateinit var featureFlagManager: FeatureFlagManager
    private lateinit var flagsContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        featureFlagManager = FeatureFlagManager.getInstance(this)

        // Create UI programmatically
        val scrollView = ScrollView(this)
        flagsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        scrollView.addView(flagsContainer)
        setContentView(scrollView)

        // Add header
        addHeader()

        // Add feature flag toggles
        addFeatureFlagToggles()

        // Add reset button
        addResetButton()

        // Add current flags info
        addCurrentFlagsInfo()
    }

    private fun addHeader() {
        val header = TextView(this).apply {
            text = "Feature Flag Manager"
            textSize = 24f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 32)
        }
        flagsContainer.addView(header)

        val subtitle = TextView(this).apply {
            text = "Enable/disable features for debugging and testing"
            setPadding(0, 0, 0, 16)
        }
        flagsContainer.addView(subtitle)
    }

    private fun addFeatureFlagToggles() {
        val flags = mapOf(
            FeatureFlagManager.FALLBACK_HANDLING to "Fallback Format Handling",
            FeatureFlagManager.DEBUG_LOGGING to "Enhanced Debug Logging",
            FeatureFlagManager.EXPERIMENTAL_UI to "Experimental UI Features"
        )

        flags.forEach { (flagKey, flagName) ->
            addFeatureFlagToggle(flagKey, flagName)
        }
    }

    private fun addFeatureFlagToggle(flagKey: String, flagName: String) {
        // Create container for this flag
        val flagContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 16)
            weightSum = 1f
        }

        // Flag name
        val nameText = TextView(this).apply {
            text = flagName
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.7f)
        }

        // Toggle switch
        val toggleSwitch = Switch(this).apply {
            isChecked = featureFlagManager.isEnabled(flagKey)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.3f)

            setOnCheckedChangeListener { _, isChecked ->
                featureFlagManager.setEnabled(flagKey, isChecked)
                Log.d(TAG, "Feature flag $flagKey set to: $isChecked")

                // Update the current flags info
                updateCurrentFlagsInfo()
            }
        }

        flagContainer.addView(nameText)
        flagContainer.addView(toggleSwitch)
        flagsContainer.addView(flagContainer)

        // Add separator
        val separator = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
            ).apply {
                setMargins(0, 8, 0, 8)
            }
            setBackgroundColor(0xFFE0E0E0.toInt())
        }
        flagsContainer.addView(separator)
    }

    private fun addResetButton() {
        val resetButton = Button(this).apply {
            text = "Reset All to Defaults"
            setPadding(16, 16, 16, 16)

            setOnClickListener {
                AlertDialog.Builder(this@FeatureFlagDebugActivity)
                    .setTitle("Reset Feature Flags")
                    .setMessage("This will reset all feature flags to their default values. Are you sure?")
                    .setPositiveButton("Reset") { _, _ ->
                        featureFlagManager.resetToDefaults()
                        recreate() // Refresh the activity
                        Log.d(TAG, "All feature flags reset to defaults")
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        flagsContainer.addView(resetButton)
    }

    private fun addCurrentFlagsInfo() {
        val infoText = TextView(this).apply {
            text = "Current Feature Flags:"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 32, 0, 8)
        }
        flagsContainer.addView(infoText)

        // This will be updated dynamically
        updateCurrentFlagsInfo()
    }

    private fun updateCurrentFlagsInfo() {
        // Remove existing info views if they exist (find views by tag)
        val childCount = flagsContainer.childCount
        for (i in childCount - 1 downTo 0) {
            val child = flagsContainer.getChildAt(i)
            if (child is TextView && child.text.startsWith("Current flags:")) {
                flagsContainer.removeView(child)
                break
            }
        }

        val flags = featureFlagManager.getAllFlags()
        val flagsText = flags.entries.joinToString("\n") { (key, value) ->
            "$key: ${if (value) "ENABLED" else "DISABLED"}"
        }

        val infoText = TextView(this).apply {
            text = "Current flags:\n$flagsText"
            setPadding(0, 8, 0, 16)
            setBackgroundColor(0xFFF5F5F5.toInt())
            tag = "current_flags_info" // Add tag for easier identification
        }
        flagsContainer.addView(infoText)
    }
}
