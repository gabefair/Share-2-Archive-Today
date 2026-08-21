package org.gnosco.share2archivetoday.ytdlp

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Losslessly transmux separate video + audio files into one MP4 via Media3 Transformer.
 */
class Media3Muxer(private val context: Context) {

    suspend fun mux(videoFile: File, audioFile: File, outputFile: File): File {
        if (outputFile.exists()) outputFile.delete()
        outputFile.parentFile?.mkdirs()

        return suspendCancellableCoroutine { cont ->
            val videoItem = EditedMediaItem.Builder(MediaItem.fromUri(UriFromFile(videoFile))).build()
            val audioItem = EditedMediaItem.Builder(MediaItem.fromUri(UriFromFile(audioFile))).build()

            val videoSeq = EditedMediaItemSequence.Builder(videoItem).build()
            val audioSeq = EditedMediaItemSequence.Builder(audioItem).build()
            val composition = Composition.Builder(videoSeq, audioSeq)
                .setTransmuxAudio(true)
                .setTransmuxVideo(true)
                .build()

            val transformer = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (cont.isActive) cont.resume(outputFile)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        if (cont.isActive) cont.resumeWithException(exportException)
                    }
                })
                .build()

            cont.invokeOnCancellation {
                runCatching { transformer.cancel() }
            }
            transformer.start(composition, outputFile.absolutePath)
        }
    }

    /** Blocking helper for callers not on a coroutine dispatcher yet. */
    fun muxBlocking(videoFile: File, audioFile: File, outputFile: File): File {
        val latch = CountDownLatch(1)
        val result = AtomicReference<File?>()
        val error = AtomicReference<Throwable?>()

        val videoItem = EditedMediaItem.Builder(MediaItem.fromUri(UriFromFile(videoFile))).build()
        val audioItem = EditedMediaItem.Builder(MediaItem.fromUri(UriFromFile(audioFile))).build()
        val composition = Composition.Builder(
            EditedMediaItemSequence.Builder(videoItem).build(),
            EditedMediaItemSequence.Builder(audioItem).build(),
        ).setTransmuxAudio(true).setTransmuxVideo(true).build()

        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    result.set(outputFile)
                    latch.countDown()
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    error.set(exportException)
                    latch.countDown()
                }
            })
            .build()

        if (outputFile.exists()) outputFile.delete()
        outputFile.parentFile?.mkdirs()
        transformer.start(composition, outputFile.absolutePath)
        latch.await()
        error.get()?.let { throw it }
        return result.get() ?: error("Mux produced no file")
    }

    private fun UriFromFile(file: File): android.net.Uri = android.net.Uri.fromFile(file)

    companion object {
        private const val TAG = "Media3Muxer"
        fun logFailure(t: Throwable) = Log.e(TAG, "Mux failed", t)
    }
}
