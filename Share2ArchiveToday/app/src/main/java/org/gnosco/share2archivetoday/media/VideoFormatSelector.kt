package org.gnosco.share2archivetoday.media

import org.gnosco.share2archivetoday.PythonVideoDownloader
import org.gnosco.share2archivetoday.utils.FileUtils
import android.util.Log

/**
 * Handles video format selection, filtering, and quality option building
 */
class VideoFormatSelector {
    
    companion object {
        private const val TAG = "VideoFormatSelector"
    }

    // Data classes for unified quality selection
    data class QualityOption(
        val displayName: String,
        val height: Int,
        val estimatedSize: Long,
        val downloadStrategy: DownloadStrategy
    )

    sealed class DownloadStrategy {
        data class Combined(val formatId: String, val quality: String) : DownloadStrategy()
        data class Separate(val videoFormatId: String, val audioFormatId: String?, val quality: String) : DownloadStrategy()
        data class QualityBased(val quality: String) : DownloadStrategy() // Fallback to quality string
    }

    /**
     * Build unified quality options from video info
     */
    fun buildUnifiedQualityOptions(videoInfo: PythonVideoDownloader.VideoInfo): List<QualityOption> {
        val formats = filterFormatsByQualityRules(videoInfo.formats)

        // Group formats by height and pick the best one for each height
        val formatsByHeight = formats.groupBy { it.height }
        val uniqueHeightOptions = mutableListOf<QualityOption>()

        // Get all unique heights, filter out 0 and 9999, and sort descending
        val sortedHeights = formatsByHeight.keys
            .filter { it > 0 && it < 9999 }
            .sortedDescending()

        // Check if we have any video-only formats (indicates DASH)
        val hasVideoOnlyFormats = formats.any { !it.hasAudio && it.height > 0 }

        // Create options for each height
        for ((index, height) in sortedHeights.withIndex()) {
            val heightFormats = formatsByHeight[height] ?: continue

            // Pick the best format for this height (prefer ones with audio)
            val bestFormat = heightFormats
                .sortedWith(compareByDescending<PythonVideoDownloader.FormatInfo> { it.hasAudio }
                    .thenByDescending { it.tbr })
                .firstOrNull() ?: continue

            val displayName = createSimpleDisplayName(
                height = height,
                format = bestFormat,
                duration = videoInfo.duration,
                isHighest = index == 0  // Only first one gets star and "Highest Quality"
            )

            uniqueHeightOptions.add(
                QualityOption(
                    displayName = displayName,
                    height = height,
                    estimatedSize = estimateFormatSize(bestFormat, videoInfo.duration),
                    downloadStrategy = DownloadStrategy.QualityBased("${height}p")
                )
            )
        }

        // Add audio-only option if there are video-only formats (DASH indicator)
        if (hasVideoOnlyFormats) {
            val audioOnlyFormats = formats.filter { it.hasAudio && it.height <= 0 }
            val bestAudioFormat = audioOnlyFormats.maxByOrNull { it.tbr }

            if (bestAudioFormat != null) {
                uniqueHeightOptions.add(
                    QualityOption(
                        displayName = "Audio Only - ${FileUtils.formatFileSize(estimateFormatSize(bestAudioFormat, videoInfo.duration))}",
                        height = 0,
                        estimatedSize = estimateFormatSize(bestAudioFormat, videoInfo.duration),
                        downloadStrategy = DownloadStrategy.QualityBased("audio_mp3")
                    )
                )
            }
        }

        // Fallback if no options found
        if (uniqueHeightOptions.isEmpty()) {
            uniqueHeightOptions.add(
                QualityOption(
                    displayName = "Best Available Quality",
                    height = 9999,
                    estimatedSize = estimateSize(9999, videoInfo.duration),
                    downloadStrategy = DownloadStrategy.QualityBased("best")
                )
            )
        }

        return uniqueHeightOptions
    }

    /**
     * Create simple display name for quality option
     */
    private fun createSimpleDisplayName(
        height: Int,
        format: PythonVideoDownloader.FormatInfo,
        duration: Int,
        isHighest: Boolean
    ): String {
        return buildString {
            // Add star only for highest quality
            if (isHighest) append("★ ")

            append("${height}p")

            // Show FPS if available
            val fps = if (format.fps > 0) format.fps else 30
            append(" ${fps}fps")

            // Estimate size
            val size = estimateFormatSize(format, duration)
            if (size > 0) {
                append(" - ${FileUtils.formatFileSize(size)}")
            }

            // Mark highest quality only for the first option
            if (isHighest) {
                append(" (Highest Quality)")
            }
        }
    }

    /**
     * Estimate format size based on available data
     */
    private fun estimateFormatSize(format: PythonVideoDownloader.FormatInfo, duration: Int): Long {
        return when {
            format.filesize > 0 -> format.filesize
            format.tbr > 0 && duration > 0 -> {
                // Calculate: bitrate (kbps) * duration (sec) * 125 (bytes per kbps-second)
                (format.tbr * duration * 125).toLong()
            }
            else -> estimateSize(format.height, duration)
        }
    }

    /**
     * Estimate size based on height and duration
     */
    private fun estimateSize(height: Int, duration: Int): Long {
        // Rough estimates based on typical bitrates
        val estimatedBitrate = when {
            height >= 1440 -> 8000 // 8 Mbps for 1440p+
            height >= 1080 -> 5000 // 5 Mbps for 1080p
            height >= 720 -> 2500  // 2.5 Mbps for 720p
            height >= 480 -> 1200  // 1.2 Mbps for 480p
            else -> 800            // 800 kbps for lower
        }
        return (estimatedBitrate * duration * 125).toLong()
    }

    /**
     * Filter formats based on quality rules:
     * - Don't offer anything less than 360p if the quality available is at least 360p
     * - Don't offer anything less than 720p if at least 720p is available
     * - Always keep "Original Quality" formats (height = 9999) from generic extractors
     * - Handle both landscape and vertical videos properly
     */
    fun filterFormatsByQualityRules(formats: List<PythonVideoDownloader.FormatInfo>): List<PythonVideoDownloader.FormatInfo> {
        if (formats.isEmpty()) return formats

        // Find formats that have video (check for valid video codec, not just hasVideo flag)
        val videoFormats = formats.filter { format ->
            // A format has video if:
            // 1. It has a height > 0, OR
            // 2. It has a non-"none" video codec, OR
            // 3. The hasVideo flag is true
            val hasValidHeight = format.height > 0
            val hasVideoCodec = !format.vcodec.isNullOrEmpty() && format.vcodec != "none"
            val hasVideoFlag = format.hasVideo

            hasValidHeight || hasVideoCodec || hasVideoFlag
        }

        // Exclude special "Original Quality" markers for max height calculation
        val videoFormatsWithKnownHeight = videoFormats.filter { it.height < 9999 && it.height > 0 }
        val maxHeight = videoFormatsWithKnownHeight.maxOfOrNull { it.height } ?: 0

        Log.d(TAG, "Video formats found: ${videoFormats.size}")
        Log.d(TAG, "Video formats with known height: ${videoFormatsWithKnownHeight.size}")
        Log.d(TAG, "Max available height: ${maxHeight}p")

        // For vertical videos, we need different thresholds
        // Detect if this is primarily vertical content by checking if heights are unusually high
        // Vertical videos typically have heights > 1000p while landscape rarely exceeds 1080p
        val isVerticalContent = videoFormatsWithKnownHeight.isNotEmpty() &&
                videoFormatsWithKnownHeight.any { it.height > 1000 }

        Log.d(TAG, "Content appears to be vertical: $isVerticalContent")

        // Apply quality filtering rules
        val filteredFormats = formats.filter { format ->
            if (!videoFormats.contains(format)) {
                // Keep all non-video formats (audio-only)
                Log.d(TAG, "Keeping non-video format ${format.formatId}")
                true
            } else if (format.height >= 9999) {
                // Always keep "Original Quality" formats (unknown resolution from generic extractor)
                Log.d(TAG, "Keeping format ${format.formatId}: Original Quality (unknown resolution)")
                true
            } else if (maxHeight == 0) {
                // If we have no known resolutions, keep all video formats
                Log.d(TAG, "No known resolutions, keeping all video formats")
                true
            } else {
                val height = format.height

                // Adjust thresholds based on content orientation
                val shouldKeep = if (isVerticalContent) {
                    // For vertical videos, use more generous thresholds
                    // since vertical "720p" is actually quite good quality
                    when {
                        maxHeight >= 1000 -> height >= 500  // If very high res available, show 500p+
                        maxHeight >= 700 -> height >= 400   // If high res available, show 400p+
                        maxHeight >= 500 -> height >= 300   // If medium res available, show 300p+
                        else -> true  // If low res available, show all
                    }
                } else {
                    // For landscape videos, use traditional thresholds
                    when {
                        maxHeight >= 720 -> height >= 720   // If 720p+ available, only show 720p+
                        maxHeight >= 360 -> height >= 360   // If 360p+ available, only show 360p+
                        else -> true  // If less than 360p available, show all
                    }
                }

                if (!shouldKeep) {
                    Log.d(TAG, "Filtering out format ${format.formatId}: ${height}p (below threshold for ${if (isVerticalContent) "vertical" else "landscape"} content)")
                }
                shouldKeep
            }
        }

        Log.d(TAG, "Filtered formats: ${filteredFormats.size} (from ${formats.size})")
        return filteredFormats.sortedWith(
            compareByDescending<PythonVideoDownloader.FormatInfo> { videoFormats.contains(it) }
                .thenByDescending { it.height }
        )
    }

    /**
     * Determine optimal quality based on video duration
     * Always prioritize original format, but limit resolution for longer videos
     * Videos ≤6 minutes: original quality in original format
     * Videos >6 minutes: 720p in original format
     */
    fun determineOptimalQuality(videoInfo: PythonVideoDownloader.VideoInfo): String {
        val durationMinutes = videoInfo.duration / 60.0
        return when {
            durationMinutes <= 6.0 -> "best"  // Original quality and format for short videos
            else -> "720p"  // 720p in original format for longer videos
        }
    }
}

