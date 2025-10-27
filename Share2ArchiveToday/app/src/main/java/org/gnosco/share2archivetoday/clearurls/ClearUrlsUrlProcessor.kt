package org.gnosco.share2archivetoday.clearurls

import android.net.Uri
import android.util.Log
import java.net.URLDecoder
import java.util.regex.Pattern

/**
 * Handles URL processing and cleaning operations
 */
class ClearUrlsUrlProcessor {
    private val TAG = "ClearUrlsUrlProcessor"

    // Configuration
    var domainBlocking = true
    var referralMarketingEnabled = false
    var loggingEnabled = true
    var localHostsSkipping = true

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
     * Remove tracking fields from URL following the original algorithm
     */
    fun removeFieldsFromURL(provider: ClearUrlsProvider, pureUrl: String, quiet: Boolean = false): Map<String, Any> {
        var url = pureUrl
        var domain = ""
        val rules = provider.getRules(referralMarketingEnabled)
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
}

