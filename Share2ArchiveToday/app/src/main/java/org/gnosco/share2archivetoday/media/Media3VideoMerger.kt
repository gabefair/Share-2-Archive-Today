package org.gnosco.share2archivetoday.media


import android.content.Context
import android.util.Log
import android.media.MediaMetadataRetriever
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import androidx.media3.transformer.Transformer
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi

/**
 * Media3 Transformer implementation for merging video and audio
 * 
 * NOTE: This is a placeholder for future Media3 Transformer integration.
 * Currently, this class exists for codec compatibility but the actual merging
 * is handled by FFmpegWrapper (MediaMuxer) which works fine for H.264+AAC.
 * 
 * If you encounter codec errors with MediaMuxer (e.g., VP9, AV1), you would
 * need to implement proper Media3 Transformer logic here. The challenge is
 * that Media3 Transformer API has changed significantly across versions.
 * 
 * Requires:
 * - implementation "androidx.media3:media3-transformer:1.8.0"
 * - implementation "androidx.media3:media3-common:1.8.0"
 */
class Media3VideoMerger(private val context: Context) {

    companion object {
        private const val TAG = "Media3VideoMerger"
    }

    /**
     * Merge video and audio using Media3 Transformer
     * 
     * TODO: Implement proper Media3 1.8.0 API for merging separate video/audio
     * For now, returns an error to fall back to MediaMuxer
     */
    @UnstableApi
    fun mergeVideoAudio(
        videoPath: String,
        audioPath: String,
        outputPath: String,
        progressCallback: ((Float) -> Unit)? = null
    ): MergeResult {
        // return try {
        //     Log.d(TAG, "Starting Media3 Transformer merge")
        //     Log.d(TAG, "Video: $videoPath")
        //     Log.d(TAG, "Audio: $audioPath")
        //     Log.d(TAG, "Output: $outputPath")

        //     val videoFile = File(videoPath)
        //     val audioFile = File(audioPath)

        //     if (!videoFile.exists() || !audioFile.exists()) {
        //         return MergeResult.Error("Input files do not exist")
        //     }

        //     // Create MediaItems for video and audio
        //     val videoMediaItem = MediaItem.fromUri(videoFile.toURI().toString())
        //     val audioMediaItem = MediaItem.fromUri(audioFile.toURI().toString())

        //     // Set up transformer with listener
        //     val latch = CountDownLatch(1)
        //     var result: MergeResult = MergeResult.Error("Unknown error")

        //     val transformer = Transformer.Builder(context)
        //         .addListener(object : Transformer.Listener {
        //             override fun onCompleted(composition: Composition, exportResult: ExportResult) {
        //                 Log.d(TAG, "Export completed successfully")
        //                 Log.d(TAG, "Duration: ${exportResult.durationMs}ms")
        //                 Log.d(TAG, "File size: ${exportResult.fileSizeBytes} bytes")
        //                 result = MergeResult.Success(outputPath)
        //                 latch.countDown()
        //             }

        //             override fun onError(
        //                 composition: Composition,
        //                 exportResult: ExportResult,
        //                 exportException: ExportException
        //             ) {
        //                 Log.e(TAG, "Export failed", exportException)
        //                 result = when {
        //                     exportException.message?.contains("codec", ignoreCase = true) == true -> {
        //                         MergeResult.UnsupportedCodec(exportException.message ?: "Codec not supported")
        //                     }
        //                     else -> {
        //                         MergeResult.Error("Export failed: ${exportException.message}")
        //                     }
        //                 }
        //                 latch.countDown()
        //             }
        //         })
        //         .build()

        //     // Create EditedMediaItems - video with audio track from separate file
        //     // This is the proper way to merge separate video and audio in Media3
        //     val editedVideoItem = EditedMediaItem.Builder(videoMediaItem)
        //         .setRemoveAudio(false) // Keep if video has audio (will be ignored anyway)
        //         .setRemoveVideo(false)
        //         .build()

        //     val editedAudioItem = EditedMediaItem.Builder(audioMediaItem)
        //         .setRemoveAudio(false)
        //         .setRemoveVideo(true) // Remove video from audio file
        //         .build()

        //     // Create sequence with both items - Media3 will merge them
        //     val sequence = EditedMediaItemSequence.Builder(editedVideoItem, editedAudioItem)
        //         .build()

        //     // Create composition
        //     val composition = Composition.Builder(sequence)
        //         .build()

        //     // Start export
        //     transformer.start(composition, outputPath)

        //     // Wait for completion (with timeout)
        //     val completed = latch.await(120, TimeUnit.SECONDS) // 2 minutes for large files

        //     if (!completed) {
        //         transformer.cancel()
        //         result = MergeResult.Error("Export timed out after 120 seconds")
        //     }

        //     result

        // } catch (e: Exception) {
        //     Log.e(TAG, "Error in Media3 transformation", e)
        //     MergeResult.Error("Failed to merge: ${e.message}")
        // }
        Log.w(TAG, "Media3VideoMerger.mergeVideoAudio() not fully implemented")
        Log.w(TAG, "Falling back to FFmpegWrapper (MediaMuxer) for merging")
        
        // Return error to trigger fallback to MediaMuxer
        return MergeResult.Error(
            "Media3 Transformer merging not implemented yet. Use FFmpegWrapper (MediaMuxer) instead."
        )
        
        /* TODO: Implement proper Media3 1.8.0 merging logic
         * 
         * The challenge is that Media3 Transformer API for merging separate
         * video and audio files requires using Effects/EditedMediaItem APIs
         * that vary significantly between versions.
         * 
         * For Media3 1.8.0+, you would need to:
         * 1. Create MediaItem for video
         * 2. Create MediaItem for audio  
         * 3. Use Effects API or EditedMediaItem to combine them
         * 4. Use Transformer to export the result
         * 
         * Example skeleton:
         * 
         * val videoItem = MediaItem.fromUri(videoPath)
         * val audioItem = MediaItem.fromUri(audioPath)
         * 
         * val transformer = Transformer.Builder(context)
         *     .addListener(...)
         *     .build()
         * 
         * // Need to figure out proper way to combine video + audio
         * transformer.start(videoItem, outputPath)
         */
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
                // Supports most common codecs including VP9 and AV1
                val supportedVideoCombos = setOf(
                    "video/avc",     // H.264
                    "video/hevc",    // H.265
                    "video/av01",    // AV1 - Media3 supports this!
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
