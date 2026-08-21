package org.gnosco.share2archivetoday.ytdlp

/**
 * Opinionated quality list for the picker (pure logic, unit-testable).
 *
 * Rules:
 * - If any height > 720 exists, floor is 720 (drop SD).
 * - If more than 3 video options remain, keep top 2 by height (+ tbr) and always include 1080 if present.
 * - Audio-only is appended separately by the UI using [bestAudioOnly].
 */
object QualityPickerModel {

    data class VideoOption(
        val label: String,
        val height: Int,
        val videoFormatId: String,
        val audioFormatId: String?,
        val combinedFormatId: String?,
        val estimatedBytes: Long?,
        val needsMux: Boolean,
    )

    data class AudioOption(
        val label: String,
        val formatId: String?,
        val estimatedBytes: Long?,
        /** True when we must download a progressive A/V file and extract audio later. */
        val requiresVideoExtract: Boolean,
    )

    fun buildVideoOptions(formats: List<FormatInfo>): List<VideoOption> {
        val heights = formats.mapNotNull { it.height }.toSet()
        val hdAvailable = heights.any { it > 720 }

        // One representative stream set per height bucket.
        val byHeight = linkedMapOf<Int, VideoOption>()
        val candidateHeights = heights
            .filter { h -> if (hdAvailable) h >= 720 else true }
            .sortedDescending()

        for (height in candidateHeights) {
            val option = optionForHeight(formats, height) ?: continue
            byHeight[height] = option
        }

        var options = byHeight.values.toList()
        if (options.size > 3) {
            val top2 = options.take(2)
            val has1080 = options.any { it.height == 1080 }
            val with1080 = if (has1080 && top2.none { it.height == 1080 }) {
                top2 + options.first { it.height == 1080 }
            } else {
                top2
            }
            options = with1080.distinctBy { it.height }.sortedByDescending { it.height }
        }
        return options
    }

    fun bestAudioOnly(formats: List<FormatInfo>): AudioOption {
        val audioOnly = formats
            .filter { it.hasAudio && !it.hasVideo }
            .sortedWith(compareByDescending<FormatInfo> { it.tbr ?: 0.0 }.thenByDescending { it.filesize ?: 0L })

        if (audioOnly.isNotEmpty()) {
            val best = audioOnly.first()
            return AudioOption(
                label = "Audio only",
                formatId = best.formatId,
                estimatedBytes = best.filesize,
                requiresVideoExtract = false,
            )
        }

        val progressive = formats
            .filter { it.hasAudio && it.hasVideo }
            .sortedWith(compareByDescending<FormatInfo> { it.tbr ?: 0.0 }.thenByDescending { it.filesize ?: 0L })
        val best = progressive.firstOrNull()
        return AudioOption(
            label = "Audio only",
            formatId = best?.formatId,
            estimatedBytes = best?.filesize,
            requiresVideoExtract = best != null,
        )
    }

    /** Ranked audio-only format ids, best first. */
    fun rankedAudioFormatIds(formats: List<FormatInfo>): List<String> =
        formats
            .filter { it.hasAudio && !it.hasVideo }
            .sortedWith(compareByDescending<FormatInfo> { it.tbr ?: 0.0 }.thenByDescending { it.filesize ?: 0L })
            .map { it.formatId }

    private fun optionForHeight(formats: List<FormatInfo>, height: Int): VideoOption? {
        val atHeight = formats.filter { it.height == height }
        val combined = atHeight
            .filter { it.hasVideo && it.hasAudio }
            .maxByOrNull { it.tbr ?: 0.0 }
        val videoOnly = atHeight
            .filter { it.hasVideo && !it.hasAudio }
            .maxByOrNull { it.tbr ?: 0.0 }
        val bestAudio = formats
            .filter { it.hasAudio && !it.hasVideo }
            .maxByOrNull { it.tbr ?: 0.0 }

        return when {
            combined != null && videoOnly != null -> {
                // Prefer separate if clearly higher video bitrate; else combined.
                val separateBetter = (videoOnly.tbr ?: 0.0) > (combined.tbr ?: 0.0) * 1.1
                if (separateBetter && bestAudio != null) {
                    videoOption(height, videoOnly, bestAudio, combined = null, needsMux = true)
                } else {
                    videoOption(height, null, null, combined, needsMux = false)
                }
            }
            combined != null -> videoOption(height, null, null, combined, needsMux = false)
            videoOnly != null && bestAudio != null ->
                videoOption(height, videoOnly, bestAudio, null, needsMux = true)
            videoOnly != null ->
                videoOption(height, videoOnly, null, null, needsMux = false)
            else -> null
        }
    }

    private fun videoOption(
        height: Int,
        video: FormatInfo?,
        audio: FormatInfo?,
        combined: FormatInfo?,
        needsMux: Boolean,
    ): VideoOption {
        val est = listOfNotNull(
            combined?.filesize,
            video?.filesize,
            audio?.filesize,
        ).let { parts ->
            when {
                combined?.filesize != null -> combined.filesize
                video?.filesize != null && audio?.filesize != null -> video.filesize + audio.filesize
                else -> parts.maxOrNull()
            }
        }
        return VideoOption(
            label = "${height}p",
            height = height,
            videoFormatId = video?.formatId ?: combined!!.formatId,
            audioFormatId = audio?.formatId,
            combinedFormatId = combined?.formatId,
            estimatedBytes = est,
            needsMux = needsMux,
        )
    }
}
