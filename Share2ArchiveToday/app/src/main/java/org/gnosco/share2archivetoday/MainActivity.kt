package org.gnosco.share2archivetoday
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import org.gnosco.share2archivetoday.ublock.UBlockRemoveParamCleaner

open class MainActivity : Activity() {
    private var clearUrlsRulesManager: ClearUrlsRulesManager? = null
    private var uBlockRemoveParamCleaner: UBlockRemoveParamCleaner? = null
    private var qrCodeScanner: QRCodeScanner? = null
    
    // Lazy initialization for components that don't need context
    private val urlExtractor: UrlExtractor by lazy { UrlExtractor() }
    private val urlCleaner: UrlCleaner by lazy { UrlCleaner() }
    private val urlOptimizer: UrlOptimizer by lazy { UrlOptimizer() }
    internal val archiveUrlProcessor: ArchiveUrlProcessor by lazy { ArchiveUrlProcessor() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize components that need context
        clearUrlsRulesManager = ClearUrlsRulesManager(applicationContext)
        uBlockRemoveParamCleaner = UBlockRemoveParamCleaner(applicationContext)
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
        val prefs = getSharedPreferences("share2archive_prefs", MODE_PRIVATE)
        val isFirstTime = prefs.getBoolean("is_first_time", true)

        if (isFirstTime) {
            Toast.makeText(this, "Hold icon to pin to share menu", Toast.LENGTH_LONG).show()

            // Mark as no longer first time
            prefs.edit()
                .putBoolean("is_first_time", false)
                .apply()
        }
    }

    /** FOSS download UI may defer until runtime permissions are granted. */
    protected open fun deferShareIntentHandling(): Boolean = false

    private fun handleShareIntent(intent: Intent?) {
        if (deferShareIntentHandling()) return
        if (intent?.action == Intent.ACTION_SEND) {
            // Show first-time usage tip only when actually sharing
            showFirstTimeToast()

            when (intent.type) {
                "text/plain" -> {
                    intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedText ->
                        val url = extractUrl(sharedText)

                        if (url != null) {
                            fourSteps(url)
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
                            val imageUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
        if (shouldFinishAfterShareIntent()) {
            finish()
        }
    }

    /** Download UI overrides this so dialogs can stay open. */
    protected open fun shouldFinishAfterShareIntent(): Boolean = true

    open fun fourSteps(url: String) {
        val processedUrl = processArchiveUrl(url)
        val cleanedUrl = handleURL(processedUrl)
        
        // Check if the cleaned URL can be archived (single check - getNonArchivableReason returns null if archivable)
        val nonArchivableReason = archiveUrlProcessor.getNonArchivableReason(cleanedUrl)
        if (nonArchivableReason != null) {
            Toast.makeText(this, nonArchivableReason, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        nowOpenInBrowser(cleanedUrl)
    }

    open fun nowOpenInBrowser(cleanedUrl: String): Boolean {
        openInBrowser(ArchiveToday.submissionUrl(cleanedUrl))
        return true
    }

    internal fun handleImageShare(imageUri: Uri) {
        try {
            val qrCodeText = qrCodeScanner?.extractQRCodeFromImage(imageUri) ?: return
            val qrUrl = extractUrl(qrCodeText)

            if (qrUrl != null) {
                fourSteps(qrUrl)
                Toast.makeText(this, "URL found in QR code", Toast.LENGTH_SHORT).show()
            } else {
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
        var rulesCleanedUrl: String
        // Find the last occurrence of a protocol in the URL, which should be the start of the valid part
        val lastValidUrlIndex = findLastHttpProtocolStart(url)
        // Sometimes nested urls which have already been archived by the service are saved with double or param expanded urls. This cleans that up. For example archives from fascist news site westernjournal
        if (lastValidUrlIndex != null) {
            rulesCleanedUrl = url.substring(lastValidUrlIndex).replace(Regex("%09+"), "")
        } else {
            rulesCleanedUrl = url.replace(Regex("%09+"), "")
        }

        // Clean with ClearURLs rules
        if (clearUrlsRulesManager?.areRulesLoaded() == true) {
            rulesCleanedUrl = clearUrlsRulesManager!!.clearUrl(rulesCleanedUrl)
        }

        // Clean with uBlock Origin removeparam rules
        if (uBlockRemoveParamCleaner?.areRulesLoaded() == true) {
            rulesCleanedUrl = uBlockRemoveParamCleaner!!.cleanUrl(rulesCleanedUrl)
        }

        rulesCleanedUrl = cleanTrackingParamsFromUrl(rulesCleanedUrl)

        // Remove anchors and text fragments
        rulesCleanedUrl = removeAnchorsAndTextFragments(rulesCleanedUrl)

        // Then apply additional platform-specific optimizations that might not are in the rules
        return applyPlatformSpecificOptimizations(rulesCleanedUrl)
    }

    internal fun processArchiveUrl(url: String): String {
        return archiveUrlProcessor.processArchiveUrl(url)
    }

    internal fun extractUrl(text: String): String? {
        val extractedUrl = urlExtractor.extractUrl(text)
        return if (extractedUrl != null) {
            val cleaned = cleanUrl(extractedUrl)
            cleaned
        } else {
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
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(browserIntent)
        finish()
    }
}
