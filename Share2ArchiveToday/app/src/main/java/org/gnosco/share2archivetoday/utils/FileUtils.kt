package org.gnosco.share2archivetoday.utils

import org.gnosco.share2archivetoday.utils.*

/**
 * Utility class for file operations and formatting
 */
class FileUtils {
    
    companion object {
        /**
         * Get MIME type from file path
         */
        fun getMimeType(filePath: String): String {
            val extension = filePath.substringAfterLast('.', "").lowercase()
            return when (extension) {
                "mp4" -> "video/mp4"
                "webm" -> "video/webm"
                "mkv" -> "video/x-matroska"
                "avi" -> "video/x-msvideo"
                "mov" -> "video/quicktime"
                "mp3" -> "audio/mpeg"
                "aac" -> "audio/aac"
                "m4a" -> "audio/mp4"
                "wav" -> "audio/wav"
                else -> "video/*"
            }
        }

        /**
         * Format file size in human-readable format
         */
        fun formatFileSize(bytes: Long): String {
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0

            return when {
                gb >= 1 -> "%.1f GB".format(gb)
                mb >= 1 -> "%.1f MB".format(mb)
                kb >= 1 -> "%.1f KB".format(kb)
                else -> "$bytes B"
            }
        }

        /**
         * Format duration in a readable format
         */
        fun formatDuration(seconds: Long): String {
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            val secs = seconds % 60

            return when {
                hours > 0 -> "${hours}h ${minutes}m ${secs}s"
                minutes > 0 -> "${minutes}m ${secs}s"
                else -> "${secs}s"
            }
        }

        /**
         * Estimate download size based on quality - deprecated, but kept for compatibility
         */
        fun estimateDownloadSize(quality: String): String {
            return when (quality) {
                "best" -> "50-200 MB"
                "720p" -> "30-100 MB"
                "480p" -> "15-50 MB"
                "360p" -> "8-25 MB"
                "audio_mp3", "audio_aac" -> "3-10 MB"
                else -> "Unknown"
            }
        }
    }
}

