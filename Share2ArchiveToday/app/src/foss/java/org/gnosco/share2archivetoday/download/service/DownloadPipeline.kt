package org.gnosco.share2archivetoday.download.service

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import org.gnosco.share2archivetoday.ArchiveToday
import org.gnosco.share2archivetoday.BuildConfig
import org.gnosco.share2archivetoday.R
import org.gnosco.share2archivetoday.download.DownloadErrorMessages
import org.gnosco.share2archivetoday.download.history.BestPartialStore
import org.gnosco.share2archivetoday.download.history.DownloadHistoryStore
import org.gnosco.share2archivetoday.download.history.HistoryEntry
import org.gnosco.share2archivetoday.download.history.RetrySpec
import org.gnosco.share2archivetoday.ytdlp.ArchiveManifest
import org.gnosco.share2archivetoday.ytdlp.DownloadNaming
import org.gnosco.share2archivetoday.ytdlp.DownloadSidecar
import org.gnosco.share2archivetoday.ytdlp.DownloadsPublisher
import org.gnosco.share2archivetoday.ytdlp.MediaTypes
import org.gnosco.share2archivetoday.ytdlp.YtDlpBridge
import org.gnosco.share2archivetoday.ytdlp.YtDlpFailureClassifier

/** Parameters for one download job (service, worker, or history retry). */
data class DownloadRequest(
    val downloadId: String,
    val url: String,
    val originalUrl: String,
    val title: String,
    val videoFormatId: String? = null,
    val audioFormatIds: List<String> = emptyList(),
    val combinedFormatId: String? = null,
    val needsMux: Boolean = false,
    val audioOnly: Boolean = false,
    val requiresVideoExtract: Boolean = false,
    val archiveMetadata: Boolean = false,
    val includeComments: Boolean = false,
    val estimatedBytes: Long? = null,
    val webpageUrl: String? = null,
    val videoId: String? = null,
    val wifiOnly: Boolean = false,
) {
    fun toRetrySpec() = RetrySpec(
        url = url,
        originalUrl = originalUrl,
        videoFormatId = videoFormatId,
        audioFormatIds = audioFormatIds,
        combinedFormatId = combinedFormatId,
        needsMux = needsMux,
        audioOnly = audioOnly,
        requiresVideoExtract = requiresVideoExtract,
        archiveMetadata = archiveMetadata,
        includeComments = includeComments,
        estimatedBytes = estimatedBytes,
        wifiOnly = wifiOnly,
    )

    companion object {
        fun fromRetry(downloadId: String, title: String, retry: RetrySpec, videoId: String? = null, webpageUrl: String? = null) =
            DownloadRequest(
                downloadId = downloadId,
                url = retry.url,
                originalUrl = retry.originalUrl,
                title = title,
                videoFormatId = retry.videoFormatId,
                audioFormatIds = retry.audioFormatIds,
                combinedFormatId = retry.combinedFormatId,
                needsMux = retry.needsMux,
                audioOnly = retry.audioOnly,
                requiresVideoExtract = retry.requiresVideoExtract,
                archiveMetadata = retry.archiveMetadata,
                includeComments = retry.includeComments,
                estimatedBytes = retry.estimatedBytes,
                webpageUrl = webpageUrl,
                videoId = videoId,
                wifiOnly = retry.wifiOnly,
            )
    }
}

sealed class DownloadOutcome {
    data class Success(val uri: Uri) : DownloadOutcome()
    data class Failed(val message: String, val retryable: Boolean) : DownloadOutcome()
    data object Cancelled : DownloadOutcome()
}

/**
 * Runs yt-dlp + publish + history for one [DownloadRequest].
 * Shared by WorkManager; cancel is polled via [cancelSignal].
 */
class DownloadPipeline(
    private val context: Context,
    private val onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    private val onStatus: (String) -> Unit = {},
) {
    private val history = DownloadHistoryStore(context)
    private val partials = BestPartialStore(context)

    fun run(request: DownloadRequest, cancelSignal: AtomicBoolean): DownloadOutcome {
        val finished = AtomicBoolean(false)
        return try {
            val copies = DownloadsPublisher.peakCopies(request.needsMux, request.requiresVideoExtract)
            if (!DownloadsPublisher.hasRoomFor(context, request.estimatedBytes, copies)) {
                throw java.io.IOException("Not enough space left on device for this download")
            }

            val executor = DownloadExecutor(
                context = context,
                bridge = YtDlpBridge.get(context),
                partials = partials,
                onProgress = onProgress,
                onStatus = onStatus,
                cancelSignal = { cancelSignal.get() },
            )
            val result = executor.execute(
                downloadId = request.downloadId,
                url = request.url,
                videoFormatId = request.videoFormatId,
                audioFormatIds = request.audioFormatIds,
                combinedFormatId = request.combinedFormatId,
                needsMux = request.needsMux,
                audioOnly = request.audioOnly,
                requiresVideoExtract = request.requiresVideoExtract,
                archiveMetadata = request.archiveMetadata,
                includeComments = request.includeComments,
                writeSubtitles = request.archiveMetadata,
            )

            if (cancelSignal.get()) return DownloadOutcome.Cancelled.also { recordCancel(request) }

            // Re-check space before the publish copy (mux peak may have filled the disk).
            if (!DownloadsPublisher.hasRoomFor(context, result.primary.file.length(), 2)) {
                throw java.io.IOException("Not enough space left on device for this download")
            }

            onStatus("Saving…")
            val published = publishAll(request, result)
            val videoId = request.videoId
                ?: result.provenance?.optString("id")?.takeIf { it.isNotBlank() }
            val webpageUrl = request.webpageUrl
                ?: result.provenance?.optString("webpage_url")?.takeIf { it.isNotBlank() }

            history.add(
                HistoryEntry(
                    id = request.downloadId,
                    url = request.originalUrl,
                    title = request.title,
                    mediaStoreUri = published.toString(),
                    success = true,
                    error = null,
                    timestamp = System.currentTimeMillis(),
                    estimatedBytes = result.primary.file.length(),
                    webpageUrl = webpageUrl,
                    videoId = videoId,
                    retry = request.toRetrySpec(),
                )
            )
            partials.clear(request.downloadId)
            finished.set(true)
            DownloadOutcome.Success(published)
        } catch (t: Throwable) {
            val kind = YtDlpFailureClassifier.classify(t)
            val cancelled = cancelSignal.get() || kind == YtDlpFailureClassifier.Kind.CANCELLED
            if (cancelled) {
                recordCancel(request)
                return DownloadOutcome.Cancelled
            }
            val friendly = DownloadErrorMessages.message(context, t)
            Log.e(TAG, "Download failed kind=$kind for ${request.url}", t)
            history.add(
                HistoryEntry(
                    id = request.downloadId,
                    url = request.originalUrl,
                    title = request.title,
                    mediaStoreUri = null,
                    success = false,
                    error = friendly,
                    timestamp = System.currentTimeMillis(),
                    estimatedBytes = null,
                    webpageUrl = request.webpageUrl,
                    videoId = request.videoId,
                    retry = request.toRetrySpec(),
                )
            )
            val retryable = kind == YtDlpFailureClassifier.Kind.NETWORK ||
                kind == YtDlpFailureClassifier.Kind.HTTP_FORBIDDEN
            DownloadOutcome.Failed(friendly, retryable)
        } finally {
            runCatching { partials.gc(keepIds = setOf(request.downloadId)) }
        }
    }

    private fun recordCancel(request: DownloadRequest) {
        history.add(
            HistoryEntry(
                id = request.downloadId,
                url = request.originalUrl,
                title = request.title,
                mediaStoreUri = null,
                success = false,
                error = context.getString(R.string.download_cancelled),
                timestamp = System.currentTimeMillis(),
                estimatedBytes = null,
                webpageUrl = request.webpageUrl,
                videoId = request.videoId,
                retry = request.toRetrySpec(),
            )
        )
    }

    private fun publishAll(request: DownloadRequest, result: DownloadExecutor.Result): Uri {
        val baseName = DownloadNaming.sanitize(request.title)
        val sidecarFiles = result.sidecars.map { File(it.path) to it.kind }.filter { it.first.isFile }
        val multiFile = result.allArtifacts.size > 1 || sidecarFiles.isNotEmpty()
        val folder = if (multiFile) {
            val id = result.provenance?.optString("id").orEmpty()
            DownloadNaming.folderName(
                baseName,
                DownloadNaming.sanitize(id.ifBlank { request.downloadId }, maxChars = 16),
            )
        } else {
            null
        }

        val manifestArtifacts = mutableListOf<ArchiveManifest.Artifact>()
        var primaryUri: Uri? = null

        for (artifact in result.allArtifacts) {
            val name = DownloadNaming.artifactName(
                baseName, artifact.nameSuffix, artifact.file.extension,
            )
            val published = DownloadsPublisher.publish(
                context, artifact.file, name, artifact.mimeType, folder,
            )
            if (primaryUri == null) primaryUri = published.uri
            manifestArtifacts += ArchiveManifest.Artifact(
                displayName = published.displayName,
                file = artifact.file,
                kind = artifact.role,
            )
        }

        for ((file, kind) in sidecarFiles) {
            val name = DownloadNaming.sidecarName(baseName, file.name)
            runCatching {
                val published = DownloadsPublisher.publish(
                    context, file, name, MediaTypes.sidecarMime(file.name), folder,
                )
                manifestArtifacts += ArchiveManifest.Artifact(
                    displayName = published.displayName,
                    file = file,
                    kind = kind,
                )
            }.onFailure { Log.w(TAG, "Failed to publish sidecar ${file.name}", it) }
        }

        writeManifest(baseName, folder, request, result, manifestArtifacts)
        return primaryUri ?: error("Nothing was published")
    }

    private fun writeManifest(
        baseName: String,
        folder: String?,
        request: DownloadRequest,
        result: DownloadExecutor.Result,
        artifacts: List<ArchiveManifest.Artifact>,
    ) {
        runCatching {
            val json = ArchiveManifest.build(
                capture = ArchiveManifest.Capture(
                    originalUrl = request.originalUrl,
                    resolvedUrl = request.url.takeIf { it != request.originalUrl },
                    appVersion = BuildConfig.VERSION_NAME,
                    appFlavor = BuildConfig.FLAVOR,
                    ytdlpVersion = runCatching { YtDlpBridge.get(context).version }
                        .getOrDefault("unknown"),
                    remuxTool = result.remuxTool,
                    remuxOperation = result.remuxOperation,
                    pageArchiveUrl = ArchiveToday.submissionUrl(request.originalUrl),
                    notes = result.notes,
                ),
                provenance = result.provenance,
                artifacts = artifacts,
            )
            DownloadsPublisher.publishBytes(
                context,
                json.toByteArray(Charsets.UTF_8),
                baseName + ArchiveManifest.FILE_SUFFIX,
                "application/json",
                folder,
            )
        }.onFailure { Log.w(TAG, "Failed to write archive manifest", it) }
    }

    companion object {
        private const val TAG = "DownloadPipeline"
    }
}
