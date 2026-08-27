package org.gnosco.share2archivetoday.ytdlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadNamingTest {

    @Test
    fun stripsPathSeparatorsAndControlCharacters() {
        assertEquals("a_b_c", DownloadNaming.sanitize("a/b\\c"))
        assertEquals("no_tabs", DownloadNaming.sanitize("no\ttabs"))
        assertEquals("q_uote", DownloadNaming.sanitize("q\"uote"))
    }

    @Test
    fun refusesEmptyAndDotOnlyNames() {
        assertEquals("download", DownloadNaming.sanitize("   "))
        assertEquals("download", DownloadNaming.sanitize("..."))
    }

    @Test
    fun truncationDoesNotSplitASurrogatePair() {
        // Each emoji is two UTF-16 units, so a naive take(n) can cut one in half.
        val name = "\uD83C\uDFA5".repeat(60)
        val out = DownloadNaming.sanitize(name)
        assertTrue(out.length <= DownloadNaming.MAX_NAME_CHARS)
        assertFalse("unpaired high surrogate", Character.isHighSurrogate(out.last()))
        // Round-tripping through UTF-8 must not introduce replacement characters.
        assertFalse(String(out.toByteArray(Charsets.UTF_8), Charsets.UTF_8).contains('\uFFFD'))
    }

    @Test
    fun downloadIdIsStableForTheSameRequest() {
        val a = DownloadNaming.downloadId("https://x/v", "137", listOf("140"), null)
        val b = DownloadNaming.downloadId("https://x/v", "137", listOf("140"), null)
        assertEquals(a, b)
    }

    @Test
    fun downloadIdChangesWithFormatSelection() {
        val a = DownloadNaming.downloadId("https://x/v", "137", listOf("140"), null)
        val b = DownloadNaming.downloadId("https://x/v", "248", listOf("140"), null)
        val c = DownloadNaming.downloadId("https://y/v", "137", listOf("140"), null)
        assertNotEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun sidecarKeepsCompoundAndLanguageSuffixes() {
        assertEquals("Clip.info.json", DownloadNaming.sidecarName("Clip", "video.info.json"))
        assertEquals("Clip.en.vtt", DownloadNaming.sidecarName("Clip", "video.en.vtt"))
        assertEquals("Clip.es-419.srt", DownloadNaming.sidecarName("Clip", "video.es-419.srt"))
        assertEquals("Clip.description", DownloadNaming.sidecarName("Clip", "video.description"))
    }

    @Test
    fun artifactNamesKeepStreamsDistinguishable() {
        assertEquals("Clip.mp4", DownloadNaming.artifactName("Clip", "", "mp4"))
        assertEquals("Clip.video.webm", DownloadNaming.artifactName("Clip", ".video", "webm"))
        assertEquals("Clip.audio.webm", DownloadNaming.artifactName("Clip", ".audio", "webm"))
        assertEquals("Clip.bin", DownloadNaming.artifactName("Clip", "", ""))
    }

    @Test
    fun folderNameIncludesShortId() {
        assertEquals("Clip [abc123]", DownloadNaming.folderName("Clip", "abc123"))
        assertEquals("Clip", DownloadNaming.folderName("Clip", ""))
    }
}
