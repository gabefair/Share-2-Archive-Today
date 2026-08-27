package org.gnosco.share2archivetoday.ytdlp

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArchiveMediaEmbedderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun embedWritesItunesTagsIntoSyntheticMp4() {
        val mp4 = tmp.newFile("clip.mp4")
        writeMinimalMp4(mp4)

        val desc = tmp.newFile("clip.description").apply {
            writeText("A short archival description.")
        }
        val info = tmp.newFile("clip.info.json").apply {
            writeText(
                """
                {"title":"Demo Title","uploader":"Demo Channel","webpage_url":"https://example.test/v","upload_date":"20260827","description":"from json"}
                """.trimIndent(),
            )
        }

        val result = ArchiveMediaEmbedder.embed(
            media = mp4,
            sidecars = listOf(
                DownloadSidecar(desc.absolutePath, "description"),
                DownloadSidecar(info.absolutePath, "infojson"),
            ),
            titleFallback = "fallback",
        )

        assertTrue(result.embedded.contains("title"))
        assertTrue(result.embedded.contains("description"))
        assertTrue(result.embedded.contains("artist"))
        val bytes = mp4.readBytes()
        // © is stored as MacRoman 0xA9, not UTF-8.
        assertTrue(bytes.toList().windowed(4).any { it == listOf(0xA9.toByte(), 'n'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte()) })
        val asLatin = String(bytes, StandardCharsets.ISO_8859_1)
        assertTrue(asLatin.contains("Demo Title"))
        assertTrue(asLatin.contains("Demo Channel"))
        assertTrue(asLatin.contains("A short archival description."))
    }

    /** ftyp + empty mdat + moov(mvhd-less stub trak-free) — enough for the box rewriter. */
    private fun writeMinimalMp4(file: File) {
        val ftyp = box(
            "ftyp",
            byteArrayOf(
                *("isom".toByteArray()),
                0, 0, 0, 1,
                *("isom".toByteArray()),
                *("mp41".toByteArray()),
            ),
        )
        val mdat = box("mdat", ByteArray(8))
        val free = box("free", ByteArray(4))
        // moov with a dummy free child so inject has somewhere to walk.
        val moov = box("moov", free)
        file.writeBytes(ftyp + mdat + moov)
    }

    private fun box(type: String, content: ByteArray): ByteArray {
        val buf = ByteBuffer.allocate(8 + content.size).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(8 + content.size)
        buf.put(type.toByteArray(StandardCharsets.US_ASCII))
        buf.put(content)
        return buf.array()
    }
}
