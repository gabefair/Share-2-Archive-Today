package org.gnosco.share2archivetoday.ytdlp

/**
 * Classifies yt-dlp / Chaquopy failures so the UI can show a useful message
 * instead of a raw `PyException: DownloadError: ERROR: …` string.
 */
object YtDlpFailureClassifier {

    enum class Kind {
        /** No extractor matched this URL (listing page, unknown site, etc.). */
        UNSUPPORTED_URL,
        /** CDN / site rejected the stream (common on YouTube with stale extractors). */
        HTTP_FORBIDDEN,
        HTTP_NOT_FOUND,
        PRIVATE_OR_LOGIN,
        GEO_BLOCKED,
        NETWORK,
        NO_FORMATS,
        /** The user stopped the download from the notification. */
        CANCELLED,
        /** A fragment was unavailable; the file would have had holes. */
        INCOMPLETE_FRAGMENTS,
        /** Ran out of room on the device. */
        NO_SPACE,
        /** Media3 could not put these codecs in an MP4 container. */
        MUX_FAILED,
        OTHER,
    }

    fun classify(throwable: Throwable): Kind {
        val msg = rootMessage(throwable).lowercase()
        return when {
            "downloadcancelled" in msg ||
                "download cancelled" in msg ||
                "media3 export cancelled" in msg ||
                throwable is Media3Cancelled -> Kind.CANCELLED
            "unsupported url" in msg -> Kind.UNSUPPORTED_URL
            "http error 403" in msg || "error 403" in msg || "status code 403" in msg ->
                Kind.HTTP_FORBIDDEN
            "http error 404" in msg || "error 404" in msg || "not found" in msg && "http" in msg ->
                Kind.HTTP_NOT_FOUND
            "private video" in msg ||
                "sign in" in msg ||
                "login required" in msg ||
                "members only" in msg ||
                "confirm your age" in msg -> Kind.PRIVATE_OR_LOGIN
            "geo" in msg && ("restrict" in msg || "block" in msg) ||
                "not available in your country" in msg -> Kind.GEO_BLOCKED
            "timed out" in msg ||
                "timeout" in msg ||
                "network is unreachable" in msg ||
                "failed to establish" in msg ||
                "name or service not known" in msg ||
                "temporary failure in name resolution" in msg -> Kind.NETWORK
            "requested format is not available" in msg ||
                "no video formats" in msg ||
                "no formats found" in msg -> Kind.NO_FORMATS
            "fragment" in msg && ("not found" in msg || "unavailable" in msg) ->
                Kind.INCOMPLETE_FRAGMENTS
            "enospc" in msg || "no space left" in msg || "not enough space" in msg ->
                Kind.NO_SPACE
            "muxer" in msg || "exportexception" in msg || "transformer" in msg ->
                Kind.MUX_FAILED
            else -> Kind.OTHER
        }
    }

    /** Unwrap ExecutionException / cause chain and strip Chaquopy noise. */
    fun rootMessage(throwable: Throwable): String {
        var t: Throwable? = throwable
        var best = throwable.message.orEmpty()
        while (t != null) {
            val m = t.message?.trim().orEmpty()
            if (m.isNotEmpty()) best = m
            t = t.cause
        }
        return best
            .removePrefix("ERROR:")
            .trim()
            .removePrefix("DownloadError:")
            .trim()
            .removePrefix("ERROR:")
            .trim()
    }

    /** Short remnant for logs / OTHER toasts (no Python exception type spam). */
    fun shortDetail(throwable: Throwable, maxLen: Int = 160): String {
        var msg = rootMessage(throwable)
        // Drop leading "Unsupported URL: …" host noise for OTHER paths that still include it.
        msg = msg.replace(Regex("""^DownloadError:\s*""", RegexOption.IGNORE_CASE), "")
        if (msg.length <= maxLen) return msg
        return msg.take(maxLen - 1) + "…"
    }
}
