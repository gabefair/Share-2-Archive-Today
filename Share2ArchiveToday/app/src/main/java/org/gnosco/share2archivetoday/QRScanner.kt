package org.gnosco.share2archivetoday
// This file is: QRScanner.kt

import android.annotation.TargetApi
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import kotlin.math.max
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import kotlin.math.max

class QrCodeScanner(private val contentResolver: ContentResolver, private val context: Context) {

    private val googleApiAvailability = GoogleApiAvailability.getInstance()

    /**
     * Checks if Google Play Services is available on the device
     */
    private fun isGooglePlayServicesAvailable(): Boolean {
        return googleApiAvailability.isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }
    /**
     * Checks if Google Play Services and ML Kit classes are available
     */
    private fun isMLKitAvailable(): Boolean {
        return try {
            // Check if the classes exist
            Class.forName("com.google.android.gms.common.GoogleApiAvailability")
            Class.forName("com.google.mlkit.vision.barcode.BarcodeScanning")

            // Check if API level is sufficient
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                // Try to verify Google Play Services availability
                val googleApiAvailability = Class.forName("com.google.android.gms.common.GoogleApiAvailability")
                    .getMethod("getInstance")
                    .invoke(null)

                val method = googleApiAvailability::class.java
                    .getMethod("isGooglePlayServicesAvailable", Context::class.java)

                val result = method.invoke(googleApiAvailability, context) as Int
                result == 0 // ConnectionResult.SUCCESS = 0
            } else {
                false
            }
        } catch (e: Exception) {
            Log.d("QrCodeScanner", "ML Kit not available: ${e.message}")
            false
        }
    }

    /**
     * Main method to extract QR code from image
     */
    fun extractQRCodeFromImage(imageUri: Uri): String {
        var result = ""

        // Try ML Kit if available
        if (isMLKitAvailable()) {
            try {
                result = extractQRCodeUsingMLKit(imageUri)
                Log.d("QrCodeScanner", "ML Kit scan result: ${if (result.isEmpty()) "empty" else "found"}")
            } catch (e: Exception) {
                Log.e("QrCodeScanner", "ML Kit scanning failed", e)
            }
        }

        // If no result from MLKit or ML Kit not available, try ZXing
        if (result.isEmpty()) {
            result = extractQRCodeUsingZXing(imageUri)
            Log.d("QrCodeScanner", "ZXing scan result: ${if (result.isEmpty()) "empty" else "found"}")
        }

        return result
    }

    /**
     * Extract QR code using Google Play Services ML Kit
     */
    private fun extractQRCodeUsingMLKit(imageUri: Uri): String {
        try {
            val InputImageClass = Class.forName("com.google.mlkit.vision.common.InputImage")
            val BarcodeScanningClass = Class.forName("com.google.mlkit.vision.barcode.BarcodeScanning")
            val BarcodeClass = Class.forName("com.google.mlkit.vision.barcode.common.Barcode")

            val inputStream = contentResolver.openInputStream(imageUri) ?: return ""

            // Create InputImage using reflection
            val image = InputImageClass.getMethod("fromStream", java.io.InputStream::class.java, Int::class.javaPrimitiveType)
                .invoke(null, inputStream, 0)

            // Get scanner
            val scanner = BarcodeScanningClass.getMethod("getClient")
                .invoke(null)

            // Process image
            val task = scanner::class.java.getMethod("process", InputImageClass)
                .invoke(scanner, image)

            // Get result (simplified synchronous wait)
            Thread.sleep(1000) // In production, use proper async handling

            val isComplete = task::class.java.getMethod("isComplete").invoke(task) as Boolean
            if (isComplete) {
                val barcodes = task::class.java.getMethod("getResult").invoke(task) as List<*>

                for (barcode in barcodes) {
                    val format = BarcodeClass.getField("FORMAT_QR_CODE").getInt(null)
                    val barcodeFormat = BarcodeClass.getMethod("getFormat").invoke(barcode) as Int

                    if (barcodeFormat == format) {
                        return BarcodeClass.getMethod("getRawValue").invoke(barcode) as? String ?: ""
                    }
                }
            }

            inputStream.close()
            return ""
        } catch (e: Exception) {
            Log.e("QrCodeScanner", "Error using ML Kit reflection", e)
            return ""
        }
    }

    /**
     * Extract QR code using ZXing (fallback method)
     */
    private fun extractQRCodeUsingZXing(imageUri: Uri): String {
        val inputStream = contentResolver.openInputStream(imageUri) ?: return ""

        // Read image dimensions first
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeStream(inputStream, null, options)
        inputStream.close()

        // Calculate sample size to avoid OOM
        val maxDimension = max(options.outWidth, options.outHeight)
        val sampleSize = max(1, maxDimension / 2048)

        // Read the actual bitmap with sampling
        val scaledOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        contentResolver.openInputStream(imageUri)?.use { stream ->
            val bitmap = BitmapFactory.decodeStream(stream, null, scaledOptions) ?: return ""

            try {
                // Convert to ZXing format
                val width = bitmap.width
                val height = bitmap.height
                val pixels = IntArray(width * height)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

                val source = RGBLuminanceSource(width, height, pixels)
                val binarizer = HybridBinarizer(source)
                val binaryBitmap = BinaryBitmap(binarizer)

                // Try to decode QR code
                val reader = MultiFormatReader()
                val hints = mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to arrayListOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true
                )

                try {
                    val result = reader.decode(binaryBitmap, hints)
                    return result.text
                } catch (e: NotFoundException) {
                    // No QR code found
                    Log.d("QrCodeScanner", "No QR code found in image")
                    return ""
                }
            } finally {
                bitmap.recycle()
            }
        }
        return ""
    }
}