package org.gnosco.share2archivetoday.crypto

import org.gnosco.share2archivetoday.crypto.*

import android.util.Log

/**
 * AES-128 HLS stream decryption implementation
 * 
 * This class provides Android-native AES-128 decryption for HLS streams,
 * equivalent to the pycryptodomex functionality in yt-dlp.
 * 
 * ## Features
 * - ✅ AES-128-CBC decryption
 * - ✅ AES-128-CTR decryption  
 * - ✅ PKCS7 padding support
 * - ✅ IV handling (explicit and implicit)
 * - ✅ Key derivation (PBKDF2, HKDF)
 * - ✅ HLS-specific decryption patterns
 * 
 * ## Usage Example
 * ```kotlin
 * val decryptor = AESHLSDecryptor()
 * 
 * // Decrypt HLS segment
 * val key = getHLSKey()
 * val iv = getHLSIV()
 * val encryptedData = getEncryptedSegment()
 * 
 * val decryptedData = decryptor.decryptHLS(
 *     encryptedData = encryptedData,
 *     key = key,
 *     iv = iv,
 *     method = "AES-128-CBC"
 * )
 * ```
 */
class AESHLSDecryptor {
    
    companion object {
        private const val TAG = "AESHLSDecryptor"
    }
    
    private val cipherManager = AESCipherManager()
    private val keyManager = HLSKeyManager()
    private val manifestParser = HLSManifestParser()
    
    /**
     * Decrypt HLS segment data
     * 
     * @param encryptedData Encrypted segment data
     * @param key AES-128 key (16 bytes)
     * @param iv Initialization vector (16 bytes)
     * @param method Encryption method ("AES-128-CBC" or "AES-128-CTR")
     * @return Decrypted data, or null if decryption fails
     */
    fun decryptHLS(
        encryptedData: ByteArray,
        key: ByteArray,
        iv: ByteArray,
        method: String = "AES-128-CBC"
    ): ByteArray? {
        return try {
            when (method.uppercase()) {
                "AES-128-CBC" -> cipherManager.decryptAES128CBC(encryptedData, key, iv)
                "AES-128-CTR" -> cipherManager.decryptAES128CTR(encryptedData, key, iv)
                else -> {
                    Log.e(TAG, "Unsupported encryption method: $method")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting HLS data", e)
            null
        }
    }
    
    /**
     * Decrypt HLS segment with sequence-based IV
     * 
     * @param encryptedData Encrypted segment data
     * @param key AES-128 key
     * @param sequenceNumber HLS sequence number
     * @param method Encryption method
     * @return Decrypted data, or null if decryption fails
     */
    fun decryptHLSWithSequence(
        encryptedData: ByteArray,
        key: ByteArray,
        sequenceNumber: Long,
        method: String = "AES-128-CBC"
    ): ByteArray? {
        val iv = keyManager.generateSequenceIV(sequenceNumber)
        return decryptHLS(encryptedData, key, iv, method)
    }
    
    /**
     * Decrypt HLS segment with key URI and IV URI
     * 
     * @param encryptedData Encrypted segment data
     * @param keyUri Key URI (will be fetched if needed)
     * @param ivUri IV URI (will be fetched if needed)
     * @param method Encryption method
     * @return Decrypted data, or null if decryption fails
     */
    suspend fun decryptHLSWithURIs(
        encryptedData: ByteArray,
        keyUri: String,
        ivUri: String? = null,
        method: String = "AES-128-CBC"
    ): ByteArray? {
        return try {
            // Fetch key
            val key = keyManager.fetchKeyFromURI(keyUri) ?: return null
            
            // Fetch IV or generate random
            val iv = if (ivUri != null) {
                keyManager.fetchIVFromURI(ivUri) ?: return null
            } else {
                keyManager.generateRandomIV()
            }
            
            decryptHLS(encryptedData, key, iv, method)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting HLS with URIs", e)
            null
        }
    }
    
    /**
     * Decrypt multiple HLS segments
     * 
     * @param segments List of encrypted segments
     * @param key AES-128 key
     * @param iv Initialization vector
     * @param method Encryption method
     * @return List of decrypted segments
     */
    fun decryptHLSSegments(
        segments: List<ByteArray>,
        key: ByteArray,
        iv: ByteArray,
        method: String = "AES-128-CBC"
    ): List<ByteArray> {
        return segments.mapNotNull { segment ->
            decryptHLS(segment, key, iv, method)
        }
    }
    
    /**
     * Decrypt HLS segments with sequence-based IVs
     * 
     * @param segments List of encrypted segments
     * @param key AES-128 key
     * @param startSequence Starting sequence number
     * @param method Encryption method
     * @return List of decrypted segments
     */
    fun decryptHLSSegmentsWithSequence(
        segments: List<ByteArray>,
        key: ByteArray,
        startSequence: Long,
        method: String = "AES-128-CBC"
    ): List<ByteArray> {
        return segments.mapIndexed { index, segment ->
            val sequenceNumber = startSequence + index
            decryptHLSWithSequence(segment, key, sequenceNumber, method)
        }.filterNotNull()
    }
    
    /**
     * Check if data appears to be AES-encrypted
     * 
     * @param data Data to check
     * @return true if data appears to be AES-encrypted
     */
    fun isAESEncrypted(data: ByteArray): Boolean {
        return cipherManager.isAESEncrypted(data)
    }
    
    /**
     * Get encryption info from HLS manifest
     * 
     * @param manifest HLS manifest content
     * @return List of encryption info
     */
    fun parseHLSEncryptionInfo(manifest: String): List<HLSManifestParser.HLSEncryptionInfo> {
        return manifestParser.parseHLSEncryptionInfo(manifest)
    }
    
    /**
     * Parse variant streams from master playlist
     * 
     * @param manifest Master playlist content
     * @return List of variant stream info
     */
    fun parseVariantStreams(manifest: String): List<HLSManifestParser.VariantStreamInfo> {
        return manifestParser.parseVariantStreams(manifest)
    }
    
    /**
     * Check if manifest has encryption
     * 
     * @param manifest Manifest content
     * @return true if manifest has encryption
     */
    fun hasEncryption(manifest: String): Boolean {
        return manifestParser.hasEncryption(manifest)
    }
    
    /**
     * Check if manifest is a master playlist
     * 
     * @param manifest Manifest content
     * @return true if it's a master playlist
     */
    fun isMasterPlaylist(manifest: String): Boolean {
        return manifestParser.isMasterPlaylist(manifest)
    }
    
    /**
     * Derive key using PBKDF2
     * 
     * @param password Password or master key
     * @param salt Salt for key derivation
     * @param iterations Number of iterations
     * @return Derived key
     */
    fun deriveKeyPBKDF2(password: ByteArray, salt: ByteArray, iterations: Int = 10000): ByteArray? {
        return cipherManager.deriveKeyPBKDF2(password, salt, iterations)
    }
    
    /**
     * Derive key using HKDF
     * 
     * @param inputKeyMaterial Input key material
     * @param salt Salt for key derivation
     * @param info Additional info
     * @return Derived key
     */
    fun deriveKeyHKDF(inputKeyMaterial: ByteArray, salt: ByteArray, info: ByteArray): ByteArray? {
        return cipherManager.deriveKeyHKDF(inputKeyMaterial, salt, info)
    }
    
    /**
     * Generate random IV
     * 
     * @return Random IV (16 bytes)
     */
    fun generateRandomIV(): ByteArray {
        return keyManager.generateRandomIV()
    }
    
    /**
     * Generate random key
     * 
     * @param keySize Key size in bytes
     * @return Random key
     */
    fun generateRandomKey(keySize: Int = 16): ByteArray {
        return keyManager.generateRandomKey(keySize)
    }
    
    /**
     * Parse IV from hex string
     * 
     * @param ivHex IV in hex format
     * @return IV as byte array
     */
    fun parseIVFromHex(ivHex: String): ByteArray? {
        return keyManager.parseIVFromHex(ivHex)
    }
    
    /**
     * Parse key from hex string
     * 
     * @param keyHex Key in hex format
     * @return Key as byte array
     */
    fun parseKeyFromHex(keyHex: String): ByteArray? {
        return keyManager.parseKeyFromHex(keyHex)
    }
    
    /**
     * Get the cipher manager instance
     * (for advanced use cases)
     */
    fun getCipherManager(): AESCipherManager = cipherManager
    
    /**
     * Get the key manager instance
     * (for advanced use cases)
     */
    fun getKeyManager(): HLSKeyManager = keyManager
    
    /**
     * Get the manifest parser instance
     * (for advanced use cases)
     */
    fun getManifestParser(): HLSManifestParser = manifestParser
}

// Type alias for backward compatibility (must be at top level)
typealias HLSEncryptionInfo = HLSManifestParser.HLSEncryptionInfo
