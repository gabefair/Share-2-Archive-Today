package org.gnosco.share2archivetoday.utils

import org.gnosco.share2archivetoday.utils.*

/**
 * Utility class for parsing technical error messages into user-friendly messages
 */
class ErrorMessageParser {
    
    companion object {
        /**
         * Parse technical error messages into user-friendly messages
         */
        fun parseFriendlyErrorMessage(exception: Exception): String {
            val errorMessage = exception.message ?: "Unknown error occurred"
            val lowerError = errorMessage.lowercase()

            return when {
                // Rate limiting (check for rate-limit before other errors)
                lowerError.contains("rate-limit") || lowerError.contains("rate limit") -> {
                    "Your current IP address has been rate-limited.\n\n" +
                            "Solutions:\n" +
                            "• Switch to a different network (WiFi/mobile data)\n" +
                            "• If using a VPN, add this app to split tunneling\n" +
                            "• Wait a few hours and try again\n" +
                            "• Use a different video source\n\n" +
                            "The website blocks IPs that make too many requests."
                }

                lowerError.contains("login required") || lowerError.contains("sign in") -> {
                    "This content requires an account to access.\n\n" +
                            "The video owner may have restricted access to logged-in users only. " +
                            "Unfortunately, this app cannot download private or login-restricted content."
                }

                // Geo-blocking
                lowerError.contains("not available in your country") ||
                        lowerError.contains("geo") && lowerError.contains("block") -> {
                    "This content is not available in your region.\n\n" +
                            "The content owner has restricted access based on geographic location."
                }

                // Copyright / DMCA
                lowerError.contains("copyright") || lowerError.contains("dmca") ||
                        lowerError.contains("removed") && lowerError.contains("violation") -> {
                    "This content has been removed due to copyright claims.\n\n" +
                            "The video is no longer available on the platform."
                }

                // Age-restricted
                lowerError.contains("age") && (lowerError.contains("restrict") || lowerError.contains("verification")) -> {
                    "This content is age-restricted.\n\n" +
                            "The video requires age verification to view, which this app cannot provide."
                }

                // Private/unavailable video
                lowerError.contains("private") || lowerError.contains("unavailable") ||
                        lowerError.contains("video not found") || lowerError.contains("404") -> {
                    "This video is not accessible.\n\n" +
                            "The video may be:\n" +
                            "• Private or deleted\n" +
                            "• Temporarily unavailable\n" +
                            "• Moved to a different URL"
                }

                // Network errors
                lowerError.contains("network") || lowerError.contains("connection") ||
                        lowerError.contains("timeout") || lowerError.contains("refused") -> {
                    "Network connection problem.\n\n" +
                            "Please check your internet connection and try again."
                }

                // Unsupported platform
                lowerError.contains("unsupported url") || lowerError.contains("no suitable extractor") -> {
                    "This website is not supported.\n\n" +
                            "The URL you shared is not from a supported video platform."
                }

                // SSL/Certificate errors
                lowerError.contains("ssl") || lowerError.contains("certificate") -> {
                    "Secure connection error.\n\n" +
                            "There's a problem establishing a secure connection. This may be a temporary issue with the website."
                }

                // Captcha
                lowerError.contains("captcha") -> {
                    "Human verification required.\n\n" +
                            "The website is requesting verification that cannot be completed automatically."
                }

                // Format not available
                lowerError.contains("no video formats") || lowerError.contains("no formats found") -> {
                    "No downloadable formats found.\n\n" +
                            "The video may be a live stream, DRM-protected, or use an unsupported format."
                }

                // Generic fallback with hint
                else -> {
                    "Unable to access this video.\n\n" +
                            "This might be due to:\n" +
                            "• Website restrictions or changes\n" +
                            "• Login or subscription requirements\n" +
                            "• Temporary server issues\n\n" +
                            "Please try again later or use a different source."
                }
            }
        }
    }
}

