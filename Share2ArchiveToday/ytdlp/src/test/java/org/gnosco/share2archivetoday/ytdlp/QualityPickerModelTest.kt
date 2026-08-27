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
        vcodec: String? = null,
        acodec: String? = null,
    ) = FormatInfo(
        formatId = id,
        ext = "mp4",
        height = height,
        tbr = tbr,
        vcodec = if (video) (vcodec ?: "avc1") else "none",
        acodec = if (audio) (acodec ?: "mp4a") else "none",
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

    // --- archive (max fidelity) mode ---

    @Test
    fun archiveModeKeepsResolutionsAboveTheSoftCap() {
        val formats = listOf(
            fmt("720", 720, video = true, audio = true),
            fmt("1080", 1080, video = true, audio = true),
            fmt("2160", 2160, video = true, audio = true),
        )
        val options = QualityPickerModel.buildVideoOptions(formats, archiveMode = true)
        assertTrue(options.any { it.height == 2160 })
        assertEquals(2160, options.first().height)
    }

    @Test
    fun archiveModeKeepsSdVariantsThatTheDefaultListDrops() {
        val formats = listOf(
            fmt("360", 360, video = true, audio = true),
            fmt("720", 720, video = true, audio = true),
            fmt("1080", 1080, video = true, audio = true),
        )
        val archive = QualityPickerModel.buildVideoOptions(formats, archiveMode = true)
        assertTrue(archive.any { it.height == 360 })
        val default = QualityPickerModel.buildVideoOptions(formats)
        assertFalse(default.any { it.height == 360 })
    }

    @Test
    fun archiveModePrefersHigherBitrateAdaptiveOverLowerBitrateProgressive() {
        // YouTube's progressive 720p is a much lower bitrate than the adaptive stream.
        val formats = listOf(
            fmt("progressive", 720, video = true, audio = true, tbr = 800.0),
            fmt("adaptive", 720, video = true, audio = false, tbr = 4000.0),
            fmt("a", null, video = false, audio = true, tbr = 128.0),
        )
        val archive = QualityPickerModel.buildVideoOptions(formats, archiveMode = true).single()
        assertTrue(archive.needsMux)
        assertEquals("adaptive", archive.videoFormatId)

        // The phone-friendly list still avoids the merge.
        val default = QualityPickerModel.buildVideoOptions(formats).single()
        assertFalse(default.needsMux)
        assertEquals("progressive", default.combinedFormatId)
    }

    // --- codec-aware merging ---

    @Test
    fun mergePrefersCodecsTheMp4MuxerAccepts() {
        // Same height, but only avc1 + mp4a can go into an MP4 on device.
        val formats = listOf(
            fmt("vp9", 1080, video = true, audio = false, tbr = 3000.0, vcodec = "vp09.00.40.08"),
            fmt("avc", 1080, video = true, audio = false, tbr = 2500.0, vcodec = "avc1.640028"),
            fmt("opus", null, video = false, audio = true, tbr = 160.0, acodec = "opus"),
            fmt("aac", null, video = false, audio = true, tbr = 128.0, acodec = "mp4a.40.2"),
        )
        val option = QualityPickerModel.buildVideoOptions(formats).single()
        assertTrue(option.needsMux)
        assertEquals("avc", option.videoFormatId)
        assertEquals("aac", option.audioFormatId)
        assertFalse(option.muxRisk)
    }

    @Test
    fun flagsMergeRiskWhenOnlyWebmCodecsExist() {
        val formats = listOf(
            fmt("vp9", 1080, video = true, audio = false, tbr = 3000.0, vcodec = "vp09.00.40.08"),
            fmt("opus", null, video = false, audio = true, tbr = 160.0, acodec = "opus"),
        )
        val option = QualityPickerModel.buildVideoOptions(formats).single()
        assertTrue(option.needsMux)
        assertTrue(option.muxRisk)
        assertEquals("vp09 + opus", option.codecSummary)
    }

    @Test
    fun rankedAudioForMergePutsMuxableCodecsFirst() {
        val formats = listOf(
            fmt("opus", null, video = false, audio = true, tbr = 160.0, acodec = "opus"),
            fmt("aac", null, video = false, audio = true, tbr = 128.0, acodec = "mp4a.40.2"),
        )
        // Plain ranking is bitrate-led, so Opus wins.
        assertEquals(listOf("opus", "aac"), QualityPickerModel.rankedAudioFormatIds(formats))
        // For a merge, an AAC track that can actually be containerised comes first.
        assertEquals(
            listOf("aac", "opus"),
            QualityPickerModel.rankedAudioFormatIds(formats, forMux = true),
        )
    }

    @Test
    fun codecPreferenceDoesNotOverrideLanguagePreference() {
        val formats = listOf(
            fmt("v", 720, video = true, audio = false),
            fmt("en-opus", null, video = false, audio = true, acodec = "opus", language = "en"),
            fmt("es-aac", null, video = false, audio = true, acodec = "mp4a", language = "es"),
        )
        val ranked = QualityPickerModel.rankedAudioFormatIds(
            formats, listOf("en"), forMux = true,
        )
        assertEquals("en-opus", ranked.first())
    }

    @Test
    fun combinedFormatIsNeverFlaggedAsMergeRisk() {
        val formats = listOf(
            fmt("c", 720, video = true, audio = true, vcodec = "vp09", acodec = "opus"),
        )
        val option = QualityPickerModel.buildVideoOptions(formats).single()
        assertFalse(option.needsMux)
        assertFalse(option.muxRisk)
    }
}
