package org.gnosco.share2archivetoday.ytdlp

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.nio.ByteBuffer
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device/emulator coverage for Media3 remux — the piece JVM tests cannot reach.
 *
 * Generates tiny AVC + AAC elementary streams with MediaCodec, then asks
 * [Media3Muxer] to transmux them (and to honour cancel).
 */
@RunWith(AndroidJUnit4::class)
class Media3MuxerInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun muxBlockingMergesSeparateAvcAndAac() {
        val dir = File(context.cacheDir, "media3-mux-test").also {
            it.deleteRecursively()
            it.mkdirs()
        }
        val video = File(dir, "video.mp4")
        val audio = File(dir, "audio.m4a")
        val out = File(dir, "merged.mp4")

        writeAvcOnlyMp4(video, frames = 15)
        writeAacOnlyM4a(audio, samples = 24)
        assertTrue("video fixture empty", video.length() > 0)
        assertTrue("audio fixture empty", audio.length() > 0)

        val merged = Media3Muxer(context).muxBlocking(video, audio, out)
        assertTrue(merged.exists())
        assertTrue("merged output too small: ${merged.length()}", merged.length() > 0)
    }

    @Test
    fun muxBlockingHonoursCancelBeforeStart() {
        val dir = File(context.cacheDir, "media3-cancel-test").also {
            it.deleteRecursively()
            it.mkdirs()
        }
        val video = File(dir, "video.mp4").also { writeAvcOnlyMp4(it, frames = 8) }
        val audio = File(dir, "audio.m4a").also { writeAacOnlyM4a(it, samples = 16) }
        val out = File(dir, "merged.mp4")

        try {
            Media3Muxer(context).muxBlocking(video, audio, out) { true }
            fail("expected Media3Cancelled")
        } catch (_: Media3Cancelled) {
            // expected
        }
        assertTrue(!out.exists() || out.length() == 0L)
    }

    /** Minimal AVC-in-MP4 (video track only). */
    private fun writeAvcOnlyMp4(file: File, frames: Int) {
        val width = 320
        val height = 240
        val bitRate = 250_000
        val frameRate = 15
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var inputEos = false
        var outputEos = false
        var frameIndex = 0
        val info = MediaCodec.BufferInfo()
        val yuv = ByteArray(width * height * 3 / 2) // black / zeroed NV12-ish

        while (!outputEos) {
            if (!inputEos) {
                val inIx = codec.dequeueInputBuffer(10_000)
                if (inIx >= 0) {
                    val buf = codec.getInputBuffer(inIx)!!
                    buf.clear()
                    if (frameIndex >= frames) {
                        codec.queueInputBuffer(inIx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputEos = true
                    } else {
                        buf.put(yuv)
                        val ptsUs = frameIndex * 1_000_000L / frameRate
                        codec.queueInputBuffer(inIx, 0, yuv.size, ptsUs, 0)
                        frameIndex++
                    }
                }
            }
            when (val outIx = codec.dequeueOutputBuffer(info, 10_000)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                }
                else -> if (outIx >= 0) {
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        codec.releaseOutputBuffer(outIx, false)
                    } else {
                        val outBuf = codec.getOutputBuffer(outIx)!!
                        if (info.size > 0 && track >= 0) {
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            muxer.writeSampleData(track, outBuf, info)
                        }
                        codec.releaseOutputBuffer(outIx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputEos = true
                        }
                    }
                }
            }
        }
        codec.stop()
        codec.release()
        muxer.stop()
        muxer.release()
    }

    /** Minimal AAC-in-M4A (audio track only). */
    private fun writeAacOnlyM4a(file: File, samples: Int) {
        val sampleRate = 44_100
        val channelCount = 1
        val bitRate = 64_000
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount,
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            if (Build.VERSION.SDK_INT >= 24) {
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var inputEos = false
        var outputEos = false
        var sampleIndex = 0
        val info = MediaCodec.BufferInfo()
        // 1024 PCM samples per AAC frame is typical for LC.
        val pcm = ShortArray(1024)

        while (!outputEos) {
            if (!inputEos) {
                val inIx = codec.dequeueInputBuffer(10_000)
                if (inIx >= 0) {
                    val buf = codec.getInputBuffer(inIx)!!
                    buf.clear()
                    if (sampleIndex >= samples) {
                        codec.queueInputBuffer(inIx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputEos = true
                    } else {
                        val bytes = ByteBuffer.allocate(pcm.size * 2)
                        pcm.forEach { bytes.putShort(it) }
                        bytes.flip()
                        buf.put(bytes)
                        val ptsUs = sampleIndex * 1024L * 1_000_000L / sampleRate
                        codec.queueInputBuffer(inIx, 0, pcm.size * 2, ptsUs, 0)
                        sampleIndex++
                    }
                }
            }
            when (val outIx = codec.dequeueOutputBuffer(info, 10_000)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                }
                else -> if (outIx >= 0) {
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        codec.releaseOutputBuffer(outIx, false)
                    } else {
                        val outBuf = codec.getOutputBuffer(outIx)!!
                        if (info.size > 0 && track >= 0) {
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            muxer.writeSampleData(track, outBuf, info)
                        }
                        codec.releaseOutputBuffer(outIx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputEos = true
                        }
                    }
                }
            }
        }
        codec.stop()
        codec.release()
        muxer.stop()
        muxer.release()
    }
}
