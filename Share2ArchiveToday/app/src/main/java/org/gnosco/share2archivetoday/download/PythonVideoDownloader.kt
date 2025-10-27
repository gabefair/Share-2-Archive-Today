package org.gnosco.share2archivetoday

import org.gnosco.share2archivetoday.debug.*

import android.content.Context
import android.util.Log
import com.chaquo.python.PyException
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Job
import java.io.File
import org.gnosco.share2archivetoday.BuildConfig

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
            DebugLogger.python(TAG, "Initializing Python interpreter")

            if (!Python.isStarted()) {
                DebugLogger.python(TAG, "Starting Python platform")
                Python.start(AndroidPlatform(context))
            }

            val py = Python.getInstance()
            pythonModule = py.getModule(PYTHON_MODULE)

            // Set debug flag in Python module
            pythonModule?.put("DEBUG_MODE", BuildConfig.DEBUG)

            // Disable Python stdout/stderr in release builds
            if (!BuildConfig.DEBUG) {
                DebugLogger.python(TAG, "Disabling Python stdout/stderr for release build")
                disablePythonLogging(py)
            } else {
                DebugLogger.python(TAG, "Keeping Python stdout/stderr for debug build")
            }

            DebugLogger.python(TAG, "Python initialized successfully")
            Log.d(TAG, "Python initialized successfully")
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Failed to initialize Python", e)
            Log.e(TAG, "Failed to initialize Python", e)
            throw RuntimeException("Failed to initialize Python: ${e.message}", e)
        }
    }

    /**
     * Disable Python stdout/stderr logging in production builds
     */
    private fun disablePythonLogging(py: Python) {
        try {
            val sys = py.getModule("sys")
            val os = py.getModule("os")

            // Redirect stdout and stderr to devnull
            val devnull = os.callAttr("open", "/dev/null", "w")
            sys.put("stdout", devnull)
            sys.put("stderr", devnull)

            Log.d(TAG, "Python logging disabled for production")
        } catch (e: Exception) {
            Log.w(TAG, "Could not disable Python logging: ${e.message}")
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
                // Check if there's an error in the response
                val error = it.callAttr("get", "error")?.toString()
                if (!error.isNullOrBlank()) {
                    Log.e(TAG, "Python returned error: $error")
                    return null
                }

                // Use .get() method instead of bracket notation for Chaquopy compatibility
                val formatsObj = it.callAttr("get", "formats")
                Log.d(TAG, "Formats object from Python: $formatsObj")

                val formats = parseFormats(formatsObj)

                VideoInfo(
                    title = it.callAttr("get", "title")?.toString() ?: "Unknown",
                    uploader = it.callAttr("get", "uploader")?.toString() ?: "Unknown",
                    duration = it.callAttr("get", "duration")?.toDouble()?.toInt() ?: 0,
                    thumbnail = it.callAttr("get", "thumbnail")?.toString() ?: "",
                    description = it.callAttr("get", "description")?.toString() ?: "",
                    formats = formats,
                    extractor = it.callAttr("get", "extractor")?.toString(),
                    extractorKey = it.callAttr("get", "extractor_key")?.toString(),
                    webpageUrl = it.callAttr("get", "webpage_url")?.toString()
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
     * Parse formats from Python result
     */
    private fun parseFormats(formatsPyObject: PyObject?): List<FormatInfo> {
        if (formatsPyObject == null) {
            Log.w(TAG, "formatsPyObject is null")
            return emptyList()
        }

        return try {
            Log.d(TAG, "Parsing formats from Python, type: ${formatsPyObject.javaClass.name}")

            val formatsList = formatsPyObject.asList()
            Log.d(TAG, "Formats list size: ${formatsList.size}")

            val parsedFormats = formatsList.mapIndexed { index, formatObj ->
                try {
                    Log.d(TAG, "Parsing format $index")
                    // Use .callAttr("get", key) for Chaquopy compatibility
                    // Convert floats to ints by going through Double first
                    val formatInfo = FormatInfo(
                        formatId = formatObj.callAttr("get", "format_id")?.toString() ?: "",
                        ext = formatObj.callAttr("get", "ext")?.toString() ?: "",
                        resolution = formatObj.callAttr("get", "resolution")?.toString() ?: "",
                        height = formatObj.callAttr("get", "height")?.toInt() ?: 0,
                        qualityLabel = formatObj.callAttr("get", "quality_label")?.toString() ?: "",
                        filesize = formatObj.callAttr("get", "filesize")?.toLong() ?: 0L,
                        vcodec = formatObj.callAttr("get", "vcodec")?.toString() ?: "",
                        acodec = formatObj.callAttr("get", "acodec")?.toString() ?: "",
                        hasAudio = formatObj.callAttr("get", "has_audio")?.toBoolean() ?: false,
                        hasVideo = formatObj.callAttr("get", "has_video")?.toBoolean() ?: false,
                        fps = formatObj.callAttr("get", "fps")?.toDouble()?.toInt() ?: 0,  // Handle Python floats
                        formatNote = formatObj.callAttr("get", "format_note")?.toString() ?: "",
                        tbr = formatObj.callAttr("get", "tbr")?.toDouble()?.toInt() ?: 0,  // Handle Python floats
                        url = formatObj.callAttr("get", "url")?.toString() ?: ""
                    )
                    Log.d(TAG, "Successfully parsed format: ${formatInfo.formatId} - ${formatInfo.qualityLabel}")
                    formatInfo
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing format $index: ${e.message}", e)
                    null
                }
            }.filterNotNull()

            Log.d(TAG, "Successfully parsed ${parsedFormats.size} formats")
            parsedFormats
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing formats list: ${e.message}", e)
            e.printStackTrace()
            emptyList()
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
        @Suppress("UNUSED_PARAMETER") estimatedSizeMB: Long = 100,
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

            // Call Python download function with fallback if callback fails
            val result = try {
                pythonModule?.callAttr(
                    "download_video",
                    url,
                    outputDir,
                    quality,
                    pythonCallback,
                    formatId
                )
            } catch (e: Exception) {
                Log.w(TAG, "Download with callback failed, retrying without callback: ${e.message}")
                // Fallback: try without callback
                pythonModule?.callAttr(
                    "download_video",
                    url,
                    outputDir,
                    quality,
                    null, // No callback
                    formatId
                )
            }

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

            // Call Python download function with fallback if callback fails
            val result = try {
                pythonModule?.callAttr(
                    "download_audio",
                    url,
                    outputDir,
                    audioFormat,
                    pythonCallback
                )
            } catch (e: Exception) {
                Log.w(TAG, "Audio download with callback failed, retrying without callback: ${e.message}")
                // Fallback: try without callback
                pythonModule?.callAttr(
                    "download_audio",
                    url,
                    outputDir,
                    audioFormat,
                    null // No callback
                )
            }

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

            // Create a Python-compatible callback function using exec in builtins
            val callbackCode = """
                def kotlin_callback(progress):
                    try:
                        # This will be handled by the Python side
                        pass
                    except Exception as e:
                        print(f"Error in progress callback: {e}", file=sys.stderr, flush=True)
            """.trimIndent()
            // globals must be a dict, not module
            // Use builtins.exec with proper globals and locals dictionaries
            val builtins = py.getModule("builtins")
            val mainModule = py.getModule("__main__")

            // Create globals and locals dictionaries from the main module
            // builtins.exec() expects dictionary objects for the globals and locals parameters
            val globalsDict = mainModule.callAttr("__dict__")
            val localsDict = mainModule.callAttr("__dict__")

            // Execute the callback function definition
            builtins.callAttr("exec", callbackCode, globalsDict, localsDict)

            // Return the callback function from the locals dictionary
            localsDict.callAttr("get", "kotlin_callback")
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
            val status = progress.callAttr("get", "status")?.toString() ?: "unknown"

            when (status) {
                "downloading" -> {
                    val downloaded = progress.callAttr("get", "downloaded_bytes")?.toLong() ?: 0L
                    val total = progress.callAttr("get", "total_bytes")?.toLong()
                        ?: progress.callAttr("get", "total_bytes_estimate")?.toLong()
                        ?: 0L
                    val speed = progress.callAttr("get", "speed")?.toDouble() ?: 0.0
                    val eta = progress.callAttr("get", "eta")?.toInt() ?: 0
                    val filename = progress.callAttr("get", "filename")?.toString() ?: ""

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
                    val filename = progress.callAttr("get", "filename")?.toString() ?: ""
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
            Log.d(TAG, "Result type: ${result.javaClass.name}")

            // Use Python's .get() method instead of [] operator for dictionary access
            val success = try {
                val successObj = result.callAttr("get", "success")
                Log.d(TAG, "success object: $successObj, type: ${successObj?.javaClass?.name}")
                successObj?.toBoolean() ?: false
            } catch (e: Exception) {
                Log.e(TAG, "Error getting success: ${e.message}", e)
                false
            }

            val error = try {
                val errorObj = result.callAttr("get", "error")
                if (errorObj != null && errorObj.toString() != "None") {
                    errorObj.toString()
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting error field: ${e.message}", e)
                null
            }

            val filePath = try {
                val filePathObj = result.callAttr("get", "file_path")
                if (filePathObj != null && filePathObj.toString() != "None") {
                    filePathObj.toString()
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting file_path: ${e.message}", e)
                null
            }

            val videoPath = try {
                val videoPathObj = result.callAttr("get", "video_path")
                if (videoPathObj != null && videoPathObj.toString() != "None") {
                    videoPathObj.toString()
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting video_path: ${e.message}", e)
                null
            }

            val audioPath = try {
                val audioPathObj = result.callAttr("get", "audio_path")
                if (audioPathObj != null && audioPathObj.toString() != "None") {
                    audioPathObj.toString()
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting audio_path: ${e.message}", e)
                null
            }

            val separateAv = try {
                val separateAvObj = result.callAttr("get", "separate_av")
                separateAvObj?.toBoolean() ?: false
            } catch (e: Exception) {
                Log.e(TAG, "Error getting separate_av: ${e.message}", e)
                false
            }

            val fileSize = try {
                val fileSizeObj = result.callAttr("get", "file_size")
                fileSizeObj?.toLong() ?: 0L
            } catch (e: Exception) {
                Log.e(TAG, "Error getting file_size: ${e.message}", e)
                0L
            }

            val needsExtraction = try {
                val needsExtractionObj = result.callAttr("get", "needs_extraction")
                needsExtractionObj?.toBoolean() ?: false
            } catch (e: Exception) {
                Log.e(TAG, "Error getting needs_extraction: ${e.message}", e)
                false
            }

            Log.d(TAG, "Parsed result - success: $success, error: $error, filePath: $filePath, videoPath: $videoPath, audioPath: $audioPath, separateAv: $separateAv, fileSize: $fileSize, needsExtraction: $needsExtraction")

            DownloadResult(
                success = success,
                error = error,
                filePath = filePath,
                videoPath = videoPath,
                audioPath = audioPath,
                separateAv = separateAv,
                fileSize = fileSize,
                needsExtraction = needsExtraction
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
     * Reset cancellation flag for a new download
     */
    fun resetCancellation() {
        try {
            pythonModule?.callAttr("reset_cancellation")
            Log.d(TAG, "Cancellation flag reset for new download")
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting cancellation flag", e)
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
        val description: String,
        val formats: List<FormatInfo> = emptyList(),
        val extractor: String? = null,
        val extractorKey: String? = null,
        val webpageUrl: String? = null
    )

    /**
     * Format information data class
     */
    data class FormatInfo(
        val formatId: String,
        val ext: String,
        val resolution: String,
        val height: Int,
        val qualityLabel: String,
        val filesize: Long,
        val vcodec: String,
        val acodec: String,
        val hasAudio: Boolean,
        val hasVideo: Boolean,
        val fps: Int,
        val formatNote: String,
        val tbr: Int,
        val url: String
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
        val fileSize: Long,
        val needsExtraction: Boolean = false  // True if audio needs to be extracted from video
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
