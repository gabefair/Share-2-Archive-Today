package org.gnosco.share2archivetoday.download.service

import android.content.Context
import android.util.Log
import java.io.File
import org.gnosco.share2archivetoday.download.history.BestPartialStore
import org.gnosco.share2archivetoday.ytdlp.ArchiveMediaEmbedder
import org.gnosco.share2archivetoday.ytdlp.DownloadSidecar
import org.gnosco.share2archivetoday.ytdlp.Media3Muxer
import org.gnosco.share2archivetoday.ytdlp.MediaTypes
import org.gnosco.share2archivetoday.ytdlp.YtDlpBridge
import org.gnosco.share2archivetoday.ytdlp.YtDlpFailureClassifier
import org.json.JSONObject

/** Runs yt-dlp download + optional Media3 mux/extract for [DownloadPipeline]. */
class DownloadExecutor(
    private val context: Context,
    private val bridge: YtDlpBridge,
    private val partials: BestPartialStore,
    private val onProgress: (downloaded: Long, total: Long) -> Unit,
    private val onStatus: (String) -> Unit,
    private val cancelSignal: YtDlpBridge.CancelSignal? = null,
) {

    /**
     * One file to publish.
     *
     * @param nameSuffix appended to the shared base name, so the two halves of an
     *   unmerged pair land next to each other as "<title>.video.mp4" / "<title>.audio.webm".
     */
    data class Artifact(
        val file: File,
        val mimeType: String,
        val role: String,
        val nameSuffix: String = "",
    )

    data class Result(
        val primary: Artifact,
        val extras: List<Artifact> = emptyList(),
        val sidecars: List<DownloadSidecar> = emptyList(),
        val provenance: JSONObject? = null,
        /** Non-null when Media3 rewrote the container, for the manifest. */
        val remuxTool: String? = null,
        val remuxOperation: String? = null,
        val notes: List<String> = emptyList(),
    ) {
        val allArtifacts: List<Artifact> get() = listOf(primary) + extras
    }

    fun execute(
        downloadId: String,
        url: String,
        videoFormatId: String?,
        audioFormatIds: List<String>,
        combinedFormatId: String?,
        needsMux: Boolean,
        audioOnly: Boolean,
        requiresVideoExtract: Boolean,
        archiveMetadata: Boolean = false,
        includeComments: Boolean = false,
        writeSubtitles: Boolean = false,
    ): Result {
        val work = partials.workDir(downloadId)
        val options = YtDlpBridge.DownloadOptions(
            archiveMetadata = archiveMetadata,
            includeComments = includeComments,
            writeSubtitles = writeSubtitles || archiveMetadata,
            subtitleLangs = YtDlpBridge.defaultSubtitleLangs(context),
        )
        return when {
            audioOnly && requiresVideoExtract && combinedFormatId != null ->
                downloadThenExtractAudio(downloadId, url, combinedFormatId, work, options)

            audioOnly && audioFormatIds.isNotEmpty() ->
                downloadFirstWorkingAudio(downloadId, url, audioFormatIds, work, options)

            needsMux && videoFormatId != null ->
                downloadMux(downloadId, url, videoFormatId, audioFormatIds, work, options)

            combinedFormatId != null -> {
                if (options.archiveMetadata) onStatus("Fetching metadata…")
                val result = fetch(downloadId, "combined", url, combinedFormatId, work, options)
                val file = File(result.filepath)
                Result(
                    primary = Artifact(file, MediaTypes.forFile(file), "video"),
                    sidecars = result.sidecars,
                    provenance = result.provenance,
                )
            }

            else -> error("Invalid download parameters")
        }
    }

    private fun fetch(
        downloadId: String,
        role: String,
        url: String,
        formatSpec: String,
        work: File,
        options: YtDlpBridge.DownloadOptions,
    ) = bridge.download(
        url = url,
        formatSpec = formatSpec,
        outDir = work.absolutePath,
        options = options,
        onProgress = { onProgress(it.downloaded, it.total) },
        cancelSignal = cancelSignal,
        onLog = { level, message ->
            if (level == "error") Log.w(TAG, "yt-dlp: $message") else Log.i(TAG, "yt-dlp: $message")
        },
    ).also { partials.considerPartial(downloadId, role, File(it.filepath)) }

    private fun downloadFirstWorkingAudio(
        downloadId: String,
        url: String,
        audioFormatIds: List<String>,
        work: File,
        options: YtDlpBridge.DownloadOptions,
    ): Result {
        var lastError: Throwable? = null
        for (id in audioFormatIds) {
            try {
                if (options.archiveMetadata) onStatus("Fetching metadata…")
                val result = fetch(downloadId, "audio", url, id, work, options)
                val file = File(result.filepath)
                return Result(
                    primary = Artifact(
                        file,
                        MediaTypes.forFile(file, preferAudio = true),
                        "audio",
                    ),
                    sidecars = result.sidecars,
                    provenance = result.provenance,
                )
            } catch (t: Throwable) {
                if (isCancelled(t)) throw t
                lastError = t
            }
        }
        throw lastError ?: IllegalStateException("No audio track downloaded")
    }

    private fun downloadThenExtractAudio(
        downloadId: String,
        url: String,
        combinedFormatId: String,
        work: File,
        options: YtDlpBridge.DownloadOptions,
    ): Result {
        if (options.archiveMetadata) onStatus("Fetching metadata…")
        val result = fetch(downloadId, "combined", url, combinedFormatId, work, options)
        val av = File(result.filepath)
        onStatus("Extracting audio…")
        val out = File(work, "audio_only.m4a")
        return try {
            val audio = Media3Muxer(context).extractAudioBlocking(av, out) {
            cancelSignal?.isCancelled() == true
        }
            Result(
                // Deliberately not compared against the source file by size: the
                // extracted track is always smaller than the video it came from.
                primary = Artifact(audio, MediaTypes.forFile(audio, preferAudio = true), "audio"),
                sidecars = result.sidecars,
                provenance = result.provenance,
                remuxTool = MEDIA3,
                remuxOperation = "extract-audio",
            )
        } catch (t: Throwable) {
            if (isCancelled(t) || t is org.gnosco.share2archivetoday.ytdlp.Media3Cancelled) throw t
            Media3Muxer.logFailure(t)
            onStatus("Couldn't extract audio — saving video")
            Result(
                primary = Artifact(av, MediaTypes.forFile(av), "video"),
                sidecars = result.sidecars,
                provenance = result.provenance,
                notes = listOf(
                    "Audio extraction failed on device (${short(t)}); " +
                        "the complete audio/video file was saved instead.",
                ),
            )
        }
    }

    private fun downloadMux(
        downloadId: String,
        url: String,
        videoFormatId: String,
        audioFormatIds: List<String>,
        work: File,
        options: YtDlpBridge.DownloadOptions,
    ): Result {
        if (options.archiveMetadata) onStatus("Fetching metadata…")
        val ids = audioFormatIds.ifEmpty { error("No audio format ids for mux") }

        // One extraction fetches both streams. Doing a separate extract_info per stream
        // tripled the requests to the site for every merged download, which matters for
        // a tool whose users care about not being rate-limited or blocked.
        val pairTemplate = "stream_%(format_id)s.%(ext)s"
        var pair: PairResult? = null
        var lastError: Throwable? = null
        for (aid in ids) {
            try {
                val result = fetch(
                    downloadId,
                    "pair",
                    url,
                    "$videoFormatId,$aid",
                    work,
                    options.copy(outTemplate = pairTemplate),
                )
                val v = result.fileFor(videoFormatId)
                val a = result.fileFor(aid)
                if (v == null || a == null) {
                    // Some extractors collapse a comma selector to a single stream.
                    lastError = IllegalStateException(
                        "Expected two streams for $videoFormatId,$aid; got ${result.files.size}",
                    )
                    continue
                }
                pair = PairResult(File(v.path), File(a.path), result)
                break
            } catch (t: Throwable) {
                if (isCancelled(t)) throw t
                lastError = t
            }
        }

        val resolved = pair ?: downloadStreamsSeparately(
            downloadId, url, videoFormatId, ids, work, options, lastError,
        )
        val videoFile = resolved.video
        val audio = resolved.audio
        val video = resolved.result
        partials.considerPartial(downloadId, "video", videoFile)
        partials.considerPartial(downloadId, "audio", audio)

        onStatus("Merging…")
        val out = File(work, "merged.mp4")
        return try {
            val merged = Media3Muxer(context).muxBlocking(videoFile, audio, out) {
                cancelSignal?.isCancelled() == true
            }
            partials.considerPartial(downloadId, "merged", merged)
            var primaryFile = merged
            val embedNotes = mutableListOf<String>()
            if (options.archiveMetadata) {
                onStatus("Embedding metadata…")
                val embedded = ArchiveMediaEmbedder.embed(
                    media = merged,
                    sidecars = video.sidecars,
                    titleFallback = video.title,
                    provenance = video.provenance,
                )
                primaryFile = embedded.file
                embedNotes += embedded.notes
                if (embedded.embedded.isNotEmpty()) {
                    Log.i(TAG, "Embedded into MP4: ${embedded.embedded}")
                }
            }
            onStatus("Saving…")
            Result(
                primary = Artifact(primaryFile, MediaTypes.forFile(primaryFile), "video"),
                sidecars = video.sidecars,
                provenance = video.provenance,
                remuxTool = MEDIA3,
                remuxOperation = if (options.archiveMetadata) {
                    "mux-audio-video+embed-metadata"
                } else {
                    "mux-audio-video"
                },
                notes = embedNotes,
            )
        } catch (t: Throwable) {
            if (isCancelled(t)) throw t
            // The transfer already succeeded; only the container step failed. Publishing
            // both streams keeps the bytes, and the pair remuxes losslessly off-device.
            Media3Muxer.logFailure(t)
            onStatus("Couldn't merge — saving both streams")
            Result(
                primary = Artifact(videoFile, MediaTypes.forFile(videoFile), "video-stream", ".video"),
                extras = listOf(
                    Artifact(
                        audio,
                        MediaTypes.forFile(audio, preferAudio = true),
                        "audio-stream",
                        ".audio",
                    ),
                ),
                sidecars = video.sidecars,
                provenance = video.provenance,
                notes = listOf(
                    "On-device merge failed (${short(t)}). Video and audio were saved as " +
                        "separate files; merge them losslessly with " +
                        "\"ffmpeg -i *.video.* -i *.audio.* -c copy out.mkv\".",
                ),
            )
        }
    }

    private class PairResult(
        val video: File,
        val audio: File,
        val result: org.gnosco.share2archivetoday.ytdlp.DownloadResult,
    )

    /**
     * Fallback for extractors that will not honour a comma format selector: fetch the
     * streams one at a time, as before, at the cost of an extra extraction.
     */
    private fun downloadStreamsSeparately(
        downloadId: String,
        url: String,
        videoFormatId: String,
        audioFormatIds: List<String>,
        work: File,
        options: YtDlpBridge.DownloadOptions,
        pairError: Throwable?,
    ): PairResult {
        Log.w(TAG, "Falling back to per-stream extraction", pairError)
        val video = fetch(
            downloadId, "video", url, videoFormatId, work,
            options.copy(outTemplate = "video.%(ext)s"),
        )
        var lastError: Throwable? = pairError
        for (aid in audioFormatIds) {
            try {
                val audio = fetch(
                    downloadId, "audio", url, aid, work,
                    options.copy(
                        outTemplate = "audio_%(format_id)s.%(ext)s",
                        archiveMetadata = false,
                        writeSubtitles = false,
                    ),
                )
                return PairResult(File(video.filepath), File(audio.filepath), video)
            } catch (t: Throwable) {
                if (isCancelled(t)) throw t
                lastError = t
            }
        }
        throw lastError ?: IllegalStateException("No audio track")
    }

    /** Cancellation must abort the whole job, not be swallowed by a per-format retry loop. */
    private fun isCancelled(t: Throwable): Boolean =
        cancelSignal?.isCancelled() == true ||
            t is org.gnosco.share2archivetoday.ytdlp.Media3Cancelled ||
            YtDlpFailureClassifier.classify(t) == YtDlpFailureClassifier.Kind.CANCELLED

    private fun short(t: Throwable): String =
        (t.message ?: t::class.java.simpleName).take(120)

    companion object {
        private const val TAG = "DownloadExecutor"
        private const val MEDIA3 = "androidx.media3 Transformer"
    }
}
