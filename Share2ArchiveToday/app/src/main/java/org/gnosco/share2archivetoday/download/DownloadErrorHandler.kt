package org.gnosco.share2archivetoday.download

import org.gnosco.share2archivetoday.download.*

import android.util.Log

/**
 * Helper class for converting technical error messages to user-friendly messages
 */
class DownloadErrorHandler {
    
    companion object {
        private const val TAG = "DownloadErrorHandler"
        
        /**
         * Convert technical error messages to user-friendly messages
         */
        fun convertToUserFriendlyError(error: String): String {
            return when {
                // Rate limiting (check first to catch rate-limit errors early)
                error.contains("rate-limit", ignoreCase = true) || error.contains("rate limit", ignoreCase = true) ->
                    "Your IP address has been rate-limited. Switch to a different network (WiFi/mobile data), add this app to your VPN's split tunneling, or wait a few hours."

                // Login/authentication required
                error.contains("login required", ignoreCase = true) || error.contains("sign in", ignoreCase = true) ->
                    "This content requires an account to access. The app cannot download private or login-restricted content."
                error.contains("cookies", ignoreCase = true) && error.contains("login", ignoreCase = true) ->
                    "This video requires login or special access that cannot be provided."

                // Format issues
                error.contains("format is not available", ignoreCase = true) ->
                    "This video format is not available. Try a different quality setting."
                error.contains("requested format", ignoreCase = true) ->
                    "The selected video quality is not available for this video."
                error.contains("no video formats", ignoreCase = true) || error.contains("no formats found", ignoreCase = true) ->
                    "No downloadable formats found. The video may be live, DRM-protected, or use an unsupported format."

                // Network errors
                error.contains("network", ignoreCase = true) || error.contains("connection", ignoreCase = true) ->
                    "Network connection problem. Please check your internet connection."
                error.contains("timeout", ignoreCase = true) ->
                    "Download timed out. Please try again."
                error.contains("ssl", ignoreCase = true) || error.contains("certificate", ignoreCase = true) ->
                    "Secure connection error. This may be a temporary issue with the website."

                // Too many requests (general, different from rate-limit)
                error.contains("too many requests", ignoreCase = true) ->
                    "Too many requests. Please wait a few hours before trying again."

                // Permissions and storage
                error.contains("permission", ignoreCase = true) ->
                    "Permission denied. Please check app permissions."
                error.contains("storage", ignoreCase = true) || error.contains("space", ignoreCase = true) ->
                    "Not enough storage space available."

                // Content availability
                error.contains("not found", ignoreCase = true) || error.contains("404", ignoreCase = true) ->
                    "Video not found. The link may be invalid or the video may have been removed."
                error.contains("private", ignoreCase = true) || error.contains("unavailable", ignoreCase = true) ->
                    "This video is private or unavailable."
                error.contains("removed", ignoreCase = true) && error.contains("violation", ignoreCase = true) ->
                    "This content has been removed due to violations."

                // Access restrictions
                error.contains("age", ignoreCase = true) && (error.contains("restrict", ignoreCase = true) || error.contains("verification", ignoreCase = true)) ->
                    "This video has age restrictions and cannot be downloaded."
                error.contains("blocked", ignoreCase = true) || error.contains("forbidden", ignoreCase = true) ->
                    "This video is blocked or restricted in your region."
                error.contains("geo", ignoreCase = true) && error.contains("block", ignoreCase = true) ->
                    "This content is not available in your region."
                error.contains("region", ignoreCase = true) || (error.contains("not available", ignoreCase = true) && error.contains("country", ignoreCase = true)) ->
                    "This video is not available in your region."

                // Copyright
                error.contains("copyright", ignoreCase = true) || error.contains("dmca", ignoreCase = true) ->
                    "This video cannot be downloaded due to copyright restrictions."

                // Server issues
                error.contains("server error", ignoreCase = true) || error.contains("http 5", ignoreCase = true) ->
                    "Server error occurred. Please try again later."

                // URL and extractor issues
                error.contains("invalid url", ignoreCase = true) || error.contains("malformed", ignoreCase = true) ->
                    "Invalid video URL. Please check the link and try again."
                error.contains("extractor", ignoreCase = true) || error.contains("unsupported url", ignoreCase = true) ->
                    "This video site is not supported or the video format is incompatible."
                error.contains("no suitable extractor", ignoreCase = true) ->
                    "This website is not supported for video downloads."

                // Content type issues
                error.contains("live", ignoreCase = true) || error.contains("streaming", ignoreCase = true) ->
                    "Live streams cannot be downloaded. Please try with a regular video."
                error.contains("playlist", ignoreCase = true) ->
                    "Playlist downloads are not supported. Please try with individual video links."

                // Captcha
                error.contains("captcha", ignoreCase = true) ->
                    "Human verification required. The website is requesting verification that cannot be completed automatically."

                else -> {
                    // Log the novel error for debugging while showing a generic message
                    Log.w(TAG, "Novel error encountered: $error")
                    "Download failed. This may be due to website restrictions, temporary server issues, or changes to the video platform. Please try again later."
                }
            }
        }
        
        /**
         * Determine if a download error should trigger a retry
         */
        fun shouldRetryDownload(error: String): Boolean {
            val retryableErrors = listOf(
                "network",
                "connection",
                "timeout",
                "temporary",
                "server error",
                "rate limit",
                "HTTP 500", "502", "503", "504"
            )

            val errorLower = error.lowercase()
            return retryableErrors.any { retryableError ->
                errorLower.contains(retryableError)
            }
        }
    }
}

