package org.gnosco.share2archivetoday.media

import android.content.Context
import android.util.Log
import org.gnosco.share2archivetoday.PythonVideoDownloader

class SmartFormatSelector(private val context: Context) {

    companion object {
        private const val TAG = "SmartFormatSelector"
    }

    private val pythonDownloader = PythonVideoDownloader(context)

    /**
     * Determine the optimal download strategy based on device capabilities
     * and available formats
     */
    suspend fun determineOptimalStrategy(
        url: String,
        requestedQuality: String
    ): DownloadStrategy {

        return try {
            Log.d(TAG, "Analyzing optimal download strategy for: $url")

            // Step 1: Get available formats from yt-dlp
            val videoInfo = pythonDownloader.getVideoInfo(url)
            if (videoInfo == null) {
                Log.w(TAG, "Could not get video info, falling back to quality-based")
                return DownloadStrategy.QualityBased(
                    requestedQuality,
                    "Could not analyze formats"
                )
            }

            Log.d(TAG, "Found ${videoInfo.formats.size} available formats")

            // Step 2: Analyze the best available formats
            val formatAnalysis = analyzeFormats(videoInfo.formats, requestedQuality)

            // Step 3: Check device muxing capabilities for the best separate streams
            val deviceCapabilities = checkDeviceCapabilities()

            // Step 4: Make the decision
            val strategy = selectOptimalStrategy(
                formatAnalysis,
                deviceCapabilities,
                requestedQuality
            )

            Log.i(TAG, "Selected strategy: ${strategy.description}")
            strategy

        } catch (e: Exception) {
            Log.e(TAG, "Error in format analysis, falling back to safe strategy", e)
            DownloadStrategy.QualityBased(requestedQuality, "Analysis failed: ${e.message}")
        }
    }

    private fun analyzeFormats(
        formats: List<PythonVideoDownloader.FormatInfo>,
        requestedQuality: String
    ): FormatAnalysis {

        // Find the best combined stream for the requested quality
        val bestCombined = formats
            .filter { it.hasAudio && it.hasVideo }
            .filter { matchesQuality(it, requestedQuality) }
            .maxByOrNull { it.tbr }

        // Find the best separate video stream for the requested quality
        val bestVideoOnly = formats
            .filter { it.hasVideo && !it.hasAudio }
            .filter { matchesQuality(it, requestedQuality) }
            .maxByOrNull { it.tbr }

        // Find the best audio stream
        val bestAudio = formats
            .filter { it.hasAudio && !it.hasVideo }
            .maxByOrNull { it.tbr }

        Log.d(TAG, "Format analysis:")
        Log.d(
            TAG,
            "  Best combined: ${bestCombined?.let { "${it.formatId} (${it.vcodec}/${it.acodec}, ${it.tbr}kbps)" } ?: "None"}")
        Log.d(
            TAG,
            "  Best video-only: ${bestVideoOnly?.let { "${it.formatId} (${it.vcodec}, ${it.tbr}kbps)" } ?: "None"}")
        Log.d(
            TAG,
            "  Best audio: ${bestAudio?.let { "${it.formatId} (${it.acodec}, ${it.tbr}kbps)" } ?: "None"}")

        return FormatAnalysis(
            bestCombined = bestCombined,
            bestVideoOnly = bestVideoOnly,
            bestAudio = bestAudio,
            allFormats = formats
        )
    }

    private fun matchesQuality(
        format: PythonVideoDownloader.FormatInfo,
        requestedQuality: String
    ): Boolean {
        return when (requestedQuality) {
            "best" -> true
            "1908p", "1080p" -> format.height <= 1080 && format.height >= 720
            "720p" -> format.height <= 720 && format.height >= 480
            "480p" -> format.height <= 480 && format.height >= 360
            "360p" -> format.height <= 360
            else -> true
        }
    }

    private fun checkDeviceCapabilities(): DeviceCapabilities {
        val supportedVideoCodecs = mutableSetOf<String>()
        val supportedAudioCodecs = mutableSetOf<String>()

        // Check what MediaMuxer supports
        val knownVideoCodecs = listOf(
            "video/avc",           // H.264 - widely supported
            "video/hevc",          // H.265 - supported on many devices
            "video/mp4v-es",       // MPEG-4 - widely supported
            "video/3gpp",          // 3GPP - widely supported
            "video/av01",          // AV1 - NOT supported by MediaMuxer
            "video/vp8",           // VP8 - NOT supported by MediaMuxer
            "video/vp9"            // VP9 - NOT supported by MediaMuxer
        )

        val knownAudioCodecs = listOf(
            "audio/mp4a-latm",     // AAC - widely supported
            "audio/mpeg",          // MP3 - widely supported
            "audio/3gpp",          // 3GPP - widely supported
            "audio/amr-wb",        // AMR-WB - supported
            "audio/amr-nb",        // AMR-NB - supported
            "audio/opus",          // Opus - NOT supported by MediaMuxer
            "audio/vorbis"         // Vorbis - NOT supported by MediaMuxer
        )

        // MediaMuxer supported codecs (conservative list)
        val mediaMuxerSupportedVideo = setOf(
            "video/avc",
            "video/hevc",
            "video/mp4v-es",
            "video/3gpp"
        )

        val mediaMuxerSupportedAudio = setOf(
            "audio/mp4a-latm",
            "audio/mpeg",
            "audio/3gpp",
            "audio/amr-wb",
            "audio/amr-nb"
        )

        Log.d(TAG, "Device capabilities:")
        Log.d(TAG, "  MediaMuxer video codecs: ${mediaMuxerSupportedVideo.size}")
        Log.d(TAG, "  MediaMuxer audio codecs: ${mediaMuxerSupportedAudio.size}")

        return DeviceCapabilities(
            supportedVideoCodecs = mediaMuxerSupportedVideo,
            supportedAudioCodecs = mediaMuxerSupportedAudio,
            hasMedia3 = hasMedia3Available()
        )
    }

    private fun selectOptimalStrategy(
        analysis: FormatAnalysis,
        capabilities: DeviceCapabilities,
        requestedQuality: String
    ): DownloadStrategy {

        // Check if we can merge the best separate streams
        val canMergeBestSeparate = analysis.bestVideoOnly != null &&
                analysis.bestAudio != null &&
                canMergeFormats(analysis.bestVideoOnly, analysis.bestAudio, capabilities)

        // Calculate quality scores
        val combinedQuality = analysis.bestCombined?.tbr ?: 0
        val separateQuality =
            (analysis.bestVideoOnly?.tbr ?: 0) + (analysis.bestAudio?.tbr ?: 0)

        Log.d(TAG, "Quality comparison:")
        Log.d(TAG, "  Combined stream: ${combinedQuality}kbps")
        Log.d(
            TAG,
            "  Separate streams: ${separateQuality}kbps (mergeable: $canMergeBestSeparate)"
        )

        return when {
            // Strategy 1: Use separate streams if they're higher quality AND we can merge them
            canMergeBestSeparate && separateQuality > combinedQuality * 1.1 -> {
                Log.i(TAG, "Using separate streams (higher quality + mergeable)")
                DownloadStrategy.SeparateStreams(
                    videoFormatId = analysis.bestVideoOnly!!.formatId,
                    audioFormatId = analysis.bestAudio!!.formatId,
                    quality = requestedQuality,
                    description = "High-quality separate streams (mergeable)"
                )
            }

            // Strategy 2: Use combined stream if available and separate streams can't be merged
            !canMergeBestSeparate && analysis.bestCombined != null -> {
                Log.i(TAG, "Using combined stream (separate streams not mergeable)")
                DownloadStrategy.CombinedStream(
                    formatId = analysis.bestCombined.formatId,
                    quality = requestedQuality,
                    description = "Combined stream (separate streams use incompatible codecs)"
                )
            }

            // Strategy 3: Use separate streams even if we can't merge (keep best single file)
            analysis.bestVideoOnly != null && separateQuality > combinedQuality * 1.5 -> {
                Log.i(TAG, "Using separate streams (much higher quality, will keep video file)")
                DownloadStrategy.SeparateStreams(
                    videoFormatId = analysis.bestVideoOnly.formatId,
                    audioFormatId = analysis.bestAudio?.formatId,
                    quality = requestedQuality,
                    description = "High-quality video (merge not possible but keeping best file)"
                )
            }

            // Strategy 4: Fall back to combined stream
            analysis.bestCombined != null -> {
                Log.i(TAG, "Using combined stream (fallback)")
                DownloadStrategy.CombinedStream(
                    formatId = analysis.bestCombined.formatId,
                    quality = requestedQuality,
                    description = "Combined stream (fallback)"
                )
            }

            // Strategy 5: Last resort - let yt-dlp decide
            else -> {
                Log.w(TAG, "No optimal strategy found, using quality-based selection")
                DownloadStrategy.QualityBased(
                    quality = requestedQuality,
                    description = "Quality-based fallback (no optimal formats found)"
                )
            }
        }
    }

    private fun canMergeFormats(
        videoFormat: PythonVideoDownloader.FormatInfo,
        audioFormat: PythonVideoDownloader.FormatInfo,
        capabilities: DeviceCapabilities
    ): Boolean {

        // Map format codecs to MIME types
        val videoMime = mapCodecToMime(videoFormat.vcodec, isVideo = true)
        val audioMime = mapCodecToMime(audioFormat.acodec, isVideo = false)

        Log.d(TAG, "Checking merge compatibility:")
        Log.d(TAG, "  Video: ${videoFormat.vcodec} -> $videoMime")
        Log.d(TAG, "  Audio: ${audioFormat.acodec} -> $audioMime")

        val videoSupported = videoMime in capabilities.supportedVideoCodecs
        val audioSupported = audioMime in capabilities.supportedAudioCodecs

        Log.d(TAG, "  Video supported: $videoSupported")
        Log.d(TAG, "  Audio supported: $audioSupported")

        return videoSupported && audioSupported
    }

    private fun mapCodecToMime(codec: String, isVideo: Boolean): String {
        return if (isVideo) {
            when {
                codec.startsWith("avc1") -> "video/avc"
                codec.startsWith("hev1") || codec.startsWith("hvc1") -> "video/hevc"
                codec.startsWith("av01") -> "video/av01"
                codec.startsWith("vp8") -> "video/vp8"
                codec.startsWith("vp9") -> "video/vp9"
                codec.startsWith("mp4v") -> "video/mp4v-es"
                else -> "video/unknown"
            }
        } else {
            when {
                codec.startsWith("mp4a") -> "audio/mp4a-latm"
                codec == "mp3" -> "audio/mpeg"
                codec == "opus" -> "audio/opus"
                codec == "vorbis" -> "audio/vorbis"
                else -> "audio/unknown"
            }
        }
    }

    private fun hasMedia3Available(): Boolean {
        return try {
            Class.forName("androidx.media3.transformer.Transformer")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    // Data classes
    data class FormatAnalysis(
        val bestCombined: PythonVideoDownloader.FormatInfo?,
        val bestVideoOnly: PythonVideoDownloader.FormatInfo?,
        val bestAudio: PythonVideoDownloader.FormatInfo?,
        val allFormats: List<PythonVideoDownloader.FormatInfo>
    )

    data class DeviceCapabilities(
        val supportedVideoCodecs: Set<String>,
        val supportedAudioCodecs: Set<String>,
        val hasMedia3: Boolean
    )

    sealed class DownloadStrategy {
        abstract val description: String

        data class CombinedStream(
            val formatId: String,
            val quality: String,
            override val description: String
        ) : DownloadStrategy()

        data class SeparateStreams(
            val videoFormatId: String,
            val audioFormatId: String?,
            val quality: String,
            override val description: String
        ) : DownloadStrategy()

        data class QualityBased(
            val quality: String,
            override val description: String
        ) : DownloadStrategy()
    }
}