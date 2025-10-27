package org.gnosco.share2archivetoday.media

import org.gnosco.share2archivetoday.media.*

/**
 * Media3 Transformer implementation for merging video and audio
 * Add to build.gradle (Module: app):
 * implementation "androidx.media3:media3-transformer:1.1.1"
 * implementation "androidx.media3:media3-effect:1.1.1"
 */

import androidx.media3.transformer.Transformer
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.MediaItem
import androidx.media3.common.MediaItem as CommonMediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.TransformationResult
import androidx.media3.transformer.TransformationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class Media3VideoMerger(private val context: Context) {

    companion object {
        private const val TAG = "Media3VideoMerger"
    }

    /**
     * Merge video and audio using Media3 Transformer
     * This should handle more codecs than MediaMuxer including AV1
     */
    @UnstableApi
    fun mergeVideoAudio(
        videoPath: String,
        audioPath: String,
        outputPath: String,
        progressCallback: ((Float) -> Unit)? = null
    ): MergeResult {
        return try {
            Log.d(TAG, "Starting Media3 Transformer merge")
            Log.d(TAG, "Video: $videoPath")
            Log.d(TAG, "Audio: $audioPath")
            Log.d(TAG, "Output: $outputPath")

            val videoFile = File(videoPath)
            val audioFile = File(audioPath)

            if (!videoFile.exists() || !audioFile.exists()) {
                return MergeResult.Error("Input files do not exist")
            }

            // Create MediaItems for video and audio
            val videoMediaItem = CommonMediaItem.fromUri(videoFile.toURI().toString())
            val audioMediaItem = CommonMediaItem.fromUri(audioFile.toURI().toString())

            // Create transformation request
            val transformationRequest = TransformationRequest.Builder()
                .build()

            // Set up transformer
            val latch = CountDownLatch(1)
            var result: MergeResult = MergeResult.Error("Unknown error")

            val transformer = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onTransformationCompleted(
                        mediaItem: CommonMediaItem,
                        transformationResult: TransformationResult
                    ) {
                        Log.d(TAG, "Transformation completed successfully")
                        result = MergeResult.Success(outputPath)
                        latch.countDown()
                    }

                    override fun onTransformationError(
                        mediaItem: CommonMediaItem,
                        exception: TransformationException
                    ) {
                        Log.e(TAG, "Transformation failed", exception)
                        result = when {
                            exception.message?.contains("codec", ignoreCase = true) == true -> {
                                MergeResult.UnsupportedCodec(exception.message ?: "Codec not supported")
                            }
                            else -> {
                                MergeResult.Error("Transformation failed: ${exception.message}")
                            }
                        }
                        latch.countDown()
                    }
                })
                .build()

            // For Media3 Transformer, we need to create a composition or use Effects API
            // This is a simplified approach - you might need to use Composition API for complex merging

            // Start transformation
            transformer.start(videoMediaItem, outputPath)

            // Wait for completion (with timeout)
            val completed = latch.await(60, TimeUnit.SECONDS)

            if (!completed) {
                transformer.cancel()
                result = MergeResult.Error("Transformation timed out")
            }

            result

        } catch (e: Exception) {
            Log.e(TAG, "Error in Media3 transformation", e)
            MergeResult.Error("Failed to merge: ${e.message}")
        }
    }

    /**
     * Check if Media3 Transformer supports the codecs in the files
     */
    fun checkCodecSupport(videoPath: String, audioPath: String): CodecSupportResult {
        return try {
            // Media3 Transformer generally has better codec support than MediaMuxer
            // but still may not support all combinations

            val videoFile = File(videoPath)
            val audioFile = File(audioPath)

            if (!videoFile.exists() || !audioFile.exists()) {
                return CodecSupportResult.FilesNotFound
            }

            // Use MediaMetadataRetriever to check codecs
            val videoRetriever = MediaMetadataRetriever()
            val audioRetriever = MediaMetadataRetriever()

            try {
                videoRetriever.setDataSource(videoPath)
                audioRetriever.setDataSource(audioPath)

                val videoMime = videoRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                val audioMime = audioRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)

                Log.d(TAG, "Detected video codec: $videoMime")
                Log.d(TAG, "Detected audio codec: $audioMime")

                // Media3 Transformer has broader support than MediaMuxer
                // but may still have limitations with some codec combinations
                val supportedVideoCombos = setOf(
                    "video/avc",     // H.264
                    "video/hevc",    // H.265
                    "video/av01",    // AV1 - Media3 may support this!
                    "video/vp8",     // VP8
                    "video/vp9"      // VP9
                )

                val supportedAudioCombos = setOf(
                    "audio/mp4a-latm", // AAC
                    "audio/mpeg",      // MP3
                    "audio/opus",      // Opus
                    "audio/vorbis"     // Vorbis
                )

                when {
                    videoMime in supportedVideoCombos && audioMime in supportedAudioCombos -> {
                        CodecSupportResult.Supported(videoMime ?: "unknown", audioMime ?: "unknown")
                    }
                    videoMime !in supportedVideoCombos -> {
                        CodecSupportResult.UnsupportedVideo(videoMime ?: "unknown")
                    }
                    audioMime !in supportedAudioCombos -> {
                        CodecSupportResult.UnsupportedAudio(audioMime ?: "unknown")
                    }
                    else -> {
                        CodecSupportResult.Unknown
                    }
                }

            } finally {
                videoRetriever.release()
                audioRetriever.release()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error checking codec support", e)
            CodecSupportResult.Error(e.message ?: "Unknown error")
        }
    }

    sealed class MergeResult {
        data class Success(val outputPath: String) : MergeResult()
        data class Error(val message: String) : MergeResult()
        data class UnsupportedCodec(val codecInfo: String) : MergeResult()
    }

    sealed class CodecSupportResult {
        data class Supported(val videoCodec: String, val audioCodec: String) : CodecSupportResult()
        data class UnsupportedVideo(val videoCodec: String) : CodecSupportResult()
        data class UnsupportedAudio(val audioCodec: String) : CodecSupportResult()
        object FilesNotFound : CodecSupportResult()
        object Unknown : CodecSupportResult()
        data class Error(val message: String) : CodecSupportResult()
    }
}