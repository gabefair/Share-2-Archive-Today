package org.gnosco.share2archivetoday.debug

import org.gnosco.share2archivetoday.BuildConfig
import android.util.Log
import android.content.Context

/**
 * Debug logging utility for enhanced debugging
 * Provides structured logging with automatic tag generation and debug build detection
 */
object DebugLogger {
    
    private const val GLOBAL_TAG = "Share2ArchiveToday"
    private var isDebugEnabled = true
    
    /**
     * Initialize debug logging
     */
    fun init(context: Context) {
        isDebugEnabled = BuildConfig.DEBUG
        if (isDebugEnabled) {
            Log.d(GLOBAL_TAG, "=== DEBUG LOGGING ENABLED ===")
            Log.d(GLOBAL_TAG, "Build Type: DEBUG")
            Log.d(GLOBAL_TAG, "Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            Log.d(GLOBAL_TAG, "=============================")
        }
    }
    
    /**
     * Verbose logging
     */
    fun v(tag: String, message: String, throwable: Throwable? = null) {
        if (isDebugEnabled) {
            if (throwable != null) {
                Log.v(tag, message, throwable)
            } else {
                Log.v(tag, message)
            }
        }
    }
    
    /**
     * Debug logging
     */
    fun d(tag: String, message: String, throwable: Throwable? = null) {
        if (isDebugEnabled) {
            if (throwable != null) {
                Log.d(tag, message, throwable)
            } else {
                Log.d(tag, message)
            }
        }
    }
    
    /**
     * Info logging
     */
    fun i(tag: String, message: String, throwable: Throwable? = null) {
        if (isDebugEnabled) {
            if (throwable != null) {
                Log.i(tag, message, throwable)
            } else {
                Log.i(tag, message)
            }
        }
    }
    
    /**
     * Warning logging
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (isDebugEnabled) {
            if (throwable != null) {
                Log.w(tag, message, throwable)
            } else {
                Log.w(tag, message)
            }
        }
    }
    
    /**
     * Error logging
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (isDebugEnabled) {
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        }
    }
    
    /**
     * Log method entry
     */
    fun methodEntry(tag: String, methodName: String, vararg params: Any?) {
        if (isDebugEnabled) {
            val paramString = if (params.isNotEmpty()) {
                params.joinToString(", ") { it?.toString() ?: "null" }
            } else {
                "no parameters"
            }
            Log.d(tag, "→ $methodName($paramString)")
        }
    }
    
    /**
     * Log method exit
     */
    fun methodExit(tag: String, methodName: String, result: Any? = null) {
        if (isDebugEnabled) {
            val resultString = if (result != null) " = $result" else ""
            Log.d(tag, "← $methodName()$resultString")
        }
    }
    
    /**
     * Log performance timing
     */
    fun timing(tag: String, operation: String, durationMs: Long) {
        if (isDebugEnabled) {
            Log.d(tag, "⏱️ $operation took ${durationMs}ms")
        }
    }
    
    /**
     * Log network operations
     */
    fun network(tag: String, operation: String, url: String, statusCode: Int? = null) {
        if (isDebugEnabled) {
            val status = if (statusCode != null) " [$statusCode]" else ""
            Log.d(tag, "🌐 $operation: $url$status")
        }
    }
    
    /**
     * Log file operations
     */
    fun file(tag: String, operation: String, filePath: String, sizeBytes: Long? = null) {
        if (isDebugEnabled) {
            val size = if (sizeBytes != null) " (${formatBytes(sizeBytes)})" else ""
            Log.d(tag, "📁 $operation: $filePath$size")
        }
    }
    
    /**
     * Log Python operations
     */
    fun python(tag: String, operation: String, details: String? = null) {
        if (isDebugEnabled) {
            val detailString = if (details != null) ": $details" else ""
            Log.d(tag, "🐍 Python $operation$detailString")
        }
    }
    
    /**
     * Format bytes for human reading
     */
    private fun formatBytes(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        
        return when {
            gb >= 1 -> "%.1f GB".format(gb)
            mb >= 1 -> "%.1f MB".format(mb)
            kb >= 1 -> "%.1f KB".format(kb)
            else -> "$bytes B"
        }
    }
}
