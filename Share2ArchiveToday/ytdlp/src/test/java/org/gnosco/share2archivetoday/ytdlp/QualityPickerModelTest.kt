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
        protocol: String? = null,
        language: String? = null,
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
        protocol = protocol,
        language = language,
    )

    @Test
    fun softCapsAt1080WhenAvailable() {
        val formats = listOf(
            fmt("720", 720, video = true, audio = true),
            fmt("1080", 1080, video = true, audio = true),
            fmt("1440", 1440, video = true, audio = true),
            fmt("2160", 2160, video = true, audio = true),
        )
        val options = QualityPickerModel.buildVideoOptions(formats)
        assertTrue(options.all { it.height <= QualityPickerModel.MAX_HEIGHT })
        assertTrue(options.any { it.height == 1080 })
        assertFalse(options.any { it.height == 2160 })
    }

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
    fun capsToTop2WithinSoftCap() {
        val formats = listOf(
            fmt("720", 720, video = true, audio = true, tbr = 100.0),
            fmt("1080", 1080, video = true, audio = true, tbr = 200.0),
            fmt("1440", 1440, video = true, audio = true, tbr = 300.0),
            fmt("2160", 2160, video = true, audio = true, tbr = 400.0),
        )
        val options = QualityPickerModel.buildVideoOptions(formats)
        assertTrue(options.size <= 3)
        assertTrue(options.any { it.height == 1080 })
        assertFalse(options.any { it.height > QualityPickerModel.MAX_HEIGHT })
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
    fun prefersNativeHlsOverHttps() {
        val formats = listOf(
            fmt("https", 720, video = true, audio = false, tbr = 2000.0, protocol = "https"),
            fmt("hls", 720, video = true, audio = false, tbr = 1500.0, protocol = "m3u8_native"),
            fmt("a", null, video = false, audio = true, tbr = 128.0, protocol = "m3u8_native"),
        )
        val options = QualityPickerModel.buildVideoOptions(formats)
        assertEquals(1, options.size)
        assertEquals("hls", options[0].videoFormatId)
        assertTrue(options[0].needsMux)
    }

    @Test
    fun muxSizeDoesNotUseAudioOnlyWhenVideoUnknown() {
        val formats = listOf(
            fmt("v4k", 2160, video = true, audio = false, tbr = 8000.0, size = null),
            fmt("a", null, video = false, audio = true, tbr = 128.0, size = 2_600_000),
        )
        val options = QualityPickerModel.buildVideoOptions(formats, durationSec = 60.0)
        assertEquals(1, options.size)
        // ~8000 kbps * 60s / 8 ≈ 60MB video, not the 2.6MB audio alone
        assertTrue((options[0].estimatedBytes ?: 0L) > 10_000_000)
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

    @Test
    fun prefersSystemLanguageOverLargerForeignAudio() {
        val formats = listOf(
            fmt("v", 720, video = true, audio = false, tbr = 2000.0),
            fmt("es", null, video = false, audio = true, tbr = 256.0, size = 8_000_000, language = "es"),
            fmt("en", null, video = false, audio = true, tbr = 128.0, size = 4_000_000, language = "en"),
        )
        val langs = listOf("en-US", "en")
        val audio = QualityPickerModel.bestAudioOnly(formats, preferredLanguages = langs)
        assertEquals("en", audio.formatId)

        val ranked = QualityPickerModel.rankedAudioFormatIds(formats, langs)
        assertEquals(listOf("en", "es"), ranked)

        val video = QualityPickerModel.buildVideoOptions(formats, preferredLanguages = langs).single()
        assertEquals("en", video.audioFormatId)
    }

    @Test
    fun languageScoreExactBeatsPrimaryBeatsUntaggedBeatsForeign() {
        val preferred = listOf("en-US", "en")
        assertTrue(
            QualityPickerModel.languageScore("en-US", preferred) >
                QualityPickerModel.languageScore("en", preferred),
        )
        assertTrue(
            QualityPickerModel.languageScore("en", preferred) >
                QualityPickerModel.languageScore(null, preferred),
        )
        assertTrue(
            QualityPickerModel.languageScore(null, preferred) >
                QualityPickerModel.languageScore("es", preferred),
        )
    }

    @Test
    fun heightlessProgressiveStillOfferedAsVideo() {
        // Sites like xnxx expose low/high MP4 without height metadata.
        val formats = listOf(
            fmt("low", null, video = true, audio = true, tbr = 500.0, size = 10_000_000),
            fmt("high", null, video = true, audio = true, tbr = 2000.0, size = 40_000_000),
        )
        val options = QualityPickerModel.buildVideoOptions(formats)
        assertEquals(1, options.size)
        assertEquals("Video", options[0].label)
        assertEquals("high", options[0].combinedFormatId)

        val audio = QualityPickerModel.bestAudioOnly(formats)
        assertEquals("high", audio.formatId)
        assertTrue(audio.requiresVideoExtract)
    }
}
