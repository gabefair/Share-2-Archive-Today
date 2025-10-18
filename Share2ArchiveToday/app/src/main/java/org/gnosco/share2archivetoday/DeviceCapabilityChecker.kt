package org.gnosco.share2archivetoday

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Checks device capabilities for feature availability
 * Determines which features should be available based on device architecture and capabilities
 */
class DeviceCapabilityChecker(private val context: Context) {
    
    companion object {
        private const val TAG = "DeviceCapabilityChecker"
        
        // Minimum requirements for video download feature
        private const val MIN_RAM_MB = 192  // 192MB RAM minimum
        private const val MIN_STORAGE_MB = 1024  // 1GB free storage minimum
        
        // Supported 64-bit architectures
        private val SUPPORTED_64BIT_ABIS = setOf(
            "arm64-v8a",  // ARM 64-bit (most common)
            "x86_64",     // Intel/AMD 64-bit
            "riscv64"     // RISC-V 64-bit (newer architecture)
        )
    }
    
    /**
     * Check if device supports video download feature
     * Requirements:
     * - 64-bit architecture (arm64-v8a, x86_64, or riscv64)
     * - Sufficient RAM (192MB+)
     * - Sufficient storage (1GB+ free)
     * - Android 9+ (API 28+)
     */
    fun supportsVideoDownload(): Boolean {
        return try {
            val is64Bit = is64BitArchitecture()
            val hasEnoughRAM = hasEnoughRAM()
            val hasEnoughStorage = hasEnoughStorage()
            val isAndroid9Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
            
            val supports = is64Bit && hasEnoughRAM && hasEnoughStorage && isAndroid9Plus
            
            Log.d(TAG, "Video download support check:")
            Log.d(TAG, "  64-bit architecture: $is64Bit")
            Log.d(TAG, "  Sufficient RAM (${getRAMMB()}MB >= ${MIN_RAM_MB}MB): $hasEnoughRAM")
            Log.d(TAG, "  Sufficient storage (${getFreeStorageMB()}MB >= ${MIN_STORAGE_MB}MB): $hasEnoughStorage")
            Log.d(TAG, "  Android 9+ (API ${Build.VERSION.SDK_INT} >= ${Build.VERSION_CODES.P}): $isAndroid9Plus")
            Log.d(TAG, "  Overall support: $supports")
            
            supports
        } catch (e: Exception) {
            Log.e(TAG, "Error checking video download support", e)
            false
        }
    }
    
    /**
     * Check if device has 64-bit architecture
     * Supports arm64-v8a, x86_64, and riscv64
     */
    private fun is64BitArchitecture(): Boolean {
        return try {
            val supportedAbis = Build.SUPPORTED_ABIS
            val has64BitAbi = supportedAbis.any { abi -> 
                SUPPORTED_64BIT_ABIS.contains(abi)
            }
            Log.d(TAG, "Supported ABIs: ${supportedAbis.joinToString(", ")}")
            Log.d(TAG, "Has 64-bit ABI: $has64BitAbi")
            has64BitAbi
        } catch (e: Exception) {
            Log.e(TAG, "Error checking architecture", e)
            false
        }
    }
    
    /**
     * Check if device has sufficient RAM
     */
    private fun hasEnoughRAM(): Boolean {
        return try {
            val runtime = Runtime.getRuntime()
            val maxMemoryMB = runtime.maxMemory() / (1024 * 1024)
            val hasEnough = maxMemoryMB >= MIN_RAM_MB
            Log.d(TAG, "Max memory: ${maxMemoryMB}MB, required: ${MIN_RAM_MB}MB, sufficient: $hasEnough")
            hasEnough
        } catch (e: Exception) {
            Log.e(TAG, "Error checking RAM", e)
            false
        }
    }
    
    /**
     * Check if device has sufficient free storage
     */
    private fun hasEnoughStorage(): Boolean {
        return try {
            val freeStorageMB = getFreeStorageMB()
            val hasEnough = freeStorageMB >= MIN_STORAGE_MB
            Log.d(TAG, "Free storage: ${freeStorageMB}MB, required: ${MIN_STORAGE_MB}MB, sufficient: $hasEnough")
            hasEnough
        } catch (e: Exception) {
            Log.e(TAG, "Error checking storage", e)
            false
        }
    }
    
    /**
     * Get device RAM in MB
     */
    fun getRAMMB(): Long {
        return try {
            val runtime = Runtime.getRuntime()
            runtime.maxMemory() / (1024 * 1024)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting RAM", e)
            0L
        }
    }
    
    /**
     * Get free storage in MB
     */
    fun getFreeStorageMB(): Long {
        return try {
            val dataDir = context.filesDir
            val stat = android.os.StatFs(dataDir.absolutePath)
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            availableBytes / (1024 * 1024)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting free storage", e)
            0L
        }
    }
    
    /**
     * Get device architecture info
     */
    fun getArchitectureInfo(): String {
        return try {
            val abis = Build.SUPPORTED_ABIS.joinToString(", ")
            val primaryAbi = Build.SUPPORTED_ABIS[0]
            "$primaryAbi (supported: $abis)"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting architecture info", e)
            "Unknown"
        }
    }
    
    /**
     * Get device capability summary
     */
    fun getCapabilitySummary(): DeviceCapabilities {
        return DeviceCapabilities(
            supportsVideoDownload = supportsVideoDownload(),
            architecture = getArchitectureInfo(),
            ramMB = getRAMMB(),
            freeStorageMB = getFreeStorageMB(),
            androidVersion = Build.VERSION.SDK_INT,
            is64Bit = is64BitArchitecture()
        )
    }
    
    /**
     * Data class for device capabilities
     */
    data class DeviceCapabilities(
        val supportsVideoDownload: Boolean,
        val architecture: String,
        val ramMB: Long,
        val freeStorageMB: Long,
        val androidVersion: Int,
        val is64Bit: Boolean
    ) {
        fun getFormattedRAM(): String {
            val ramGB = ramMB / 1024.0
            return if (ramGB >= 1.0) {
                "%.1f GB".format(ramGB)
            } else {
                "$ramMB MB"
            }
        }
        
        fun getFormattedStorage(): String {
            val storageGB = freeStorageMB / 1024.0
            return if (storageGB >= 1.0) {
                "%.1f GB".format(storageGB)
            } else {
                "$freeStorageMB MB"
            }
        }
        
        fun getAndroidVersionName(): String {
            return when (androidVersion) {
                Build.VERSION_CODES.P -> "Android 9"
                Build.VERSION_CODES.Q -> "Android 10"
                Build.VERSION_CODES.R -> "Android 11"
                Build.VERSION_CODES.S -> "Android 12"
                Build.VERSION_CODES.TIRAMISU -> "Android 13"
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> "Android 14"
                else -> "Android $androidVersion"
            }
        }
    }
}
