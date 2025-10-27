package org.gnosco.share2archivetoday.utils

import org.gnosco.share2archivetoday.media.*

import org.gnosco.share2archivetoday.utils.*

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/**
 * Helper class for processing video and audio files
 * Handles merging, extraction, and audio file processing
 */
class FileProcessor(private val context: Context) {
    
    companion object {
        private const val TAG = "FileProcessor"
    }
    
    /**
     * Merge separate video and audio streams using Android MediaMuxer
     */
    fun mergeVideoAudio(videoPath: String, audioPath: String, outputPath: String): String? {
        return try {
            val ffmpegWrapper = FFmpegWrapper(context)

            val success = ffmpegWrapper.mergeAudioVideo(
                videoPath = videoPath,
                audioPath = audioPath,
                outputPath = outputPath,
                outputFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )

            if (success) {
                // Clean up separate files
                File(videoPath).delete()
                File(audioPath).delete()
                Log.d(TAG, "Successfully merged video and audio streams")
                outputPath
            } else {
                Log.e(TAG, "Failed to merge video and audio streams")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error merging video and audio streams", e)
            null
        }
    }
    
    /**
     * Extract audio from video file using MediaExtractor and MediaMuxer
     */
    fun extractAudioFromVideo(videoPath: String, title: String, requestedAudioFormat: String): String? {
        return try {
            Log.d(TAG, "Extracting audio from video: $videoPath (requested format: $requestedAudioFormat)")

            val extractor = MediaExtractor()
            extractor.setDataSource(videoPath)

            // Find audio track
            val audioTrack = findAudioTrack(extractor)
            if (audioTrack == null) {
                Log.e(TAG, "No audio track found in video file")
                extractor.release()
                return null
            }

            // Determine output format
            val (outputPath, muxerFormat) = getOutputFormat(videoPath, title, audioTrack.format)
            Log.d(TAG, "Output audio path: $outputPath")

            // Setup muxer and extract audio
            val success = extractAudioToFile(extractor, audioTrack, outputPath, muxerFormat)
            
            extractor.release()

            if (success) {
                Log.d(TAG, "Successfully extracted audio to: $outputPath")
                outputPath
            } else {
                null
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error extracting audio from video", e)
            null
        }
    }
    
    /**
     * Process audio files that may need conversion (DASH m4a -> MP3)
     */
    fun processAudioFile(filePath: String, title: String, isAudioOnlyDownload: Boolean = true): String? {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "File does not exist for processing: $filePath")
                return filePath
            }

            // Check if this is an audio file that needs re-muxing
            if (!AudioRemuxer.needsRemuxing(file, isAudioOnlyDownload)) {
                Log.d(TAG, "File does not need re-muxing: ${file.name}")
                return filePath
            }

            Log.d(TAG, "Processing audio file for MP3 conversion: ${file.name}")

            // Create temporary file for re-muxed output
            val tempFile = AudioRemuxer.createTempFile(file)

            // Perform re-muxing
            val success = AudioRemuxer.remuxAudioFile(file, tempFile)

            if (success && tempFile.exists()) {
                Log.d(TAG, "Audio conversion successful: ${tempFile.name}")

                // Create new M4A file with original name but .m4a extension
                val m4aFile = File(file.parent, "${file.nameWithoutExtension}.m4a")

                // Replace original file with converted M4A version
                if (file.delete()) {
                    if (tempFile.renameTo(m4aFile)) {
                        Log.d(TAG, "Replaced original file with M4A version: ${m4aFile.name}")
                        return m4aFile.absolutePath
                    } else {
                        Log.e(TAG, "Failed to rename converted file to M4A name")
                        tempFile.renameTo(file)
                    }
                } else {
                    Log.e(TAG, "Failed to delete original file for replacement")
                }
            } else {
                Log.e(TAG, "Audio conversion failed for: ${file.name}")
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing audio file: $filePath", e)
        }

        return filePath
    }
    
    private data class AudioTrackInfo(val index: Int, val format: MediaFormat)
    
    private fun findAudioTrack(extractor: MediaExtractor): AudioTrackInfo? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) {
                Log.d(TAG, "Found audio track $i with mime type: $mime")
                return AudioTrackInfo(i, format)
            }
        }
        return null
    }
    
    private fun getOutputFormat(videoPath: String, title: String, trackFormat: MediaFormat): Pair<String, Int> {
        val mime = trackFormat.getString(MediaFormat.KEY_MIME)
        val (outputExt, muxerFormat) = when {
            mime?.contains("mp4a") == true || mime?.contains("aac") == true ->
                "m4a" to MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            mime?.contains("opus") == true ->
                "webm" to MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
            else ->
                "m4a" to MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        }
        
        val outputDir = File(videoPath).parent
        val outputPath = "$outputDir/${title}_audio.$outputExt"
        
        return outputPath to muxerFormat
    }
    
    private fun extractAudioToFile(
        extractor: MediaExtractor,
        audioTrack: AudioTrackInfo,
        outputPath: String,
        muxerFormat: Int
    ): Boolean {
        return try {
            val muxer = MediaMuxer(outputPath, muxerFormat)
            extractor.selectTrack(audioTrack.index)
            val muxerTrackIndex = muxer.addTrack(audioTrack.format)
            muxer.start()

            // Copy audio data
            val bufferInfo = MediaCodec.BufferInfo()
            val buffer = ByteBuffer.allocate(1024 * 1024) // 1MB buffer
            var extractedFrames = 0

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) {
                    bufferInfo.presentationTimeUs = 0
                    bufferInfo.flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    bufferInfo.size = 0
                    bufferInfo.offset = 0
                    muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                    Log.d(TAG, "End of stream reached")
                    break
                }

                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = when {
                    extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0 ->
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0 ->
                        MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
                    else -> 0
                }
                bufferInfo.size = sampleSize
                bufferInfo.offset = 0

                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                extractor.advance()
                extractedFrames++

                if (extractedFrames % 100 == 0) {
                    Log.d(TAG, "Extracted $extractedFrames audio frames")
                }
            }

            Log.d(TAG, "Total extracted frames: $extractedFrames")

            muxer.stop()
            muxer.release()
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting audio frames", e)
            false
        }
    }
}

