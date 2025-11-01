package org.gnosco.share2archivetoday.download

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import org.gnosco.share2archivetoday.QRCodeScanner
import org.gnosco.share2archivetoday.utils.UrlExtractor

/**
 * Handles share intents for video downloads, including text URLs and QR code images
 */
class VideoDownloadIntentHandler(
    private val activity: Activity
) {
    companion object {
        private const val TAG = "VideoDownloadIntentHandler"
    }
    
    /**
     * Process a share intent and extract URL
     * @return URL if found, null otherwise
     */
    fun processIntent(intent: Intent?): IntentResult {
        if (intent?.action != Intent.ACTION_SEND) {
            return IntentResult.NoIntent
        }
        
        return when (intent.type) {
            "text/plain" -> handleTextShare(intent)
            else -> {
                if (intent.type?.startsWith("image/") == true) {
                    handleImageShare(intent)
                } else {
                    IntentResult.UnsupportedType
                }
            }
        }
    }
    
    /**
     * Handle text share (URL directly shared)
     */
    private fun handleTextShare(intent: Intent): IntentResult {
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        
        if (sharedText == null) {
            return IntentResult.NoData
        }
        
        Log.d(TAG, "Shared text for video download: $sharedText")
        val url = UrlExtractor.extractUrl(sharedText)
        
        return if (url != null) {
            IntentResult.Success(url)
        } else {
            IntentResult.NoUrlFound
        }
    }
    
    /**
     * Handle image share (QR code)
     */
    private fun handleImageShare(intent: Intent): IntentResult {
        try {
            val imageUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }
            
            if (imageUri == null) {
                return IntentResult.NoData
            }
            
            val qrCodeScanner = QRCodeScanner(activity.applicationContext)
            val qrCodeText = qrCodeScanner.extractQRCodeFromImage(imageUri)
            val qrUrl = UrlExtractor.extractUrl(qrCodeText)
            
            return if (qrUrl != null) {
                Toast.makeText(activity, "URL found in QR code", Toast.LENGTH_SHORT).show()
                IntentResult.Success(qrUrl)
            } else {
                Log.d(TAG, "No QR code found in image")
                IntentResult.NoQRCode
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing QR code", e)
            return IntentResult.QRCodeError(e)
        }
    }
    
    /**
     * Result of intent processing
     */
    sealed class IntentResult {
        data class Success(val url: String) : IntentResult()
        object NoIntent : IntentResult()
        object NoData : IntentResult()
        object NoUrlFound : IntentResult()
        object NoQRCode : IntentResult()
        object UnsupportedType : IntentResult()
        data class QRCodeError(val exception: Exception) : IntentResult()
        
        fun getUserMessage(): String? = when (this) {
            is Success -> null
            NoIntent, UnsupportedType -> null
            NoData -> "No data received"
            NoUrlFound -> "No URL found in shared text"
            NoQRCode -> "No URL found in QR code image"
            is QRCodeError -> "Error processing QR code"
        }
    }
}

