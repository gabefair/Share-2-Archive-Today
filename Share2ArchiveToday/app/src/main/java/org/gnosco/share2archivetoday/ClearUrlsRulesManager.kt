package org.gnosco.share2archivetoday

import android.content.Context
import android.net.Uri
import android.util.Log
import android.util.LruCache
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
    private var providersTrackingParams: Map<String, Set<String>> = emptyMap()

    // Caches for improved performance
    private val patternCache = mutableMapOf<String, Pattern?>()
    private val urlProviderMatchCache = LruCache<String, List<String>>(100) // Cache last 100 URL matches
    private val trackingParamCache = LruCache<String, Boolean>(500) // Cache parameter tracking status

    // Stats for monitoring
    private var patternsCompiled = 0
    private var patternCompilationErrors = 0
    private var rawRulesApplied = 0

    // Initialize and load rules from assets
    init {
        try {
            loadRules()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading ClearURLs rules", e)
        }
    }

    /**
     * Clear caches to free memory or force recompilation of patterns
     */
    fun clearCaches() {
        patternCache.clear()
        urlProviderMatchCache.evictAll()
        trackingParamCache.evictAll()
        Log.d(TAG, "All caches cleared")
    }

    /**
     * Reload rules from assets
     */
    fun reloadRules() {
        clearCaches()
        try {
            loadRules()
            Log.d(TAG, "Rules reloaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reload rules", e)
        }
    }

    /**
     * Check if rules loaded successfully
     */
    fun areRulesLoaded(): Boolean {
        return providers != null && providerUrlPatterns.isNotEmpty()
    }

    /**
     * Get stats about the rule manager for debugging
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "providersCount" to (providers?.length() ?: 0),
            "globalTrackersCount" to globalTrackers.size,
            "rawRulesCount" to rawRules.values.sumOf { it.size },
            "redirectionsCount" to redirections.values.sumOf { it.size },
            "exceptionsCount" to exceptions.values.sumOf { it.size },
            "completeProvidersCount" to completeProviders.size,
            "patternsCompiled" to patternsCompiled,
            "patternErrors" to patternCompilationErrors,
            "rawRulesApplied" to rawRulesApplied,
            "patternCacheSize" to patternCache.size,
            "urlMatchCacheSize" to urlProviderMatchCache.size(),
            "trackingParamCacheSize" to trackingParamCache.size()
        )
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
     * Centralized method to compile regex patterns with proper error handling and caching
     */
    private fun compilePattern(patternStr: String, providerName: String, ruleType: String): Pattern? {
        // Create a cache key
        val cacheKey = "$providerName:$ruleType:$patternStr"

        // Check cache first
        patternCache[cacheKey]?.let { return it }

        try {
            // Ensure proper handling of the pattern
            val pattern = Pattern.compile(patternStr)
            // Cache the successful pattern
            patternCache[cacheKey] = pattern
            patternsCompiled++
            return pattern
        } catch (e: PatternSyntaxException) {
            Log.e(TAG, "Invalid pattern in $ruleType for provider $providerName: $patternStr", e)
            // Cache the failure to avoid repeated compilation attempts
            patternCache[cacheKey] = null
            patternCompilationErrors++
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error compiling pattern in $ruleType for provider $providerName: $patternStr", e)
            patternCompilationErrors++
            return null
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
        val providersTrackingParamsMap = mutableMapOf<String, MutableSet<String>>()

        providers?.let { providersObj ->
            val providerIter = providersObj.keys()
            while (providerIter.hasNext()) {
                val providerName = providerIter.next()
                val provider = providersObj.optJSONObject(providerName) ?: continue

                // Store URL pattern
                val urlPatternStr = provider.optString("urlPattern", "")
                if (urlPatternStr.isNotEmpty()) {
                    compilePattern(urlPatternStr, providerName, "urlPattern")?.let {
                        urlPatternsMap[providerName] = it
                    }
                }

                // Check if this is a complete provider (should be blocked entirely)
                if (provider.optBoolean("completeProvider", false)) {
                    completeProviderSet.add(providerName)
                    Log.d(TAG, "Added complete provider: $providerName with pattern: $urlPatternStr")
                }

                // Process raw rules (applied to the entire URL path)
                processPatternArray(provider.optJSONArray("rawRules"), providerName, "rawRules")?.let { patterns ->
                    if (patterns.isNotEmpty()) {
                        rawRulesMap[providerName] = patterns
                        Log.d(TAG, "Added ${patterns.size} raw rules for provider $providerName")
                    }
                }

                // Process redirections
                processPatternArray(provider.optJSONArray("redirections"), providerName, "redirections")?.let { patterns ->
                    if (patterns.isNotEmpty()) {
                        redirectionsMap[providerName] = patterns
                    }
                }

                // Process exceptions
                processPatternArray(provider.optJSONArray("exceptions"), providerName, "exceptions")?.let { patterns ->
                    if (patterns.isNotEmpty()) {
                        exceptionsMap[providerName] = patterns
                    }
                }

                // Process rules (tracking parameters)
                val rulesArray = provider.optJSONArray("rules")
                if (rulesArray != null) {
                    val trackingParams = parseTrackers(rulesArray)
                    if (trackingParams.isNotEmpty()) {
                        providersTrackingParamsMap[providerName] = trackingParams.toMutableSet()
                        Log.d(TAG, "Added ${trackingParams.size} tracking parameters for provider $providerName")
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
        providersTrackingParams = providersTrackingParamsMap
    }

    /**
     * Process a JSON array of patterns and compile them
     */
    private fun processPatternArray(jsonArray: JSONArray?, providerName: String, ruleType: String): MutableList<Pattern>? {
        if (jsonArray == null) return null

        val patterns = mutableListOf<Pattern>()
        for (i in 0 until jsonArray.length()) {
            try {
                val patternStr = jsonArray.getString(i)
                compilePattern(patternStr, providerName, ruleType)?.let {
                    patterns.add(it)
                    if (ruleType == "rawRules") {
                        Log.d(TAG, "Added raw rule for $providerName: $patternStr")
                    }
                    if (ruleType != "exceptions") { // Don't log exceptions to reduce noise
                        Log.d(TAG, "Added $ruleType for $providerName: $patternStr")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing $ruleType pattern for $providerName at index $i", e)
            }
        }
        return patterns
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
     * Parse tracker parameter patterns that use regex
     */
    private fun isParameterMatchingRegex(param: String, providerRules: List<String>): Boolean {
        for (rule in providerRules) {
            try {
                // If the rule is a simple string (not a regex)
                if (!rule.contains("[") && !rule.contains("*") && !rule.contains("+") && !rule.contains("?")) {
                    if (param == rule) {
                        return true
                    }
                } else {
                    // It's a regex pattern, compile and check
                    val pattern = Pattern.compile(rule)
                    if (pattern.matcher(param).matches()) {
                        return true
                    }
                }
            } catch (e: Exception) {
                // If there's an error with this rule, continue to the next one
                Log.e(TAG, "Error matching parameter $param against rule $rule", e)
            }
        }
        return false
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
        val matchingProviders = findMatchingProviders(url)
        for (providerName in matchingProviders) {
            if (providerName == "globalRules") continue

            // Check against this provider's exception patterns
            val providerExceptions = exceptions[providerName] ?: continue
            for (exceptionPattern in providerExceptions) {
                if (exceptionPattern.matcher(url).find()) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Find providers that match a given URL with caching
     */
    private fun findMatchingProviders(url: String): List<String> {
        // Check cache first
        urlProviderMatchCache.get(url)?.let { return it }

        val matchingProviders = mutableListOf<String>()

        for ((providerName, pattern) in providerUrlPatterns) {
            if (providerName == "globalRules") continue

            try {
                if (pattern.matcher(url).find()) {
                    matchingProviders.add(providerName)
                    Log.d(TAG, "URL $url matches provider: $providerName")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error matching URL against provider pattern: $providerName", e)
            }
        }

        // Cache the result
        urlProviderMatchCache.put(url, matchingProviders)
        return matchingProviders
    }

    /**
     * Check if a parameter is a tracking parameter based on ClearURLs rules
     */
    fun isTrackingParam(param: String, url: String): Boolean {
        // Create a cache key for this param+url combination
        val cacheKey = "$param:$url"

        // Check cache first
        val cachedResult = trackingParamCache.get(cacheKey)
        if (cachedResult != null) {
            return cachedResult
        }

        // First check against global trackers (most common case)
        if (globalTrackers.contains(param)) {
            trackingParamCache.put(cacheKey, true)
            return true
        }

        // Then check provider-specific rules
        val matchingProviders = findMatchingProviders(url)
        for (providerName in matchingProviders) {
            // Get tracking parameters for this provider
            val trackingParams = providersTrackingParams[providerName] ?: continue

            // First check for exact match
            if (trackingParams.contains(param)) {
                trackingParamCache.put(cacheKey, true)
                return true
            }

            // Then check for regex matches
            val provider = providers?.optJSONObject(providerName) ?: continue
            val rulesArray = provider.optJSONArray("rules") ?: continue
            val rulesList = mutableListOf<String>()

            for (i in 0 until rulesArray.length()) {
                rulesList.add(rulesArray.optString(i, ""))
            }

            if (isParameterMatchingRegex(param, rulesList)) {
                trackingParamCache.put(cacheKey, true)
                return true
            }
        }

        // Default to the legacy list for backward compatibility
        val result = isTrackingParamLegacy(param)
        trackingParamCache.put(cacheKey, result)
        return result
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
            "gs_lp", "sclient", "oq", "uact", "bih", "biw",
            // Additional common tracking params
            "m_entstream_source", "entstream_source", "fb_source"
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

        Log.d(TAG, "Applying raw rules to URL: $url for ${matchingProviders.size} matching providers")

        // Apply provider-specific raw rules
        for (providerName in matchingProviders) {
            val providerRawRules = rawRules[providerName] ?: continue

            Log.d(TAG, "Provider $providerName has ${providerRawRules.size} raw rules")

            for (pattern in providerRawRules) {
                val before = result
                try {
                    // Apply the pattern with proper error handling
                    result = pattern.matcher(result).replaceAll("")

                    if (before != result) {
                        rawRulesApplied++
                        Log.d(TAG, "Applied raw rule from $providerName: $before -> $result")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error applying raw rule for provider $providerName", e)
                }
            }
        }

        // Apply global raw rules
        val globalRawRules = rawRules["globalRules"]
        if (globalRawRules != null) {
            for (pattern in globalRawRules) {
                val before = result
                try {
                    result = pattern.matcher(result).replaceAll("")
                    if (before != result) {
                        rawRulesApplied++
                        Log.d(TAG, "Applied global raw rule: $before -> $result")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error applying global raw rule", e)
                }
            }
        }

        return result
    }

    private fun isValidUri(uri: Uri): Boolean {
        return !uri.scheme.isNullOrEmpty() &&
                !uri.host.isNullOrEmpty() &&
                (uri.scheme == "http" || uri.scheme == "https")
    }

    /**
     * Dump all relevant information about a URL for debugging
     */
    fun dumpUrlInfo(url: String): Map<String, Any> {
        val matchingProviders = findMatchingProviders(url)
        val info = mutableMapOf<String, Any>()

        info["url"] = url
        info["matchingProviders"] = matchingProviders

        val uri = Uri.parse(url)
        val paramMap = mutableMapOf<String, Map<String, Boolean>>()

        if (!uri.query.isNullOrEmpty()) {
            val paramNames = uri.legacyGetQueryParameterNames()
            for (param in paramNames) {
                val isTracking = isTrackingParam(param, url)
                val isReferral = isReferralMarketingParam(param, url)
                paramMap[param] = mapOf(
                    "isTracking" to isTracking,
                    "isReferral" to isReferral
                )
            }
        }

        info["parameters"] = paramMap

        // Check raw rules
        val rawRulesInfo = mutableMapOf<String, List<String>>()
        for (provider in matchingProviders) {
            val rules = rawRules[provider]?.map { it.pattern() } ?: emptyList()
            if (rules.isNotEmpty()) {
                rawRulesInfo[provider] = rules
            }
        }
        info["rawRules"] = rawRulesInfo

        return info
    }

    /**
     * Clean tracking parameters from a URL
     *
     * @param url The URL to clean
     * @param allowReferralMarketing Whether to allow referral marketing parameters (default: false)
     * @return The cleaned URL
     */
    fun cleanTrackingParamsFromUrl(url: String, allowReferralMarketing: Boolean = false,
                                   maxRedirections: Int = 10, visited: MutableSet<String> = mutableSetOf()): String {
        if (url.isEmpty()) {
            return url
        }

        // Quick return for common non-URL strings
        if (!url.contains("://") && !url.contains(".")) {
            return url
        }

        // Prevent infinite redirection loops
        if (visited.contains(url) || visited.size >= maxRedirections) {
            Log.w(TAG, "Redirection limit reached or loop detected for: $url")
            return url
        }
        visited.add(url)

        try {
            Log.d(TAG, "Cleaning URL: $url")

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
                // Recursively clean with visited URLs tracking
                return cleanTrackingParamsFromUrl(redirectedUrl, allowReferralMarketing, maxRedirections, visited)
            }

            // Apply raw rules to the URL (pattern based replacements)
            var cleanUrl = applyRawRules(url)

            // After applying raw rules
            try {
                var uri = Uri.parse(cleanUrl)
                if (!isValidUri(uri)) {
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

                Log.d(TAG, "Final cleaned URL: $cleanUrl")
                return cleanUrl
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing URL after raw rules: $cleanUrl", e)
                return url
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning URL: $url", e)
            return url
        }
    }
}