package org.gnosco.share2archivetoday.ublock

import android.content.Context
import android.net.Uri
import android.util.Log
import org.gnosco.share2archivetoday.legacyClearQuery
import org.gnosco.share2archivetoday.legacyGetQueryParameterNames
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Cleans URLs using uBlock Origin's privacy-removeparam rules from uAssets.
 * Rules are bundled in assets/privacy-removeparam.txt.
 */
class UBlockRemoveParamCleaner(context: Context) {
    private val TAG = "UBlockRemoveParamCleaner"
    private val rules: List<UBlockRemoveParamRule>

    init {
        rules = loadRules(context)
        Log.d(TAG, "Loaded ${rules.size} uBO removeparam rules")
    }

    fun areRulesLoaded(): Boolean = rules.isNotEmpty()

    fun cleanUrl(url: String): String {
        if (url.isEmpty() || rules.isEmpty()) return url

        val uri = try {
            Uri.parse(url)
        } catch (e: Exception) {
            return url
        }

        val host = uri.host ?: return url
        val path = uri.path ?: ""
        val paramNames = uri.legacyGetQueryParameterNames()
        if (paramNames.isEmpty()) return url

        val applicableRules = rules.filter { it.matchesUrl(url, host, path) }
        if (applicableRules.isEmpty()) return url

        val removeAll = applicableRules.any { !it.isException && it.paramSpec is ParamSpec.RemoveAll }
        if (removeAll) {
            val hasException = applicableRules.any { it.isException && it.paramSpec is ParamSpec.RemoveAll }
            if (!hasException) {
                return uri.buildUpon().legacyClearQuery().build().toString()
            }
        }

        val paramsToRemove = mutableSetOf<String>()
        val paramsToKeep = mutableSetOf<String>()

        for (rule in applicableRules) {
            if (rule.paramSpec is ParamSpec.RemoveAll) continue

            for (paramName in paramNames) {
                if (!rule.matchesParam(paramName)) continue
                if (rule.isException) {
                    paramsToKeep.add(paramName)
                } else {
                    paramsToRemove.add(paramName)
                }
            }
        }

        paramsToRemove.removeAll(paramsToKeep)
        if (paramsToRemove.isEmpty()) return url

        val builder = uri.buildUpon().legacyClearQuery()
        for (paramName in paramNames) {
            if (paramName !in paramsToRemove) {
                builder.appendQueryParameter(paramName, uri.getQueryParameter(paramName))
            }
        }
        return builder.build().toString()
    }

    private fun loadRules(context: Context): List<UBlockRemoveParamRule> {
        return try {
            context.assets.open(ASSET_FILE).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    UBlockRemoveParamRuleParser.parseAll(reader.lineSequence())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load uBO removeparam rules", e)
            emptyList()
        }
    }

    companion object {
        private const val ASSET_FILE = "privacy-removeparam.txt"
    }
}
