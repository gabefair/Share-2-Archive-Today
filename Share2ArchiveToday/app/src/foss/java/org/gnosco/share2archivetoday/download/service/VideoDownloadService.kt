package org.gnosco.share2archivetoday.download.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.gnosco.share2archivetoday.ArchiveToday
import org.gnosco.share2archivetoday.BuildConfig
import org.gnosco.share2archivetoday.R
import org.gnosco.share2archivetoday.download.DownloadErrorMessages
import org.gnosco.share2archivetoday.download.history.BestPartialStore
import org.gnosco.share2archivetoday.download.history.DownloadHistoryStore
import org.gnosco.share2archivetoday.download.history.HistoryEntry
import org.gnosco.share2archivetoday.download.ui.DownloadHistoryActivity
import org.gnosco.share2archivetoday.ytdlp.ArchiveManifest
import org.gnosco.share2archivetoday.ytdlp.DownloadNaming
import org.gnosco.share2archivetoday.ytdlp.DownloadSidecar
import org.gnosco.share2archivetoday.ytdlp.DownloadsPublisher
import org.gnosco.share2archivetoday.ytdlp.MediaTypes
import org.gnosco.share2archivetoday.ytdlp.YtDlpBridge
import org.gnosco.share2archivetoday.ytdlp.YtDlpFailureClassifier

class VideoDownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var history: DownloadHistoryStore
    private lateinit var partials: BestPartialStore

    /** Cancel flags polled by the Python progress hook, keyed by download id. */
    private val cancelFlags = ConcurrentHashMap<String, AtomicBoolean>()

    @Volatile private var currentTitle: String = ""
    @Volatile private var timedOut = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        history = DownloadHistoryStore(this)
        partials = BestPartialStore(this)
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            val target = intent.getStringExtra(EXTRA_DOWNLOAD_ID)
            cancelDownloads(target)
            return START_NOT_STICKY
        }

        val url = intent?.getStringExtra(EXTRA_URL) ?: return stopAndFinish()
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "video"
        val originalUrl = intent.getStringExtra(EXTRA_ORIGINAL_URL) ?: url
        val videoFormatId = intent.getStringExtra(EXTRA_VIDEO_FORMAT_ID)
        val audioFormatIds = intent.getStringArrayListExtra(EXTRA_AUDIO_FORMAT_IDS).orEmpty()
        val combinedFormatId = intent.getStringExtra(EXTRA_COMBINED_FORMAT_ID)
        val needsMux = intent.getBooleanExtra(EXTRA_NEEDS_MUX, false)
        val audioOnly = intent.getBooleanExtra(EXTRA_AUDIO_ONLY, false)
        val requiresVideoExtract = intent.getBooleanExtra(EXTRA_REQUIRES_VIDEO_EXTRACT, false)
        val archiveMetadata = intent.getBooleanExtra(EXTRA_ARCHIVE_METADATA, false)
        val includeComments = intent.getBooleanExtra(EXTRA_INCLUDE_COMMENTS, false)
        val estimatedBytes = intent.getLongExtra(EXTRA_ESTIMATED_BYTES, 0L).takeIf { it > 0 }
        val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID)
            ?: DownloadNaming.downloadId(url, videoFormatId, audioFormatIds, combinedFormatId)

        val cancelled = AtomicBoolean(false)
        // Ids are derived from url + format, so re-sharing the same link while it is
        // downloading would otherwise start a second identical job over the same files.
        val duplicate = cancelFlags.putIfAbsent(downloadId, cancelled) != null
        currentTitle = title

        startForegroundCompat(
            buildNotification(
                if (duplicate) "Downloading…" else "Starting download…",
                title,
                downloadId,
                indeterminate = true,
            )
        )
        if (duplicate) {
            // Deliberately no stopSelf: this startId is the most recent, so stopping on
            // it would tear down the download that is already running.
            Log.i(TAG, "Ignoring duplicate start for $downloadId")
            return START_NOT_STICKY
        }

        scope.launch {
            try {
                // Publishing copies rather than moves, so the work file and the copy in
                // Downloads coexist. Finding this out mid-copy would waste the transfer.
                if (!DownloadsPublisher.hasRoomFor(this@VideoDownloadService, estimatedBytes)) {
                    throw java.io.IOException(
                        "Not enough space left on device for this download",
                    )
                }

                val executor = DownloadExecutor(
                    context = this@VideoDownloadService,
                    bridge = YtDlpBridge.get(this@VideoDownloadService),
                    partials = partials,
                    onProgress = { done, total ->
                        postProgress(title, downloadId, done, total)
                    },
                    onStatus = { msg -> postStatus(msg, title, downloadId) },
                    cancelSignal = { cancelled.get() },
                )
                val result = executor.execute(
                    downloadId = downloadId,
                    url = url,
                    videoFormatId = videoFormatId,
                    audioFormatIds = audioFormatIds,
                    combinedFormatId = combinedFormatId,
                    needsMux = needsMux,
                    audioOnly = audioOnly,
                    requiresVideoExtract = requiresVideoExtract,
                    archiveMetadata = archiveMetadata,
                    includeComments = includeComments,
                    writeSubtitles = archiveMetadata,
                )

                postStatus("Saving…", title, downloadId)
                val published = publishAll(title, originalUrl, url, result)

                history.add(
                    HistoryEntry(
                        id = downloadId,
                        url = originalUrl,
                        title = title,
                        mediaStoreUri = published.primaryUri.toString(),
                        success = true,
                        error = null,
                        timestamp = System.currentTimeMillis(),
                        estimatedBytes = result.primary.file.length(),
                    )
                )
                partials.clear(downloadId)
                notifyFinished(true, title, published.primaryUri, null, pageUrl = originalUrl)
            } catch (t: Throwable) {
                val kind = YtDlpFailureClassifier.classify(t)
                val userCancelled = cancelled.get() ||
                    kind == YtDlpFailureClassifier.Kind.CANCELLED
                val friendly = when {
                    timedOut -> getString(R.string.download_timeout)
                    userCancelled -> getString(R.string.download_cancelled)
                    else -> DownloadErrorMessages.message(this@VideoDownloadService, t)
                }
                if (userCancelled) {
                    Log.i(TAG, "Download cancelled for $url")
                } else {
                    Log.e(TAG, "Download failed kind=$kind for $url", t)
                }
                history.add(
                    HistoryEntry(
                        id = downloadId,
                        url = originalUrl,
                        title = title,
                        mediaStoreUri = null,
                        success = false,
                        error = friendly,
                        timestamp = System.currentTimeMillis(),
                        estimatedBytes = null,
                    )
                )
                // Partials are deliberately kept so a retry can resume; the sweep below
                // removes them once they are clearly abandoned.
                notifyFinished(false, title, null, friendly, silent = userCancelled && !timedOut)
            } finally {
                cancelFlags.remove(downloadId)
                // Runs after the download so a resume can never race the sweep.
                runCatching { partials.gc(keepIds = cancelFlags.keys.toSet() + downloadId) }
                    .onSuccess { if (it > 0) Log.i(TAG, "Removed $it abandoned partial dir(s)") }
                // Stop only once nothing is in flight; stopSelf(startId) would not fire
                // for a job whose start was superseded by a later one.
                if (cancelFlags.isEmpty()) stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private data class PublishOutcome(val primaryUri: Uri, val folder: String?)

    private fun publishAll(
        title: String,
        originalUrl: String,
        resolvedUrl: String,
        result: DownloadExecutor.Result,
    ): PublishOutcome {
        val baseName = DownloadNaming.sanitize(title)
        val sidecarFiles = result.sidecars.map(::sidecarFile).filter { it.first.isFile }
        // A single media file stays directly in Share2Archive/; anything with companions
        // gets its own folder so the set cannot be split up or mismatched later.
        val multiFile = result.allArtifacts.size > 1 || sidecarFiles.isNotEmpty()
        val folder = if (multiFile) {
            DownloadNaming.folderName(baseName, shortIdFor(result))
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
                this, artifact.file, name, artifact.mimeType, folder,
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
                    this, file, name, MediaTypes.sidecarMime(file.name), folder,
                )
                manifestArtifacts += ArchiveManifest.Artifact(
                    displayName = published.displayName,
                    file = file,
                    kind = kind,
                )
            }.onFailure { Log.w(TAG, "Failed to publish sidecar ${file.name}", it) }
        }

        writeManifest(baseName, folder, originalUrl, resolvedUrl, result, manifestArtifacts)
        return PublishOutcome(primaryUri ?: error("Nothing was published"), folder)
    }

    private fun writeManifest(
        baseName: String,
        folder: String?,
        originalUrl: String,
        resolvedUrl: String,
        result: DownloadExecutor.Result,
        artifacts: List<ArchiveManifest.Artifact>,
    ) {
        runCatching {
            val json = ArchiveManifest.build(
                capture = ArchiveManifest.Capture(
                    originalUrl = originalUrl,
                    resolvedUrl = resolvedUrl.takeIf { it != originalUrl },
                    appVersion = BuildConfig.VERSION_NAME,
                    appFlavor = BuildConfig.FLAVOR,
                    ytdlpVersion = runCatching { YtDlpBridge.get(this).version }
                        .getOrDefault("unknown"),
                    remuxTool = result.remuxTool,
                    remuxOperation = result.remuxOperation,
                    pageArchiveUrl = ArchiveToday.submissionUrl(originalUrl),
                    notes = result.notes,
                ),
                provenance = result.provenance,
                artifacts = artifacts,
            )
            DownloadsPublisher.publishBytes(
                this,
                json.toByteArray(Charsets.UTF_8),
                baseName + ArchiveManifest.FILE_SUFFIX,
                "application/json",
                folder,
            )
        }.onFailure { Log.w(TAG, "Failed to write archive manifest", it) }
    }

    private fun sidecarFile(sidecar: DownloadSidecar): Pair<File, String> =
        File(sidecar.path) to sidecar.kind

    private fun shortIdFor(result: DownloadExecutor.Result): String {
        val id = result.provenance?.optString("id").orEmpty()
        return DownloadNaming.sanitize(
            id.ifBlank { java.util.UUID.randomUUID().toString() },
            maxChars = 16,
        )
    }

    // Notification bookkeeping is confined to the main thread: progress arrives on the
    // Python thread, and Service lifecycle calls belong on main anyway.
    private var lastNotifyAtMs = 0L
    private var lastNotifyPct = -1
    private var lastStatusText: String? = null

    private fun postProgress(title: String, downloadId: String, downloaded: Long, total: Long) {
        mainHandler.post { updateFromProgress(title, downloadId, downloaded, total) }
    }

    private fun postStatus(content: String, title: String, downloadId: String) {
        mainHandler.post { updateStatus(content, title, downloadId) }
    }

    private fun updateFromProgress(
        title: String,
        downloadId: String,
        downloaded: Long,
        total: Long,
    ) {
        val now = System.currentTimeMillis()
        if (total > 0) {
            val pct = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
            // Android sheds package notifies around ~5/s; keep well under that.
            if (pct != 100 && pct == lastNotifyPct) return
            if (pct != 100 && now - lastNotifyAtMs < NOTIFY_MIN_INTERVAL_MS) return
            if (pct != 100 && pct - lastNotifyPct < NOTIFY_MIN_PCT_STEP) return
            lastNotifyPct = pct
            lastNotifyAtMs = now
            lastStatusText = null
            publishOngoing("Downloading… $pct%", title, downloadId, pct, 100, indeterminate = false)
        } else if (now - lastNotifyAtMs >= NOTIFY_MIN_INTERVAL_MS) {
            lastNotifyAtMs = now
            publishOngoing("Downloading…", title, downloadId, indeterminate = true)
        }
    }

    private fun updateStatus(content: String, title: String, downloadId: String) {
        val now = System.currentTimeMillis()
        if (content == lastStatusText) return
        // Status changes (metadata -> merging) should show, but not reset the rate budget.
        if (now - lastNotifyAtMs < NOTIFY_STATUS_MIN_INTERVAL_MS) return
        lastStatusText = content
        lastNotifyAtMs = now
        publishOngoing(content, title, downloadId, indeterminate = true)
    }

    /**
     * Update the FGS notification via [startForeground] instead of [NotificationManager.notify]
     * so progress updates are less likely to be shed by the system enqueue rate limiter.
     */
    private fun publishOngoing(
        content: String,
        title: String,
        downloadId: String,
        progress: Int = 0,
        max: Int = 0,
        indeterminate: Boolean = true,
    ) {
        startForegroundCompat(
            buildNotification(content, title, downloadId, progress, max, indeterminate)
        )
    }

    private fun startForegroundCompat(notification: Notification) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    private fun cancelDownloads(downloadId: String?) {
        val entries = if (downloadId != null) {
            cancelFlags.filterKeys { it == downloadId }
        } else {
            cancelFlags.toMap()
        }
        if (entries.isEmpty()) return
        entries.values.forEach { it.set(true) }
        val id = downloadId ?: entries.keys.first()
        mainHandler.post {
            publishOngoing("Cancelling…", currentTitle, id, indeterminate = true)
        }
    }

    /**
     * Android 15+ caps how long a dataSync foreground service may run. The system calls
     * this when the budget is spent and expects the service to stop; not stopping is an
     * ANR. Partial files are left in place so the download can be retried and resumed.
     */
    @RequiresApi(35)
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "Foreground service timed out; stopping and keeping partial data")
        timedOut = true
        cancelDownloads(null)
        notifyFinished(
            success = false,
            title = getString(R.string.download_timeout_title),
            uri = null,
            error = getString(R.string.download_timeout),
        )
        stopSelf(startId)
    }

    private fun notifyFinished(
        success: Boolean,
        title: String,
        uri: Uri?,
        error: String?,
        silent: Boolean = false,
        pageUrl: String? = null,
    ) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        stopForeground(STOP_FOREGROUND_REMOVE)
        nm.cancel(NOTIFICATION_ID)
        if (silent) return
        val text = if (success) "Download complete" else (error ?: "Download failed")
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.download_history_action), historyPendingIntent())
        // The video alone loses the page around it; offer the snapshot in the same breath.
        pageUrl?.let { builder.addAction(0, getString(R.string.download_archive_page), archivePendingIntent(it)) }
        if (success && uri != null) {
            val open = org.gnosco.share2archivetoday.download.OpenDownloadedMedia
                .viewIntent(this, uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            builder.setContentIntent(
                PendingIntent.getActivity(
                    this, 2, open,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
        }
        nm.notify(NOTIFICATION_DONE_ID, builder.build())
    }

    private fun buildNotification(
        content: String,
        title: String,
        downloadId: String,
        progress: Int = 0,
        max: Int = 0,
        indeterminate: Boolean = true,
    ): Notification {
        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(content)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(0, getString(R.string.download_cancel), cancelPendingIntent(downloadId))
            .addAction(0, getString(R.string.download_history_action), historyPendingIntent())
        if (indeterminate) b.setProgress(0, 0, true) else b.setProgress(max, progress, false)
        return b.build()
    }

    private fun cancelPendingIntent(downloadId: String): PendingIntent {
        val i = Intent(this, VideoDownloadService::class.java)
            .setAction(ACTION_CANCEL)
            .putExtra(EXTRA_DOWNLOAD_ID, downloadId)
        return PendingIntent.getService(
            this, 3, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun archivePendingIntent(pageUrl: String): PendingIntent {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(ArchiveToday.submissionUrl(pageUrl)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            this, 4, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun historyPendingIntent(): PendingIntent {
        val i = Intent(this, DownloadHistoryActivity::class.java)
        return PendingIntent.getActivity(
            this, 1, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    private fun stopAndFinish(): Int {
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        cancelDownloads(null)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "VideoDownload"
        const val CHANNEL_ID = "video_downloads"
        const val NOTIFICATION_ID = 42
        const val NOTIFICATION_DONE_ID = 43
        private const val NOTIFY_MIN_INTERVAL_MS = 2_500L
        private const val NOTIFY_STATUS_MIN_INTERVAL_MS = 1_200L
        private const val NOTIFY_MIN_PCT_STEP = 10

        const val ACTION_CANCEL = "org.gnosco.share2archivetoday.action.CANCEL_DOWNLOAD"

        const val EXTRA_URL = "url"
        const val EXTRA_ORIGINAL_URL = "original_url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_VIDEO_FORMAT_ID = "video_format_id"
        const val EXTRA_AUDIO_FORMAT_IDS = "audio_format_ids"
        const val EXTRA_COMBINED_FORMAT_ID = "combined_format_id"
        const val EXTRA_NEEDS_MUX = "needs_mux"
        const val EXTRA_AUDIO_ONLY = "audio_only"
        const val EXTRA_REQUIRES_VIDEO_EXTRACT = "requires_video_extract"
        const val EXTRA_ARCHIVE_METADATA = "archive_metadata"
        const val EXTRA_INCLUDE_COMMENTS = "include_comments"
        const val EXTRA_ESTIMATED_BYTES = "estimated_bytes"
        const val EXTRA_DOWNLOAD_ID = "download_id"

        fun start(
            context: Context,
            url: String,
            title: String,
            originalUrl: String = url,
            videoFormatId: String? = null,
            audioFormatIds: List<String> = emptyList(),
            combinedFormatId: String? = null,
            needsMux: Boolean = false,
            audioOnly: Boolean = false,
            requiresVideoExtract: Boolean = false,
            archiveMetadata: Boolean = false,
            includeComments: Boolean = false,
            estimatedBytes: Long? = null,
        ) {
            val i = Intent(context, VideoDownloadService::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_ORIGINAL_URL, originalUrl)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_VIDEO_FORMAT_ID, videoFormatId)
                putStringArrayListExtra(EXTRA_AUDIO_FORMAT_IDS, ArrayList(audioFormatIds))
                putExtra(EXTRA_COMBINED_FORMAT_ID, combinedFormatId)
                putExtra(EXTRA_NEEDS_MUX, needsMux)
                putExtra(EXTRA_AUDIO_ONLY, audioOnly)
                putExtra(EXTRA_REQUIRES_VIDEO_EXTRACT, requiresVideoExtract)
                putExtra(EXTRA_ARCHIVE_METADATA, archiveMetadata)
                putExtra(EXTRA_INCLUDE_COMMENTS, includeComments)
                putExtra(EXTRA_ESTIMATED_BYTES, estimatedBytes ?: 0L)
                putExtra(
                    EXTRA_DOWNLOAD_ID,
                    DownloadNaming.downloadId(
                        url, videoFormatId, audioFormatIds, combinedFormatId,
                    ),
                )
            }
            context.startForegroundService(i)
        }
    }
}
