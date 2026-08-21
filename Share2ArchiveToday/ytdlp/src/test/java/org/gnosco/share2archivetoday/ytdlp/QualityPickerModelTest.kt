package org.gnosco.share2archivetoday.ytdlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityPickerModelTest {

    private fun fmt(
        id: String,
        height: Int?,
        video: Boolean,
        audio: Boolean,
        tbr: Double = 1000.0,
        size: Long? = 1_000_000L,
    ) = FormatInfo(
        formatId = id,
        ext = "mp4",
        height = height,
        tbr = tbr,
        vcodec = if (video) "avc1" else "none",
        acodec = if (audio) "mp4a" else "none",
        hasVideo = video,
        hasAudio = audio,
        filesize = size,
        formatNote = null,
    )

    @Test
    fun floorsAt720WhenHdExists() {
        val formats = listOf(
            fmt("360", 360, video = true, audio = true),
            fmt("720", 720, video = true, audio = true),
            fmt("1080", 1080, video = true, audio = true),
        )
        val options = QualityPickerModel.buildVideoOptions(formats)
        assertTrue(options.all { it.height >= 720 })
        assertFalse(options.any { it.height == 360 })
    }

    @Test
    fun showsAllWhenNoHd() {
        val formats = listOf(
            fmt("360", 360, video = true, audio = true),
            fmt("480", 480, video = true, audio = true),
            fmt("720", 720, video = true, audio = true),
        )
        val options = QualityPickerModel.buildVideoOptions(formats)
        assertEquals(3, options.size)
    }

    @Test
    fun capsToTop2Plus1080WithoutDuplicate() {
        val formats = listOf(
            fmt("720", 720, video = true, audio = true, tbr = 100.0),
            fmt("1080", 1080, video = true, audio = true, tbr = 200.0),
            fmt("1440", 1440, video = true, audio = true, tbr = 300.0),
            fmt("2160", 2160, video = true, audio = true, tbr = 400.0),
        )
        val options = QualityPickerModel.buildVideoOptions(formats)
        assertTrue(options.size <= 3)
        assertTrue(options.any { it.height == 1080 })
        assertEquals(options.map { it.height }.distinct().size, options.size)
    }

    @Test
    fun audioOnlyPrefersDedicatedTrack() {
        val formats = listOf(
            fmt("v", 720, video = true, audio = true, size = 50_000_000),
            fmt("a", null, video = false, audio = true, tbr = 160.0, size = 5_000_000),
        )
        val audio = QualityPickerModel.bestAudioOnly(formats)
        assertEquals("a", audio.formatId)
        assertFalse(audio.requiresVideoExtract)
    }

    @Test
    fun audioOnlyFallsBackToExtractFlag() {
        val formats = listOf(
            fmt("v", 720, video = true, audio = true, size = 50_000_000),
        )
        val audio = QualityPickerModel.bestAudioOnly(formats)
        assertTrue(audio.requiresVideoExtract)
        assertEquals("v", audio.formatId)
    }
}
