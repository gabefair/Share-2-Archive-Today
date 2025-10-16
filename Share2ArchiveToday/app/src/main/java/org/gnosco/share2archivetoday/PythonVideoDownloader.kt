package org.gnosco.share2archivetoday

import android.content.Context
import android.util.Log
import com.chaquo.python.PyException
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Job
import java.io.File

/**
 * Kotlin wrapper for Python yt-dlp video downloader
 * Handles video downloads using yt-dlp via Chaquopy
 */
class PythonVideoDownloader(private val context: Context) {
    
    companion object {
        private const val TAG = "PythonVideoDownloader"
        private const val PYTHON_MODULE = "ytdlp_downloader"
    }
    
    private var pythonModule: PyObject? = null
    private val memoryManager = MemoryManager(context)
    var currentDownloadJob: Job? = null
    
    init {
        initPython()
    }
    
    /**
     * Initialize Python interpreter
     */
    private fun initPython() {
        try {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(context))
            }
            
            val py = Python.getInstance()
            pythonModule = py.getModule(PYTHON_MODULE)
            
            Log.d(TAG, "Python initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Python", e)
            throw RuntimeException("Failed to initialize Python: ${e.message}", e)
        }
    }
    
    /**
     * Get video information without downloading
     */
    fun getVideoInfo(url: String): VideoInfo? {
        try {
            Log.d(TAG, "Getting video info for: $url")
            
            val result = pythonModule?.callAttr("get_video_info", url)
            
            return result?.let {
                VideoInfo(
                    title = it["title"]?.toString() ?: "Unknown",
                    uploader = it["uploader"]?.toString() ?: "Unknown",
                    duration = it["duration"]?.toInt() ?: 0,
                    thumbnail = it["thumbnail"]?.toString() ?: "",
                    description = it["description"]?.toString() ?: ""
                )
            }
        } catch (e: PyException) {
            Log.e(TAG, "Python error getting video info: ${e.message}")
            Log.e(TAG, "Python traceback: ${e.printStackTrace()}")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting video info", e)
            return null
        }
    }
    
    /**
     * Download video with progress tracking
     */
    fun downloadVideo(
        url: String,
        outputDir: String,
        quality: String = "best",
        progressCallback: ((ProgressInfo) -> Unit)? = null,
        estimatedSizeMB: Long = 100,
        formatId: String? = null
    ): DownloadResult {
        try {
            Log.d(TAG, "Starting video download")
            Log.d(TAG, "  URL: $url")
            Log.d(TAG, "  Output: $outputDir")
            if (formatId != null) {
                Log.d(TAG, "  Format ID: $formatId")
            } else {
                Log.d(TAG, "  Quality: $quality")
            }
            
            // Create Python progress callback
            val pythonCallback = createPythonProgressCallback(progressCallback)
            
            // Call Python download function
            val result = pythonModule?.callAttr(
                "download_video",
                url,
                outputDir,
                quality,
                pythonCallback,
                formatId
            )
            
            // Parse result
            return parseDownloadResult(result)
            
        } catch (e: PyException) {
            val errorMsg = "Python error: ${e.message}"
            Log.e(TAG, errorMsg)
            Log.e(TAG, "Python traceback:")
            e.printStackTrace()
            return DownloadResult(
                success = false,
                error = errorMsg,
                filePath = null,
                videoPath = null,
                audioPath = null,
                separateAv = false,
                fileSize = 0
            )
        } catch (e: Exception) {
            val errorMsg = "Download error: ${e.message}"
            Log.e(TAG, errorMsg, e)
            return DownloadResult(
                success = false,
                error = errorMsg,
                filePath = null,
                videoPath = null,
                audioPath = null,
                separateAv = false,
                fileSize = 0
            )
        }
    }
    
    /**
     * Download audio only
     */
    fun downloadAudio(
        url: String,
        outputDir: String,
        audioFormat: String = "mp3",
        progressCallback: ((ProgressInfo) -> Unit)? = null
    ): DownloadResult {
        try {
            Log.d(TAG, "Starting audio download")
            Log.d(TAG, "  URL: $url")
            Log.d(TAG, "  Output: $outputDir")
            Log.d(TAG, "  Format: $audioFormat")
            
            // Create Python progress callback
            val pythonCallback = createPythonProgressCallback(progressCallback)
            
            // Call Python download function
            val result = pythonModule?.callAttr(
                "download_audio",
                url,
                outputDir,
                audioFormat,
                pythonCallback
            )
            
            // Parse result
            return parseDownloadResult(result)
            
        } catch (e: PyException) {
            val errorMsg = "Python error: ${e.message}"
            Log.e(TAG, errorMsg)
            e.printStackTrace()
            return DownloadResult(
                success = false,
                error = errorMsg,
                filePath = null,
                videoPath = null,
                audioPath = null,
                separateAv = false,
                fileSize = 0
            )
        } catch (e: Exception) {
            val errorMsg = "Download error: ${e.message}"
            Log.e(TAG, errorMsg, e)
            return DownloadResult(
                success = false,
                error = errorMsg,
                filePath = null,
                videoPath = null,
                audioPath = null,
                separateAv = false,
                fileSize = 0
            )
        }
    }
    
    /**
     * Create Python progress callback that bridges to Kotlin callback
     */
    private fun createPythonProgressCallback(kotlinCallback: ((ProgressInfo) -> Unit)?): PyObject? {
        if (kotlinCallback == null) return null
        
        return try {
            val py = Python.getInstance()
            val builtins = py.getBuiltins()
            
            // Create a Python-compatible callback function
            py.getModule("__main__").put("kotlin_callback", object : Any() {
                fun __call__(progress: PyObject) {
                    val progressInfo = parsePythonProgress(progress)
                    kotlinCallback(progressInfo)
                }
            })
            
            py.getModule("__main__")["kotlin_callback"]
        } catch (e: Exception) {
            Log.e(TAG, "Error creating Python callback", e)
            null
        }
    }
    
    /**
     * Parse Python progress dictionary to Kotlin ProgressInfo
     */
    private fun parsePythonProgress(progress: PyObject): ProgressInfo {
        return try {
            val status = progress["status"]?.toString() ?: "unknown"
            
            when (status) {
                "downloading" -> {
                    val downloaded = progress["downloaded_bytes"]?.toLong() ?: 0L
                    val total = progress["total_bytes"]?.toLong() 
                        ?: progress["total_bytes_estimate"]?.toLong() 
                        ?: 0L
                    val speed = progress["speed"]?.toDouble() ?: 0.0
                    val eta = progress["eta"]?.toInt() ?: 0
                    val filename = progress["filename"]?.toString() ?: ""
                    
                    val percentage = if (total > 0) {
                        ((downloaded.toDouble() / total.toDouble()) * 100).toInt()
                    } else {
                        0
                    }
                    
                    ProgressInfo.Downloading(
                        percentage = percentage,
                        downloadedBytes = downloaded,
                        totalBytes = total,
                        speedBytesPerSec = speed.toLong(),
                        etaSeconds = eta,
                        filename = filename
                    )
                }
                "finished" -> {
                    val filename = progress["filename"]?.toString() ?: ""
                    ProgressInfo.Finished(filename)
                }
                "error" -> {
                    ProgressInfo.Error("Download error occurred")
                }
                else -> {
                    ProgressInfo.Unknown
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Python progress", e)
            ProgressInfo.Unknown
        }
    }
    
    /**
     * Parse Python download result dictionary to Kotlin DownloadResult
     */
    private fun parseDownloadResult(result: PyObject?): DownloadResult {
        return try {
            if (result == null) {
                Log.e(TAG, "Python returned null result")
                return DownloadResult(
                    success = false,
                    error = "No result from Python",
                    filePath = null,
                    videoPath = null,
                    audioPath = null,
                    separateAv = false,
                    fileSize = 0
                )
            }
            
            // Log raw result for debugging
            Log.d(TAG, "Parsing Python result: ${result.toString()}")
            
            // Try to extract each field and log any issues
            val success = result["success"]?.toBoolean() ?: false
            val error = result["error"]?.toString()
            val filePath = result["file_path"]?.toString()
            val videoPath = result["video_path"]?.toString()
            val audioPath = result["audio_path"]?.toString()
            val separateAv = result["separate_av"]?.toBoolean() ?: false
            val fileSize = result["file_size"]?.toLong() ?: 0L
            
            Log.d(TAG, "Parsed result - success: $success, error: $error, filePath: $filePath, videoPath: $videoPath, audioPath: $audioPath, separateAv: $separateAv, fileSize: $fileSize")
            
            DownloadResult(
                success = success,
                error = error,
                filePath = filePath,
                videoPath = videoPath,
                audioPath = audioPath,
                separateAv = separateAv,
                fileSize = fileSize
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing download result", e)
            DownloadResult(
                success = false,
                error = "Failed to parse result: ${e.message}",
                filePath = null,
                videoPath = null,
                audioPath = null,
                separateAv = false,
                fileSize = 0
            )
        }
    }
    
    /**
     * Cancel the current download
     */
    fun cancelDownload() {
        try {
            Log.d(TAG, "Cancelling download")
            pythonModule?.callAttr("cancel_download")
            currentDownloadJob?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling download", e)
        }
    }
    
    /**
     * Check if download is cancelled
     */
    fun isCancelled(): Boolean {
        return try {
            pythonModule?.callAttr("is_cancelled")?.toBoolean() ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking cancelled status", e)
            false
        }
    }
    
    /**
     * Get memory manager
     */
    fun getMemoryManager(): MemoryManager = memoryManager
    
    /**
     * Test Python functionality
     */
    fun testPythonFunctionality(): Boolean {
        return try {
            pythonModule != null && Python.isStarted()
        } catch (e: Exception) {
            Log.e(TAG, "Error testing Python functionality", e)
            false
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        try {
            cancelDownload()
            pythonModule = null
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
    
    /**
     * Video information data class
     */
    data class VideoInfo(
        val title: String,
        val uploader: String,
        val duration: Int,
        val thumbnail: String,
        val description: String
    )
    
    /**
     * Download result data class
     */
    data class DownloadResult(
        val success: Boolean,
        val error: String?,
        val filePath: String?,
        val videoPath: String?,
        val audioPath: String?,
        val separateAv: Boolean,
        val fileSize: Long
    )
    
    /**
     * Progress information sealed class
     */
    sealed class ProgressInfo {
        data class Starting(val title: String) : ProgressInfo()
        
        data class Downloading(
            val percentage: Int,
            val downloadedBytes: Long,
            val totalBytes: Long,
            val speedBytesPerSec: Long,
            val etaSeconds: Int,
            val filename: String
        ) : ProgressInfo() {
            fun getFormattedSpeed(): String {
                val speedKB = speedBytesPerSec / 1024.0
                val speedMB = speedKB / 1024.0
                return if (speedMB >= 1.0) {
                    String.format("%.1f MB/s", speedMB)
                } else {
                    String.format("%.1f KB/s", speedKB)
                }
            }
            
            fun getFormattedDownloaded(): String {
                val downloadedMB = downloadedBytes / (1024.0 * 1024.0)
                return String.format("%.1f MB", downloadedMB)
            }
            
            fun getFormattedTotal(): String {
                val totalMB = totalBytes / (1024.0 * 1024.0)
                return String.format("%.1f MB", totalMB)
            }
            
            fun getFormattedEta(): String {
                val hours = etaSeconds / 3600
                val minutes = (etaSeconds % 3600) / 60
                val seconds = etaSeconds % 60
                
                return when {
                    hours > 0 -> String.format("%dh %dm", hours, minutes)
                    minutes > 0 -> String.format("%dm %ds", minutes, seconds)
                    else -> String.format("%ds", seconds)
                }
            }
        }
        
        data class Finished(val filename: String) : ProgressInfo()
        
        data class Retrying(val title: String, val message: String) : ProgressInfo()
        
        data class Error(val error: String) : ProgressInfo()
        
        object Unknown : ProgressInfo()
    }
}
