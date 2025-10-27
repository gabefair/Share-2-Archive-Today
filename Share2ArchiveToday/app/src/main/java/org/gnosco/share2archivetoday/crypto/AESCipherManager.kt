package org.gnosco.share2archivetoday.crypto

import org.gnosco.share2archivetoday.crypto.*

import android.util.Log
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Manages core AES encryption and decryption operations
 */
class AESCipherManager {
    
    companion object {
        private const val TAG = "AESCipherManager"
        const val AES_BLOCK_SIZE = 16
        const val AES_KEY_SIZE = 16
        const val IV_SIZE = 16
    }
    
    /**
     * Decrypt data using AES-128-CBC
     * 
     * @param encryptedData Data to decrypt
     * @param key AES-128 key (16 bytes)
     * @param iv Initialization vector (16 bytes)
     * @return Decrypted data, or null if decryption fails
     */
    fun decryptAES128CBC(encryptedData: ByteArray, key: ByteArray, iv: ByteArray): ByteArray? {
        return try {
            if (!validateInputs(encryptedData, key, iv)) {
                return null
            }
            
            val secretKey = SecretKeySpec(key, "AES")
            val ivSpec = IvParameterSpec(iv)
            
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            
            val decryptedData = cipher.doFinal(encryptedData)
            Log.d(TAG, "Successfully decrypted ${encryptedData.size} bytes using AES-128-CBC")
            decryptedData
            
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting with AES-128-CBC", e)
            null
        }
    }
    
    /**
     * Decrypt data using AES-128-CTR
     * 
     * @param encryptedData Data to decrypt
     * @param key AES-128 key (16 bytes)
     * @param iv Initialization vector (16 bytes)
     * @return Decrypted data, or null if decryption fails
     */
    fun decryptAES128CTR(encryptedData: ByteArray, key: ByteArray, iv: ByteArray): ByteArray? {
        return try {
            if (!validateInputs(encryptedData, key, iv)) {
                return null
            }
            
            val secretKey = SecretKeySpec(key, "AES")
            val ivSpec = IvParameterSpec(iv)
            
            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            
            val decryptedData = cipher.doFinal(encryptedData)
            Log.d(TAG, "Successfully decrypted ${encryptedData.size} bytes using AES-128-CTR")
            decryptedData
            
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting with AES-128-CTR", e)
            null
        }
    }
    
    /**
     * Encrypt data using AES-128-CBC
     * 
     * @param plainData Data to encrypt
     * @param key AES-128 key (16 bytes)
     * @param iv Initialization vector (16 bytes)
     * @return Encrypted data, or null if encryption fails
     */
    fun encryptAES128CBC(plainData: ByteArray, key: ByteArray, iv: ByteArray): ByteArray? {
        return try {
            if (!validateInputs(plainData, key, iv)) {
                return null
            }
            
            val secretKey = SecretKeySpec(key, "AES")
            val ivSpec = IvParameterSpec(iv)
            
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
            
            val encryptedData = cipher.doFinal(plainData)
            Log.d(TAG, "Successfully encrypted ${plainData.size} bytes using AES-128-CBC")
            encryptedData
            
        } catch (e: Exception) {
            Log.e(TAG, "Error encrypting with AES-128-CBC", e)
            null
        }
    }
    
    /**
     * Encrypt data using AES-128-CTR
     * 
     * @param plainData Data to encrypt
     * @param key AES-128 key (16 bytes)
     * @param iv Initialization vector (16 bytes)
     * @return Encrypted data, or null if encryption fails
     */
    fun encryptAES128CTR(plainData: ByteArray, key: ByteArray, iv: ByteArray): ByteArray? {
        return try {
            if (!validateInputs(plainData, key, iv)) {
                return null
            }
            
            val secretKey = SecretKeySpec(key, "AES")
            val ivSpec = IvParameterSpec(iv)
            
            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
            
            val encryptedData = cipher.doFinal(plainData)
            Log.d(TAG, "Successfully encrypted ${plainData.size} bytes using AES-128-CTR")
            encryptedData
            
        } catch (e: Exception) {
            Log.e(TAG, "Error encrypting with AES-128-CTR", e)
            null
        }
    }
    
    /**
     * Validate encryption/decryption inputs
     */
    private fun validateInputs(data: ByteArray, key: ByteArray, iv: ByteArray): Boolean {
        if (data.isEmpty()) {
            Log.w(TAG, "Empty data provided")
            return false
        }
        
        if (key.size != AES_KEY_SIZE) {
            Log.e(TAG, "Invalid key size: ${key.size} bytes (expected $AES_KEY_SIZE)")
            return false
        }
        
        if (iv.size != IV_SIZE) {
            Log.e(TAG, "Invalid IV size: ${iv.size} bytes (expected $IV_SIZE)")
            return false
        }
        
        return true
    }
    
    /**
     * Check if data appears to be AES-encrypted
     * 
     * @param data Data to check
     * @return true if data appears to be AES-encrypted
     */
    fun isAESEncrypted(data: ByteArray): Boolean {
        if (data.size < AES_BLOCK_SIZE) return false
        
        // Check if data size is multiple of block size (for CBC)
        // or has reasonable size for CTR
        return data.size % AES_BLOCK_SIZE == 0 || data.size >= AES_BLOCK_SIZE
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
        return try {
            val keySpec = javax.crypto.spec.PBEKeySpec(
                password.toString(Charsets.UTF_8).toCharArray(),
                salt,
                iterations,
                AES_KEY_SIZE * 8
            )
            
            val keyFactory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val secretKey = keyFactory.generateSecret(keySpec)
            secretKey.encoded
            
        } catch (e: Exception) {
            Log.e(TAG, "Error deriving key with PBKDF2", e)
            null
        }
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
        return try {
            // HKDF implementation would go here
            // For now, we'll use a simplified approach
            Log.w(TAG, "HKDF key derivation not fully implemented")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error deriving key with HKDF", e)
            null
        }
    }
}

