package org.gnosco.share2archivetoday.ytdlp

import android.content.Context
import android.util.Log
import androidx.core.os.ConfigurationCompat
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONArray
import org.json.JSONObject

/**
 * Thin Kotlin wrapper around ytdlp_bridge.py.
 *
 * Chaquopy allows one Python caller at a time; all JNI entry points run on a
 * dedicated single-thread executor to avoid ART/JIT races seen on emulators.
 */
class YtDlpBridge private constructor(private val py: Python) {

    private val module get() = py.getModule("ytdlp_bridge")

    /** yt-dlp release actually packaged in this APK, read from the shipped source. */
    val version: String by lazy { runPython { module.callAttr("ytdlp_version").toString() } }

    fun ping(): String = runPython { module.callAttr("ping").toString() }

    fun probe(url: String): ProbeResult = runPython {
        val raw = module.callAttr("probe", url).toString()
        ProbeResult.parse(raw)
    }

    fun interface ProgressCallback {
        fun onProgress(json: String)
    }

    /** Polled from the Python progress hook so a running download can be aborted. */
    fun interface CancelSignal {
        fun isCancelled(): Boolean
    }

    fun interface LogCallback {
        fun onLog(level: String, message: String)
    }

    data class DownloadOptions(
        val outTemplate: String = "%(title).80B [%(id)s].%(ext)s",
        val continuedl: Boolean = true,
        val archiveMetadata: Boolean = false,
        val includeComments: Boolean = false,
        val writeSubtitles: Boolean = false,
        /** BCP-47 / yt-dlp language tags. Avoid "all" — it 429s YouTube. */
        val subtitleLangs: List<String> = defaultSubtitleLangs(),
    ) {
        fun toJson(): String = JSONObject().apply {
            put("out_template", outTemplate)
            put("continuedl", continuedl)
            put("archive_metadata", archiveMetadata)
            put("include_comments", includeComments)
            put("write_subtitles", writeSubtitles)
            put("subtitle_langs", JSONArray(subtitleLangs))
        }.toString()
    }

    /**
     * @param formatSpec a yt-dlp format selector. Comma-separated ids ("137,140") fetch
     *   several streams in one extraction without invoking the unavailable merger;
     *   [DownloadOptions.outTemplate] must then contain %(format_id)s.
     */
    fun download(
        url: String,
        formatSpec: String,
        outDir: String,
        options: DownloadOptions = DownloadOptions(),
        onProgress: ((DownloadProgress) -> Unit)? = null,
        cancelSignal: CancelSignal? = null,
        onLog: LogCallback? = null,
    ): DownloadResult = runPython {
        val progress = onProgress?.let { listener ->
            ProgressCallback { json -> listener(DownloadProgress.parse(json)) }
        }
        val raw = module.callAttr(
            "download",
            url,
            formatSpec,
            outDir,
            options.toJson(),
            progress,
            cancelSignal,
            onLog,
        ).toString()
        DownloadResult.parse(raw)
    }

    /**
     * True while a Python call is running. The interpreter is serialized behind one
     * thread, so callers that would otherwise block indefinitely (a probe queued behind
     * an active download) can tell the user what they are waiting for.
     */
    val isBusy: Boolean get() = inFlight.get() > 0

    private fun <T> runPython(block: () -> T): T {
        inFlight.incrementAndGet()
        return try {
            pythonExecutor.submit<T> { block() }.get()
        } finally {
            inFlight.decrementAndGet()
        }
    }

    companion object {
        private const val TAG = "YtDlpBridge"

        @Volatile private var instance: YtDlpBridge? = null

        private val inFlight = AtomicInteger(0)

        /** Single thread for every Python call (Chaquopy + ART stability). */
        private val pythonExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
            Thread(r, "ytdlp-python").apply { isDaemon = true }
        }

        /**
         * System language(s) plus English for captions. Prefer [context] locales when
         * available; otherwise [Locale.getDefault]. Never returns `"all"`.
         */
        fun defaultSubtitleLangs(context: Context? = null): List<String> {
            val out = linkedSetOf<String>()
            if (context != null) {
                val locales = ConfigurationCompat.getLocales(context.resources.configuration)
                for (i in 0 until locales.size()) {
                    val loc = locales[i] ?: continue
                    out.add(loc.toLanguageTag())
                    loc.language.takeIf { it.isNotBlank() }?.let { out.add(it) }
                }
            }
            if (out.isEmpty()) {
                val fallback = Locale.getDefault()
                out.add(fallback.toLanguageTag())
                fallback.language.takeIf { it.isNotBlank() }?.let { out.add(it) }
            }
            out.add("en")
            return out.toList()
        }

        fun get(context: Context): YtDlpBridge {
            instance?.let { return it }
            synchronized(this) {
                instance?.let { return it }
                val app = context.applicationContext
                if (!Python.isStarted()) {
                    Python.start(AndroidPlatform(app))
                }
                val py = Python.getInstance()
                val bridge = YtDlpBridge(py)
                try {
                    // Import and configuration go through the same executor as every
                    // other Python call; android_shims must land before yt_dlp imports
                    // subprocess.
                    bridge.runPython {
                        py.getModule("android_shims")
                        val cache = File(app.cacheDir, "yt-dlp").apply { mkdirs() }
                        py.getModule("ytdlp_bridge")
                            .callAttr("configure", cache.absolutePath)
                    }
                    Log.i(TAG, "Python bridge ready: ${bridge.ping()}")
                } catch (t: Throwable) {
                    Log.e(TAG, "Python bridge warmup failed", t)
                    throw t
                }
                return bridge.also { instance = it }
            }
        }
    }
}

data class ProbeResult(
    val id: String?,
    val title: String,
    val duration: Double?,
    val uploader: String?,
    /** Canonical single-video URL; differs from the shared URL for playlist/channel links. */
    val webpageUrl: String?,
    val extractor: String?,
    val formats: List<FormatInfo>,
) {
    companion object {
        fun parse(raw: String): ProbeResult {
            val o = JSONObject(raw)
            val arr: JSONArray = o.optJSONArray("formats") ?: JSONArray()
            val formats = buildList {
                for (i in 0 until arr.length()) {
                    val f = arr.getJSONObject(i)
                    add(
                        FormatInfo(
                            formatId = f.getString("format_id"),
                            ext = f.optString("ext").ifEmpty { null },
                            height = if (f.isNull("height")) null else f.getInt("height"),
                            tbr = if (f.isNull("tbr")) null else f.getDouble("tbr"),
                            vcodec = f.optString("vcodec"),
                            acodec = f.optString("acodec"),
                            hasVideo = f.optBoolean("has_video"),
                            hasAudio = f.optBoolean("has_audio"),
                            filesize = if (f.isNull("filesize")) null else f.getLong("filesize"),
                            formatNote = f.optString("format_note").ifEmpty { null },
                            protocol = f.optString("protocol").ifEmpty { null },
                            language = f.optString("language").ifEmpty { null },
                        )
                    )
                }
            }
            return ProbeResult(
                id = o.optString("id").ifEmpty { null },
                title = o.optString("title", "video"),
                duration = if (o.isNull("duration")) null else o.getDouble("duration"),
                uploader = o.optString("uploader").ifEmpty { null },
                webpageUrl = o.optString("webpage_url").ifEmpty { null },
                extractor = o.optString("extractor").ifEmpty { null },
                formats = formats,
            )
        }
    }
}

data class FormatInfo(
    val formatId: String,
    val ext: String?,
    val height: Int?,
    val tbr: Double?,
    val vcodec: String?,
    val acodec: String?,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    val filesize: Long?,
    val formatNote: String?,
    /** yt-dlp protocol string, e.g. https / m3u8_native. */
    val protocol: String? = null,
    /** BCP-47 / ISO language from yt-dlp, e.g. en, es-419. */
    val language: String? = null,
)

data class DownloadProgress(
    val status: String,
    val downloaded: Long = 0,
    val total: Long = 0,
    val speed: Double? = null,
    val eta: Long? = null,
    val filename: String? = null,
) {
    companion object {
        fun parse(raw: String): DownloadProgress {
            val o = JSONObject(raw)
            return DownloadProgress(
                status = o.optString("status"),
                downloaded = o.optLong("downloaded"),
                total = o.optLong("total"),
                speed = if (o.isNull("speed")) null else o.getDouble("speed"),
                eta = if (o.isNull("eta")) null else o.getLong("eta"),
                filename = o.optString("filename").ifEmpty { null },
            )
        }
    }
}

data class DownloadSidecar(val path: String, val kind: String)

/** One media file written by a download; a comma format selector produces several. */
data class DownloadedFile(
    val path: String,
    val formatId: String,
    val ext: String,
    val bytes: Long,
    val vcodec: String?,
    val acodec: String?,
) {
    val hasVideo: Boolean get() = !vcodec.isNullOrBlank() && vcodec != "none"
    val hasAudio: Boolean get() = !acodec.isNullOrBlank() && acodec != "none"
}

data class DownloadResult(
    val filepath: String,
    val title: String?,
    val ext: String?,
    val files: List<DownloadedFile> = emptyList(),
    val sidecars: List<DownloadSidecar> = emptyList(),
    /** yt-dlp's account of what was fetched; folded into the archive manifest verbatim. */
    val provenance: JSONObject? = null,
) {
    fun fileFor(formatId: String): DownloadedFile? = files.firstOrNull { it.formatId == formatId }

    companion object {
        fun parse(raw: String): DownloadResult {
            val o = JSONObject(raw)
            val sideArr = o.optJSONArray("sidecars") ?: JSONArray()
            val sidecars = buildList {
                for (i in 0 until sideArr.length()) {
                    val s = sideArr.getJSONObject(i)
                    add(
                        DownloadSidecar(
                            path = s.getString("path"),
                            kind = s.optString("kind", "infojson"),
                        )
                    )
                }
            }
            val fileArr = o.optJSONArray("files") ?: JSONArray()
            val files = buildList {
                for (i in 0 until fileArr.length()) {
                    val f = fileArr.getJSONObject(i)
                    add(
                        DownloadedFile(
                            path = f.getString("path"),
                            formatId = f.optString("format_id"),
                            ext = f.optString("ext"),
                            bytes = f.optLong("bytes"),
                            vcodec = f.optString("vcodec").ifEmpty { null },
                            acodec = f.optString("acodec").ifEmpty { null },
                        )
                    )
                }
            }
            return DownloadResult(
                filepath = o.getString("filepath"),
                title = o.optString("title").ifEmpty { null },
                ext = o.optString("ext").ifEmpty { null },
                files = files,
                sidecars = sidecars,
                provenance = o.optJSONObject("provenance"),
            )
        }
    }
}
