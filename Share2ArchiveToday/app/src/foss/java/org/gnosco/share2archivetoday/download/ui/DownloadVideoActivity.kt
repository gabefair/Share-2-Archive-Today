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
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import androidx.core.os.ConfigurationCompat
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
import org.gnosco.share2archivetoday.download.service.VideoDownloadService
import org.gnosco.share2archivetoday.ytdlp.QualityPickerModel
import org.gnosco.share2archivetoday.ytdlp.YtDlpBridge
import org.gnosco.share2archivetoday.ytdlp.YtDlpFailureClassifier

/**
 * Share-target entry: clean URL → duplicate check → probe → quality picker → optional cellular warn → FGS.
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

        val history = DownloadHistoryStore(this)
        val existing = history.findSuccessful(cleaned)
        if (existing != null && history.uriStillValid(this, existing)) {
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
                            // Stay until the player/chooser is up; finish after so grants stick.
                            window.decorView.post { finish() }
                        }
                        1 -> beginProbe(cleaned)
                        else -> finish()
                    }
                }
                .setOnCancelListener { finish() }
                .show()
            return
        }
        beginProbe(cleaned)
    }

    override fun shouldFinishAfterShareIntent(): Boolean = false

    private fun beginProbe(url: String) {
        val progress = AlertDialog.Builder(this)
            .setMessage(R.string.download_analyzing)
            .setCancelable(false)
            .create()
        progress.show()

        scope.launch {
            try {
                Log.i(TAG, "Probe starting for $url")
                val probe = withContext(Dispatchers.IO) {
                    YtDlpBridge.get(this@DownloadVideoActivity).probe(url)
                }
                Log.i(TAG, "Probe ok: ${probe.title}, ${probe.formats.size} formats")
                progress.dismiss()
                showQualityPicker(url, probe.title, probe.formats, probe.duration)
            } catch (t: Throwable) {
                progress.dismiss()
                Log.e(TAG, "Probe failed kind=${YtDlpFailureClassifier.classify(t)}", t)
                AlertDialog.Builder(this@DownloadVideoActivity)
                    .setTitle(DownloadErrorMessages.title(this@DownloadVideoActivity, t))
                    .setMessage(DownloadErrorMessages.message(this@DownloadVideoActivity, t))
                    .setPositiveButton(R.string.download_err_ok) { _, _ -> finish() }
                    .setOnCancelListener { finish() }
                    .show()
            }
        }
    }

    private fun showQualityPicker(
        url: String,
        title: String,
        formats: List<org.gnosco.share2archivetoday.ytdlp.FormatInfo>,
        durationSec: Double?,
    ) {
        val langs = preferredAudioLanguages()
        val videos = QualityPickerModel.buildVideoOptions(formats, durationSec, langs)
        val audio = QualityPickerModel.bestAudioOnly(formats, durationSec, langs)
        val rankedAudioIds = QualityPickerModel.rankedAudioFormatIds(formats, langs)

        if (videos.isEmpty() && audio.formatId == null) {
            AlertDialog.Builder(this)
                .setTitle(R.string.download_err_title)
                .setMessage(R.string.download_err_no_formats)
                .setPositiveButton(R.string.download_err_ok) { _, _ -> finish() }
                .setOnCancelListener { finish() }
                .show()
            return
        }

        data class Choice(val label: String, val isAudio: Boolean, val videoIndex: Int = -1)

        val choices = mutableListOf<Choice>()
        videos.forEachIndexed { index, opt ->
            choices.add(
                Choice(
                    label = "${opt.label} — ${formatSize(opt.estimatedBytes)}",
                    isAudio = false,
                    videoIndex = index,
                )
            )
        }
        if (audio.formatId != null) {
            choices.add(
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

        val content = layoutInflater.inflate(R.layout.dialog_download_quality, null)
        val listView = content.findViewById<android.widget.ListView>(R.id.quality_list)
        val archiveMeta = content.findViewById<android.widget.CheckBox>(R.id.quality_archive_meta)
        archiveMeta.isChecked = false

        val labels = choices.map { it.label }.toTypedArray()
        listView.adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_list_item_single_choice,
            labels,
        )
        listView.choiceMode = android.widget.ListView.CHOICE_MODE_SINGLE
        listView.setItemChecked(0, true)

        // Size the list so it doesn't collapse inside AlertDialog.
        listView.post {
            var total = 0
            val adapter = listView.adapter ?: return@post
            val maxRows = minOf(adapter.count, 6)
            for (i in 0 until maxRows) {
                val row = adapter.getView(i, null, listView)
                row.measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(listView.width, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
                )
                total += row.measuredHeight
            }
            listView.layoutParams = listView.layoutParams.apply { height = total }
            listView.requestLayout()
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.download_pick_quality)
            .setView(content)
            .setPositiveButton(R.string.download_pick_download) { _, _ ->
                val which = listView.checkedItemPosition.coerceAtLeast(0)
                val choice = choices.getOrNull(which) ?: return@setPositiveButton
                val includeMeta = archiveMeta.isChecked
                if (choice.isAudio) {
                    onAudioChosen(url, title, audio, rankedAudioIds, includeMeta)
                } else {
                    val opt = videos[choice.videoIndex]
                    maybeWarnCellular(opt.estimatedBytes) {
                        startVideo(url, title, opt, rankedAudioIds, includeMeta)
                    }
                }
            }
            .setNegativeButton(R.string.download_cancel) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun onAudioChosen(
        url: String,
        title: String,
        audio: QualityPickerModel.AudioOption,
        rankedAudioIds: List<String>,
        archiveMetadata: Boolean,
    ) {
        val audioId = audio.formatId
        if (audioId == null) {
            Toast.makeText(this, "No audio format available", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val proceed = {
            maybeWarnCellular(audio.estimatedBytes) {
                val ids = when {
                    audio.requiresVideoExtract -> emptyList()
                    rankedAudioIds.isNotEmpty() -> rankedAudioIds
                    else -> listOf(audioId)
                }
                VideoDownloadService.start(
                    this,
                    url = url,
                    title = title,
                    audioFormatIds = ids,
                    combinedFormatId = if (audio.requiresVideoExtract) audioId else null,
                    needsMux = false,
                    audioOnly = true,
                    requiresVideoExtract = audio.requiresVideoExtract,
                    archiveMetadata = archiveMetadata,
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
        url: String,
        title: String,
        opt: QualityPickerModel.VideoOption,
        rankedAudioIds: List<String>,
        archiveMetadata: Boolean,
    ) {
        val preferred = opt.audioFormatId
        val audioIds = when {
            !opt.needsMux -> emptyList()
            preferred != null ->
                listOf(preferred) + rankedAudioIds.filter { it != preferred }
            else -> rankedAudioIds
        }
        VideoDownloadService.start(
            this,
            url = url,
            title = title,
            videoFormatId = if (opt.needsMux) opt.videoFormatId else null,
            audioFormatIds = audioIds,
            combinedFormatId = if (!opt.needsMux) (opt.combinedFormatId ?: opt.videoFormatId) else null,
            needsMux = opt.needsMux,
            audioOnly = false,
            archiveMetadata = archiveMetadata,
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
