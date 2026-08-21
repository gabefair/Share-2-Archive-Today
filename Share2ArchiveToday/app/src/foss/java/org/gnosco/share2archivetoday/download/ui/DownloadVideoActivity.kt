package org.gnosco.share2archivetoday.download.ui

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gnosco.share2archivetoday.MainActivity
import org.gnosco.share2archivetoday.R
import org.gnosco.share2archivetoday.download.history.DownloadHistoryStore
import org.gnosco.share2archivetoday.download.service.VideoDownloadService
import org.gnosco.share2archivetoday.ytdlp.QualityPickerModel
import org.gnosco.share2archivetoday.ytdlp.YtDlpBridge

/**
 * Share-target entry: clean URL → duplicate check → probe → quality picker → optional cellular warn → FGS.
 */
class DownloadVideoActivity : MainActivity() {

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private var cleanedUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // MainActivity.onCreate calls handleShareIntent → fourSteps; we need permission first.
        // Request notification permission on API 33+ then proceed via super.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQ_NOTIF,
            )
        }
        super.onCreate(savedInstanceState)
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
                            startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW)
                                    .setData(android.net.Uri.parse(existing.mediaStoreUri))
                                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            )
                            finish()
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
                val probe = withContext(Dispatchers.IO) {
                    YtDlpBridge.get(this@DownloadVideoActivity).probe(url)
                }
                progress.dismiss()
                showQualityPicker(url, probe.title, probe.formats)
            } catch (t: Throwable) {
                progress.dismiss()
                Toast.makeText(this@DownloadVideoActivity, t.message ?: "Probe failed", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun showQualityPicker(
        url: String,
        title: String,
        formats: List<org.gnosco.share2archivetoday.ytdlp.FormatInfo>,
    ) {
        val videos = QualityPickerModel.buildVideoOptions(formats)
        val audio = QualityPickerModel.bestAudioOnly(formats)
        val rankedAudioIds = QualityPickerModel.rankedAudioFormatIds(formats)
        val labels = videos.map { opt ->
            buildString {
                append(opt.label)
                append(" — ")
                append(formatSize(opt.estimatedBytes))
            }
        }.toMutableList()
        labels.add(
            buildString {
                append(getString(R.string.download_audio_only))
                append(" — ")
                append(formatSize(audio.estimatedBytes))
                if (audio.requiresVideoExtract) append(" (extract)")
            }
        )

        AlertDialog.Builder(this)
            .setTitle(R.string.download_pick_quality)
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == labels.lastIndex) {
                    onAudioChosen(url, title, audio, rankedAudioIds)
                } else {
                    val opt = videos[which]
                    maybeWarnCellular(opt.estimatedBytes) {
                        startVideo(url, title, opt, rankedAudioIds)
                    }
                }
            }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun onAudioChosen(
        url: String,
        title: String,
        audio: QualityPickerModel.AudioOption,
        rankedAudioIds: List<String>,
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
                )
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
        )
        finish()
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

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    companion object {
        private const val REQ_NOTIF = 1001
    }
}
