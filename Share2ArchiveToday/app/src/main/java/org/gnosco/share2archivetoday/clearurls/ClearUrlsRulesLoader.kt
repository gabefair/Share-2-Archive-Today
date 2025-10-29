package org.gnosco.share2archivetoday.clearurls

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles loading and caching of ClearURLs rules
 */
class ClearUrlsRulesLoader(private val context: Context) {
    private val TAG = "ClearUrlsRulesLoader"

    // Preferences for caching
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("clearurls_prefs", Context.MODE_PRIVATE)
    }

    // Rules version tracking
    private var rulesVersion: String = "1.27.3"
    private var cachedRulesVersion: String = ""

    // Configuration
    var domainBlocking = true
        private set
    var referralMarketingEnabled = false
        private set
    var localHostsSkipping = true
        private set

    /**
     * Helper method to check if the device is running Honeycomb or higher
     */
    private fun isHoneycombOrHigher(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB
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
     * Load rules - either from cache or assets
     */
    fun loadRules(): RulesLoadResult {
        return try {
            if (shouldUseCachedRules()) {
                loadCachedRules()
            } else {
                val result = loadRulesFromAssets()
                // Only cache if on supported SDK
                if (isHoneycombOrHigher() && result.providers.isNotEmpty()) {
                    cacheRules(result)
                }
                result
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading ClearURLs rules", e)
            // If error occurs with cached rules, try loading from assets
            if (isCachedRulesLoaded()) {
                try {
                    loadRulesFromAssets()
                } catch (e2: Exception) {
                    Log.e(TAG, "Error loading rules from assets after cache failure", e2)
                    throw e2
                }
            } else {
                throw e
            }
        }
    }

    /**
     * Cache the current rules to shared preferences
     * Only if the device is running Honeycomb or higher
     */
    private fun cacheRules(result: RulesLoadResult) {
        // Don't cache on pre-Honeycomb devices
        if (!isHoneycombOrHigher()) {
            return
        }

        if (result.providers.isEmpty() || rulesVersion.isEmpty()) {
            return
        }

        try {
            val providersJson = JSONObject()

            // Save each provider
            result.providers.forEachIndexed { index, provider ->
                providersJson.put(index.toString(), provider.toJSON())
            }

            // Save provider keys
            val keysJson = JSONObject()
            result.providerKeys.forEachIndexed { index, key ->
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
     * Load rules from cache
     */
    private fun loadCachedRules(): RulesLoadResult {
        try {
            val cachedData = prefs.getString("cached_rules", null)
                ?: throw IOException("No cached data found")
            val masterJson = JSONObject(cachedData)

            // Load configuration
            domainBlocking = masterJson.optBoolean("domainBlocking", true)
            referralMarketingEnabled = masterJson.optBoolean("referralMarketingEnabled", false)
            localHostsSkipping = masterJson.optBoolean("localHostsSkipping", true)
            rulesVersion = masterJson.optString("version", "")

            val providerKeys = mutableListOf<String>()
            val providers = mutableListOf<ClearUrlsProvider>()

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
                    val provider = ClearUrlsProvider.fromJSON(providerJson)
                    if (provider != null) {
                        providers.add(provider)
                    }
                }
            }

            Log.d(TAG, "Loaded ${providers.size} providers from cached rules, version: $rulesVersion")
            
            return RulesLoadResult(providers, providerKeys, domainBlocking, referralMarketingEnabled, localHostsSkipping)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cached rules", e)
            throw e
        }
    }

    /**
     * Load rules from assets file
     */
    private fun loadRulesFromAssets(): RulesLoadResult {
        try {
            val jsonString = loadJSONFromAsset()
                ?: throw IOException("Failed to load JSON from assets")
            val clearURLsData = JSONObject(jsonString)

            // Get version info
            val jsonVersion = clearURLsData.optString("version", "")
            if (jsonVersion.isNotEmpty()) {
                rulesVersion = jsonVersion
            }

            val providerKeys = mutableListOf<String>()
            val providers = mutableListOf<ClearUrlsProvider>()

            // Get provider keys
            val providersObj = clearURLsData.optJSONObject("providers")
            if (providersObj != null) {
                val providerIter = providersObj.keys()
                while (providerIter.hasNext()) {
                    providerKeys.add(providerIter.next())
                }

                // Create providers
                createProviders(clearURLsData, providerKeys, providers)

                Log.d(TAG, "Loaded ${providers.size} providers from rules, version: $rulesVersion")
            }

            return RulesLoadResult(providers, providerKeys, domainBlocking, referralMarketingEnabled, localHostsSkipping)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading ClearURLs rules", e)
            throw e
        }
    }

    /**
     * Create providers from the JSON data
     */
    private fun createProviders(
        data: JSONObject,
        providerKeys: List<String>,
        providers: MutableList<ClearUrlsProvider>
    ) {
        val providersObj = data.optJSONObject("providers")

        for (providerKey in providerKeys) {
            val providerJson = providersObj?.optJSONObject(providerKey) ?: continue

            // Create provider with appropriate flags
            val completeProvider = providerJson.optBoolean("completeProvider", false)
            val provider = ClearUrlsProvider(providerKey, completeProvider)

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
     * Build the domain-to-provider mapping for faster lookups
     */
    fun buildDomainProviderMap(providers: List<ClearUrlsProvider>): ConcurrentHashMap<String, MutableList<ClearUrlsProvider>> {
        val domainToProviderMap = ConcurrentHashMap<String, MutableList<ClearUrlsProvider>>()

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
        return domainToProviderMap
    }

    /**
     * Data class to hold the result of loading rules
     */
    data class RulesLoadResult(
        val providers: List<ClearUrlsProvider>,
        val providerKeys: List<String>,
        val domainBlocking: Boolean,
        val referralMarketingEnabled: Boolean,
        val localHostsSkipping: Boolean
    )
}

