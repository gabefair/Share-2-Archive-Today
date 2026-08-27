package org.gnosco.share2archivetoday.ytdlp

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaTypesTest {

    @Test
    fun containersAreNotAllReportedAsMp4() {
        // The old code hardcoded video/mp4, so a WebM download was published with an
        // .mp4 name and an mp4 MIME type.
        assertEquals("video/webm", MediaTypes.forFile(File("a.webm")))
        assertEquals("video/x-matroska", MediaTypes.forFile(File("a.mkv")))
        assertEquals("video/x-flv", MediaTypes.forFile(File("a.flv")))
        assertEquals("video/mp4", MediaTypes.forFile(File("a.mp4")))
    }

    @Test
    fun audioOnlyDownloadsGetAudioMimeTypes() {
        assertEquals("audio/mp4", MediaTypes.forFile(File("a.m4a"), preferAudio = true))
        assertEquals("audio/opus", MediaTypes.forFile(File("a.opus"), preferAudio = true))
        assertEquals("audio/mpeg", MediaTypes.forFile(File("a.mp3"), preferAudio = true))
    }

    @Test
    fun ambiguousContainersFollowTheRequestedKind() {
        assertEquals("video/webm", MediaTypes.forFile(File("a.webm")))
        assertEquals("audio/webm", MediaTypes.forFile(File("a.webm"), preferAudio = true))
        assertEquals("audio/mp4", MediaTypes.forFile(File("a.mp4"), preferAudio = true))
    }

    @Test
    fun sidecarMimeHandlesCompoundSuffixes() {
        assertEquals("application/json", MediaTypes.sidecarMime("Clip.info.json"))
        assertEquals("text/plain", MediaTypes.sidecarMime("Clip.description"))
        assertEquals("text/vtt", MediaTypes.sidecarMime("Clip.en.vtt"))
        assertEquals("application/x-subrip", MediaTypes.sidecarMime("Clip.en.srt"))
        assertEquals("image/webp", MediaTypes.sidecarMime("Clip.webp"))
        assertEquals("text/plain", MediaTypes.sidecarMime("Clip.ytdlp.log"))
    }

    @Test
    fun unknownExtensionsFallBackToOctetStream() {
        assertEquals("application/octet-stream", MediaTypes.forFile(File("a.zzz")))
    }
}
