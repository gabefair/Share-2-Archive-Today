package org.gnosco.share2archivetoday

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import kotlin.math.max

/**
 * QR Code scanner that tries ML Kit first, then falls back to ZXing
 */
class QRCodeScanner(private val context: Context) {

    companion object {
        private const val TAG = "QRCodeScanner"
        private var mlKitAvailable: Boolean? = null
    }

    /**
     * Extract QR code content from an image URI
     */
    fun extractQRCodeFromImage(imageUri: Uri): String {
        return try {
            // First try ML Kit if available
            if (isMlKitAvailable()) {
                val result = extractWithMlKit(imageUri)
                if (result.isNotEmpty()) {
                    Log.d(TAG, "QR code extracted using ML Kit")
                    return result
                }
            }

            // Fallback to ZXing
            Log.d(TAG, "Using ZXing for QR code extraction")
            extractWithZXing(imageUri)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting QR code", e)
            ""
        }
    }

    /**
     * Check if ML Kit is available on this device
     */
    private fun isMlKitAvailable(): Boolean {
        // Cache the result to avoid repeated reflection calls
        if (mlKitAvailable != null) {
            return mlKitAvailable!!
        }

        mlKitAvailable = try {
            Class.forName("com.google.mlkit.vision.barcode.BarcodeScanning")
            Class.forName("com.google.mlkit.vision.common.InputImage")
            true
        } catch (e: ClassNotFoundException) {
            Log.d(TAG, "ML Kit not available, will use ZXing fallback")
            false
        } catch (e: Exception) {
            Log.w(TAG, "Error checking ML Kit availability", e)
            false
        }

        return mlKitAvailable!!
    }

    /**
     * Extract QR code using ML Kit (if available)
     */
    private fun extractWithMlKit(imageUri: Uri): String {
        try {
            // Use reflection to access ML Kit classes to avoid compile-time dependency
            val inputImageClass = Class.forName("com.google.mlkit.vision.common.InputImage")
            val barcodeScanningClass = Class.forName("com.google.mlkit.vision.barcode.BarcodeScanning")

            // Create InputImage from URI - try fromFilePath first, then fallback to fromBitmap
            var inputImage: Any? = null

            try {
                val fromFilePathMethod = inputImageClass.getMethod("fromFilePath", Context::class.java, Uri::class.java)
                inputImage = fromFilePathMethod.invoke(null, context, imageUri)
            } catch (e: Exception) {
                // Fallback to creating from bitmap if fromFilePath fails
                Log.d(TAG, "fromFilePath failed, trying fromBitmap fallback")
                context.contentResolver.openInputStream(imageUri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        val fromBitmapMethod = inputImageClass.getMethod("fromBitmap", Bitmap::class.java, Int::class.java)
                        inputImage = fromBitmapMethod.invoke(null, bitmap, 0) // 0 = no rotation
                        bitmap.recycle()
                    }
                }
            }

            if (inputImage == null) {
                Log.w(TAG, "Could not create InputImage from URI")
                return ""
            }

            // Get barcode scanner
            val getClientMethod = barcodeScanningClass.getMethod("getClient")
            val scanner = getClientMethod.invoke(null)

            // Process the image
            val processMethod = scanner.javaClass.getMethod("process", inputImageClass)
            val task = processMethod.invoke(scanner, inputImage)

            // Wait for result (blocking call)
            val tasksClass = Class.forName("com.google.android.gms.tasks.Tasks")
            val awaitMethod = tasksClass.getMethod("await", Class.forName("com.google.android.gms.tasks.Task"))
            val barcodes = awaitMethod.invoke(null, task) as List<*>

            // Extract text from first barcode
            if (barcodes.isNotEmpty()) {
                val barcode = barcodes[0]
                val getRawValueMethod = barcode!!.javaClass.getMethod("getRawValue")
                val rawValue = getRawValueMethod.invoke(barcode) as String?
                return rawValue ?: ""
            }

        } catch (e: Exception) {
            Log.w(TAG, "ML Kit extraction failed, falling back to ZXing", e)
        }

        return ""
    }

    /**
     * Extract QR code using ZXing (fallback method)
     */
    private fun extractWithZXing(imageUri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(imageUri) ?: return ""

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

        context.contentResolver.openInputStream(imageUri)?.use { stream ->
            val bitmap = BitmapFactory.decodeStream(stream, null, scaledOptions) ?: return ""

            try {
                // Convert to ZXing format
                val width = bitmap.width
                val height = bitmap.height
                val pixels = IntArray(width * height)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

                // Use ZXing directly (no reflection needed since it's a regular dependency)
                val source = com.google.zxing.RGBLuminanceSource(width, height, pixels)
                val binarizer = com.google.zxing.common.HybridBinarizer(source)
                val binaryBitmap = com.google.zxing.BinaryBitmap(binarizer)

                // Try to decode QR code
                val reader = com.google.zxing.MultiFormatReader()
                val hints = mapOf(
                    com.google.zxing.DecodeHintType.POSSIBLE_FORMATS to arrayListOf(com.google.zxing.BarcodeFormat.QR_CODE),
                    com.google.zxing.DecodeHintType.TRY_HARDER to true
                )

                try {
                    val result = reader.decode(binaryBitmap, hints)
                    return result.text
                } catch (e: com.google.zxing.NotFoundException) {
                    // No QR code found
                    Log.d(TAG, "No QR code found in image")
                    return ""
                }
            } finally {
                bitmap.recycle()
            }
        }
        return ""
    }
}