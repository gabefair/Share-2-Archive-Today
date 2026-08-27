package org.gnosco.share2archivetoday.ytdlp

import java.util.concurrent.ExecutionException
import org.junit.Assert.assertEquals
import org.junit.Test

class YtDlpFailureClassifierTest {

    @Test
    fun unsupportedUrl() {
        val t = ExecutionException(
            RuntimeException("DownloadError: ERROR: Unsupported URL: https://www.xnxx.com/todays-selection"),
        )
        assertEquals(YtDlpFailureClassifier.Kind.UNSUPPORTED_URL, YtDlpFailureClassifier.classify(t))
    }

    @Test
    fun http403() {
        val t = RuntimeException("ERROR: unable to download video data: HTTP Error 403: Forbidden")
        assertEquals(YtDlpFailureClassifier.Kind.HTTP_FORBIDDEN, YtDlpFailureClassifier.classify(t))
    }

    @Test
    fun networkTimeout() {
        val t = RuntimeException("ERROR: [youtube] Unable to download webpage: timed out")
        assertEquals(YtDlpFailureClassifier.Kind.NETWORK, YtDlpFailureClassifier.classify(t))
    }

    @Test
    fun rootMessageStripsPrefixes() {
        val t = ExecutionException(RuntimeException("DownloadError: ERROR: Unsupported URL: https://x.test/a"))
        assertEquals(
            "Unsupported URL: https://x.test/a",
            YtDlpFailureClassifier.rootMessage(t),
        )
    }
}
