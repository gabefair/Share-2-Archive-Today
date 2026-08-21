package org.gnosco.share2archivetoday.download.service

import android.content.Context
import java.io.File
import org.gnosco.share2archivetoday.download.history.BestPartialStore
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

    data class Result(val file: File, val mimeType: String)

    fun execute(
        downloadId: String,
        url: String,
        videoFormatId: String?,
        audioFormatIds: List<String>,
        combinedFormatId: String?,
        needsMux: Boolean,
        audioOnly: Boolean,
        requiresVideoExtract: Boolean,
    ): Result {
        val work = partials.workDir(downloadId)
        return when {
            audioOnly && requiresVideoExtract && combinedFormatId != null ->
                downloadThenExtractAudio(downloadId, url, combinedFormatId, work)

            audioOnly && audioFormatIds.isNotEmpty() ->
                downloadFirstWorkingAudio(downloadId, url, audioFormatIds, work)

            needsMux && videoFormatId != null ->
                downloadMux(downloadId, url, videoFormatId, audioFormatIds, work)

            combinedFormatId != null -> {
                val result = bridge.download(url, combinedFormatId, work.absolutePath, continuedl = true) {
                    onProgress(it.downloaded, it.total)
                }
                val file = File(result.filepath).also { partials.considerPartial(downloadId, it) }
                Result(file, "video/mp4")
            }

            else -> error("Invalid download parameters")
        }
    }

    private fun downloadFirstWorkingAudio(
        downloadId: String,
        url: String,
        audioFormatIds: List<String>,
        work: File,
    ): Result {
        var lastError: Throwable? = null
        for (id in audioFormatIds) {
            try {
                val result = bridge.download(url, id, work.absolutePath, continuedl = true) {
                    onProgress(it.downloaded, it.total)
                }
                val file = File(result.filepath).also { partials.considerPartial(downloadId, it) }
                return Result(file, guessAudioMime(file))
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
    ): Result {
        val result = bridge.download(url, combinedFormatId, work.absolutePath, continuedl = true) {
            onProgress(it.downloaded, it.total)
        }
        val av = File(result.filepath).also { partials.considerPartial(downloadId, it) }
        onStatus("Extracting audio…")
        val out = File(work, "audio_only.m4a")
        val audio = Media3Muxer(context).extractAudioBlocking(av, out)
        partials.considerPartial(downloadId, audio)
        return Result(audio, "audio/mp4")
    }

    private fun downloadMux(
        downloadId: String,
        url: String,
        videoFormatId: String,
        audioFormatIds: List<String>,
        work: File,
    ): Result {
        val video = bridge.download(
            url, videoFormatId, work.absolutePath,
            outTemplate = "video.%(ext)s", continuedl = true,
        ) { onProgress(it.downloaded, it.total) }
        val videoFile = File(video.filepath).also { partials.considerPartial(downloadId, it) }

        val ids = audioFormatIds.ifEmpty { error("No audio format ids for mux") }
        var audioFile: File? = null
        var lastError: Throwable? = null
        for (aid in ids) {
            try {
                val audio = bridge.download(
                    url, aid, work.absolutePath,
                    outTemplate = "audio_%(format_id)s.%(ext)s", continuedl = true,
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
        partials.considerPartial(downloadId, merged)
        return Result(merged, "video/mp4")
    }

    private fun guessAudioMime(file: File): String = when (file.extension.lowercase()) {
        "mp3" -> "audio/mpeg"
        "m4a", "mp4" -> "audio/mp4"
        "webm" -> "audio/webm"
        "opus" -> "audio/opus"
        else -> "audio/*"
    }
}
