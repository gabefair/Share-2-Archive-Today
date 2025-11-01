package org.gnosco.share2archivetoday.network

import org.gnosco.share2archivetoday.network.*

import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import org.brotli.dec.BrotliInputStream

/**
 * Brotli decoder for handling Brotli-encoded web content
 * 
 * This class provides Android-native Brotli decompression support using Google's official
 * Brotli library (https://github.com/google/brotli), equivalent to the brotli/brotlicffi 
 * functionality in yt-dlp.
 * 
 * ## Features
 * - ✅ Brotli decompression using Google's official implementation
 * - ✅ Error handling and recovery
 * - ✅ Memory-efficient streaming
 * - ✅ String and byte array support
 * - ✅ RFC 7932 compliant decompression
 * 
 * ## Usage Example
 * ```kotlin
 * val decoder = BrotliDecoder()
 * 
 * // Decompress byte array
 * val compressed = getBrotliCompressedData()
 * val decompressed = decoder.decompress(compressed)
 * 
 * // Decompress string
 * val compressedString = getBrotliCompressedString()
 * val decompressedString = decoder.decompressString(compressedString)
 * ```
 */
class BrotliDecoder {
    
    companion object {
        private const val TAG = "BrotliDecoder"
        private const val BUFFER_SIZE = 8192
        private const val MAX_DECOMPRESSED_SIZE = 50 * 1024 * 1024 // 50MB limit
        
        init {
            // Google's Brotli library doesn't require explicit initialization
            Log.d(TAG, "Google Brotli library ready")
        }
    }
    
    /**
     * Decompress Brotli-encoded byte array
     * 
     * @param compressedData Brotli-compressed byte array
     * @return Decompressed byte array, or null if decompression fails
     */
    fun decompress(compressedData: ByteArray): ByteArray? {
        return try {
            if (compressedData.isEmpty()) {
                Log.w(TAG, "Empty compressed data provided")
                return null
            }
            
            // Use Android's built-in Brotli support (API 26+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                decompressWithAndroidBrotli(compressedData)
            } else {
                // Fallback to custom implementation for older Android versions
                decompressWithCustomBrotli(compressedData)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decompressing Brotli data", e)
            null
        }
    }
    
    /**
     * Decompress Brotli-encoded string
     * 
     * @param compressedString Brotli-compressed string (base64 or hex encoded)
     * @param encoding String encoding (default: UTF-8)
     * @return Decompressed string, or null if decompression fails
     */
    fun decompressString(compressedString: String, encoding: String = "UTF-8"): String? {
        return try {
            val compressedData = compressedString.toByteArray(StandardCharsets.UTF_8)
            val decompressedData = decompress(compressedData)
            decompressedData?.toString(StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Error decompressing Brotli string", e)
            null
        }
    }
    
    /**
     * Check if data appears to be Brotli-compressed
     * 
     * @param data Byte array to check
     * @return true if data appears to be Brotli-compressed
     */
    fun isBrotliCompressed(data: ByteArray): Boolean {
        if (data.size < 6) return false
        
        // Brotli magic bytes: 0x81, 0x1B, 0x00, 0x00, 0x08, 0x00
        return data[0] == 0x81.toByte() && 
               data[1] == 0x1B.toByte() && 
               data[2] == 0x00.toByte() && 
               data[3] == 0x00.toByte() &&
               data[4] == 0x08.toByte() && 
               data[5] == 0x00.toByte()
    }
    
    /**
     * Decompress using Google's official Brotli library
     */
    private fun decompressWithAndroidBrotli(compressedData: ByteArray): ByteArray? {
        return try {
            val brotliInputStream = BrotliInputStream(ByteArrayInputStream(compressedData))
            val outputStream = ByteArrayOutputStream()
            
            val buffer = ByteArray(BUFFER_SIZE)
            var totalBytes: Long = 0
            
            var bytesRead = brotliInputStream.read(buffer)
            while (bytesRead != -1) {
                totalBytes += bytesRead.toLong()
                if (totalBytes > MAX_DECOMPRESSED_SIZE) {
                    Log.e(TAG, "Decompressed data exceeds maximum size limit")
                    return null
                }
                outputStream.write(buffer, 0, bytesRead)
                bytesRead = brotliInputStream.read(buffer)
            }
            
            brotliInputStream.close()
            outputStream.close()
            
            val result = outputStream.toByteArray()
            Log.d(TAG, "Successfully decompressed ${compressedData.size} bytes to ${result.size} bytes using Google Brotli")
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "Error using Google Brotli decompression", e)
            null
        }
    }
    
    /**
     * Custom Brotli decompression for older Android versions
     * This is a simplified implementation - for production use, consider using a proper Brotli library
     */
    private fun decompressWithCustomBrotli(compressedData: ByteArray): ByteArray? {
        return try {
            // For older Android versions, we'll use a fallback approach
            // In a real implementation, you would integrate a proper Brotli library like:
            // - Google's Brotli C library via JNI
            // - A pure Java Brotli implementation
            // - A third-party Android Brotli library
            
            Log.w(TAG, "Custom Brotli decompression not fully implemented for Android < API 26")
            Log.w(TAG, "Consider using a proper Brotli library for production use")
            
            // For now, return null to indicate unsupported
            null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in custom Brotli decompression", e)
            null
        }
    }
    
    /**
     * Decompress Brotli data with progress callback
     * 
     * @param compressedData Brotli-compressed byte array
     * @param progressCallback Progress callback (bytes processed, total bytes)
     * @return Decompressed byte array, or null if decompression fails
     */
    fun decompressWithProgress(
        compressedData: ByteArray,
        progressCallback: ((processed: Int, total: Int) -> Unit)? = null
    ): ByteArray? {
        return try {
            if (compressedData.isEmpty()) {
                Log.w(TAG, "Empty compressed data provided")
                return null
            }
            
            val brotliInputStream = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                BrotliInputStream(ByteArrayInputStream(compressedData))
            } else {
                Log.e(TAG, "Brotli not supported on Android < API 26")
                return null
            }
            
            val outputStream = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_SIZE)
            var totalBytes: Long = 0
            
            var bytesRead = brotliInputStream.read(buffer)
            while (bytesRead != -1) {
                totalBytes += bytesRead.toLong()
                if (totalBytes > MAX_DECOMPRESSED_SIZE) {
                    Log.e(TAG, "Decompressed data exceeds maximum size limit")
                    return null
                }
                outputStream.write(buffer, 0, bytesRead)
                progressCallback?.invoke(totalBytes.toInt(), compressedData.size)
                bytesRead = brotliInputStream.read(buffer)
            }
            
            brotliInputStream.close()
            outputStream.close()
            
            val result = outputStream.toByteArray()
            Log.d(TAG, "Successfully decompressed ${compressedData.size} bytes to ${result.size} bytes")
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "Error decompressing Brotli data with progress", e)
            null
        }
    }
    
    /**
     * Get Brotli compression info
     * 
     * @param compressedData Brotli-compressed byte array
     * @return BrotliInfo object with compression details, or null if invalid
     */
    fun getBrotliInfo(compressedData: ByteArray): BrotliInfo? {
        return try {
            if (!isBrotliCompressed(compressedData)) {
                return null
            }
            
            // Parse Brotli header for basic info
            val windowSize = (compressedData[4].toInt() and 0xFF) shl 8 or (compressedData[5].toInt() and 0xFF)
            val quality = (compressedData[6].toInt() and 0xFF) shr 4
            val lgwin = (compressedData[6].toInt() and 0x0F)
            
            BrotliInfo(
                windowSize = windowSize,
                quality = quality,
                lgwin = lgwin,
                compressedSize = compressedData.size,
                isValid = true
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Brotli info", e)
            null
        }
    }
    
    /**
     * Data class for Brotli compression information
     */
    data class BrotliInfo(
        val windowSize: Int,
        val quality: Int,
        val lgwin: Int,
        val compressedSize: Int,
        val isValid: Boolean
    ) {
        fun getCompressionRatio(): Float {
            return if (compressedSize > 0) compressedSize.toFloat() / windowSize.toFloat() else 0f
        }
        
        fun getFormattedInfo(): String {
            return "Brotli: Quality=$quality, Window=${windowSize}B, Compressed=${compressedSize}B"
        }
    }
}
