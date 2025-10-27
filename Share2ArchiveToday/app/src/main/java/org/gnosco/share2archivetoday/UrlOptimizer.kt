package org.gnosco.share2archivetoday

import android.net.Uri
import android.util.Log

/**
 * Handles platform-specific URL optimizations and tracking parameter removal
 */
class UrlOptimizer {
    /**
     * Apply platform-specific optimizations that may not be covered by ClearURLs rules
     */
    fun applyPlatformSpecificOptimizations(url: String): String {
        val uri = Uri.parse(url)
        val newUriBuilder = uri.buildUpon()
        var changed = false

        // YouTube-specific handling
        if (uri.host?.contains("youtube.com") == true || uri.host?.contains("youtu.be") == true) {
            // Convert shorts to regular videos
            if (uri.path?.contains("/shorts/") == true) {
                newUriBuilder.path(uri.path?.replace("/shorts/", "/v/"))
                changed = true
            }

            // Remove music. prefix
            val modifiedHost = uri.host?.removePrefix("music.")
            if (modifiedHost != uri.host) {
                newUriBuilder.authority(modifiedHost)
                changed = true
            }

            // Handle nested query parameters in YouTube search links
            val nestedQueryParams = uri.getQueryParameter("q")
            if (nestedQueryParams != null && nestedQueryParams.contains("?")) {
                try {
                    val nestedUri = Uri.parse(nestedQueryParams)
                    val newNestedUriBuilder = nestedUri.buildUpon().legacyClearQuery()

                    nestedUri.legacyGetQueryParameterNames().forEach { nestedParam ->
                        if (!isTrackingParam(nestedParam)) {
                            newNestedUriBuilder.appendQueryParameter(nestedParam, nestedUri.getQueryParameter(nestedParam))
                        }
                    }

                    newUriBuilder.appendQueryParameter("q", newNestedUriBuilder.build().toString())
                    changed = true
                } catch (e: Exception) {
                    Log.e("UrlOptimizer", "Error handling nested query params", e)
                }
            }
        }

        // Substack-specific handling
        else if(uri.host?.endsWith(".substack.com") == true) {
            // Add "no_cover=true" parameter for better archive quality
            if (uri.getQueryParameter("no_cover") == null) {
                newUriBuilder.appendQueryParameter("no_cover", "true")
                changed = true
            }
        }

        // Amazon-specific handling for path-based tracking
        else if (uri.host?.contains("amazon.com") == true || uri.host?.contains("amazon.") == true) {
            val path = uri.path ?: ""
            // Remove /ref=... tracking from Amazon URLs
            val refPattern = Regex("/ref=[^/]*")
            val cleanedPath = path.replace(refPattern, "")
            if (cleanedPath != path) {
                newUriBuilder.path(cleanedPath)
                changed = true
            }
        }

        //mailchimp-specific handling
        else if (uri.host?.contains("list-manage.com") == true) {
            if (uri.getQueryParameter("e") != null) {
                // Rebuild query parameters without the "e" parameter
                newUriBuilder.legacyClearQuery()
                uri.legacyGetQueryParameterNames().forEach { param ->
                    if (param != "e") {
                        newUriBuilder.appendQueryParameter(param, uri.getQueryParameter(param))
                    }
                }
                changed = true
            }
        }

        // Google search result URL handling
        else if (uri.host?.equals("www.google.com", ignoreCase = true) == true && uri.path?.equals("/url") == true) {
            val targetUrl = uri.getQueryParameter("url")
            if (targetUrl != null) {
                try {
                    // Parse the nested target URL
                    val targetUri = Uri.parse(targetUrl)

                    val cleanedTargetUrl = cleanTrackingParamsFromUrl(targetUri.toString())
                    val cleanedTargetUri = Uri.parse(cleanedTargetUrl)
                    
                    // Create a new URI builder with the target URL
                    val newTargetUriBuilder = cleanedTargetUri.buildUpon().legacyClearQuery()
                    
                    // Add an empty parameter first to match expected format (?&ved=...)
                    newTargetUriBuilder.appendQueryParameter("", "")
                    
                    // Add Google tracking parameters (ved, usg) to the cleaned target URL
                    uri.getQueryParameter("ved")?.let { newTargetUriBuilder.appendQueryParameter("ved", it) }
                    uri.getQueryParameter("usg")?.let { newTargetUriBuilder.appendQueryParameter("usg", it) }
                    
                    // Build the final URL
                    val finalUrl = newTargetUriBuilder.build().toString()
                    
                    // Return the target URL with Google tracking parameters
                    return finalUrl
                } catch (e: Exception) {
                    Log.e("UrlOptimizer", "Error processing Google URL: $url", e)
                    // If parsing fails, return the original URL
                    return url
                }
            }
        }

        else if (uri.host?.equals("t.me", ignoreCase = true) == true) {
            val path = uri.path?.trimStart('/') ?: ""
            if (!path.startsWith("s/") && path.isNotEmpty()) {
                newUriBuilder.path("/s/$path") //This is to archive some parts of the group chat if the web preview feature is enabled, otherwise the about page will be shown by telegram.
                changed = true
            }
        }

        return if (changed) newUriBuilder.build().toString() else url
    }

    /**
     * Clean tracking parameters from URLs
     */
    fun cleanTrackingParamsFromUrl(url: String): String {
        val uri = Uri.parse(url)
        if (uri.legacyGetQueryParameterNames().isEmpty()) {
            return url
        }

        val newUriBuilder = uri.buildUpon().legacyClearQuery()
        var removeYouTubeParams = false
        var removeSubstackParams = false

        // Additional handling for YouTube URLs
        if (uri.host?.contains("youtube.com") == true || uri.host?.contains("youtu.be") == true) {
            removeYouTubeParams = true
            val nestedQueryParams = uri.getQueryParameter("q")
            if (nestedQueryParams != null) {
                val nestedUri = Uri.parse(nestedQueryParams)
                val newNestedUriBuilder = nestedUri.buildUpon().legacyClearQuery()

                nestedUri.legacyGetQueryParameterNames().forEach { nestedParam ->
                    if (!isTrackingParam(nestedParam)) {
                        newNestedUriBuilder.appendQueryParameter(nestedParam, nestedUri.getQueryParameter(nestedParam))
                    }
                }
                newUriBuilder.appendQueryParameter("q", newNestedUriBuilder.build().toString())
            }
        }

        else if(uri.host?.endsWith(".substack.com") == true) {
            removeSubstackParams = true
        }

        uri.legacyGetQueryParameterNames().forEach { param ->
            // Add only non-tracking parameters to the new URL
            if (!isTrackingParam(param) && !(removeYouTubeParams && isUnwantedYoutubeParam(param)) && !(removeSubstackParams && isUnwantedSubstackParam(param))) {
                newUriBuilder.appendQueryParameter(param, uri.getQueryParameter(param))
            }
        }
        return newUriBuilder.build().toString()
    }

    /**
     * Check if a parameter is a tracking parameter
     */
    fun isTrackingParam(param: String): Boolean {
        val trackingParams = setOf(
            "utm_source", "utm_medium", "utm_campaign", "utm_content", "utm_term",
            "fbclid", "gclid", "dclid", "gbraid", "wbraid", "msclkid", "tclid",
            "aff_id", "affiliate_id", "ref", "referer", "campaign_id", "ad_id",
            "adgroup_id", "adset_id", "creativetype", "placement", "network",
            "mc_eid", "mc_cid", "si", "icid", "_ga", "_gid", "scid", "click_id",
            "trk", "track", "trk_sid", "sid", "mibextid", "fb_action_ids",
            "fb_action_types", "fb_medium", "fb_campaign", "fb_source",
            "m_entstream_source", "twclid", "igshid", "s_kwcid", "sxsrf", "sca_esv",
            "source", "tbo", "sa", "ved", "usg", "pi", "fbs", "fbc", "fb_ref", "client", "ei",
            "gs_lp", "sclient", "oq", "uact", "bih", "biw",
            "ref_source", "ref_medium", "ref_campaign", "ref_content", "ref_term", "ref_keyword",
            "ref_type", "ref_campaign_id", "ref_ad_id", "ref_adgroup_id", "entstream_source",
            "ref_creativetype", "ref_placement", "ref_network", "ref_sid", "ref_mc_eid",
            "ref_mc_cid", "ref_scid", "ref_click_id", "ref_trk", "ref_track", "ref_trk_sid",
            "ref_sid", "ref", "ref_url", "ref_campaign_id", "ref_adgroup_id", "ref_adset_id",
            "wprov", //wikipedia's mostly harmless tracker
            "rcm", //Linkedin's new tracker
            "xmt", //threads new tracker
            "gc_id","h_ga_id","h_ad_id","h_keyword_id","gad_source", "impressionid", //reddit ad tracker
            "ga_source", "ga_medium", "ga_campaign", "ga_content", "ga_term", "int_source",
            "chainedPosts", // Reddits new tracker
            "mibextid" //facebooks new tracker
        )
        return param in trackingParams
    }

    /**
     * Check if a parameter is an unwanted YouTube parameter
     */
    fun isUnwantedYoutubeParam(param: String): Boolean {
        val youtubeParams = setOf(
            "feature",
            "ab_channel",
            "t",
            "si"
        )
        return param in youtubeParams
    }

    /**
     * Check if a parameter is an unwanted Substack parameter
     */
    fun isUnwantedSubstackParam(param: String): Boolean {
        val substackParams = setOf(
            "r",
            "showWelcomeOnShare"
        )
        return param in substackParams
    }
}

