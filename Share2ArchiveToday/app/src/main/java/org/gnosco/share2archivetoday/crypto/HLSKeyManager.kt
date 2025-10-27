package org.gnosco.share2archivetoday.crypto

import org.gnosco.share2archivetoday.crypto.*

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom

/**
 * Manages key and IV generation/fetching for HLS streams
 */
class HLSKeyManager {
    
    companion object {
        private const val TAG = "HLSKeyManager"
        private const val IV_SIZE = 16
    }
    
    private val secureRandom = SecureRandom()
    
    /**
     * Generate IV from HLS sequence number
     * 
     * @param sequenceNumber HLS sequence number
     * @return Generated IV (16 bytes)
     */
    fun generateSequenceIV(sequenceNumber: Long): ByteArray {
        val iv = ByteArray(IV_SIZE)
        val buffer = ByteBuffer.wrap(iv)
        buffer.order(ByteOrder.BIG_ENDIAN)
        buffer.putLong(8, sequenceNumber) // Put sequence in last 8 bytes
        return iv
    }
    
    /**
     * Generate random IV
     * 
     * @return Random IV (16 bytes)
     */
    fun generateRandomIV(): ByteArray {
        val iv = ByteArray(IV_SIZE)
        secureRandom.nextBytes(iv)
        return iv
    }
    
    /**
     * Generate random key
     * 
     * @param keySize Key size in bytes (default 16 for AES-128)
     * @return Random key
     */
    fun generateRandomKey(keySize: Int = 16): ByteArray {
        val key = ByteArray(keySize)
        secureRandom.nextBytes(key)
        return key
    }
    
    /**
     * Fetch key from URI
     * 
     * @param keyUri Key URI
     * @return Key data, or null if fetching fails
     */
    suspend fun fetchKeyFromURI(keyUri: String): ByteArray? {
        return try {
            // This would typically use an HTTP client to fetch the key
            // For now, we'll assume it's a base64-encoded key
            if (keyUri.startsWith("data:")) {
                val base64Key = keyUri.substringAfter("base64,")
                android.util.Base64.decode(base64Key, android.util.Base64.DEFAULT)
            } else {
                // In a real implementation, you would fetch from the URI
                Log.w(TAG, "Key URI fetching not implemented: $keyUri")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching key from URI: $keyUri", e)
            null
        }
    }
    
    /**
     * Fetch IV from URI
     * 
     * @param ivUri IV URI
     * @return IV data, or null if fetching fails
     */
    suspend fun fetchIVFromURI(ivUri: String): ByteArray? {
        return try {
            // This would typically use an HTTP client to fetch the IV
            // For now, we'll assume it's a base64-encoded IV
            if (ivUri.startsWith("data:")) {
                val base64IV = ivUri.substringAfter("base64,")
                android.util.Base64.decode(base64IV, android.util.Base64.DEFAULT)
            } else {
                // In a real implementation, you would fetch from the URI
                Log.w(TAG, "IV URI fetching not implemented: $ivUri")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching IV from URI: $ivUri", e)
            null
        }
    }
    
    /**
     * Parse IV from hex string
     * 
     * @param ivHex IV in hex format (e.g., "0x1234567890ABCDEF...")
     * @return IV as byte array
     */
    fun parseIVFromHex(ivHex: String): ByteArray? {
        return try {
            val cleanHex = ivHex.removePrefix("0x").removePrefix("0X")
            cleanHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing IV from hex: $ivHex", e)
            null
        }
    }
    
    /**
     * Parse key from hex string
     * 
     * @param keyHex Key in hex format
     * @return Key as byte array
     */
    fun parseKeyFromHex(keyHex: String): ByteArray? {
        return try {
            val cleanHex = keyHex.removePrefix("0x").removePrefix("0X")
            cleanHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing key from hex: $keyHex", e)
            null
        }
    }
    
    /**
     * Convert byte array to hex string
     * 
     * @param bytes Byte array to convert
     * @return Hex string representation
     */
    fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Validate key size
     * 
     * @param key Key to validate
     * @param expectedSize Expected key size in bytes
     * @return true if key is valid
     */
    fun validateKeySize(key: ByteArray, expectedSize: Int = 16): Boolean {
        return key.size == expectedSize
    }
    
    /**
     * Validate IV size
     * 
     * @param iv IV to validate
     * @param expectedSize Expected IV size in bytes
     * @return true if IV is valid
     */
    fun validateIVSize(iv: ByteArray, expectedSize: Int = IV_SIZE): Boolean {
        return iv.size == expectedSize
    }
}

