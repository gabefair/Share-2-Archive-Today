package org.gnosco.share2archivetoday

import android.content.Context
import android.util.Log
import org.gnosco.share2archivetoday.clearurls.ClearUrlsProvider
import org.gnosco.share2archivetoday.clearurls.ClearUrlsRulesLoader
import org.gnosco.share2archivetoday.clearurls.ClearUrlsUrlProcessor
import java.util.concurrent.ConcurrentHashMap

/**
 * Manager class for handling ClearURLs rules
 * Based directly on the original ClearURLs extension approach using rules format from https://docs.clearurls.xyz/1.27.3/specs/rules/
 * Optimized for faster URL matching and cleaning
 * 
 * This is the main coordinator that uses separate components for:
 * - Provider management (ClearUrlsProvider)
 * - Rules loading and caching (ClearUrlsRulesLoader)
 * - URL processing and cleaning (ClearUrlsUrlProcessor)
 */
class ClearUrlsRulesManager(private val context: Context) {
    private val TAG = "ClearUrlsRulesManager"

    // Store providers
    private var providers = mutableListOf<ClearUrlsProvider>()
    private var providerKeys = mutableListOf<String>()

    // Domain pattern to Provider map for quick lookups
    private val domainToProviderMap = ConcurrentHashMap<String, MutableList<ClearUrlsProvider>>()

    // Components
    private val rulesLoader = ClearUrlsRulesLoader(context)
    private val urlProcessor = ClearUrlsUrlProcessor()

    // Configuration flags
    private var domainBlocking = true
    private var referralMarketingEnabled = false
    private var localHostsSkipping = true

    init {
        try {
            // Load rules using the rules loader
            val result = rulesLoader.loadRules()
            
            // Store the loaded data
            providers.addAll(result.providers)
            providerKeys.addAll(result.providerKeys)
            domainBlocking = result.domainBlocking
            referralMarketingEnabled = result.referralMarketingEnabled
            localHostsSkipping = result.localHostsSkipping

            // Build the domain to provider map
            val map = rulesLoader.buildDomainProviderMap(providers)
            domainToProviderMap.putAll(map)

            // Configure the URL processor
            urlProcessor.domainBlocking = domainBlocking
            urlProcessor.referralMarketingEnabled = referralMarketingEnabled
            urlProcessor.localHostsSkipping = localHostsSkipping

            Log.d(TAG, "ClearUrlsRulesManager initialized with ${providers.size} providers")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing ClearURLs rules manager", e)
        }
    }

    /**
     * Checks if a string is likely a valid URL that's missing a protocol.
     * Returns true for strings like "example.com" or "www.example.com/path"
     * but returns false for complete URLs like "https://example.com" or non-URL strings.
     */
    fun isValidUrlMissingProtocol(url: String): Boolean {
        return urlProcessor.isValidUrlMissingProtocol(url)
    }

    /**
     * Clean a URL by removing tracking fields
     * This is the main public method to use
     */
    fun clearUrl(url: String): String {
        if (url.isEmpty()) {
            return url
        }

        // If it looks like a valid URL missing protocol, add https:// protocol
        if (urlProcessor.isValidUrlMissingProtocol(url)) {
            return clearUrl("https://$url") // Recursively call with added protocol
        }

        // If it doesn't have a protocol and doesn't look like a valid URL, return as is
        if (!url.contains("://")) {
            return url
        }

        var result = url

        // Check each provider
        for (provider in providers) {
            if (provider.matchURL(result)) {
                val cleanResult = urlProcessor.removeFieldsFromURL(provider, result)

                // Handle redirection
                if (cleanResult["redirect"] == true) {
                    result = cleanResult["url"] as String
                    return result
                }

                // Handle cancellation (domain blocking)
                if (cleanResult["cancel"] == true) {
                    return "" // Return empty for canceled URLs
                }

                // Handle changes
                if (cleanResult["changes"] == true) {
                    result = cleanResult["url"] as String
                }
            }
        }

        return result
    }

    /**
     * Check if rules are loaded
     */
    fun areRulesLoaded(): Boolean {
        return providers.isNotEmpty()
    }
}