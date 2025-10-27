package org.gnosco.share2archivetoday.crypto

import org.gnosco.share2archivetoday.crypto.*

import android.util.Log

/**
 * Parses HLS manifests to extract encryption information
 */
class HLSManifestParser {
    
    companion object {
        private const val TAG = "HLSManifestParser"
    }
    
    /**
     * Get encryption info from HLS manifest
     * 
     * @param manifest HLS manifest content
     * @return List of encryption info for each segment
     */
    fun parseHLSEncryptionInfo(manifest: String): List<HLSEncryptionInfo> {
        val encryptionInfo = mutableListOf<HLSEncryptionInfo>()
        
        try {
            val lines = manifest.split("\n")
            var currentSequence = 0L
            var currentKeyUri: String? = null
            var currentIV: String? = null
            var currentMethod = "AES-128-CBC"
            
            for (line in lines) {
                when {
                    line.startsWith("#EXT-X-KEY:") -> {
                        val keyInfo = parseKeyLine(line)
                        currentKeyUri = keyInfo.uri
                        currentMethod = keyInfo.method
                        currentIV = keyInfo.iv
                    }
                    line.startsWith("#EXTINF:") -> {
                        // This is a segment info line
                    }
                    line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> {
                        currentSequence = line.substringAfter(":").trim().toLongOrNull() ?: 0L
                    }
                    !line.startsWith("#") && line.isNotBlank() -> {
                        // This is a segment URI
                        encryptionInfo.add(
                            HLSEncryptionInfo(
                                sequence = currentSequence,
                                uri = line.trim(),
                                keyUri = currentKeyUri,
                                iv = currentIV,
                                method = currentMethod
                            )
                        )
                        currentSequence++
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing HLS encryption info", e)
        }
        
        return encryptionInfo
    }
    
    /**
     * Parse EXT-X-KEY line from HLS manifest
     * 
     * @param line The #EXT-X-KEY line
     * @return Parsed key information
     */
    fun parseKeyLine(line: String): KeyInfo {
        val attributes = mutableMapOf<String, String>()
        
        // Remove #EXT-X-KEY: prefix
        val content = line.substringAfter("#EXT-X-KEY:")
        
        // Parse attributes
        val parts = content.split(",")
        for (part in parts) {
            val keyValue = part.split("=", limit = 2)
            if (keyValue.size == 2) {
                val key = keyValue[0].trim()
                val value = keyValue[1].trim().removeSurrounding("\"")
                attributes[key] = value
            }
        }
        
        return KeyInfo(
            method = attributes["METHOD"] ?: "AES-128-CBC",
            uri = attributes["URI"],
            iv = attributes["IV"]
        )
    }
    
    /**
     * Parse master playlist to find variant streams
     * 
     * @param manifest Master playlist content
     * @return List of variant stream URIs
     */
    fun parseVariantStreams(manifest: String): List<VariantStreamInfo> {
        val variants = mutableListOf<VariantStreamInfo>()
        
        try {
            val lines = manifest.split("\n")
            var currentBandwidth: Long? = null
            var currentResolution: String? = null
            var currentCodecs: String? = null
            
            for (i in lines.indices) {
                val line = lines[i]
                
                if (line.startsWith("#EXT-X-STREAM-INF:")) {
                    val streamInfo = parseStreamInfoLine(line)
                    currentBandwidth = streamInfo.bandwidth
                    currentResolution = streamInfo.resolution
                    currentCodecs = streamInfo.codecs
                    
                    // Next line should be the URI
                    if (i + 1 < lines.size) {
                        val uri = lines[i + 1].trim()
                        if (!uri.startsWith("#") && uri.isNotBlank()) {
                            variants.add(
                                VariantStreamInfo(
                                    uri = uri,
                                    bandwidth = currentBandwidth,
                                    resolution = currentResolution,
                                    codecs = currentCodecs
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing variant streams", e)
        }
        
        return variants
    }
    
    /**
     * Parse EXT-X-STREAM-INF line
     */
    private fun parseStreamInfoLine(line: String): StreamInfo {
        val attributes = mutableMapOf<String, String>()
        
        // Remove #EXT-X-STREAM-INF: prefix
        val content = line.substringAfter("#EXT-X-STREAM-INF:")
        
        // Parse attributes
        val parts = content.split(",")
        for (part in parts) {
            val keyValue = part.split("=", limit = 2)
            if (keyValue.size == 2) {
                val key = keyValue[0].trim()
                val value = keyValue[1].trim().removeSurrounding("\"")
                attributes[key] = value
            }
        }
        
        return StreamInfo(
            bandwidth = attributes["BANDWIDTH"]?.toLongOrNull(),
            resolution = attributes["RESOLUTION"],
            codecs = attributes["CODECS"]
        )
    }
    
    /**
     * Check if manifest is a master playlist
     * 
     * @param manifest Manifest content
     * @return true if it's a master playlist
     */
    fun isMasterPlaylist(manifest: String): Boolean {
        return manifest.contains("#EXT-X-STREAM-INF:")
    }
    
    /**
     * Check if manifest has encryption
     * 
     * @param manifest Manifest content
     * @return true if manifest has encryption
     */
    fun hasEncryption(manifest: String): Boolean {
        return manifest.contains("#EXT-X-KEY:")
    }
    
    /**
     * Get total duration from manifest
     * 
     * @param manifest Manifest content
     * @return Total duration in seconds
     */
    fun getTotalDuration(manifest: String): Double {
        var totalDuration = 0.0
        
        try {
            val lines = manifest.split("\n")
            for (line in lines) {
                if (line.startsWith("#EXTINF:")) {
                    val durationStr = line.substringAfter("#EXTINF:")
                        .substringBefore(",")
                        .trim()
                    val duration = durationStr.toDoubleOrNull() ?: 0.0
                    totalDuration += duration
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting total duration", e)
        }
        
        return totalDuration
    }
    
    /**
     * Data class for HLS encryption information
     */
    data class HLSEncryptionInfo(
        val sequence: Long,
        val uri: String,
        val keyUri: String?,
        val iv: String?,
        val method: String
    )
    
    /**
     * Data class for key information
     */
    data class KeyInfo(
        val method: String,
        val uri: String?,
        val iv: String?
    )
    
    /**
     * Data class for variant stream information
     */
    data class VariantStreamInfo(
        val uri: String,
        val bandwidth: Long?,
        val resolution: String?,
        val codecs: String?
    )
    
    /**
     * Data class for stream information
     */
    private data class StreamInfo(
        val bandwidth: Long?,
        val resolution: String?,
        val codecs: String?
    )
}

