package org.gnosco.share2archivetoday.debug

import org.gnosco.share2archivetoday.download.PythonVideoDownloader
import org.gnosco.share2archivetoday.crypto.*
import org.gnosco.share2archivetoday.network.*
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*

/**
 * Test class for advanced yt-dlp features
 * 
 * This class demonstrates the usage of Brotli, WebSocket, and AES-128 HLS features
 * that have been implemented to match yt-dlp's optional dependencies.
 */
class AdvancedFeaturesTest(private val context: Context) {
    
    companion object {
        private const val TAG = "AdvancedFeaturesTest"
    }
    
    private val videoDownloader = PythonVideoDownloader(context)
    private val brotliDecoder = BrotliDecoder()
    private val webSocketManager = WebSocketManager()
    private val aesHLSDecryptor = AESHLSDecryptor()
    
    /**
     * Test Brotli decompression functionality
     */
    fun testBrotliDecompression() {
        Log.d(TAG, "Testing Brotli decompression...")
        
        try {
            // Test with sample data (in real usage, this would come from web responses)
            val sampleData = "Hello, World! This is a test string for Brotli compression.".toByteArray()
            
            // Check if data appears to be Brotli compressed
            val isCompressed = brotliDecoder.isBrotliCompressed(sampleData)
            Log.d(TAG, "Is sample data Brotli compressed: $isCompressed")
            
            // Test decompression with progress
            val decompressedData = brotliDecoder.decompressWithProgress(sampleData) { processed, total ->
                Log.d(TAG, "Brotli decompression progress: $processed/$total bytes")
            }
            
            if (decompressedData != null) {
                Log.d(TAG, "Brotli decompression successful: ${decompressedData.size} bytes")
            } else {
                Log.w(TAG, "Brotli decompression returned null (expected for non-compressed data)")
            }
            
            // Test Brotli info extraction
            val brotliInfo = brotliDecoder.getBrotliInfo(sampleData)
            if (brotliInfo != null) {
                Log.d(TAG, "Brotli info: ${brotliInfo.getFormattedInfo()}")
            } else {
                Log.d(TAG, "No Brotli info available (expected for non-compressed data)")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error testing Brotli decompression", e)
        }
    }
    
    /**
     * Test WebSocket functionality
     */
    fun testWebSocketConnection() {
        Log.d(TAG, "Testing WebSocket connection...")
        
        try {
            val testUrl = "wss://echo.websocket.org"
            
            val listener = object : WebSocketClient.WebSocketListener {
                override fun onConnected() {
                    Log.d(TAG, "WebSocket connected successfully")
                }
                
                override fun onDisconnected() {
                    Log.d(TAG, "WebSocket disconnected")
                }
                
                override fun onMessage(text: String) {
                    Log.d(TAG, "Received text message: $text")
                }
                
                override fun onMessage(bytes: ByteArray) {
                    Log.d(TAG, "Received binary message: ${bytes.size} bytes")
                }
                
                override fun onError(error: Throwable) {
                    Log.e(TAG, "WebSocket error", error)
                }
            }
            
            // Connect to test WebSocket
            webSocketManager.connect(testUrl, listener)
            
            // Send test message
            webSocketManager.sendMessage(testUrl, "Hello WebSocket!")
            
            // Wait a bit then disconnect
            GlobalScope.launch {
                delay(5000)
                webSocketManager.disconnect(testUrl)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error testing WebSocket", e)
        }
    }
    
    /**
     * Test AES-128 HLS decryption
     */
    fun testAESHLSDecryption() {
        Log.d(TAG, "Testing AES-128 HLS decryption...")
        
        try {
            // Test with sample encrypted data
            val sampleKey = ByteArray(16) { it.toByte() } // 16-byte key
            val sampleIV = ByteArray(16) { (it + 1).toByte() } // 16-byte IV
            val sampleData = "This is sample data for AES encryption testing.".toByteArray()
            
            // Test AES-128-CBC decryption
            val decryptedCBC = aesHLSDecryptor.decryptHLS(sampleData, sampleKey, sampleIV, "AES-128-CBC")
            if (decryptedCBC != null) {
                Log.d(TAG, "AES-128-CBC decryption successful: ${decryptedCBC.size} bytes")
            } else {
                Log.w(TAG, "AES-128-CBC decryption failed (expected for non-encrypted data)")
            }
            
            // Test AES-128-CTR decryption
            val decryptedCTR = aesHLSDecryptor.decryptHLS(sampleData, sampleKey, sampleIV, "AES-128-CTR")
            if (decryptedCTR != null) {
                Log.d(TAG, "AES-128-CTR decryption successful: ${decryptedCTR.size} bytes")
            } else {
                Log.w(TAG, "AES-128-CTR decryption failed (expected for non-encrypted data)")
            }
            
            // Test sequence-based IV generation
            val sequenceIV = aesHLSDecryptor.decryptHLSWithSequence(sampleData, sampleKey, 12345L, "AES-128-CBC")
            if (sequenceIV != null) {
                Log.d(TAG, "Sequence-based IV decryption successful: ${sequenceIV.size} bytes")
            } else {
                Log.w(TAG, "Sequence-based IV decryption failed (expected for non-encrypted data)")
            }
            
            // Test key derivation
            val password = "test_password".toByteArray()
            val salt = "test_salt".toByteArray()
            val derivedKey = aesHLSDecryptor.deriveKeyPBKDF2(password, salt, 1000)
            if (derivedKey != null) {
                Log.d(TAG, "PBKDF2 key derivation successful: ${derivedKey.size} bytes")
            } else {
                Log.e(TAG, "PBKDF2 key derivation failed")
            }
            
            // Test HLS manifest parsing
            val sampleManifest = """
                #EXTM3U
                #EXT-X-VERSION:3
                #EXT-X-KEY:METHOD=AES-128,URI="https://example.com/key",IV=0x1234567890abcdef
                #EXTINF:10.0,
                segment1.ts
                #EXTINF:10.0,
                segment2.ts
                #EXT-X-ENDLIST
            """.trimIndent()
            
            val encryptionInfo = aesHLSDecryptor.parseHLSEncryptionInfo(sampleManifest)
            Log.d(TAG, "Parsed ${encryptionInfo.size} HLS encryption info entries")
            encryptionInfo.forEach { info ->
                Log.d(TAG, "HLS Info: sequence=${info.sequence}, method=${info.method}, keyUri=${info.keyUri}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error testing AES-128 HLS decryption", e)
        }
    }
    
    /**
     * Test video download with advanced features
     * 
     * Note: Advanced features like Brotli, WebSocket, and HLS are now handled
     * automatically by yt-dlp in PythonVideoDownloader. No special methods needed!
     */
    suspend fun testAdvancedVideoDownload() {
        Log.d(TAG, "Testing video download (advanced features handled by yt-dlp)...")
        
        try {
            val outputDir = context.getExternalFilesDir(null)?.absolutePath ?: return
            
            // Note: YouTube downloads are blocked for Play Store compliance
            // Use a different test site (e.g., Vimeo, Dailymotion)
            val testUrl = "https://vimeo.com/148751763" // Public Vimeo test video
            
            Log.d(TAG, "Testing video download with automatic Brotli/HLS support...")
            Log.d(TAG, "Note: yt-dlp handles Brotli, WebSocket, and HLS encryption automatically")
            
            // PythonVideoDownloader automatically handles:
            // - Brotli decompression (via brotli Python package)
            // - WebSocket streams (via websockets Python package)
            // - HLS AES-128 decryption (via pycryptodomex Python package)
            val result = videoDownloader.downloadVideo(
                url = testUrl,
                outputDir = outputDir,
                quality = "best",
                progressCallback = { progressInfo ->
                    when (progressInfo) {
                        is PythonVideoDownloader.ProgressInfo.Downloading -> {
                            Log.d(TAG, "Download progress: ${progressInfo.percentage}% - ${progressInfo.getFormattedSpeed()}")
                        }
                        is PythonVideoDownloader.ProgressInfo.Finished -> {
                            Log.d(TAG, "Download finished: ${progressInfo.filename}")
                        }
                        is PythonVideoDownloader.ProgressInfo.Error -> {
                            Log.e(TAG, "Download error: ${progressInfo.error}")
                        }
                        else -> {}
                    }
                }
            )

            Log.d(TAG,"videoDownloader Results: ${result}")
            
            if (result.success) {
                Log.d(TAG, "Video download successful: ${result.filePath}")
                Log.d(TAG, "Title: result.title")
                Log.d(TAG, "File size: ${result.fileSize} bytes")
            } else {
                Log.w(TAG, "Video download failed: ${result.error}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error testing video download", e)
        }
    }
    
    /**
     * Run all advanced feature tests
     */
    suspend fun runAllTests() {
        Log.d(TAG, "Starting comprehensive advanced features test...")
        
        try {
            // Test individual components
            testBrotliDecompression()
            testWebSocketConnection()
            testAESHLSDecryption()
            
            // Test integrated features
            testAdvancedVideoDownload()
            
            Log.d(TAG, "All advanced features tests completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error running advanced features tests", e)
        } finally {
            // Cleanup
            webSocketManager.cleanup()
            videoDownloader.cleanup()
        }
    }
    
    /**
     * Test specific feature based on name
     */
    suspend fun testFeature(featureName: String) {
        when (featureName.lowercase()) {
            "brotli" -> testBrotliDecompression()
            "websocket" -> testWebSocketConnection()
            "aes" -> testAESHLSDecryption()
            "hls" -> testAESHLSDecryption()
            "video" -> testAdvancedVideoDownload()
            "all" -> runAllTests()
            else -> Log.w(TAG, "Unknown feature: $featureName")
        }
    }
}
