package org.gnosco.share2archivetoday.ytdlp

import android.content.Context
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
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

    fun ping(): String = runPython { module.callAttr("ping").toString() }

    fun probe(url: String): ProbeResult = runPython {
        val raw = module.callAttr("probe", url).toString()
        ProbeResult.parse(raw)
    }

    fun interface ProgressCallback {
        fun onProgress(json: String)
    }

    fun download(
        url: String,
        formatId: String,
        outDir: String,
        outTemplate: String = "%(title).80B [%(id)s].%(ext)s",
        continuedl: Boolean = true,
        archiveMetadata: Boolean = false,
        onProgress: ((DownloadProgress) -> Unit)? = null,
    ): DownloadResult = runPython {
        val cb = onProgress?.let { listener ->
            ProgressCallback { json -> listener(DownloadProgress.parse(json)) }
        }
        val raw = module.callAttr(
            "download",
            url,
            formatId,
            outDir,
            outTemplate,
            cb,
            continuedl,
            archiveMetadata,
        ).toString()
        DownloadResult.parse(raw)
    }

    private fun <T> runPython(block: () -> T): T =
        pythonExecutor.submit<T> { block() }.get()

    companion object {
        private const val TAG = "YtDlpBridge"

        @Volatile private var instance: YtDlpBridge? = null

        /** Single thread for every Python call (Chaquopy + ART stability). */
        private val pythonExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
            Thread(r, "ytdlp-python").apply { isDaemon = true }
        }

        fun get(context: Context): YtDlpBridge {
            instance?.let { return it }
            synchronized(this) {
                instance?.let { return it }
                if (!Python.isStarted()) {
                    Python.start(AndroidPlatform(context.applicationContext))
                }
                val py = Python.getInstance()
                // Must run before ytdlp_bridge / yt_dlp import subprocess.
                py.getModule("android_shims")
                val bridge = YtDlpBridge(py)
                try {
                    val pong = bridge.ping()
                    Log.i(TAG, "Python bridge ready: $pong")
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

data class DownloadResult(
    val filepath: String,
    val title: String?,
    val ext: String?,
    val sidecars: List<DownloadSidecar> = emptyList(),
) {
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
            return DownloadResult(
                filepath = o.getString("filepath"),
                title = o.optString("title").ifEmpty { null },
                ext = o.optString("ext").ifEmpty { null },
                sidecars = sidecars,
            )
        }
    }
}
