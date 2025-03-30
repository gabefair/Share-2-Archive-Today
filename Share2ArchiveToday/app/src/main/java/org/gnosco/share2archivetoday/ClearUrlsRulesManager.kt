package org.gnosco.share2archivetoday

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URLDecoder
import java.util.regex.Pattern

/**
 * Manager class for handling ClearURLs rules
 * Based directly on the original ClearURLs extension approach using rules format from https://docs.clearurls.xyz/1.27.3/specs/rules/
 */
class ClearUrlsRulesManager(private val context: Context) {
    private val TAG = "ClearUrlsRulesManager"

    // Store providers
    private var providers = mutableListOf<Provider>()
    private var providerKeys = mutableListOf<String>()

    // Configuration
    private var domainBlocking = true
    private var referralMarketingEnabled = false
    private var loggingEnabled = true
    private var localHostsSkipping = true

    init {
        try {
            loadRules()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading ClearURLs rules", e)
        }
    }

    /**
     * Provider class that matches the JavaScript implementation
     */
    inner class Provider(
        private val name: String,
        private val completeProvider: Boolean = false
    ) {
        private var urlPattern: Pattern? = null
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
                urlPattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            } catch (e: Exception) {
                Log.e(TAG, "Error compiling URL pattern for provider $name: $pattern", e)
            }
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
    }

    /**
     * Load rules from assets file
     */
    private fun loadRules() {
        try {
            val jsonString = loadJSONFromAsset()
            if (jsonString != null) {
                val clearURLsData = JSONObject(jsonString)

                // Get provider keys
                val providersObj = clearURLsData.optJSONObject("providers")
                if (providersObj != null) {
                    val providerIter = providersObj.keys()
                    while (providerIter.hasNext()) {
                        providerKeys.add(providerIter.next())
                    }

                    // Create providers
                    createProviders(clearURLsData)
                    Log.d(TAG, "Loaded ${providers.size} providers from rules")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading ClearURLs rules", e)
        }
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