package org.gnosco.share2archivetoday

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URLDecoder
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * Manager class for handling ClearURLs rules
 * Based on the rules format from https://docs.clearurls.xyz/1.27.3/specs/rules/
 */
class ClearUrlsRulesManager(private val context: Context) {
    private val TAG = "ClearUrlsRulesManager"

    // Cached rules data
    private var providers: JSONObject? = null
    private var globalTrackers: Set<String> = emptySet()
    private var rawRules: Map<String, List<Pattern>> = emptyMap()
    private var redirections: Map<String, List<Pattern>> = emptyMap()
    private var completeProviders: Set<String> = emptySet()
    private var exceptions: Map<String, List<Pattern>> = emptyMap()
    private var referralMarketing: Map<String, Set<String>> = emptyMap()
    private var providerUrlPatterns: Map<String, Pattern> = emptyMap()

    // Initialize and load rules from assets
    init {
        try {
            loadRules()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading ClearURLs rules", e)
        }
    }

    /**
     * Load rules from the bundled JSON file in assets
     */
    private fun loadRules() {
        try {
            val jsonString = loadJSONFromAsset("data.minify.json")
            if (jsonString != null) {
                val rulesData = JSONObject(jsonString)

                // Load provider-specific rules
                providers = rulesData.optJSONObject("providers")

                // Load global trackers (common tracking parameters across all sites)
                val trackersArray = rulesData.optJSONObject("providers")?.optJSONObject("globalRules")?.optJSONArray("rules")
                if (trackersArray != null) {
                    globalTrackers = parseTrackers(trackersArray)
                }

                // Process raw rules, redirections, and exceptions
                processProviderSpecificRules()

                Log.d(TAG, "Loaded ClearURLs rules: ${providers?.length() ?: 0} providers, ${globalTrackers.size} global trackers")
            } else {
                Log.e(TAG, "Failed to load rules file from assets")
            }
        } catch (e: JSONException) {
            Log.e(TAG, "Error parsing ClearURLs rules", e)
        }
    }

    /**
     * Process provider-specific rules including raw rules, redirections, and exceptions
     */
    private fun processProviderSpecificRules() {
        val rawRulesMap = mutableMapOf<String, MutableList<Pattern>>()
        val redirectionsMap = mutableMapOf<String, MutableList<Pattern>>()
        val exceptionsMap = mutableMapOf<String, MutableList<Pattern>>()
        val completeProviderSet = mutableSetOf<String>()
        val referralMarketingMap = mutableMapOf<String, MutableSet<String>>()
        val urlPatternsMap = mutableMapOf<String, Pattern>()

        providers?.let { providersObj ->
            val providerIter = providersObj.keys()
            while (providerIter.hasNext()) {
                val providerName = providerIter.next()
                val provider = providersObj.optJSONObject(providerName) ?: continue

                // Store URL pattern
                val urlPatternStr = provider.optString("urlPattern", "")
                if (urlPatternStr.isNotEmpty()) {
                    try {
                        urlPatternsMap[providerName] = Pattern.compile(urlPatternStr)
                    } catch (e: PatternSyntaxException) {
                        Log.e(TAG, "Invalid URL pattern for provider $providerName: $urlPatternStr", e)
                    }
                }

                // Check if this is a complete provider (should be blocked entirely)
                if (provider.optBoolean("completeProvider", false)) {
                    completeProviderSet.add(providerName)
                    Log.d(TAG, "Added complete provider: $providerName with pattern: $urlPatternStr")
                }

                // Process raw rules (applied to the entire URL path)
                val rawRulesArray = provider.optJSONArray("rawRules")
                if (rawRulesArray != null) {
                    val patterns = mutableListOf<Pattern>()
                    for (i in 0 until rawRulesArray.length()) {
                        try {
                            val rulePattern = rawRulesArray.getString(i)
                            patterns.add(Pattern.compile(rulePattern))
                            Log.d(TAG, "Added raw rule for $providerName: $rulePattern")
                        } catch (e: PatternSyntaxException) {
                            Log.e(TAG, "Invalid pattern in rawRules for $providerName", e)
                        }
                    }
                    if (patterns.isNotEmpty()) {
                        rawRulesMap[providerName] = patterns
                    }
                }

                // Process redirections
                val redirectionsArray = provider.optJSONArray("redirections")
                if (redirectionsArray != null) {
                    val patterns = mutableListOf<Pattern>()
                    for (i in 0 until redirectionsArray.length()) {
                        try {
                            val redirectPattern = redirectionsArray.getString(i)
                            patterns.add(Pattern.compile(redirectPattern))
                            Log.d(TAG, "Added redirection for $providerName: $redirectPattern")
                        } catch (e: PatternSyntaxException) {
                            Log.e(TAG, "Invalid pattern in redirections for $providerName", e)
                        }
                    }
                    if (patterns.isNotEmpty()) {
                        redirectionsMap[providerName] = patterns
                    }
                }

                // Process exceptions
                val exceptionsArray = provider.optJSONArray("exceptions")
                if (exceptionsArray != null) {
                    val patterns = mutableListOf<Pattern>()
                    for (i in 0 until exceptionsArray.length()) {
                        try {
                            val exceptionPattern = exceptionsArray.getString(i)
                            patterns.add(Pattern.compile(exceptionPattern))
                        } catch (e: PatternSyntaxException) {
                            Log.e(TAG, "Invalid pattern in exceptions for $providerName", e)
                        }
                    }
                    if (patterns.isNotEmpty()) {
                        exceptionsMap[providerName] = patterns
                    }
                }

                // Process referral marketing rules
                val referralMarketingArray = provider.optJSONArray("referralMarketing")
                if (referralMarketingArray != null) {
                    val refParams = mutableSetOf<String>()
                    for (i in 0 until referralMarketingArray.length()) {
                        val refPattern = referralMarketingArray.optString(i, "")
                        if (refPattern.isNotEmpty()) {
                            // Convert to simple parameter name if possible
                            val cleanParam = refPattern.trim('&', '=', '^', '(', ')', '?', '%', '3', 'F')
                            if (cleanParam.isNotEmpty()) {
                                refParams.add(cleanParam)
                            }
                        }
                    }
                    if (refParams.isNotEmpty()) {
                        referralMarketingMap[providerName] = refParams
                    }
                }
            }
        }

        // Store the processed rules
        rawRules = rawRulesMap
        redirections = redirectionsMap
        exceptions = exceptionsMap
        completeProviders = completeProviderSet
        referralMarketing = referralMarketingMap
        providerUrlPatterns = urlPatternsMap
    }

    /**
     * Parse tracker parameter patterns from a JSON array
     */
    private fun parseTrackers(trackersArray: JSONArray): Set<String> {
        val trackers = mutableSetOf<String>()
        for (i in 0 until trackersArray.length()) {
            val tracker = trackersArray.optString(i)
            if (tracker.isNotEmpty()) {
                // Convert ClearURLs pattern to a simple parameter name
                // Example: "&utm_source=" becomes "utm_source"
                val cleanTracker = tracker.trim('&', '=', '^', '(', ')', '?', '%', '3', 'F')
                if (cleanTracker.isNotEmpty()) {
                    trackers.add(cleanTracker)
                }
            }
        }
        return trackers
    }

    /**
     * Load JSON from assets file
     */
    private fun loadJSONFromAsset(fileName: String): String? {
        return try {
            val inputStream = context.assets.open(fileName)
            val bufferedReader = BufferedReader(InputStreamReader(inputStream))
            val stringBuilder = StringBuilder()
            var line: String?
            while (bufferedReader.readLine().also { line = it } != null) {
                stringBuilder.append(line)
            }
            bufferedReader.close()
            stringBuilder.toString()
        } catch (e: IOException) {
            Log.e(TAG, "Error reading rules file from assets", e)
            null
        }
    }

    /**
     * Check if a URL matches an exception pattern
     */
    private fun isUrlException(url: String): Boolean {
        // Check global exceptions first
        val globalExceptions = exceptions["globalRules"]
        if (globalExceptions != null) {
            for (pattern in globalExceptions) {
                if (pattern.matcher(url).find()) {
                    return true
                }
            }
        }

        // Then check provider-specific exceptions
        for ((providerName, pattern) in providerUrlPatterns) {
            if (providerName == "globalRules") continue

            // Check if URL matches the provider's pattern
            if (pattern.matcher(url).find()) {
                // Check against this provider's exception patterns
                val providerExceptions = exceptions[providerName] ?: continue
                for (exceptionPattern in providerExceptions) {
                    if (exceptionPattern.matcher(url).find()) {
                        return true
                    }
                }
            }
        }

        return false
    }

    /**
     * Find providers that match a given URL
     */
    private fun findMatchingProviders(url: String): List<String> {
        val matchingProviders = mutableListOf<String>()

        for ((providerName, pattern) in providerUrlPatterns) {
            if (providerName == "globalRules") continue

            if (pattern.matcher(url).find()) {
                matchingProviders.add(providerName)
            }
        }

        return matchingProviders
    }

    /**
     * Check if a parameter is a tracking parameter based on ClearURLs rules
     */
    fun isTrackingParam(param: String, url: String): Boolean {
        // First check against global trackers
        if (globalTrackers.contains(param)) {
            return true
        }

        // Then check provider-specific rules
        val matchingProviders = findMatchingProviders(url)
        for (providerName in matchingProviders) {
            val provider = providers?.optJSONObject(providerName) ?: continue

            // Check against this provider's rules
            val rules = provider.optJSONArray("rules") ?: continue
            val providerTrackers = parseTrackers(rules)

            if (providerTrackers.contains(param)) {
                return true
            }
        }

        // Default to the original list for backward compatibility
        return isTrackingParamLegacy(param)
    }

    /**
     * Check if a parameter is a referral marketing parameter
     */
    private fun isReferralMarketingParam(param: String, url: String): Boolean {
        val matchingProviders = findMatchingProviders(url)

        for (providerName in matchingProviders) {
            val refParams = referralMarketing[providerName] ?: continue
            if (refParams.contains(param)) {
                return true
            }
        }

        return false
    }

    /**
     * Legacy list of tracking parameters as a fallback
     */
    private fun isTrackingParamLegacy(param: String): Boolean {
        val trackingParams = setOf(
            "utm_source", "utm_medium", "utm_campaign", "utm_content", "utm_term",
            "fbclid", "gclid", "dclid", "gbraid", "wbraid", "msclkid", "tclid",
            "aff_id", "affiliate_id", "ref", "referer", "campaign_id", "ad_id",
            "adgroup_id", "adset_id", "creativetype", "placement", "network",
            "mc_eid", "mc_cid", "si", "icid", "_ga", "_gid", "scid", "click_id",
            "trk", "track", "trk_sid", "sid", "mibextid", "fb_action_ids",
            "fb_action_types", "twclid", "igshid", "s_kwcid", "sxsrf", "sca_esv",
            "source", "tbo", "sa", "ved", "pi", "fbs", "fbc", "fb_ref", "client", "ei",
            "gs_lp", "sclient", "oq", "uact", "bih", "biw"
        )
        return param in trackingParams
    }

    /**
     * Check if a YouTube parameter should be removed
     */
    fun isUnwantedYoutubeParam(param: String): Boolean {
        val youtubeParams = setOf(
            "feature", "app", "ab_channel"
        )
        return param in youtubeParams
    }

    /**
     * Check if a URL matches a complete provider (should be blocked entirely)
     */
    private fun isCompleteProvider(url: String): Boolean {
        for (providerName in completeProviders) {
            val pattern = providerUrlPatterns[providerName] ?: continue
            if (pattern.matcher(url).find()) {
                Log.d(TAG, "Matched complete provider: $providerName for URL: $url")
                return true
            }
        }

        return false
    }

    /**
     * Check for and extract redirected URL
     */
    private fun checkForRedirections(url: String): String? {
        val matchingProviders = findMatchingProviders(url)

        // Check provider-specific redirections
        for (providerName in matchingProviders) {
            val redirectPatterns = redirections[providerName] ?: continue

            for (pattern in redirectPatterns) {
                val matcher = pattern.matcher(url)
                if (matcher.find() && matcher.groupCount() >= 1) {
                    var redirectUrl = matcher.group(1) ?: continue

                    // Some URLs might be double-encoded
                    try {
                        redirectUrl = URLDecoder.decode(redirectUrl, "UTF-8")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error decoding URL: $redirectUrl", e)
                    }

                    Log.d(TAG, "Found redirection in $providerName: $url -> $redirectUrl")
                    return redirectUrl
                }
            }
        }

        // Check global redirections
        val globalRedirections = redirections["globalRules"]
        if (globalRedirections != null) {
            for (pattern in globalRedirections) {
                val matcher = pattern.matcher(url)
                if (matcher.find() && matcher.groupCount() >= 1) {
                    var redirectUrl = matcher.group(1) ?: continue

                    // Try to decode the URL
                    try {
                        redirectUrl = URLDecoder.decode(redirectUrl, "UTF-8")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error decoding URL: $redirectUrl", e)
                    }

                    Log.d(TAG, "Found global redirection: $url -> $redirectUrl")
                    return redirectUrl
                }
            }
        }

        return null
    }

    /**
     * Apply raw rules to a URL
     */
    private fun applyRawRules(url: String): String {
        var result = url
        val matchingProviders = findMatchingProviders(url)

        // Apply provider-specific raw rules
        for (providerName in matchingProviders) {
            val providerRawRules = rawRules[providerName] ?: continue

            for (pattern in providerRawRules) {
                val before = result
                result = pattern.matcher(result).replaceAll("")
                if (before != result) {
                    Log.d(TAG, "Applied raw rule from $providerName: $before -> $result")
                }
            }
        }

        // Apply global raw rules
        val globalRawRules = rawRules["globalRules"]
        if (globalRawRules != null) {
            for (pattern in globalRawRules) {
                val before = result
                result = pattern.matcher(result).replaceAll("")
                if (before != result) {
                    Log.d(TAG, "Applied global raw rule: $before -> $result")
                }
            }
        }

        return result
    }

    /**
     * Clean tracking parameters from a URL
     *
     * @param url The URL to clean
     * @param allowReferralMarketing Whether to allow referral marketing parameters (default: false)
     * @return The cleaned URL
     */
    fun cleanTrackingParamsFromUrl(url: String, allowReferralMarketing: Boolean = false): String {
        if (url.isEmpty()) {
            return url
        }

        try {
            // Check if the URL matches any exceptions
            if (isUrlException(url)) {
                Log.d(TAG, "URL matches exception pattern, skipping: $url")
                return url
            }

            // Check if the URL matches a complete provider (to be completely blocked)
            if (isCompleteProvider(url)) {
                Log.d(TAG, "URL matches complete provider, returning empty URL: $url")
                return ""
            }

            // Check for redirections first
            val redirectedUrl = checkForRedirections(url)
            if (redirectedUrl != null && redirectedUrl != url) {
                Log.d(TAG, "Redirection found: $url -> $redirectedUrl")
                // Recursively clean the redirected URL
                return cleanTrackingParamsFromUrl(redirectedUrl, allowReferralMarketing)
            }

            // Apply raw rules to the URL
            var cleanUrl = applyRawRules(url)

            // Parse the URL for query parameter cleaning
            var uri = Uri.parse(cleanUrl)

            // If the URL becomes invalid after raw rules, return the original URL
            if (uri.scheme.isNullOrEmpty()) {
                Log.d(TAG, "URL became invalid after raw rules, using original")
                return url
            }

            // Clean query parameters if present
            if (!uri.query.isNullOrEmpty()) {
                val paramNames = uri.legacyGetQueryParameterNames()
                if (paramNames.isNotEmpty()) {
                    val builder = uri.buildUpon().legacyClearQuery()

                    for (param in paramNames) {
                        val value = uri.getQueryParameter(param)

                        // Special case for YouTube parameters
                        val isYouTubeParam = isUnwantedYoutubeParam(param) &&
                                uri.host?.contains("youtube.com") == true

                        // Check if it's a referral marketing parameter
                        val isReferralParam = isReferralMarketingParam(param, cleanUrl)

                        // Check if it's a tracking parameter
                        val isTracking = isTrackingParam(param, cleanUrl)

                        // Determine if we should keep this parameter
                        val shouldKeepParam = value != null &&
                                !isYouTubeParam &&
                                (!isTracking || (allowReferralMarketing && isReferralParam))

                        if (shouldKeepParam) {
                            builder.appendQueryParameter(param, value)
                        } else {
                            Log.d(TAG, "Removing parameter: $param from URL: $cleanUrl")
                        }
                    }

                    uri = builder.build()
                    cleanUrl = uri.toString()
                }
            }

            return cleanUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning URL: $url", e)
            return url
        }
    }
}