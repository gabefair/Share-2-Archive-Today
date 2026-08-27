package org.gnosco.share2archivetoday.download

import android.content.Context
import org.gnosco.share2archivetoday.R
import org.gnosco.share2archivetoday.ytdlp.YtDlpFailureClassifier
import org.gnosco.share2archivetoday.ytdlp.YtDlpFailureClassifier.Kind

/** User-facing copy for probe / download failures. */
object DownloadErrorMessages {

    fun message(context: Context, throwable: Throwable): String {
        return when (YtDlpFailureClassifier.classify(throwable)) {
            Kind.UNSUPPORTED_URL ->
                context.getString(R.string.download_err_unsupported)
            Kind.HTTP_FORBIDDEN ->
                context.getString(R.string.download_err_forbidden)
            Kind.HTTP_NOT_FOUND ->
                context.getString(R.string.download_err_not_found)
            Kind.PRIVATE_OR_LOGIN ->
                context.getString(R.string.download_err_private)
            Kind.GEO_BLOCKED ->
                context.getString(R.string.download_err_geo)
            Kind.NETWORK ->
                context.getString(R.string.download_err_network)
            Kind.NO_FORMATS ->
                context.getString(R.string.download_err_no_formats)
            Kind.OTHER ->
                context.getString(
                    R.string.download_err_generic,
                    YtDlpFailureClassifier.shortDetail(throwable),
                )
        }
    }

    fun title(context: Context, throwable: Throwable): String {
        return when (YtDlpFailureClassifier.classify(throwable)) {
            Kind.UNSUPPORTED_URL -> context.getString(R.string.download_err_unsupported_title)
            else -> context.getString(R.string.download_err_title)
        }
    }
}
