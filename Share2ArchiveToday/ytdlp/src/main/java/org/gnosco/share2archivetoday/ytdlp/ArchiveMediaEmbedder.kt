package org.gnosco.share2archivetoday.ytdlp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import org.json.JSONObject

/**
 * Embeds archivist metadata into a muxed MP4 using iTunes/QuickTime tags
 * (`©nam`, `©des`, `©cmt`, `©ART`, `covr`, …) so players like VLC show them.
 *
 * Sidecar files are still published separately; this is an extra in-file copy for
 * the merge path where ffmpeg would normally have done [EmbedThumbnail]/Metadata].
 */
object ArchiveMediaEmbedder {

    data class Result(
        val file: File,
        val embedded: List<String>,
        val notes: List<String> = emptyList(),
    )

    /**
     * @param media merged MP4 to tag in place (rewritten via a sibling temp file)
     * @param sidecars yt-dlp description / thumbnail / info.json next to the streams
     * @param titleFallback download title when info.json is missing
     * @param provenance probe/download provenance (uploader, webpage_url, …)
     */
    fun embed(
        media: File,
        sidecars: List<DownloadSidecar>,
        titleFallback: String? = null,
        provenance: JSONObject? = null,
    ): Result {
        if (!media.isFile || media.length() < 32L) {
            return Result(media, emptyList(), listOf("Skip embed: media missing or empty"))
        }
        val lower = media.name.lowercase()
        if (!lower.endsWith(".mp4") && !lower.endsWith(".m4a") && !lower.endsWith(".m4v")) {
            return Result(media, emptyList(), listOf("Skip embed: not an MP4 container"))
        }

        val info = sidecars.firstOrNull { it.kind == "infojson" }?.let { readJson(File(it.path)) }
        val title = firstNonBlank(
            info?.optString("title"),
            provenance?.optString("title"),
            titleFallback,
        )
        val description = firstNonBlank(
            readTextSidecar(sidecars, "description"),
            info?.optString("description"),
        )?.take(MAX_DESCRIPTION_CHARS)
        val artist = firstNonBlank(
            info?.optString("uploader"),
            info?.optString("channel"),
            provenance?.optString("uploader"),
        )
        val album = firstNonBlank(info?.optString("extractor_key"), provenance?.optString("extractor"))
        val date = firstNonBlank(info?.optString("upload_date"), provenance?.optString("upload_date"))
        val comment = buildComment(description, webpageUrl(info, provenance), info?.optString("id"))
        val coverJpeg = loadCoverJpeg(sidecars)

        val atoms = buildList {
            title?.let { add(textAtom(apple("nam"), it)) }
            artist?.let { add(textAtom(apple("ART"), it)) }
            album?.let { add(textAtom(apple("alb"), it)) }
            description?.let {
                add(textAtom(apple("des"), it))
                add(textAtom("desc".toAsciiType(), it))
            }
            comment?.let { add(textAtom(apple("cmt"), it)) }
            date?.let { add(textAtom(apple("day"), formatUploadDate(date))) }
            webpageUrl(info, provenance)?.let { add(textAtom(apple("lyr"), it)) }
            coverJpeg?.let { add(covrAtom(it)) }
        }

        if (atoms.isEmpty()) {
            return Result(media, emptyList(), listOf("Skip embed: no metadata available"))
        }

        return try {
            val tagged = File(media.parentFile, media.nameWithoutExtension + ".tagged.mp4")
            writeWithUdta(media, tagged, atoms)
            if (!tagged.isFile || tagged.length() < media.length() / 2) {
                tagged.delete()
                error("Tagged output looked truncated")
            }
            if (!media.delete() || !tagged.renameTo(media)) {
                // Fall back to copying over the original path.
                tagged.copyTo(media, overwrite = true)
                tagged.delete()
            }
            val embedded = buildList {
                if (title != null) add("title")
                if (artist != null) add("artist")
                if (description != null) add("description")
                if (comment != null) add("comment")
                if (date != null) add("date")
                if (coverJpeg != null) add("thumbnail")
                if (webpageUrl(info, provenance) != null) add("url")
            }
            Result(
                file = media,
                embedded = embedded,
                notes = listOf("Embedded ${embedded.joinToString(", ")} into MP4"),
            )
        } catch (t: Throwable) {
            Log.w(TAG, "MP4 metadata embed failed", t)
            Result(media, emptyList(), listOf("Could not embed metadata into MP4 (${t.message})"))
        }
    }

    private fun buildComment(description: String?, url: String?, id: String?): String? {
        val parts = listOfNotNull(
            description?.take(1_500),
            url?.let { "Source: $it" },
            id?.let { "id=$it" },
        )
        return parts.takeIf { it.isNotEmpty() }?.joinToString("\n\n")
    }

    private fun webpageUrl(info: JSONObject?, provenance: JSONObject?): String? =
        firstNonBlank(info?.optString("webpage_url"), provenance?.optString("webpage_url"))

    private fun formatUploadDate(raw: String): String {
        // yt-dlp uses YYYYMMDD; iTunes prefers YYYY-MM-DD.
        if (raw.length == 8 && raw.all { it.isDigit() }) {
            return "${raw.substring(0, 4)}-${raw.substring(4, 6)}-${raw.substring(6, 8)}"
        }
        return raw
    }

    private fun loadCoverJpeg(sidecars: List<DownloadSidecar>): ByteArray? {
        val thumb = sidecars.firstOrNull { it.kind == "thumbnail" }?.let { File(it.path) }
            ?: return null
        if (!thumb.isFile) return null
        val name = thumb.name.lowercase()
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return thumb.readBytes().takeIf { it.isNotEmpty() && it.size <= MAX_COVER_BYTES }
        }
        // webp/png → JPEG so the covr atom type is universally understood.
        val bitmap = BitmapFactory.decodeFile(thumb.absolutePath) ?: return null
        return try {
            val out = ByteArrayOutputStream()
            val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            bitmap.recycle()
            if (!ok) null else out.toByteArray().takeIf { it.isNotEmpty() && it.size <= MAX_COVER_BYTES }
        } catch (_: Throwable) {
            runCatching { bitmap.recycle() }
            null
        }
    }

    private fun readTextSidecar(sidecars: List<DownloadSidecar>, kind: String): String? {
        val file = sidecars.firstOrNull { it.kind == kind }?.let { File(it.path) } ?: return null
        if (!file.isFile) return null
        return runCatching { file.readText(StandardCharsets.UTF_8).trim() }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun readJson(file: File): JSONObject? {
        if (!file.isFile) return null
        return runCatching { JSONObject(file.readText(StandardCharsets.UTF_8)) }.getOrNull()
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.mapNotNull { it?.trim()?.takeIf { s -> s.isNotEmpty() && s != "null" } }.firstOrNull()

    // region MP4 box helpers

    private fun textAtom(type: ByteArray, text: String): ByteArray {
        require(type.size == 4) { "MP4 type must be 4 bytes" }
        val payload = text.toByteArray(StandardCharsets.UTF_8)
        // [size|type][size|data|typeIndicator|locale|utf8]
        val total = 8 + 16 + payload.size
        val data = ByteBuffer.allocate(total).order(ByteOrder.BIG_ENDIAN)
        data.putInt(total)
        data.put(type)
        data.putInt(16 + payload.size)
        data.put("data".toAsciiType())
        data.putInt(1) // type = UTF-8 text
        data.putInt(0) // locale
        data.put(payload)
        return data.array()
    }

    private fun covrAtom(jpeg: ByteArray): ByteArray {
        val total = 8 + 16 + jpeg.size
        val data = ByteBuffer.allocate(total).order(ByteOrder.BIG_ENDIAN)
        data.putInt(total)
        data.put("covr".toAsciiType())
        data.putInt(16 + jpeg.size)
        data.put("data".toAsciiType())
        data.putInt(13) // JPEG
        data.putInt(0)
        data.put(jpeg)
        return data.array()
    }

    private fun box(type: String, content: ByteArray): ByteArray {
        val buf = ByteBuffer.allocate(8 + content.size).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(8 + content.size)
        buf.put(type.toAsciiType())
        buf.put(content)
        return buf.array()
    }

    /** iTunes fourcc: 0xA9 ('©' in MacRoman) + three ASCII letters. */
    private fun apple(suffix: String): ByteArray {
        require(suffix.length == 3)
        return byteArrayOf(0xA9.toByte()) + suffix.toByteArray(StandardCharsets.US_ASCII)
    }

    private fun String.toAsciiType(): ByteArray {
        val b = toByteArray(StandardCharsets.US_ASCII)
        require(b.size == 4) { "MP4 type must be 4 ASCII chars: $this" }
        return b
    }

    private fun buildUdta(atoms: List<ByteArray>): ByteArray {
        val ilstContent = atoms.fold(ByteArray(0)) { acc, a -> acc + a }
        val ilst = box("ilst", ilstContent)
        // QuickTime meta: 4-byte version/flags then hdlr + ilst
        val hdlrBody = ByteBuffer.allocate(25).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(0) // version/flags
            putInt(0) // pre_defined
            put("mdir".toByteArray(StandardCharsets.US_ASCII))
            putInt(0); putInt(0); putInt(0) // reserved
            put(0) // name
        }.array()
        val hdlr = box("hdlr", hdlrBody)
        val metaBody = ByteBuffer.allocate(4 + hdlr.size + ilst.size).order(ByteOrder.BIG_ENDIAN)
        metaBody.putInt(0) // version/flags
        metaBody.put(hdlr)
        metaBody.put(ilst)
        val meta = box("meta", metaBody.array())
        return box("udta", meta)
    }

    /**
     * Copy [src] to [dst], replacing or appending a `udta` inside `moov`.
     * Handles 32-bit box sizes only (fine for phone downloads).
     */
    private fun writeWithUdta(src: File, dst: File, atoms: List<ByteArray>) {
        val udta = buildUdta(atoms)
        RandomAccessFile(src, "r").use { input ->
            val top = readTopLevelBoxes(input)
            val moov = top.firstOrNull { it.type == "moov" }
                ?: error("No moov box in ${src.name}")
            val moovBytes = ByteArray(moov.contentSize).also {
                input.seek(moov.contentOffset)
                input.readFully(it)
            }
            val newMoovContent = injectUdta(moovBytes, udta)
            val newMoov = box("moov", newMoovContent)

            dst.parentFile?.mkdirs()
            if (dst.exists()) dst.delete()
            RandomAccessFile(dst, "rw").use { output ->
                for (b in top) {
                    if (b.type == "moov") {
                        output.write(newMoov)
                    } else {
                        copyBox(input, output, b)
                    }
                }
            }
        }
    }

    private data class TopBox(val type: String, val headerOffset: Long, val contentOffset: Long, val contentSize: Int)

    private fun readTopLevelBoxes(raf: RandomAccessFile): List<TopBox> {
        val out = mutableListOf<TopBox>()
        var offset = 0L
        val length = raf.length()
        while (offset + 8 <= length) {
            raf.seek(offset)
            val size32 = raf.readInt().toLong() and 0xffffffffL
            val typeBytes = ByteArray(4).also { raf.readFully(it) }
            val type = String(typeBytes, StandardCharsets.US_ASCII)
            val (headerSize, boxSize) = when {
                size32 == 1L -> {
                    val large = raf.readLong()
                    16 to large
                }
                size32 == 0L -> 8 to (length - offset)
                else -> 8 to size32
            }
            if (boxSize < headerSize || offset + boxSize > length) break
            out.add(
                TopBox(
                    type = type,
                    headerOffset = offset,
                    contentOffset = offset + headerSize,
                    contentSize = (boxSize - headerSize).toInt(),
                ),
            )
            offset += boxSize
        }
        return out
    }

    private fun copyBox(input: RandomAccessFile, output: RandomAccessFile, box: TopBox) {
        input.seek(box.headerOffset)
        val total = (box.contentOffset - box.headerOffset).toInt() + box.contentSize
        val buf = ByteArray(minOf(64 * 1024, total))
        var left = total
        while (left > 0) {
            val n = input.read(buf, 0, minOf(buf.size, left))
            if (n <= 0) break
            output.write(buf, 0, n)
            left -= n
        }
    }

    /** Replace existing udta in moov bytes, or append one. */
    private fun injectUdta(moovContent: ByteArray, udta: ByteArray): ByteArray {
        var offset = 0
        val children = mutableListOf<ByteArray>()
        var replaced = false
        while (offset + 8 <= moovContent.size) {
            val size = ByteBuffer.wrap(moovContent, offset, 4).order(ByteOrder.BIG_ENDIAN).int
            if (size < 8 || offset + size > moovContent.size) break
            val type = String(moovContent, offset + 4, 4, StandardCharsets.US_ASCII)
            val slice = moovContent.copyOfRange(offset, offset + size)
            if (type == "udta") {
                children.add(udta)
                replaced = true
            } else {
                children.add(slice)
            }
            offset += size
        }
        if (!replaced) children.add(udta)
        val total = children.sumOf { it.size }
        val out = ByteArray(total)
        var at = 0
        for (c in children) {
            System.arraycopy(c, 0, out, at, c.size)
            at += c.size
        }
        return out
    }

    // endregion

    private const val TAG = "ArchiveMediaEmbedder"
    private const val MAX_DESCRIPTION_CHARS = 8_000
    private const val MAX_COVER_BYTES = 2_500_000
}
