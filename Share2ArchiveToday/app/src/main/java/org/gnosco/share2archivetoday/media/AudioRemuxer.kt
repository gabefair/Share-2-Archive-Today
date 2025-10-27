package org.gnosco.share2archivetoday.media

import org.gnosco.share2archivetoday.media.*

import android.media.MediaExtractor
import android.media.MediaMuxer
import android.media.MediaFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.util.Log
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Utility class for re-muxing audio files using Android's MediaMuxer
 * Converts DASH m4a files to standard m4a files for better compatibility
 */
class AudioRemuxer {
    
    companion object {
        private const val TAG = "AudioRemuxer"
        
        /**
         * Re-mux an audio file to AAC format for cross-platform compatibility
         * @param inputFile The input audio file (potentially DASH m4a)
         * @param outputFile The output AAC file path
         * @return true if conversion was successful, false otherwise
         */
        fun remuxAudioFile(inputFile: File, outputFile: File): Boolean {
            if (!inputFile.exists()) {
                Log.e(TAG, "Input file does not exist: ${inputFile.absolutePath}")
                return false
            }
            
            var extractor: MediaExtractor? = null
            var encoder: MediaCodec? = null
            var muxer: MediaMuxer? = null
            
            try {
                Log.d(TAG, "Starting audio conversion to AAC: ${inputFile.name}")
                Log.d(TAG, "Input: ${inputFile.absolutePath}")
                Log.d(TAG, "Output: ${outputFile.absolutePath}")
                
                // Create MediaExtractor to read the input file
                extractor = MediaExtractor()
                extractor.setDataSource(inputFile.absolutePath)
                
                // Find the audio track
                var audioTrackIndex = -1
                var audioFormat: MediaFormat? = null
                
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME)
                    
                    if (mime?.startsWith("audio/") == true) {
                        audioTrackIndex = i
                        audioFormat = format
                        Log.d(TAG, "Found audio track: $mime")
                        break
                    }
                }
                
                if (audioTrackIndex == -1 || audioFormat == null) {
                    Log.e(TAG, "No audio track found in input file")
                    return false
                }
                
                // Try to create AAC encoder (cross-platform compatibility)
                var useAAC = false
                
                try {
                    val encoderName = "audio/mp4a-latm"
                    encoder = MediaCodec.createEncoderByType(encoderName)
                    
                    // Get actual audio format from the input file to preserve channels and sample rate
                    val inputSampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) ?: 44100
                    val inputChannels = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) ?: 2
                    
                    Log.d(TAG, "Input audio: ${inputSampleRate}Hz, ${inputChannels} channels")
                    
                    // Configure AAC encoder with input file's actual parameters
                    val encoderFormat = MediaFormat.createAudioFormat(encoderName, inputSampleRate, inputChannels)
                    encoderFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000) // 128 kbps
                    encoderFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    encoderFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 16) // 16KB buffer
                    
                    encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    encoder.start()
                    useAAC = true
                    Log.d(TAG, "Using AAC encoder for cross-platform compatibility")
                } catch (e: Exception) {
                    Log.w(TAG, "AAC encoder not available, falling back to direct copy: ${e.message}")
                    encoder?.release()
                    encoder = null
                }
                
                if (useAAC && encoder != null) {
                    // Use AAC encoding with MP4 container (cross-platform)
                    val outputFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                    muxer = MediaMuxer(outputFile.absolutePath, outputFormat)
                    
                    // Process audio with AAC encoding
                    val bufferInfo = MediaCodec.BufferInfo()
                    val inputBufferSize = 1024 * 1024 // 1MB buffer
                    val inputBuffer = ByteBuffer.allocate(inputBufferSize)
                    
                    var totalBytesProcessed = 0L
                    val fileSize = inputFile.length()
                    var muxerTrackIndex = -1
                    var muxerStarted = false
                    
                    extractor.selectTrack(audioTrackIndex)
                    
                    while (true) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            Log.d(TAG, "End of audio data reached")
                            break
                        }
                        
                        // Feed data to AAC encoder
                        val inputBufferIndex = encoder.dequeueInputBuffer(10000)
                        if (inputBufferIndex >= 0) {
                            val encoderInputBuffer = encoder.getInputBuffer(inputBufferIndex)
                            encoderInputBuffer?.clear()
                            encoderInputBuffer?.put(inputBuffer)
                            encoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, extractor.sampleFlags)
                        }
                        
                        // Get encoded AAC data
                        val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                        if (outputBufferIndex >= 0) {
                            val encoderOutputBuffer = encoder.getOutputBuffer(outputBufferIndex)
                            
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                                // Codec config data - add track to muxer
                                if (muxerTrackIndex == -1) {
                                    val encoderOutputFormat = encoder.outputFormat
                                    muxerTrackIndex = muxer.addTrack(encoderOutputFormat)
                                    muxer.start()
                                    muxerStarted = true
                                }
                            } else if (bufferInfo.size > 0 && muxerStarted) {
                                // Encoded AAC audio data - write to muxer
                                encoderOutputBuffer?.position(bufferInfo.offset)
                                encoderOutputBuffer?.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(muxerTrackIndex, encoderOutputBuffer!!, bufferInfo)
                            }
                            
                            encoder.releaseOutputBuffer(outputBufferIndex, false)
                        }
                        
                        extractor.advance()
                        totalBytesProcessed += sampleSize
                        
                        // Log progress every 10%
                        val progress = (totalBytesProcessed * 100) / fileSize
                        if (progress % 10 == 0L && progress > 0) {
                            Log.d(TAG, "AAC conversion progress: $progress%")
                        }
                    }
                    
                    // Flush AAC encoder
                    val inputBufferIndex = encoder.dequeueInputBuffer(10000)
                    if (inputBufferIndex >= 0) {
                        val encoderInputBuffer = encoder.getInputBuffer(inputBufferIndex)
                        encoderInputBuffer?.clear()
                        encoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    }
                    
                    // Process remaining encoded data
                    while (true) {
                        val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                        if (outputBufferIndex >= 0) {
                            val encoderOutputBuffer = encoder.getOutputBuffer(outputBufferIndex)
                            
                            if (bufferInfo.size > 0 && muxerStarted) {
                                encoderOutputBuffer?.position(bufferInfo.offset)
                                encoderOutputBuffer?.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(muxerTrackIndex, encoderOutputBuffer!!, bufferInfo)
                            }
                            
                            encoder.releaseOutputBuffer(outputBufferIndex, false)
                            
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                break
                            }
                        }
                    }
                    
                } else {
                    // Fallback: Direct copy without re-encoding (preserves original quality)
                    Log.d(TAG, "Using direct copy to preserve original audio quality")
                    val outputFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                    muxer = MediaMuxer(outputFile.absolutePath, outputFormat)
                    
                    // Add the audio track to the muxer
                    val muxerTrackIndex = muxer.addTrack(audioFormat)
                    muxer.start()
                    
                    // Select the audio track for extraction
                    extractor.selectTrack(audioTrackIndex)
                    
                    // Copy audio data directly without re-encoding
                    val bufferInfo = MediaCodec.BufferInfo()
                    val inputBufferSize = 1024 * 1024 // 1MB buffer
                    val inputBuffer = ByteBuffer.allocate(inputBufferSize)
                    
                    var totalBytesProcessed = 0L
                    val fileSize = inputFile.length()
                    
                    while (true) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            Log.d(TAG, "End of audio data reached")
                            break
                        }
                        
                        // Write audio data directly to muxer
                        inputBuffer.position(0)
                        inputBuffer.limit(sampleSize)
                        bufferInfo.offset = 0
                        bufferInfo.size = sampleSize
                        bufferInfo.presentationTimeUs = extractor.sampleTime
                        bufferInfo.flags = extractor.sampleFlags
                        muxer.writeSampleData(muxerTrackIndex, inputBuffer, bufferInfo)
                        
                        extractor.advance()
                        totalBytesProcessed += sampleSize
                        
                        // Log progress every 10%
                        val progress = (totalBytesProcessed * 100) / fileSize
                        if (progress % 10 == 0L && progress > 0) {
                            Log.d(TAG, "Direct copy progress: $progress%")
                        }
                    }
                }
                
                Log.d(TAG, "Audio conversion completed successfully")
                Log.d(TAG, "Output file size: ${outputFile.length()} bytes")
                
                return true
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during audio conversion", e)
                return false
            } finally {
                try {
                    extractor?.release()
                    encoder?.stop()
                    encoder?.release()
                    muxer?.stop()
                    muxer?.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing media resources", e)
                }
            }
        }
        
        /**
         * Check if a file needs re-muxing (is a DASH m4a file)
         * @param file The file to check
         * @param isAudioOnlyDownload true if this is for audio-only download, false if it's for video merging
         * @return true if the file likely needs re-muxing
         */
        fun needsRemuxing(file: File, isAudioOnlyDownload: Boolean = false): Boolean {
            if (!file.exists()) return false
            
            val extension = file.extension.lowercase()
            
            // Check if it's an audio file
            if (extension !in listOf("m4a", "mp4", "aac", "opus", "ogg", "flac")) {
                return false
            }
            
            // Different logic based on use case
            if (isAudioOnlyDownload) {
                // For audio-only downloads we want the final file to be aac. 
                // only convert DASH containers and problematic formats to aac.
                return isDASHContainer(file) || 
                       extension in listOf("opus", "ogg", "flac") ||  // Convert unsupported formats
                       (extension == "m4a" && isDASHContainer(file))  // Only convert DASH m4a files
            } else {
                // For video merging, only convert files that are actually problematic
                // We want to preserve original format for merging
                try {
                    val extractor = MediaExtractor()
                    extractor.setDataSource(file.absolutePath)
                    
                    var hasAudioTrack = false
                    var needsConversion = false
                    
                    for (i in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(i)
                        val mime = format.getString(MediaFormat.KEY_MIME)
                        
                        if (mime?.startsWith("audio/") == true) {
                            hasAudioTrack = true
                            
                            // Only convert formats that are truly problematic for merging
                            when {
                                mime.contains("opus") -> needsConversion = true
                                mime.contains("vorbis") -> needsConversion = true
                                mime.contains("flac") -> needsConversion = true
                                // DASH files might need conversion for streaming issues
                                isDASHContainer(file) -> needsConversion = true
                            }
                        }
                    }
                    
                    extractor.release()
                    
                    // Only convert if we found a problematic format
                    return hasAudioTrack && needsConversion
                    
                } catch (e: Exception) {
                    Log.w(TAG, "Could not analyze file for remuxing: ${file.name}", e)
                    // If we can't analyze, don't convert for video merging (preserve original)
                    return false
                }
            }
        }
        
        /**
         * Check if a file is a DASH container by examining its structure
         */
        private fun isDASHContainer(file: File): Boolean {
            try {
                // Check file size - DASH files are often smaller due to streaming optimization
                val fileSize = file.length()
                if (fileSize < 1024) return false // Too small to be a real audio file
                
                // Try to read the file header to detect DASH signatures
                val inputStream = file.inputStream()
                val header = ByteArray(32)
                val bytesRead = inputStream.read(header)
                inputStream.close()
                
                if (bytesRead < 8) return false
                
                // Check for MP4/M4A container signatures
                val headerString = String(header, 0, bytesRead)
                
                // Look for DASH-specific patterns in the header
                // DASH files often have specific atom structures
                return headerString.contains("ftyp") && 
                       (headerString.contains("dash") || 
                        headerString.contains("iso8") || 
                        headerString.contains("isom"))
                        
            } catch (e: Exception) {
                Log.w(TAG, "Could not analyze file for DASH container: ${file.name}", e)
                return false
            }
        }
        
        /**
         * Create a temporary file for re-muxing
         * @param originalFile The original file
         * @return A temporary M4A file
         */
        fun createTempFile(originalFile: File): File {
            val tempDir = File(originalFile.parent, "temp")
            if (!tempDir.exists()) {
                tempDir.mkdirs()
            }
            
            val baseName = originalFile.nameWithoutExtension
            val tempFile = File(tempDir, "${baseName}_converted.m4a")
            
            return tempFile
        }
    }
}
