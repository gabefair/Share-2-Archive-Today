package org.gnosco.share2archivetoday.download.service

import android.content.Context
import java.io.File
import org.gnosco.share2archivetoday.download.history.BestPartialStore
import org.gnosco.share2archivetoday.ytdlp.DownloadSidecar
import org.gnosco.share2archivetoday.ytdlp.Media3Muxer
import org.gnosco.share2archivetoday.ytdlp.YtDlpBridge

/** Runs yt-dlp download + optional Media3 mux/extract. Keeps [VideoDownloadService] slim. */
class DownloadExecutor(
    private val context: Context,
    private val bridge: YtDlpBridge,
    private val partials: BestPartialStore,
    private val onProgress: (downloaded: Long, total: Long) -> Unit,
    private val onStatus: (String) -> Unit,
) {

    data class Result(
        val file: File,
        val mimeType: String,
        val sidecars: List<DownloadSidecar> = emptyList(),
    )

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
    ): Result {
        val work = partials.workDir(downloadId)
        return when {
            audioOnly && requiresVideoExtract && combinedFormatId != null ->
                downloadThenExtractAudio(downloadId, url, combinedFormatId, work, archiveMetadata)

            audioOnly && audioFormatIds.isNotEmpty() ->
                downloadFirstWorkingAudio(downloadId, url, audioFormatIds, work, archiveMetadata)

            needsMux && videoFormatId != null ->
                downloadMux(downloadId, url, videoFormatId, audioFormatIds, work, archiveMetadata)

            combinedFormatId != null -> {
                if (archiveMetadata) onStatus("Fetching metadata…")
                val result = bridge.download(
                    url, combinedFormatId, work.absolutePath,
                    continuedl = true,
                    archiveMetadata = archiveMetadata,
                ) {
                    onProgress(it.downloaded, it.total)
                }
                val file = File(result.filepath).also { partials.considerPartial(downloadId, it) }
                Result(file, "video/mp4", result.sidecars)
            }

            else -> error("Invalid download parameters")
        }
    }

    private fun downloadFirstWorkingAudio(
        downloadId: String,
        url: String,
        audioFormatIds: List<String>,
        work: File,
        archiveMetadata: Boolean,
    ): Result {
        var lastError: Throwable? = null
        for (id in audioFormatIds) {
            try {
                if (archiveMetadata) onStatus("Fetching metadata…")
                val result = bridge.download(
                    url, id, work.absolutePath,
                    continuedl = true,
                    archiveMetadata = archiveMetadata,
                ) {
                    onProgress(it.downloaded, it.total)
                }
                val file = File(result.filepath).also { partials.considerPartial(downloadId, it) }
                return Result(file, guessAudioMime(file), result.sidecars)
            } catch (t: Throwable) {
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
        archiveMetadata: Boolean,
    ): Result {
        if (archiveMetadata) onStatus("Fetching metadata…")
        val result = bridge.download(
            url, combinedFormatId, work.absolutePath,
            continuedl = true,
            archiveMetadata = archiveMetadata,
        ) {
            onProgress(it.downloaded, it.total)
        }
        val av = File(result.filepath).also { partials.considerPartial(downloadId, it) }
        onStatus("Extracting audio…")
        val out = File(work, "audio_only.m4a")
        val audio = Media3Muxer(context).extractAudioBlocking(av, out)
        partials.considerPartial(downloadId, audio)
        return Result(audio, "audio/mp4", result.sidecars)
    }

    private fun downloadMux(
        downloadId: String,
        url: String,
        videoFormatId: String,
        audioFormatIds: List<String>,
        work: File,
        archiveMetadata: Boolean,
    ): Result {
        if (archiveMetadata) onStatus("Fetching metadata…")
        val video = bridge.download(
            url, videoFormatId, work.absolutePath,
            outTemplate = "video.%(ext)s",
            continuedl = true,
            archiveMetadata = archiveMetadata,
        ) { onProgress(it.downloaded, it.total) }
        val videoFile = File(video.filepath).also { partials.considerPartial(downloadId, it) }

        val ids = audioFormatIds.ifEmpty { error("No audio format ids for mux") }
        var audioFile: File? = null
        var lastError: Throwable? = null
        for (aid in ids) {
            try {
                val audio = bridge.download(
                    url, aid, work.absolutePath,
                    outTemplate = "audio_%(format_id)s.%(ext)s",
                    continuedl = true,
                    archiveMetadata = false,
                ) { onProgress(it.downloaded, it.total) }
                audioFile = File(audio.filepath)
                break
            } catch (t: Throwable) {
                lastError = t
            }
        }
        val audio = audioFile ?: throw lastError ?: IllegalStateException("No audio track")
        onStatus("Merging…")
        val out = File(work, "merged.mp4")
        val merged = Media3Muxer(context).muxBlocking(videoFile, audio, out)
        onStatus("Saving…")
        partials.considerPartial(downloadId, merged)
        return Result(merged, "video/mp4", video.sidecars)
    }

    private fun guessAudioMime(file: File): String = when (file.extension.lowercase()) {
        "mp3" -> "audio/mpeg"
        "m4a", "mp4" -> "audio/mp4"
        "webm" -> "audio/webm"
        "opus" -> "audio/opus"
        else -> "audio/*"
    }
}
