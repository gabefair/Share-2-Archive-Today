package org.gnosco.share2archivetoday.ytdlp

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONArray
import org.json.JSONObject

/**
 * Thin Kotlin wrapper around ytdlp_bridge.py.
 */
class YtDlpBridge private constructor(private val py: Python) {

    private val module get() = py.getModule("ytdlp_bridge")

    fun ping(): String = module.callAttr("ping").toString()

    fun probe(url: String): ProbeResult {
        val raw = module.callAttr("probe", url).toString()
        return ProbeResult.parse(raw)
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
        onProgress: ((DownloadProgress) -> Unit)? = null,
    ): DownloadResult {
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
        ).toString()
        return DownloadResult.parse(raw)
    }

    companion object {
        @Volatile private var instance: YtDlpBridge? = null

        fun get(context: Context): YtDlpBridge {
            instance?.let { return it }
            synchronized(this) {
                instance?.let { return it }
                if (!Python.isStarted()) {
                    Python.start(AndroidPlatform(context.applicationContext))
                }
                return YtDlpBridge(Python.getInstance()).also { instance = it }
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
                speed = if (o.isNull("speed")) null else o.optDouble("speed"),
                eta = if (o.isNull("eta")) null else o.optLong("eta"),
                filename = o.optString("filename").ifEmpty { null },
            )
        }
    }
}

data class DownloadResult(val filepath: String, val title: String?, val ext: String?) {
    companion object {
        fun parse(raw: String): DownloadResult {
            val o = JSONObject(raw)
            return DownloadResult(
                filepath = o.getString("filepath"),
                title = o.optString("title").ifEmpty { null },
                ext = o.optString("ext").ifEmpty { null },
            )
        }
    }
}
