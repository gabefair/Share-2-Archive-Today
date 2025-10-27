package org.gnosco.share2archivetoday
// This file is: MainActivity.kt

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast

open class MainActivity : Activity() {
    private var clearUrlsRulesManager: ClearUrlsRulesManager? = null
    private var qrCodeScanner: QRCodeScanner? = null
    
    // Lazy initialization for components that don't need context
    private val urlExtractor: UrlExtractor by lazy { UrlExtractor() }
    private val urlCleaner: UrlCleaner by lazy { UrlCleaner() }
    private val urlOptimizer: UrlOptimizer by lazy { UrlOptimizer() }
    private val archiveUrlProcessor: ArchiveUrlProcessor by lazy { ArchiveUrlProcessor() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize components that need context
        clearUrlsRulesManager = ClearUrlsRulesManager(applicationContext)
        qrCodeScanner = QRCodeScanner(applicationContext)

        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    /**
     * Show a toast on first use to help users discover they can pin the app
     */
    private fun showFirstTimeToast() {
        val prefs = getSharedPreferences("share2archive_prefs", Context.MODE_PRIVATE)
        val isFirstTime = prefs.getBoolean("is_first_time", true)

        if (isFirstTime) {
            Toast.makeText(this, "Hold icon to pin to share menu", Toast.LENGTH_LONG).show()

            // Mark as no longer first time
            prefs.edit()
                .putBoolean("is_first_time", false)
                .apply()
        }
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            // Show first-time usage tip only when actually sharing
            showFirstTimeToast()

            when (intent.type) {
                "text/plain" -> {
                    intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedText ->
                        Log.d("MainActivity", "Shared text: $sharedText")
                        val url = extractUrl(sharedText)

                        if (url != null) {
                            threeSteps(url)
                        } else {
                            Toast.makeText(this, "No URL found in shared text", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                }
                else -> {
                    // Handle image shares
                    if (intent.type?.startsWith("image/") == true) {
                        try {
                            val imageUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                            }

                            imageUri?.let {
                                handleImageShare(it)
                            }
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error handling image share", e)
                            Toast.makeText(this, "Share 2 Archive did not like that image", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                }
            }
        }
        finish()
    }

    open fun threeSteps(url: String) {
        Log.d("MainActivity", "threeSteps - Input URL: $url")
        val processedUrl = processArchiveUrl(url)
        Log.d("MainActivity", "threeSteps - After processArchiveUrl: $processedUrl")
        val cleanedUrl = handleURL(processedUrl)
        Log.d("MainActivity", "threeSteps - After handleURL: $cleanedUrl")
        openInBrowser("https://archive.today/?run=1&url=${Uri.encode(cleanedUrl)}")
    }

    internal fun handleImageShare(imageUri: Uri) {
        try {
            val qrCodeText = qrCodeScanner?.extractQRCodeFromImage(imageUri) ?: return
            val qrUrl = extractUrl(qrCodeText)

            if (qrUrl != null) {
                threeSteps(qrUrl)
                Toast.makeText(this, "URL found in QR code", Toast.LENGTH_SHORT).show()
            } else {
                Log.d("MainActivity", "No QR code found in image")
                Toast.makeText(this, "No URL found in QR code image", Toast.LENGTH_SHORT).show()
                finish()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error processing QR code", e)
            Toast.makeText(this, "Error processing QR code", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    /**
     * Main URL handling method that combines ClearURLs rules with platform-specific optimizations
     */
    internal fun handleURL(url: String): String {
        Log.d("MainActivity", "handleURL - Input: $url")
        // First clean with ClearURLs rules
        var rulesCleanedUrl = url
        if (clearUrlsRulesManager?.areRulesLoaded() == true) {
            rulesCleanedUrl = clearUrlsRulesManager!!.clearUrl(url)
            Log.d("MainActivity", "handleURL - After ClearURLs: $rulesCleanedUrl")
        }
        rulesCleanedUrl = cleanTrackingParamsFromUrl(rulesCleanedUrl)
        Log.d("MainActivity", "handleURL - After cleanTrackingParams: $rulesCleanedUrl")

        // Remove anchors and text fragments
        rulesCleanedUrl = removeAnchorsAndTextFragments(rulesCleanedUrl)
        Log.d("MainActivity", "handleURL - After removeAnchors: $rulesCleanedUrl")

        // Then apply additional platform-specific optimizations that might not are in the rules
        val result = applyPlatformSpecificOptimizations(rulesCleanedUrl)
        Log.d("MainActivity", "handleURL - Final output: $result")
        return result
    }

    internal fun processArchiveUrl(url: String): String {
        return archiveUrlProcessor.processArchiveUrl(url)
    }

    internal fun extractUrl(text: String): String? {
        Log.d("MainActivity", "extractUrl - Input text: $text")
        val extractedUrl = urlExtractor.extractUrl(text)
        Log.d("MainActivity", "extractUrl - Extracted: $extractedUrl")
        return if (extractedUrl != null) {
            val cleaned = cleanUrl(extractedUrl)
            Log.d("MainActivity", "extractUrl - After cleaning: $cleaned")
            cleaned
        } else {
            Log.d("MainActivity", "extractUrl - No URL found")
            null
        }
    }

    // Delegating methods for backward compatibility with tests
    internal fun applyPlatformSpecificOptimizations(url: String): String {
        return urlOptimizer.applyPlatformSpecificOptimizations(url)
    }

    internal fun cleanTrackingParamsFromUrl(url: String): String {
        return urlOptimizer.cleanTrackingParamsFromUrl(url)
    }

    internal fun cleanUrl(url: String): String {
        return urlCleaner.cleanUrl(url)
    }

    internal fun removeAnchorsAndTextFragments(url: String): String {
        return urlCleaner.removeAnchorsAndTextFragments(url)
    }

    open fun openInBrowser(url: String) {
        Log.d("MainActivity", "Opening URL: $url")
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(browserIntent)
        finish()
    }
}
