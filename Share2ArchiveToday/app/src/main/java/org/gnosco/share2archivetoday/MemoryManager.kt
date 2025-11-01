package org.gnosco.share2archivetoday

import android.content.Context
import android.os.StatFs
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel

/**
 * Memory management utility that monitors memory usage and provides disk-based caching
 * to reduce memory pressure during large video downloads
 */
class MemoryManager(private val context: Context) {
    
    companion object {
        private const val TAG = "MemoryManager"
        
        // Memory thresholds
        private const val LOW_MEMORY_THRESHOLD_MB = 50L  // Trigger warning at 50MB
        private const val CRITICAL_MEMORY_THRESHOLD_MB = 25L  // Critical at 25MB
        
        // Disk cache settings
        private const val CACHE_DIR_NAME = "video_cache"
        private const val MAX_CACHE_SIZE_MB = 500L  // Max 500MB cache
    }
    
    private val cacheDir: File by lazy {
        File(context.cacheDir, CACHE_DIR_NAME).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }
    
    /**
     * Check current memory status
     * @return MemoryStatus object with detailed memory information
     */
    fun checkMemoryStatus(): MemoryStatus {
        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L
        val maxMemory = runtime.maxMemory() / 1048576L
        val availableMemory = maxMemory - usedMemory
        val percentUsed = (usedMemory.toFloat() / maxMemory.toFloat() * 100).toInt()
        
        val status = when {
            availableMemory < CRITICAL_MEMORY_THRESHOLD_MB -> MemoryLevel.CRITICAL
            availableMemory < LOW_MEMORY_THRESHOLD_MB -> MemoryLevel.LOW
            percentUsed > 80 -> MemoryLevel.WARNING
            else -> MemoryLevel.NORMAL
        }
        
        Log.d(TAG, "Memory - Available: ${availableMemory}MB, Used: ${usedMemory}MB, Max: ${maxMemory}MB, Status: $status")
        
        return MemoryStatus(
            availableMemoryMB = availableMemory,
            usedMemoryMB = usedMemory,
            maxMemoryMB = maxMemory,
            percentUsed = percentUsed,
            level = status
        )
    }
    
    /**
     * Check if there's enough memory for download
     * @param estimatedSizeMB Estimated download size in MB
     * @return true if sufficient memory available
     */
    fun hasEnoughMemory(estimatedSizeMB: Long = 100): Boolean {
        val status = checkMemoryStatus()
        val requiredMemory = estimatedSizeMB / 4  // We need ~25% of file size in memory
        
        return status.availableMemoryMB >= requiredMemory + LOW_MEMORY_THRESHOLD_MB
    }
    
    /**
     * Force garbage collection and check if memory improved
     * @return true if memory was freed successfully
     */
    fun tryFreeMemory(): Boolean {
        val beforeStatus = checkMemoryStatus()
        
        Log.d(TAG, "Attempting to free memory...")
        System.gc()
        System.runFinalization()
        Thread.sleep(100)  // Give GC time to work
        
        val afterStatus = checkMemoryStatus()
        val freedMemory = afterStatus.availableMemoryMB - beforeStatus.availableMemoryMB
        
        Log.d(TAG, "Freed ${freedMemory}MB of memory")
        
        return freedMemory > 0
    }
    
    /**
     * Get disk cache directory for temporary storage
     * Use this when memory is low to write intermediate data to disk
     */
    fun getDiskCacheDir(): File = cacheDir
    
    /**
     * Create a disk-backed buffer for large downloads
     * This writes data to disk instead of keeping it in memory
     * 
     * @param fileName Name for the cache file
     * @param sizeHintMB Estimated size in MB (for pre-allocation)
     * @return DiskBackedBuffer for writing/reading data
     */
    fun createDiskBackedBuffer(fileName: String, sizeHintMB: Int = 100): DiskBackedBuffer {
        val cacheFile = File(cacheDir, fileName)
        return DiskBackedBuffer(cacheFile, sizeHintMB)
    }
    
    /**
     * Check available disk space
     * @return Available space in MB
     */
    fun getAvailableDiskSpaceMB(): Long {
        val stat = StatFs(cacheDir.path)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        return availableBytes / 1048576L
    }
    
    /**
     * Get current cache size
     * @return Cache directory size in MB
     */
    fun getCacheSizeMB(): Long {
        return cacheDir.walkTopDown()
            .filter { it.isFile }
            .map { it.length() }
            .sum() / 1048576L
    }
    
    /**
     * Clean old cache files
     * @param maxAgeDays Delete files older than this many days
     * @return Number of files deleted and MB freed
     */
    fun cleanOldCache(maxAgeDays: Int = 7): CleanupResult {
        val cutoffTime = System.currentTimeMillis() - (maxAgeDays * 24 * 60 * 60 * 1000L)
        var filesDeleted = 0
        var bytesFreed = 0L
        
        cacheDir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoffTime) {
                val size = file.length()
                if (file.delete()) {
                    filesDeleted++
                    bytesFreed += size
                    Log.d(TAG, "Deleted old cache file: ${file.name} (${size / 1048576}MB)")
                }
            }
        }
        
        Log.d(TAG, "Cache cleanup: Deleted $filesDeleted files, freed ${bytesFreed / 1048576}MB")
        
        return CleanupResult(filesDeleted, bytesFreed / 1048576L)
    }
    
    /**
     * Clean cache if it exceeds maximum size
     * Deletes oldest files first
     */
    fun cleanCacheIfNeeded(): CleanupResult {
        val currentSize = getCacheSizeMB()
        
        if (currentSize <= MAX_CACHE_SIZE_MB) {
            Log.d(TAG, "Cache size OK: ${currentSize}MB / ${MAX_CACHE_SIZE_MB}MB")
            return CleanupResult(0, 0)
        }
        
        Log.d(TAG, "Cache size exceeded: ${currentSize}MB / ${MAX_CACHE_SIZE_MB}MB - cleaning...")
        
        // Get files sorted by last modified (oldest first)
        val files = cacheDir.listFiles()
            ?.filter { it.isFile }
            ?.sortedBy { it.lastModified() }
            ?: return CleanupResult(0, 0)
        
        var filesDeleted = 0
        var bytesFreed = 0L
        var cacheSize = currentSize
        
        for (file in files) {
            if (cacheSize <= MAX_CACHE_SIZE_MB * 0.8) {  // Clean to 80% of max
                break
            }
            
            val size = file.length()
            if (file.delete()) {
                filesDeleted++
                bytesFreed += size
                cacheSize -= (size / 1048576L)
                Log.d(TAG, "Deleted cache file: ${file.name} (${size / 1048576}MB)")
            }
        }
        
        Log.d(TAG, "Cache cleanup: Deleted $filesDeleted files, freed ${bytesFreed / 1048576}MB")
        
        return CleanupResult(filesDeleted, bytesFreed / 1048576L)
    }
    
    /**
     * Clear all cache files
     * Use this when user wants to manually clear cache
     */
    fun clearAllCache(): CleanupResult {
        var filesDeleted = 0
        var bytesFreed = 0L
        
        cacheDir.listFiles()?.forEach { file ->
            val size = file.length()
            if (file.delete()) {
                filesDeleted++
                bytesFreed += size
            }
        }
        
        Log.d(TAG, "Cleared all cache: Deleted $filesDeleted files, freed ${bytesFreed / 1048576}MB")
        
        return CleanupResult(filesDeleted, bytesFreed / 1048576L)
    }
    
    /**
     * Get recommendation for download strategy based on memory
     * @param estimatedSizeMB Estimated download size
     * @return Strategy recommendation
     */
    fun getDownloadStrategy(estimatedSizeMB: Long): DownloadStrategy {
        val memoryStatus = checkMemoryStatus()
        val diskSpaceMB = getAvailableDiskSpaceMB()
        
        return when {
            memoryStatus.level == MemoryLevel.CRITICAL -> {
                DownloadStrategy.DISK_ONLY  // Write directly to disk
            }
            memoryStatus.level == MemoryLevel.LOW || estimatedSizeMB > 500 -> {
                DownloadStrategy.DISK_BUFFERED  // Use disk-backed buffer
            }
            diskSpaceMB < estimatedSizeMB -> {
                DownloadStrategy.INSUFFICIENT_SPACE  // Not enough disk space
            }
            else -> {
                DownloadStrategy.MEMORY  // Normal in-memory download
            }
        }
    }
    
    /**
     * Memory status data class
     */
    data class MemoryStatus(
        val availableMemoryMB: Long,
        val usedMemoryMB: Long,
        val maxMemoryMB: Long,
        val percentUsed: Int,
        val level: MemoryLevel
    ) {
        fun getFormattedStatus(): String {
            return "Memory: ${availableMemoryMB}MB available (${percentUsed}% used) - $level"
        }
    }
    
    /**
     * Memory level enum
     */
    enum class MemoryLevel {
        NORMAL,    // >50MB available, <80% used
        WARNING,   // >50MB but >80% used
        LOW,       // 25-50MB available
        CRITICAL   // <25MB available
    }
    
    /**
     * Download strategy recommendation
     */
    enum class DownloadStrategy {
        MEMORY,              // Normal in-memory buffering
        DISK_BUFFERED,       // Use disk-backed buffer to reduce memory usage
        DISK_ONLY,           // Write directly to disk, no memory buffering
        INSUFFICIENT_SPACE   // Not enough disk space
    }
    
    /**
     * Cleanup result
     */
    data class CleanupResult(
        val filesDeleted: Int,
        val mbFreed: Long
    )
}

/**
 * Disk-backed buffer for large data transfers
 * Writes data to disk instead of keeping it in memory
 */
class DiskBackedBuffer(
    private val file: File,
    private val sizeHintMB: Int = 100
) : AutoCloseable {
    
    companion object {
        private const val TAG = "DiskBackedBuffer"
        private const val BUFFER_SIZE = 8192  // 8KB buffer
    }
    
    private var randomAccessFile: RandomAccessFile? = null
    private var channel: FileChannel? = null
    private var position: Long = 0
    
    init {
        try {
            // Pre-allocate space if possible (improves performance)
            if (!file.exists()) {
                file.createNewFile()
                if (sizeHintMB > 0) {
                    // Pre-allocate file space (reduces fragmentation)
                    RandomAccessFile(file, "rw").use { raf ->
                        raf.setLength(sizeHintMB * 1048576L)
                    }
                }
            }
            
            randomAccessFile = RandomAccessFile(file, "rw")
            channel = randomAccessFile?.channel
            
            Log.d(TAG, "Created disk-backed buffer: ${file.name} (${sizeHintMB}MB pre-allocated)")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating disk-backed buffer", e)
            close()
        }
    }
    
    /**
     * Write data to disk buffer
     * @param data Byte array to write
     * @return Number of bytes written
     */
    fun write(data: ByteArray): Int {
        return try {
            randomAccessFile?.let { raf ->
                raf.seek(position)
                raf.write(data)
                position += data.size
                data.size
            } ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to disk buffer", e)
            0
        }
    }
    
    /**
     * Read data from disk buffer
     * @param size Number of bytes to read
     * @return Byte array with data
     */
    fun read(size: Int): ByteArray? {
        return try {
            randomAccessFile?.let { raf ->
                val buffer = ByteArray(size)
                raf.seek(position)
                val bytesRead = raf.read(buffer)
                if (bytesRead > 0) {
                    position += bytesRead
                    buffer.copyOf(bytesRead)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading from disk buffer", e)
            null
        }
    }
    
    /**
     * Seek to position in buffer
     */
    fun seek(pos: Long) {
        position = pos
    }
    
    /**
     * Get current position
     */
    fun getPosition(): Long = position
    
    /**
     * Get buffer file
     */
    fun getFile(): File = file
    
    /**
     * Flush data to disk
     */
    fun flush() {
        try {
            channel?.force(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error flushing disk buffer", e)
        }
    }
    
    override fun close() {
        try {
            channel?.close()
            randomAccessFile?.close()
            Log.d(TAG, "Closed disk-backed buffer: ${file.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing disk buffer", e)
        }
    }
    
    /**
     * Delete the buffer file
     */
    fun delete() {
        close()
        if (file.exists()) {
            file.delete()
            Log.d(TAG, "Deleted disk-backed buffer: ${file.name}")
        }
    }
}

