package org.gnosco.share2archivetoday.download.ui

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.CheckBox
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.ConfigurationCompat
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gnosco.share2archivetoday.MainActivity
import org.gnosco.share2archivetoday.R
import org.gnosco.share2archivetoday.download.DownloadErrorMessages
import org.gnosco.share2archivetoday.download.OpenDownloadedMedia
import org.gnosco.share2archivetoday.download.history.DownloadHistoryStore
import org.gnosco.share2archivetoday.download.service.ActiveDownloadStatus
import org.gnosco.share2archivetoday.download.service.DownloadScheduler
import org.gnosco.share2archivetoday.download.service.PendingShareQueue
import org.gnosco.share2archivetoday.ytdlp.FormatInfo
import org.gnosco.share2archivetoday.ytdlp.ProbeResult
import org.gnosco.share2archivetoday.ytdlp.QualityPickerModel
import org.gnosco.share2archivetoday.ytdlp.YtDlpBridge
import org.gnosco.share2archivetoday.ytdlp.YtDlpFailureClassifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
/**
 * Share-target entry: clean URL -> duplicate check -> probe -> quality picker ->
 * optional cellular warning -> WorkManager download.
 */
class DownloadVideoActivity : MainActivity() {

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private var cleanedUrl: String? = null
    private var pendingShareUrl: String? = null

    override fun deferShareIntentHandling(): Boolean {
        return Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (deferShareIntentHandling()) {
            pendingShareUrl = intent?.getStringExtra(Intent.EXTRA_TEXT)
                ?: intent?.let { extractUrlFromShare(it) }
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQ_NOTIF,
            )
        }
        super.onCreate(savedInstanceState)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_NOTIF) return
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            // The download still runs, but with no progress and no cancel button, so say so.
            Toast.makeText(this, R.string.download_notifications_denied, Toast.LENGTH_LONG).show()
        }
        pendingShareUrl?.let { url ->
            pendingShareUrl = null
            fourSteps(url)
        }
    }

    private fun extractUrlFromShare(intent: Intent): String? {
        if (intent.action != Intent.ACTION_SEND || intent.type != "text/plain") return null
        return intent.getStringExtra(Intent.EXTRA_TEXT)?.let { extractUrl(it) }
    }

    override fun fourSteps(url: String) {
        val processed = processArchiveUrl(url)
        val cleaned = handleURL(processed)
        cleanedUrl = cleaned
        // Arriving via the "queued link ready" notification — drop it from the queue.
        PendingShareQueue.remove(this, cleaned)
        PendingShareQueue.remove(this, url)

        val history = DownloadHistoryStore(this)
        val existing = history.findSuccessful(cleaned)
        if (existing != null && history.uriStillValid(this, existing)) {
            offerExisting(existing) { beginProbe(cleaned) }
            return
        }
        beginProbe(cleaned)
    }

    override fun shouldFinishAfterShareIntent(): Boolean = false

    private enum class BusyChoice { WAIT, QUEUE, CANCEL_CURRENT, ABORT }

    private fun beginProbe(url: String) {
        scope.launch {
            try {
                Log.i(TAG, "Probe starting for $url")
                val bridge = withContext(Dispatchers.IO) {
                    YtDlpBridge.get(this@DownloadVideoActivity)
                }

                if (bridge.isBusy || ActiveDownloadStatus.snapshot().active) {
                    when (askBusyChoice()) {
                        BusyChoice.ABORT -> {
                            finish()
                            return@launch
                        }
                        BusyChoice.QUEUE -> {
                            PendingShareQueue.enqueue(this@DownloadVideoActivity, url)
                            Toast.makeText(
                                applicationContext,
                                R.string.download_busy_queued_toast,
                                Toast.LENGTH_LONG,
                            ).show()
                            finish()
                            return@launch
                        }
                        BusyChoice.CANCEL_CURRENT -> {
                            val id = ActiveDownloadStatus.snapshot().downloadId
                            DownloadScheduler.cancel(this@DownloadVideoActivity, id)
                            Toast.makeText(
                                applicationContext,
                                R.string.download_busy_cancelled_toast,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        BusyChoice.WAIT -> Unit
                    }
                }

                val progress = AlertDialog.Builder(this@DownloadVideoActivity)
                    .setMessage(R.string.download_analyzing)
                    .setCancelable(true)
                    .setOnCancelListener { finish() }
                    .create()
                progress.show()

                try {
                    // Live status while queued behind a download (Wait or Cancel-in-progress).
                    val busyWatcher = launch {
                        while (isActive) {
                            val snap = ActiveDownloadStatus.snapshot()
                            if (bridge.isBusy || snap.active) {
                                progress.setMessage(busyStatusText(snap))
                            } else if (progress.isShowing) {
                                progress.setMessage(getString(R.string.download_analyzing))
                            }
                            delay(400)
                        }
                    }
                    val probe = withContext(Dispatchers.IO) { bridge.probe(url) }
                    busyWatcher.cancel()
                    Log.i(TAG, "Probe ok: ${probe.title}, ${probe.formats.size} formats")
                    runCatching { progress.dismiss() }

                    if (url != probe.webpageUrl && !probe.webpageUrl.isNullOrBlank()) {
                        Toast.makeText(
                            applicationContext,
                            R.string.download_playlist_first,
                            Toast.LENGTH_LONG,
                        ).show()
                    }

                    val history = DownloadHistoryStore(this@DownloadVideoActivity)
                    val existing = history.findSuccessful(probe.webpageUrl ?: url)
                        ?: history.findSuccessfulByVideoId(probe.id)
                    if (existing != null && history.uriStillValid(this@DownloadVideoActivity, existing)) {
                        offerExisting(existing) {
                            showQualityPicker(url, probe)
                        }
                        return@launch
                    }

                    showQualityPicker(url, probe)
                } catch (t: Throwable) {
                    runCatching { progress.dismiss() }
                    throw t
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Probe failed kind=${YtDlpFailureClassifier.classify(t)}", t)
                if (!isFinishing) {
                    AlertDialog.Builder(this@DownloadVideoActivity)
                        .setTitle(DownloadErrorMessages.title(this@DownloadVideoActivity, t))
                        .setMessage(DownloadErrorMessages.message(this@DownloadVideoActivity, t))
                        .setPositiveButton(R.string.download_err_ok) { _, _ -> finish() }
                        .setOnCancelListener { finish() }
                        .show()
                }
            }
        }
    }

    private suspend fun askBusyChoice(): BusyChoice =
        suspendCancellableCoroutine { cont ->
            fun resumeOnce(choice: BusyChoice) {
                if (cont.isActive) cont.resume(choice)
            }
            val dialog = AlertDialog.Builder(this)
                .setTitle(R.string.download_busy_title)
                .setMessage(busyStatusText(ActiveDownloadStatus.snapshot()))
                .setPositiveButton(R.string.download_busy_wait) { _, _ ->
                    resumeOnce(BusyChoice.WAIT)
                }
                .setNeutralButton(R.string.download_busy_queue) { _, _ ->
                    resumeOnce(BusyChoice.QUEUE)
                }
                .setNegativeButton(R.string.download_busy_cancel_current) { _, _ ->
                    resumeOnce(BusyChoice.CANCEL_CURRENT)
                }
                .setOnCancelListener { resumeOnce(BusyChoice.ABORT) }
                .create()

            val unsub = ActiveDownloadStatus.addListener { snap ->
                if (dialog.isShowing) {
                    runOnUiThread {
                        if (dialog.isShowing) dialog.setMessage(busyStatusText(snap))
                    }
                }
            }
            cont.invokeOnCancellation {
                unsub()
                runCatching { if (dialog.isShowing) dialog.dismiss() }
            }
            dialog.setOnDismissListener { unsub() }
            dialog.show()
        }

    private fun busyStatusText(snap: ActiveDownloadStatus.Snapshot): String {
        val detail = buildString {
            val title = snap.title?.takeIf { it.isNotBlank() }
            val prog = snap.progressLabel()
            when {
                title != null && prog != null -> {
                    append(title)
                    append("\n")
                    append(prog)
                }
                title != null -> append(title)
                prog != null -> append(prog)
                else -> append(getString(R.string.download_waiting_busy))
            }
        }
        return getString(R.string.download_busy_message, detail)
    }

    private fun offerExisting(existing: org.gnosco.share2archivetoday.download.history.HistoryEntry, onAgain: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(R.string.download_already_title)
            .setItems(
                arrayOf(
                    getString(R.string.download_open_existing),
                    getString(R.string.download_again),
                    getString(R.string.download_cancel),
                )
            ) { _, which ->
                when (which) {
                    0 -> {
                        OpenDownloadedMedia.open(
                            this,
                            android.net.Uri.parse(existing.mediaStoreUri!!),
                        )
                        window.decorView.post { finish() }
                    }
                    1 -> onAgain()
                    else -> finish()
                }
            }
            .setOnCancelListener { finish() }
            .show()
    }

    private data class Choice(
        val label: String,
        val isAudio: Boolean,
        val videoIndex: Int = -1,
    )

    private fun showQualityPicker(originalUrl: String, probe: ProbeResult) {
        val downloadUrl = probe.webpageUrl ?: originalUrl
        val title = probe.title
        val formats = probe.formats
        val durationSec = probe.duration
        val videoId = probe.id
        val webpageUrl = probe.webpageUrl

        val langs = preferredAudioLanguages()
        val audio = QualityPickerModel.bestAudioOnly(formats, durationSec, langs)

        val content = layoutInflater.inflate(R.layout.dialog_download_quality, null)
        val listView = content.findViewById<android.widget.ListView>(R.id.quality_list)
        val maxQuality = content.findViewById<CheckBox>(R.id.quality_max_quality)
        val archiveMeta = content.findViewById<CheckBox>(R.id.quality_archive_meta)
        val includeComments = content.findViewById<CheckBox>(R.id.quality_include_comments)

        // Comments are a separate, much more expensive opt-in than the rest of the metadata.
        includeComments.isEnabled = false
        archiveMeta.setOnCheckedChangeListener { _, checked ->
            includeComments.isEnabled = checked
            if (!checked) includeComments.isChecked = false
        }

        var videos: List<QualityPickerModel.VideoOption> = emptyList()
        var choices: List<Choice> = emptyList()

        fun rebuild(archiveMode: Boolean) {
            videos = QualityPickerModel.buildVideoOptions(formats, durationSec, langs, archiveMode)
            choices = buildList {
                videos.forEachIndexed { index, opt ->
                    add(Choice(videoLabel(opt, archiveMode), isAudio = false, videoIndex = index))
                }
                if (audio.formatId != null) {
                    add(
                        Choice(
                            label = buildString {
                                append(getString(R.string.download_audio_only))
                                append(" — ")
                                append(formatSize(audio.estimatedBytes))
                                if (audio.requiresVideoExtract) append(" (extract)")
                            },
                            isAudio = true,
                        )
                    )
                }
            }
            listView.adapter = android.widget.ArrayAdapter(
                this,
                android.R.layout.simple_list_item_single_choice,
                choices.map { it.label },
            )
            listView.choiceMode = android.widget.ListView.CHOICE_MODE_SINGLE
            listView.setItemChecked(0, true)
            sizeList(listView)
        }

        rebuild(archiveMode = false)

        if (videos.isEmpty() && audio.formatId == null) {
            AlertDialog.Builder(this)
                .setTitle(R.string.download_err_title)
                .setMessage(R.string.download_err_no_formats)
                .setPositiveButton(R.string.download_err_ok) { _, _ -> finish() }
                .setOnCancelListener { finish() }
                .show()
            return
        }

        maxQuality.setOnCheckedChangeListener { _, checked -> rebuild(checked) }

        AlertDialog.Builder(this)
            .setTitle(R.string.download_pick_quality)
            .setView(content)
            .setPositiveButton(R.string.download_pick_download) { _, _ ->
                val which = listView.checkedItemPosition.coerceAtLeast(0)
                val choice = choices.getOrNull(which) ?: return@setPositiveButton
                val meta = archiveMeta.isChecked
                val comments = meta && includeComments.isChecked
                if (choice.isAudio) {
                    onAudioChosen(
                        originalUrl, downloadUrl, title, audio, formats, langs,
                        meta, comments, videoId, webpageUrl,
                    )
                } else {
                    val opt = videos[choice.videoIndex]
                    maybeWarnCellular(opt.estimatedBytes) {
                        startVideo(
                            originalUrl, downloadUrl, title, opt, formats, langs,
                            meta, comments, videoId, webpageUrl,
                        )
                    }
                }
            }
            .setNegativeButton(R.string.download_cancel) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun videoLabel(opt: QualityPickerModel.VideoOption, archiveMode: Boolean): String =
        buildString {
            append(opt.label)
            append(" — ")
            append(formatSize(opt.estimatedBytes))
            if (archiveMode) {
                opt.codecSummary?.let { append(" · ").append(it) }
            }
            if (opt.needsMux) {
                append(" · ")
                append(
                    getString(
                        if (opt.muxRisk) R.string.download_mux_risk_note
                        else R.string.download_mux_note
                    )
                )
            }
        }

    /** Size the list so it doesn't collapse inside AlertDialog. */
    private fun sizeList(listView: android.widget.ListView) {
        listView.post {
            var total = 0
            val adapter = listView.adapter ?: return@post
            val maxRows = minOf(adapter.count, 6)
            for (i in 0 until maxRows) {
                val row = adapter.getView(i, null, listView)
                row.measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(
                        listView.width,
                        android.view.View.MeasureSpec.EXACTLY,
                    ),
                    android.view.View.MeasureSpec.makeMeasureSpec(
                        0,
                        android.view.View.MeasureSpec.UNSPECIFIED,
                    ),
                )
                total += row.measuredHeight
            }
            listView.layoutParams = listView.layoutParams.apply { height = total }
            listView.requestLayout()
        }
    }

    private fun onAudioChosen(
        originalUrl: String,
        downloadUrl: String,
        title: String,
        audio: QualityPickerModel.AudioOption,
        formats: List<FormatInfo>,
        langs: List<String>,
        archiveMetadata: Boolean,
        includeComments: Boolean,
        videoId: String?,
        webpageUrl: String?,
    ) {
        val audioId = audio.formatId
        if (audioId == null) {
            Toast.makeText(this, R.string.download_err_no_formats, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val proceed = {
            maybeWarnCellular(audio.estimatedBytes) {
                val ranked = QualityPickerModel.rankedAudioFormatIds(formats, langs)
                val ids = when {
                    audio.requiresVideoExtract -> emptyList()
                    ranked.isNotEmpty() -> ranked
                    else -> listOf(audioId)
                }
                DownloadScheduler.start(
                    this,
                    url = downloadUrl,
                    originalUrl = originalUrl,
                    title = title,
                    audioFormatIds = ids,
                    combinedFormatId = if (audio.requiresVideoExtract) audioId else null,
                    needsMux = false,
                    audioOnly = true,
                    requiresVideoExtract = audio.requiresVideoExtract,
                    archiveMetadata = archiveMetadata,
                    includeComments = includeComments,
                    estimatedBytes = audio.estimatedBytes,
                    webpageUrl = webpageUrl,
                    videoId = videoId,
                    wifiOnly = false,
                )
                toastDownloadContinuing()
                finish()
            }
        }
        if (audio.requiresVideoExtract) {
            AlertDialog.Builder(this)
                .setTitle(R.string.download_audio_extract_title)
                .setMessage(R.string.download_audio_extract_message)
                .setPositiveButton(R.string.download_continue) { _, _ -> proceed() }
                .setNegativeButton(R.string.download_cancel) { _, _ -> finish() }
                .setOnCancelListener { finish() }
                .show()
        } else {
            proceed()
        }
    }

    private fun startVideo(
        originalUrl: String,
        downloadUrl: String,
        title: String,
        opt: QualityPickerModel.VideoOption,
        formats: List<FormatInfo>,
        langs: List<String>,
        archiveMetadata: Boolean,
        includeComments: Boolean,
        videoId: String?,
        webpageUrl: String?,
    ) {
        val preferred = opt.audioFormatId
        val ranked = QualityPickerModel.rankedAudioFormatIds(formats, langs, forMux = opt.needsMux)
        val audioIds = when {
            !opt.needsMux -> emptyList()
            preferred != null -> listOf(preferred) + ranked.filter { it != preferred }
            else -> ranked
        }
        DownloadScheduler.start(
            this,
            url = downloadUrl,
            originalUrl = originalUrl,
            title = title,
            videoFormatId = if (opt.needsMux) opt.videoFormatId else null,
            audioFormatIds = audioIds,
            combinedFormatId =
                if (!opt.needsMux) (opt.combinedFormatId ?: opt.videoFormatId) else null,
            needsMux = opt.needsMux,
            audioOnly = false,
            archiveMetadata = archiveMetadata,
            includeComments = includeComments,
            estimatedBytes = opt.estimatedBytes,
            webpageUrl = webpageUrl,
            videoId = videoId,
        )
        toastDownloadContinuing()
        finish()
    }

    private fun toastDownloadContinuing() {
        // Application context so the toast survives finish() of this dialog host activity.
        Toast.makeText(
            applicationContext,
            R.string.download_continue_in_notifications,
            Toast.LENGTH_LONG,
        ).show()
    }

    private fun maybeWarnCellular(estimatedBytes: Long?, onContinue: () -> Unit) {
        if (!isMetered()) {
            onContinue()
            return
        }
        val size = formatSize(estimatedBytes)
        AlertDialog.Builder(this)
            .setTitle(R.string.download_cellular_title)
            .setMessage(getString(R.string.download_cellular_message) + "\n\n$size")
            .setPositiveButton(R.string.download_continue) { _, _ -> onContinue() }
            .setNegativeButton(R.string.download_cancel) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun isMetered(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun formatSize(bytes: Long?): String {
        if (bytes == null || bytes <= 0) return getString(R.string.download_size_unknown)
        val mb = bytes / (1024.0 * 1024.0)
        if (mb >= 1024) return String.format(Locale.US, "%.2f GB", mb / 1024.0)
        return String.format(Locale.US, "%.1f MB", mb)
    }

    /** System locale list as BCP-47 tags + primary codes (e.g. en-US, en). */
    private fun preferredAudioLanguages(): List<String> {
        val locales = ConfigurationCompat.getLocales(resources.configuration)
        val out = linkedSetOf<String>()
        for (i in 0 until locales.size()) {
            val loc = locales[i] ?: continue
            out.add(loc.toLanguageTag())
            loc.language.takeIf { it.isNotBlank() }?.let { out.add(it) }
        }
        if (out.isEmpty()) {
            val fallback = Locale.getDefault()
            out.add(fallback.toLanguageTag())
            out.add(fallback.language)
        }
        return out.toList()
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "DownloadVideo"
        private const val REQ_NOTIF = 1001
    }
}
