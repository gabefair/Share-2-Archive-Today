package org.gnosco.share2archivetoday.utils

import android.util.Log

/**
 * Unified utility class for parsing technical error messages into user-friendly messages
 * Consolidates error handling from ErrorMessageParser and DownloadErrorHandler
 */
class ErrorMessageParser {
    
    companion object {
        private const val TAG = "ErrorMessageParser"
        
        /**
         * Parse technical error messages into user-friendly messages
         * Accepts both Exception objects and error strings
         */
        fun parseFriendlyErrorMessage(exception: Exception): String {
            return parseFriendlyErrorMessage(exception.message ?: "Unknown error occurred")
        }
        
        /**
         * Parse technical error messages into user-friendly messages
         * Main method that handles string error messages
         */
        fun parseFriendlyErrorMessage(errorMessage: String): String {
            val lowerError = errorMessage.lowercase()

            return when {
                // Rate limiting (check first - highest priority)
                lowerError.contains("rate-limit") || lowerError.contains("rate limit") -> {
                    "Your current IP address has been rate-limited.\n\n" +
                            "Solutions:\n" +
                            "• Switch to a different network (WiFi/mobile data)\n" +
                            "• If using a VPN, add this app to split tunneling\n" +
                            "• Wait a few hours and try again\n" +
                            "• Use a different video source\n\n" +
                            "The website blocks IPs that make too many requests."
                }

                // Login/authentication required
                lowerError.contains("login required") || lowerError.contains("sign in") -> {
                    "This content requires an account to access.\n\n" +
                            "The video owner may have restricted access to logged-in users only. " +
                            "Unfortunately, this app cannot download private or login-restricted content."
                }
                lowerError.contains("cookies") && lowerError.contains("login") -> {
                    "This video requires login or special access that cannot be provided."
                }

                // Format issues
                lowerError.contains("format is not available") -> {
                    "This video format is not available. Try a different quality setting."
                }
                lowerError.contains("requested format") -> {
                    "The selected video quality is not available for this video."
                }
                lowerError.contains("no video formats") || lowerError.contains("no formats found") -> {
                    "No downloadable formats found.\n\n" +
                            "The video may be a live stream, DRM-protected, or use an unsupported format."
                }

                // Network errors
                lowerError.contains("network") || lowerError.contains("connection") -> {
                    "Network connection problem.\n\n" +
                            "Please check your internet connection and try again."
                }
                lowerError.contains("timeout") || lowerError.contains("refused") -> {
                    "Connection timed out or was refused.\n\n" +
                            "Please check your internet connection and try again."
                }
                lowerError.contains("ssl") || lowerError.contains("certificate") -> {
                    "Secure connection error.\n\n" +
                            "There's a problem establishing a secure connection. This may be a temporary issue with the website."
                }

                // Too many requests
                lowerError.contains("too many requests") -> {
                    "Too many requests. Please wait a few hours before trying again."
                }

                // Permissions and storage
                lowerError.contains("permission") -> {
                    "Permission denied. Please check app permissions."
                }
                lowerError.contains("storage") || lowerError.contains("space") -> {
                    "Not enough storage space available."
                }

                // Content availability
                lowerError.contains("not found") || lowerError.contains("404") -> {
                    "Video not found.\n\n" +
                            "The link may be invalid or the video may have been removed."
                }
                lowerError.contains("private") || lowerError.contains("unavailable") -> {
                    "This video is private or unavailable.\n\n" +
                            "The video may be:\n" +
                            "• Private or deleted\n" +
                            "• Temporarily unavailable\n" +
                            "• Moved to a different URL"
                }
                lowerError.contains("removed") && lowerError.contains("violation") -> {
                    "This content has been removed due to violations."
                }

                // Access restrictions
                lowerError.contains("age") && (lowerError.contains("restrict") || lowerError.contains("verification")) -> {
                    "This content is age-restricted.\n\n" +
                            "The video requires age verification to view, which this app cannot provide."
                }
                lowerError.contains("blocked") || lowerError.contains("forbidden") -> {
                    "This video is blocked or restricted in your region."
                }
                lowerError.contains("geo") && lowerError.contains("block") -> {
                    "This content is not available in your region.\n\n" +
                            "The content owner has restricted access based on geographic location."
                }
                lowerError.contains("region") || (lowerError.contains("not available") && lowerError.contains("country")) -> {
                    "This content is not available in your region.\n\n" +
                            "The content owner has restricted access based on geographic location."
                }

                // Copyright / DMCA
                lowerError.contains("copyright") || lowerError.contains("dmca") -> {
                    "This content has been removed due to copyright claims.\n\n" +
                            "The video is no longer available on the platform."
                }

                // Server issues
                lowerError.contains("server error") || lowerError.contains("http 5") -> {
                    "Server error occurred. Please try again later."
                }

                // URL and extractor issues
                lowerError.contains("invalid url") || lowerError.contains("malformed") -> {
                    "Invalid video URL. Please check the link and try again."
                }
                lowerError.contains("extractor") || lowerError.contains("unsupported url") -> {
                    "This video site is not supported or the video format is incompatible."
                }
                lowerError.contains("no suitable extractor") -> {
                    "This website is not supported for video downloads.\n\n" +
                            "The URL you shared is not from a supported video platform."
                }

                // Content type issues
                lowerError.contains("live") || lowerError.contains("streaming") -> {
                    "Live streams cannot be downloaded. Please try with a regular video."
                }
                lowerError.contains("playlist") -> {
                    "Playlist downloads are not supported. Please try with individual video links."
                }

                // Captcha
                lowerError.contains("captcha") -> {
                    "Human verification required.\n\n" +
                            "The website is requesting verification that cannot be completed automatically."
                }

                // Generic fallback with hint
                else -> {
                    // Log the novel error for debugging
                    Log.w(TAG, "Novel error encountered: $errorMessage")
                    "Unable to access this video.\n\n" +
                            "This might be due to:\n" +
                            "• Website restrictions or changes\n" +
                            "• Login or subscription requirements\n" +
                            "• Temporary server issues\n\n" +
                            "Please try again later or use a different source."
                }
            }
        }
        
        /**
         * Determine if a download error should trigger a retry
         * Moved from DownloadErrorHandler
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

