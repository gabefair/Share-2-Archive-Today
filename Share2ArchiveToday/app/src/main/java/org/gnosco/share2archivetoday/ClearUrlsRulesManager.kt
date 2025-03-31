package org.gnosco.share2archivetoday

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * Manager class for handling ClearURLs rules
 * Based directly on the original ClearURLs extension approach using rules format from https://docs.clearurls.xyz/1.27.3/specs/rules/
 * Optimized for faster URL matching and cleaning
 */
class ClearUrlsRulesManager(private val context: Context) {
    private val TAG = "ClearUrlsRulesManager"

    // Store providers
    private var providers = mutableListOf<Provider>()
    private var providerKeys = mutableListOf<String>()

    // Domain pattern to Provider map for quick lookups
    private val domainToProviderMap = ConcurrentHashMap<String, MutableList<Provider>>()

    // Preferences for caching
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("clearurls_prefs", Context.MODE_PRIVATE)
    }

    // Rules version tracking
    private var rulesVersion: String = "1.27.3"
    private var cachedRulesVersion: String = ""

    // Configuration
    private var domainBlocking = true
    private var referralMarketingEnabled = false
    private var loggingEnabled = true
    private var localHostsSkipping = true

    init {
        try {
            // Check for cached rules first if on supported SDK
            if (shouldUseCachedRules()) {
                loadCachedRules()
            } else {
                loadRules()
                // Only cache if on supported SDK
                if (isHoneycombOrHigher()) {
                    cacheRules()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading ClearURLs rules", e)
            // If error occurs with cached rules, try loading from assets
            if (isCachedRulesLoaded()) {
                try {
                    loadRules()
                } catch (e2: Exception) {
                    Log.e(TAG, "Error loading rules from assets after cache failure", e2)
                }
            }
        }
    }

    /**
     * Helper method to check if the device is running Honeycomb or higher
     */
    private fun isHoneycombOrHigher(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB
    }

    /**
     * Provider class that matches the JavaScript implementation
     */
    inner class Provider(
        private val name: String,
        private val completeProvider: Boolean = false
    ) {
        private var urlPattern: Pattern? = null
        private var patternString: String = ""
        private val enabledRules = mutableMapOf<String, Boolean>()
        private val enabledRawRules = mutableMapOf<String, Boolean>()
        private val enabledExceptions = mutableMapOf<String, Boolean>()
        private val enabledRedirections = mutableMapOf<String, Boolean>()
        private val enabledReferralMarketing = mutableMapOf<String, Boolean>()
        private val methods = mutableListOf<String>()

        /**
         * Set the URL pattern for this provider
         */
        fun setURLPattern(pattern: String) {
            try {
                patternString = pattern
                urlPattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            } catch (e: Exception) {
                Log.e(TAG, "Error compiling URL pattern for provider $name: $pattern", e)
            }
        }

        /**
         * Get the pattern string used for this provider
         */
        fun getPatternString(): String {
            return patternString
        }

        /**
         * Return if the Provider Request is canceled
         */
        fun isCanceling(): Boolean {
            return completeProvider
        }

        /**
         * Check if a URL matches this provider
         */
        fun matchURL(url: String): Boolean {
            return urlPattern?.matcher(url)?.find() == true && !matchException(url)
        }

        /**
         * Add a rule to this provider
         */
        fun addRule(rule: String, isActive: Boolean = true) {
            applyRule(enabledRules, rule, isActive)
        }

        /**
         * Add a raw rule to this provider
         */
        fun addRawRule(rule: String, isActive: Boolean = true) {
            applyRule(enabledRawRules, rule, isActive)
        }

        /**
         * Add a referral marketing rule
         */
        fun addReferralMarketing(rule: String, isActive: Boolean = true) {
            applyRule(enabledReferralMarketing, rule, isActive)
        }

        /**
         * Add an exception to this provider
         */
        fun addException(exception: String, isActive: Boolean = true) {
            applyRule(enabledExceptions, exception, isActive)
        }

        /**
         * Add a redirection to this provider
         */
        fun addRedirection(redirection: String, isActive: Boolean = true) {
            applyRule(enabledRedirections, redirection, isActive)
        }

        /**
         * Add HTTP method
         */
        fun addMethod(method: String) {
            if (!methods.contains(method)) {
                methods.add(method)
            }
        }

        /**
         * Apply a rule to the given map
         */
        private fun applyRule(map: MutableMap<String, Boolean>, rule: String, isActive: Boolean) {
            if (isActive) {
                map[rule] = true
            }
        }

        /**
         * Check if the URL matches any exception
         */
        fun matchException(url: String): Boolean {
            for (exception in enabledExceptions.keys) {
                try {
                    val exceptionRegex = Pattern.compile(exception, Pattern.CASE_INSENSITIVE)
                    if (exceptionRegex.matcher(url).find()) {
                        return true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error matching exception: $exception", e)
                }
            }
            return false
        }

        /**
         * Get all enabled rules
         */
        fun getRules(): List<String> {
            val rules = mutableListOf<String>()
            rules.addAll(enabledRules.keys)

            // Include referral marketing rules if enabled
            if (referralMarketingEnabled) {
                rules.addAll(enabledReferralMarketing.keys)
            }

            return rules
        }

        /**
         * Get all enabled raw rules
         */
        fun getRawRules(): List<String> {
            return enabledRawRules.keys.toList()
        }

        /**
         * Check for and get redirection if applicable
         */
        fun getRedirection(url: String): String? {
            for (redirection in enabledRedirections.keys) {
                try {
                    val pattern = Pattern.compile(redirection, Pattern.CASE_INSENSITIVE)
                    val matcher = pattern.matcher(url)

                    if (matcher.find() && matcher.groupCount() >= 1) {
                        return matcher.group(1)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error applying redirection: $redirection", e)
                }
            }

            return null
        }

        /**
         * For serialization
         */
        fun toJSON(): JSONObject {
            val json = JSONObject()
            json.put("name", name)
            json.put("completeProvider", completeProvider)
            json.put("patternString", patternString)

            // Save rules
            json.put("rules", JSONObject().apply {
                enabledRules.forEach { (rule, active) -> put(rule, active) }
            })

            // Save raw rules
            json.put("rawRules", JSONObject().apply {
                enabledRawRules.forEach { (rule, active) -> put(rule, active) }
            })

            // Save exceptions
            json.put("exceptions", JSONObject().apply {
                enabledExceptions.forEach { (exception, active) -> put(exception, active) }
            })

            // Save redirections
            json.put("redirections", JSONObject().apply {
                enabledRedirections.forEach { (redirection, active) -> put(redirection, active) }
            })

            // Save referral marketing
            json.put("referralMarketing", JSONObject().apply {
                enabledReferralMarketing.forEach { (rule, active) -> put(rule, active) }
            })

            // Save methods
            val methodsJson = JSONObject()
            methods.forEachIndexed { index, method -> methodsJson.put(index.toString(), method) }
            json.put("methods", methodsJson)

            return json
        }

        /**
         * For serialization - This function is no longer used with a companion object
         */
    }

    /**
     * Check if cached rules should be used
     * Rules should NOT be used if device is pre-Honeycomb
     */
    private fun shouldUseCachedRules(): Boolean {
        // Don't use cache if device is pre-Honeycomb
        if (!isHoneycombOrHigher()) {
            return false
        }
        cachedRulesVersion = prefs.getString("rules_version", "") ?: ""

        return cachedRulesVersion.isNotEmpty() &&
                cachedRulesVersion == rulesVersion &&
                prefs.contains("cached_rules")
    }

    /**
     * Check if we have cached rules loaded
     */
    private fun isCachedRulesLoaded(): Boolean {
        return cachedRulesVersion.isNotEmpty()
    }

    /**
     * Cache the current rules to shared preferences
     * Only if the device is running Honeycomb or higher
     */
    private fun cacheRules() {
        // Don't cache on pre-Honeycomb devices
        if (!isHoneycombOrHigher()) {
            return
        }

        if (providers.isEmpty() || rulesVersion.isEmpty()) {
            return
        }

        try {
            val providersJson = JSONObject()

            // Save each provider
            providers.forEachIndexed { index, provider ->
                providersJson.put(index.toString(), provider.toJSON())
            }

            // Save provider keys
            val keysJson = JSONObject()
            providerKeys.forEachIndexed { index, key ->
                keysJson.put(index.toString(), key)
            }

            // Create a master JSON with all data
            val masterJson = JSONObject()
            masterJson.put("providers", providersJson)
            masterJson.put("providerKeys", keysJson)
            masterJson.put("domainBlocking", domainBlocking)
            masterJson.put("referralMarketingEnabled", referralMarketingEnabled)
            masterJson.put("localHostsSkipping", localHostsSkipping)
            masterJson.put("version", rulesVersion)

            // Save to preferences
            prefs.edit()
                .putString("cached_rules", masterJson.toString())
                .putString("rules_version", rulesVersion)
                .apply()

            Log.d(TAG, "Rules cached successfully, version: $rulesVersion")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching rules", e)
        }
    }

    /**
     * Create a Provider from JSON
     */
    private fun providerFromJSON(json: JSONObject): Provider? {
        try {
            val name = json.getString("name")
            val completeProvider = json.optBoolean("completeProvider", false)
            val provider = Provider(name, completeProvider)

            // Set pattern
            if (json.has("patternString")) {
                provider.setURLPattern(json.getString("patternString"))
            }

            // Load rules
            val rules = json.optJSONObject("rules")
            if (rules != null) {
                val rulesIter = rules.keys()
                while (rulesIter.hasNext()) {
                    val rule = rulesIter.next()
                    provider.addRule(rule, rules.getBoolean(rule))
                }
            }

            // Load raw rules
            val rawRules = json.optJSONObject("rawRules")
            if (rawRules != null) {
                val rawRulesIter = rawRules.keys()
                while (rawRulesIter.hasNext()) {
                    val rule = rawRulesIter.next()
                    provider.addRawRule(rule, rawRules.getBoolean(rule))
                }
            }

            // Load exceptions
            val exceptions = json.optJSONObject("exceptions")
            if (exceptions != null) {
                val exceptionsIter = exceptions.keys()
                while (exceptionsIter.hasNext()) {
                    val exception = exceptionsIter.next()
                    provider.addException(exception, exceptions.getBoolean(exception))
                }
            }

            // Load redirections
            val redirections = json.optJSONObject("redirections")
            if (redirections != null) {
                val redirectionsIter = redirections.keys()
                while (redirectionsIter.hasNext()) {
                    val redirection = redirectionsIter.next()
                    provider.addRedirection(redirection, redirections.getBoolean(redirection))
                }
            }

            // Load referral marketing
            val referralMarketing = json.optJSONObject("referralMarketing")
            if (referralMarketing != null) {
                val referralMarketingIter = referralMarketing.keys()
                while (referralMarketingIter.hasNext()) {
                    val rule = referralMarketingIter.next()
                    provider.addReferralMarketing(rule, referralMarketing.getBoolean(rule))
                }
            }

            // Load methods
            val methods = json.optJSONObject("methods")
            if (methods != null) {
                val methodsIter = methods.keys()
                while (methodsIter.hasNext()) {
                    val method = methods.getString(methodsIter.next())
                    provider.addMethod(method)
                }
            }

            return provider
        } catch (e: Exception) {
            Log.e(TAG, "Error deserializing provider", e)
            return null
        }
    }

    /**
     * Load rules from cache
     */
    private fun loadCachedRules() {
        try {
            val cachedData = prefs.getString("cached_rules", null) ?: return
            val masterJson = JSONObject(cachedData)

            // Load configuration
            domainBlocking = masterJson.optBoolean("domainBlocking", true)
            referralMarketingEnabled = masterJson.optBoolean("referralMarketingEnabled", false)
            localHostsSkipping = masterJson.optBoolean("localHostsSkipping", true)
            rulesVersion = masterJson.optString("version", "")

            // Load provider keys
            val keysJson = masterJson.optJSONObject("providerKeys")
            if (keysJson != null) {
                val keysIter = keysJson.keys()
                while (keysIter.hasNext()) {
                    providerKeys.add(keysJson.getString(keysIter.next()))
                }
            }

            // Load providers
            val providersJson = masterJson.optJSONObject("providers")
            if (providersJson != null) {
                val providerIter = providersJson.keys()
                while (providerIter.hasNext()) {
                    val providerJson = providersJson.getJSONObject(providerIter.next())
                    val provider = providerFromJSON(providerJson)
                    if (provider != null) {
                        providers.add(provider)
                    }
                }
            }

            // Build the domain to provider map
            buildDomainProviderMap()

            Log.d(TAG, "Loaded ${providers.size} providers from cached rules, version: $rulesVersion")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cached rules", e)
            throw e
        }
    }

    /**
     * Load rules from assets file
     */
    private fun loadRules() {
        try {
            val jsonString = loadJSONFromAsset()
            if (jsonString != null) {
                val clearURLsData = JSONObject(jsonString)

                // Get version info
                val jsonVersion = clearURLsData.optString("version", "")
                if (jsonVersion.isNotEmpty()) {
                    rulesVersion = jsonVersion
                }

                // Get provider keys
                val providersObj = clearURLsData.optJSONObject("providers")
                if (providersObj != null) {
                    val providerIter = providersObj.keys()
                    while (providerIter.hasNext()) {
                        providerKeys.add(providerIter.next())
                    }

                    // Create providers
                    createProviders(clearURLsData)

                    // Build the domain to provider map
                    buildDomainProviderMap()

                    Log.d(TAG, "Loaded ${providers.size} providers from rules, version: $rulesVersion")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading ClearURLs rules", e)
            throw e
        }
    }

    /**
     * Build the domain-to-provider mapping for faster lookups
     */
    private fun buildDomainProviderMap() {
        domainToProviderMap.clear()

        for (provider in providers) {
            // Get the pattern string as a key
            val patternString = provider.getPatternString()

            // Add to the map, creating a list if needed
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                domainToProviderMap.computeIfAbsent(patternString) { mutableListOf() }.add(provider)
            } else {
                // Fallback for older Android versions
                var list = domainToProviderMap[patternString]
                if (list == null) {
                    list = mutableListOf()
                    domainToProviderMap[patternString] = list
                }
                list.add(provider)
            }
        }

        Log.d(TAG, "Built domain map with ${domainToProviderMap.size} patterns")
    }

    /**
     * Create providers from the JSON data
     */
    private fun createProviders(data: JSONObject) {
        val providersObj = data.optJSONObject("providers")

        for (providerKey in providerKeys) {
            val providerJson = providersObj?.optJSONObject(providerKey) ?: continue

            // Create provider with appropriate flags
            val completeProvider = providerJson.optBoolean("completeProvider", false)
            val provider = Provider(providerKey, completeProvider)

            // Add URL pattern
            val urlPattern = providerJson.optString("urlPattern", "")
            if (urlPattern.isNotEmpty()) {
                provider.setURLPattern(urlPattern)
            }

            // Add rules
            val rules = providerJson.optJSONArray("rules")
            if (rules != null) {
                for (i in 0 until rules.length()) {
                    provider.addRule(rules.optString(i))
                }
            }

            // Add raw rules
            val rawRules = providerJson.optJSONArray("rawRules")
            if (rawRules != null) {
                for (i in 0 until rawRules.length()) {
                    provider.addRawRule(rawRules.optString(i))
                }
            }

            // Add referral marketing rules
            val referralMarketing = providerJson.optJSONArray("referralMarketing")
            if (referralMarketing != null) {
                for (i in 0 until referralMarketing.length()) {
                    provider.addReferralMarketing(referralMarketing.optString(i))
                }
            }

            // Add exceptions
            val exceptions = providerJson.optJSONArray("exceptions")
            if (exceptions != null) {
                for (i in 0 until exceptions.length()) {
                    provider.addException(exceptions.optString(i))
                }
            }

            // Add redirections
            val redirections = providerJson.optJSONArray("redirections")
            if (redirections != null) {
                for (i in 0 until redirections.length()) {
                    provider.addRedirection(redirections.optString(i))
                }
            }

            // Add HTTP methods
            val methods = providerJson.optJSONArray("methods")
            if (methods != null) {
                for (i in 0 until methods.length()) {
                    provider.addMethod(methods.optString(i))
                }
            }

            // Add the provider to our list
            providers.add(provider)
        }
    }

    /**
     * Load JSON data from assets
     */
    private fun loadJSONFromAsset(): String? {
        return try {
            val inputStream = context.assets.open("data.minify.json")
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
     * Extract URL fragments (similar to the JavaScript extractFragments function)
     */
    private fun extractFragments(uri: Uri): MutableMap<String, String> {
        val fragments = mutableMapOf<String, String>()
        val fragmentStr = uri.fragment ?: return fragments

        // Parse fragment similar to query parameters
        val fragmentParts = fragmentStr.split("&")
        for (part in fragmentParts) {
            val keyValue = part.split("=", limit = 2)
            if (keyValue.size == 2) {
                fragments[keyValue[0]] = keyValue[1]
            } else if (keyValue.size == 1 && keyValue[0].isNotEmpty()) {
                fragments[keyValue[0]] = ""
            }
        }

        return fragments
    }

    /**
     * Convert fragments map back to string
     */
    private fun fragmentsToString(fragments: Map<String, String>): String {
        if (fragments.isEmpty()) return ""

        val parts = mutableListOf<String>()
        for ((key, value) in fragments) {
            if (value.isEmpty()) {
                parts.add(key)
            } else {
                parts.add("$key=$value")
            }
        }
        return parts.joinToString("&")
    }

    /**
     * Return URL without parameters and hash
     */
    private fun urlWithoutParamsAndHash(uri: Uri): String {
        val builder = Uri.Builder()
            .scheme(uri.scheme)
            .authority(uri.authority)
            .path(uri.path)

        return builder.build().toString()
    }

    /**
     * Check if URL is local
     */
    private fun checkLocalURL(uri: Uri): Boolean {
        val host = uri.host ?: return false
        return host == "localhost" || host == "127.0.0.1" || host.endsWith(".local")
    }

    /**
     * Decode URL (handles multiple levels of encoding)
     */
    private fun decodeURL(url: String): String {
        var decoded = url
        try {
            // Try to decode up to 5 times to handle multiple encodings
            for (i in 0 until 5) {
                val prev = decoded
                decoded = URLDecoder.decode(decoded, "UTF-8")
                if (prev == decoded) break
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding URL: $url", e)
        }
        return decoded
    }

    /**
     * Convert URLSearchParams to string
     */
    private fun urlSearchParamsToString(params: Map<String, String>): String {
        val parts = mutableListOf<String>()
        for ((key, value) in params) {
            parts.add("$key=$value")
        }
        return parts.joinToString("&")
    }

    /**
     * Remove tracking fields from URL following the original algorithm
     */
    private fun removeFieldsFormURL(provider: Provider, pureUrl: String, quiet: Boolean = false): Map<String, Any> {
        var url = pureUrl
        var domain = ""
        val rules = provider.getRules()
        var changes = false
        val rawRules = provider.getRawRules()
        var urlObject: Uri

        // Skip local URLs if enabled
        try {
            urlObject = Uri.parse(url)
            if (localHostsSkipping && checkLocalURL(urlObject)) {
                return mapOf(
                    "changes" to false,
                    "url" to url,
                    "cancel" to false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing URL: $url", e)
            return mapOf(
                "changes" to false,
                "url" to url,
                "cancel" to false
            )
        }

        // Check for redirection
        val re = provider.getRedirection(url)
        if (re != null) {
            url = decodeURL(re)

            // Log action
            if (!quiet && loggingEnabled) {
                Log.d(TAG, "Redirected: $pureUrl -> $url")
            }

            return mapOf(
                "redirect" to true,
                "url" to url
            )
        }

        // Check for canceling (domain blocking)
        if (provider.isCanceling() && domainBlocking) {
            if (!quiet && loggingEnabled) {
                Log.d(TAG, "Domain blocked: $pureUrl")
            }

            return mapOf(
                "cancel" to true,
                "url" to url
            )
        }

        // Apply raw rules
        for (rawRule in rawRules) {
            try {
                val beforeReplace = url
                val pattern = Pattern.compile(rawRule, Pattern.CASE_INSENSITIVE)
                url = pattern.matcher(url).replaceAll("")

                if (beforeReplace != url) {
                    if (loggingEnabled && !quiet) {
                        Log.d(TAG, "Applied raw rule: $rawRule")
                        Log.d(TAG, "  Before: $beforeReplace")
                        Log.d(TAG, "  After: $url")
                    }

                    changes = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error applying raw rule: $rawRule", e)
            }
        }

        // Parse the URL again after raw rules
        urlObject = Uri.parse(url)

        // Get query parameters
        val queryParams = mutableMapOf<String, String>()
        for (paramName in urlObject.queryParameterNames) {
            urlObject.getQueryParameter(paramName)?.let {
                queryParams[paramName] = it
            }
        }

        // Get fragments
        val fragmentMap = extractFragments(urlObject)

        // Get base domain
        domain = urlWithoutParamsAndHash(urlObject)

        // Only process if there are fields or fragments to clean
        if (queryParams.isNotEmpty() || fragmentMap.isNotEmpty()) {
            // Check each rule against fields and fragments
            for (rule in rules) {
                val beforeFields = queryParams.toString()
                val beforeFragments = fragmentMap.toString()
                var localChange = false

                // Check against query parameters
                val paramsToRemove = mutableListOf<String>()
                for (field in queryParams.keys) {
                    try {
                        if (Pattern.compile("^$rule$", Pattern.CASE_INSENSITIVE).matcher(field).matches()) {
                            paramsToRemove.add(field)
                            changes = true
                            localChange = true
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error matching rule against field: $rule", e)
                    }
                }

                // Remove matched parameters
                for (param in paramsToRemove) {
                    queryParams.remove(param)
                }

                // Check against fragments
                val fragmentsToRemove = mutableListOf<String>()
                for (fragment in fragmentMap.keys) {
                    try {
                        if (Pattern.compile("^$rule$", Pattern.CASE_INSENSITIVE).matcher(fragment).matches()) {
                            fragmentsToRemove.add(fragment)
                            changes = true
                            localChange = true
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error matching rule against fragment: $rule", e)
                    }
                }

                // Remove matched fragments
                for (fragment in fragmentsToRemove) {
                    fragmentMap.remove(fragment)
                }

                // Log if changes were made
                if (localChange && loggingEnabled && !quiet) {
                    var tempURL = domain
                    var tempBeforeURL = domain

                    if (queryParams.isNotEmpty()) {
                        tempURL += "?" + urlSearchParamsToString(queryParams)
                    }
                    if (fragmentMap.isNotEmpty()) {
                        tempURL += "#" + fragmentsToString(fragmentMap)
                    }
                    if (beforeFields != "{}") {
                        tempBeforeURL += "?${beforeFields}"
                    }
                    if (beforeFragments != "{}") {
                        tempBeforeURL += "#${beforeFragments}"
                    }

                    Log.d(TAG, "Applied rule: $rule")
                    Log.d(TAG, "  Before: $tempBeforeURL")
                    Log.d(TAG, "  After: $tempURL")
                }
            }

            // Build final URL
            var finalURL = domain

            if (queryParams.isNotEmpty()) {
                finalURL += "?" + urlSearchParamsToString(queryParams)
            }

            if (fragmentMap.isNotEmpty()) {
                finalURL += "#" + fragmentsToString(fragmentMap)
            }

            // Fix any URL formatting issues
            url = finalURL.replace(Regex("\\?&"), "?").replace(Regex("#&"), "#")
        }

        return mapOf(
            "changes" to changes,
            "url" to url,
            "cancel" to false,
            "redirect" to false
        )
    }

    /**
     * Checks if a string is likely a valid URL that's missing a protocol.
     * Returns true for strings like "example.com" or "www.example.com/path"
     * but returns false for complete URLs like "https://example.com" or non-URL strings.
     */
    fun isValidUrlMissingProtocol(url: String): Boolean {
        // Return false if string is empty or already has a protocol
        if (url.isEmpty() || url.contains("://")) {
            return false
        }

        // Pattern for a valid domain name:
        // - Optional www prefix
        // - Domain with at least one letter/number
        // - At least one dot
        // - TLD with 2+ letters
        // - Optional path, query parameters, etc.
        val domainPattern = "^(www\\.)?([a-zA-Z0-9][a-zA-Z0-9-]{0,61}[a-zA-Z0-9])\\." +
                "([a-zA-Z]{2,})(\\.[a-zA-Z]{2,})*(:[0-9]{1,5})?" +
                "(/[a-zA-Z0-9\\-._~:/\\?#\\[\\]@!\\$&'\\(\\)\\*\\+,;=]*)?"

        return url.matches(Regex(domainPattern))
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
        if (isValidUrlMissingProtocol(url)) {
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
                val cleanResult = removeFieldsFormURL(provider, result)

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