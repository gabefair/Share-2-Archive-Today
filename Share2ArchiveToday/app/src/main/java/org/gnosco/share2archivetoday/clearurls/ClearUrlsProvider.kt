package org.gnosco.share2archivetoday.clearurls

import android.util.Log
import org.json.JSONObject
import java.util.regex.Pattern

/**
 * Provider class that represents a ClearURLs rule provider
 * Based directly on the original ClearURLs extension approach
 */
class ClearUrlsProvider(
    private val name: String,
    private val completeProvider: Boolean = false
) {
    private val TAG = "ClearUrlsProvider"
    
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
    fun getRules(referralMarketingEnabled: Boolean): List<String> {
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

    companion object {
        /**
         * Create a Provider from JSON
         */
        fun fromJSON(json: JSONObject): ClearUrlsProvider? {
            return try {
                val name = json.getString("name")
                val completeProvider = json.optBoolean("completeProvider", false)
                val provider = ClearUrlsProvider(name, completeProvider)

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

                provider
            } catch (e: Exception) {
                Log.e("ClearUrlsProvider", "Error deserializing provider", e)
                null
            }
        }
    }
}

