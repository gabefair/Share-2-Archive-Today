package org.gnosco.share2archivetoday.ytdlp

import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveManifestTest {

    private fun tempFile(content: String): File {
        val file = Files.createTempFile("manifest-test", ".bin").toFile()
        file.deleteOnExit()
        file.writeText(content)
        return file
    }

    private fun capture(remuxTool: String? = null, notes: List<String> = emptyList()) =
        ArchiveManifest.Capture(
            originalUrl = "https://example.test/watch?v=abc",
            resolvedUrl = "https://example.test/v/abc",
            appVersion = "6.1",
            appFlavor = "foss",
            ytdlpVersion = "2026.08.19",
            remuxTool = remuxTool,
            remuxOperation = if (remuxTool != null) "mux-audio-video" else null,
            pageArchiveUrl = "https://archive.today/?run=1&url=https%3A%2F%2Fexample.test",
            notes = notes,
        )

    @Test
    fun sha256MatchesKnownVector() {
        // SHA-256 of "abc"
        val file = tempFile("abc")
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            ArchiveManifest.sha256(file),
        )
    }

    @Test
    fun everyPublishedFileIsListedWithADigest() {
        val media = tempFile("video-bytes")
        val info = tempFile("{}")
        val json = JSONObject(
            ArchiveManifest.build(
                capture(),
                JSONObject().put("id", "abc"),
                listOf(
                    ArchiveManifest.Artifact("Clip.mp4", media, "video"),
                    ArchiveManifest.Artifact("Clip.info.json", info, "infojson"),
                ),
            )
        )
        val files = json.getJSONArray("files")
        assertEquals(2, files.length())
        for (i in 0 until files.length()) {
            val entry = files.getJSONObject(i)
            assertTrue(entry.getString("sha256").length == 64)
            assertTrue(entry.getLong("bytes") > 0)
        }
        assertEquals("Clip.mp4", files.getJSONObject(0).getString("name"))
    }

    @Test
    fun recordsWhetherTheFileWasRewrittenOnDevice() {
        val media = tempFile("bytes")
        val untouched = JSONObject(
            ArchiveManifest.build(capture(), null, listOf(ArchiveManifest.Artifact("a.mp4", media, "video")))
        ).getJSONObject("processing")
        assertFalse(untouched.getBoolean("remuxed_on_device"))
        assertTrue(untouched.getString("integrity_note").contains("as delivered"))

        val remuxed = JSONObject(
            ArchiveManifest.build(
                capture(remuxTool = "androidx.media3 Transformer"),
                null,
                listOf(ArchiveManifest.Artifact("a.mp4", media, "video")),
            )
        ).getJSONObject("processing")
        assertTrue(remuxed.getBoolean("remuxed_on_device"))
        assertEquals("mux-audio-video", remuxed.getString("remux_operation"))
        assertTrue(remuxed.getString("integrity_note").contains("not"))
        // A remux copies elementary streams; it must never be described as a transcode.
        assertFalse(remuxed.getBoolean("transcoded"))
    }

    @Test
    fun keepsBothTheSharedAndResolvedUrls() {
        val media = tempFile("bytes")
        val capture = JSONObject(
            ArchiveManifest.build(capture(), null, listOf(ArchiveManifest.Artifact("a.mp4", media, "video")))
        ).getJSONObject("capture")
        assertEquals("https://example.test/watch?v=abc", capture.getString("original_url"))
        assertEquals("https://example.test/v/abc", capture.getString("resolved_url"))
        assertEquals("2026.08.19", capture.getString("ytdlp_version"))
        assertEquals("foss", capture.getString("app_flavor"))
    }

    @Test
    fun linksTheMediaBackToAnArchiveOfItsPage() {
        val media = tempFile("bytes")
        val capture = JSONObject(
            ArchiveManifest.build(capture(), null, listOf(ArchiveManifest.Artifact("a.mp4", media, "video")))
        ).getJSONObject("capture")
        assertTrue(capture.getString("page_archive_url").startsWith("https://archive.today/"))
    }

    @Test
    fun pageArchiveUrlIsNullWhenNotProvided() {
        val media = tempFile("bytes")
        val bare = ArchiveManifest.Capture(
            originalUrl = "https://example.test",
            resolvedUrl = null,
            appVersion = "6.1",
            appFlavor = "foss",
            ytdlpVersion = "2026.08.19",
            remuxTool = null,
            remuxOperation = null,
        )
        val capture = JSONObject(
            ArchiveManifest.build(bare, null, listOf(ArchiveManifest.Artifact("a.mp4", media, "video")))
        ).getJSONObject("capture")
        assertTrue(capture.isNull("page_archive_url"))
    }

    @Test
    fun carriesForwardYtdlpProvenanceAndFailureNotes() {
        val media = tempFile("bytes")
        val json = JSONObject(
            ArchiveManifest.build(
                capture(notes = listOf("On-device merge failed")),
                JSONObject().put("extractor", "Youtube").put("format_id", "137"),
                listOf(ArchiveManifest.Artifact("a.mp4", media, "video-stream")),
            )
        )
        assertEquals("Youtube", json.getJSONObject("source").getString("extractor"))
        assertEquals("137", json.getJSONObject("source").getString("format_id"))
        assertEquals("On-device merge failed", json.getJSONArray("notes").getString(0))
    }

    @Test
    fun missingFilesAreSkippedRatherThanFailingTheManifest() {
        val json = JSONObject(
            ArchiveManifest.build(
                capture(),
                null,
                listOf(ArchiveManifest.Artifact("gone.mp4", File("/nonexistent/gone.mp4"), "video")),
            )
        )
        assertEquals(0, json.getJSONArray("files").length())
    }
}
