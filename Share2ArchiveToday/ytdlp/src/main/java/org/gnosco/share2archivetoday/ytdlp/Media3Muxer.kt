package org.gnosco.share2archivetoday.ytdlp

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Media3 remux helpers: merge A/V, or strip video to keep audio only.
 * Transformer must be created/started on the main thread.
 *
 * The whole Transformer surface is marked unstable by Media3; it is the only ffmpeg
 * substitute available in a FOSS build, so the opt-in is deliberate.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class Media3Muxer(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    suspend fun mux(videoFile: File, audioFile: File, outputFile: File): File =
        withContext(Dispatchers.Main) {
            suspendExport(outputFile) {
                val videoItem = EditedMediaItem.Builder(uriItem(videoFile)).build()
                val audioItem = EditedMediaItem.Builder(uriItem(audioFile)).build()
                Composition.Builder(
                    EditedMediaItemSequence.Builder(listOf(videoItem)).build(),
                    EditedMediaItemSequence.Builder(listOf(audioItem)).build(),
                ).setTransmuxAudio(true).setTransmuxVideo(true).build()
            }
        }

    suspend fun extractAudio(inputFile: File, outputFile: File): File =
        withContext(Dispatchers.Main) {
            suspendExport(outputFile) {
                val audioOnly = EditedMediaItem.Builder(uriItem(inputFile))
                    .setRemoveVideo(true)
                    .build()
                Composition.Builder(
                    EditedMediaItemSequence.Builder(listOf(audioOnly)).build(),
                ).setTransmuxAudio(true).build()
            }
        }

    fun muxBlocking(videoFile: File, audioFile: File, outputFile: File): File =
        blockExport(outputFile) {
            val videoItem = EditedMediaItem.Builder(uriItem(videoFile)).build()
            val audioItem = EditedMediaItem.Builder(uriItem(audioFile)).build()
            Composition.Builder(
                EditedMediaItemSequence.Builder(listOf(videoItem)).build(),
                EditedMediaItemSequence.Builder(listOf(audioItem)).build(),
            ).setTransmuxAudio(true).setTransmuxVideo(true).build()
        }

    fun extractAudioBlocking(inputFile: File, outputFile: File): File =
        blockExport(outputFile) {
            val audioOnly = EditedMediaItem.Builder(uriItem(inputFile))
                .setRemoveVideo(true)
                .build()
            Composition.Builder(
                EditedMediaItemSequence.Builder(listOf(audioOnly)).build(),
            ).setTransmuxAudio(true).build()
        }

    private fun uriItem(file: File): MediaItem =
        MediaItem.fromUri(Uri.fromFile(file))

    private suspend fun suspendExport(outputFile: File, composition: () -> Composition): File {
        if (outputFile.exists()) outputFile.delete()
        outputFile.parentFile?.mkdirs()
        return suspendCancellableCoroutine { cont ->
            val transformer = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(c: Composition, exportResult: ExportResult) {
                        if (cont.isActive) cont.resume(outputFile)
                    }

                    override fun onError(
                        c: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        if (cont.isActive) cont.resumeWithException(exportException)
                    }
                })
                .build()
            cont.invokeOnCancellation { runCatching { transformer.cancel() } }
            transformer.start(composition(), outputFile.absolutePath)
        }
    }

    private fun blockExport(outputFile: File, composition: () -> Composition): File {
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "muxBlocking/extractAudioBlocking must not run on the main thread"
        }
        val latch = CountDownLatch(1)
        val result = AtomicReference<File?>()
        val error = AtomicReference<Throwable?>()
        if (outputFile.exists()) outputFile.delete()
        outputFile.parentFile?.mkdirs()

        mainHandler.post {
            try {
                val transformer = Transformer.Builder(context)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(c: Composition, exportResult: ExportResult) {
                            result.set(outputFile)
                            latch.countDown()
                        }

                        override fun onError(
                            c: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException,
                        ) {
                            error.set(exportException)
                            latch.countDown()
                        }
                    })
                    .build()
                transformer.start(composition(), outputFile.absolutePath)
            } catch (t: Throwable) {
                error.set(t)
                latch.countDown()
            }
        }
        latch.await()
        error.get()?.let { throw it }
        return result.get() ?: error("Export produced no file")
    }

    companion object {
        private const val TAG = "Media3Muxer"
        fun logFailure(t: Throwable) = Log.e(TAG, "Mux/extract failed", t)
    }
}
