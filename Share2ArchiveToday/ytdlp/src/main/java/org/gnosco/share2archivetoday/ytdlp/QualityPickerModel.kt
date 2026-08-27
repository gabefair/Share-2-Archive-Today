package org.gnosco.share2archivetoday.ytdlp

/**
 * Opinionated quality list for the picker (pure logic, unit-testable).
 *
 * Rules:
 * - Soft ceiling 1080p on mobile (4K/2K HLS + mux is slow and rarely useful on phone).
 * - If any height > 720 exists, floor is 720 (drop SD).
 * - If more than 3 video options remain, keep top 2 by height and always include 1080 if present.
 * - Prefer native HLS (m3u8) over progressive https when both exist.
 * - Prefer a single combined A/V format when available (avoids mux).
 * - Audio: prefer tracks matching [preferredLanguages] (system locale), then untagged,
 *   then other languages; among equals, largest/best quality.
 * - Size: never show audio-only bytes as a video option's size; estimate from tbr×duration when needed.
 */
object QualityPickerModel {

    /** Phone-friendly max height offered in the picker. */
    const val MAX_HEIGHT = 1080

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

    fun buildVideoOptions(
        formats: List<FormatInfo>,
        durationSec: Double? = null,
        preferredLanguages: List<String> = emptyList(),
    ): List<VideoOption> {
        val playable = formats.filter { it.isPlayableStream }
        val heights = playable.mapNotNull { it.height }.toSet()
        val cappedHeights = if (heights.any { it <= MAX_HEIGHT }) {
            heights.filter { it <= MAX_HEIGHT }.toSet()
        } else {
            heights // only >1080 available — keep them
        }
        val hdAvailable = cappedHeights.any { it > 720 }

        val byHeight = linkedMapOf<Int, VideoOption>()
        val candidateHeights = cappedHeights
            .filter { h -> if (hdAvailable) h >= 720 else true }
            .sortedDescending()

        for (height in candidateHeights) {
            val option = optionForHeight(playable, height, durationSec, preferredLanguages) ?: continue
            byHeight[height] = option
        }

        var options = byHeight.values.toList()

        // Progressive sources often omit height (e.g. xnxx low/high). Still offer them.
        if (options.isEmpty()) {
            val prefer = formatPreference(preferredLanguages)
            val combined = playable
                .filter { it.hasVideo && it.hasAudio && it.height == null }
                .sortedWith(prefer)
                .firstOrNull()
            val videoOnly = playable
                .filter { it.hasVideo && !it.hasAudio && it.height == null }
                .sortedWith(prefer)
                .firstOrNull()
            val bestAudio = playable
                .filter { it.hasAudio && !it.hasVideo }
                .sortedWith(prefer)
                .firstOrNull()
            val fallback = when {
                combined != null ->
                    videoOption(0, null, null, combined, needsMux = false, durationSec, label = "Video")
                videoOnly != null && bestAudio != null ->
                    videoOption(0, videoOnly, bestAudio, null, needsMux = true, durationSec, label = "Video")
                videoOnly != null ->
                    videoOption(0, videoOnly, null, null, needsMux = false, durationSec, label = "Video")
                else -> null
            }
            if (fallback != null) options = listOf(fallback)
        }

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

    fun bestAudioOnly(
        formats: List<FormatInfo>,
        durationSec: Double? = null,
        preferredLanguages: List<String> = emptyList(),
    ): AudioOption {
        val playable = formats.filter { it.isPlayableStream }
        val prefer = formatPreference(preferredLanguages)
        val audioOnly = playable
            .filter { it.hasAudio && !it.hasVideo }
            .sortedWith(prefer)

        if (audioOnly.isNotEmpty()) {
            val best = audioOnly.first()
            return AudioOption(
                label = "Audio only",
                formatId = best.formatId,
                estimatedBytes = estimateBytes(best, durationSec),
                requiresVideoExtract = false,
            )
        }

        val progressive = playable
            .filter { it.hasAudio && it.hasVideo }
            .sortedWith(prefer)
        val best = progressive.firstOrNull()
        return AudioOption(
            label = "Audio only",
            formatId = best?.formatId,
            estimatedBytes = estimateBytes(best, durationSec),
            requiresVideoExtract = best != null,
        )
    }

    fun rankedAudioFormatIds(
        formats: List<FormatInfo>,
        preferredLanguages: List<String> = emptyList(),
    ): List<String> =
        formats
            .filter { it.isPlayableStream && it.hasAudio && !it.hasVideo }
            .sortedWith(formatPreference(preferredLanguages))
            .map { it.formatId }

    private fun optionForHeight(
        formats: List<FormatInfo>,
        height: Int,
        durationSec: Double?,
        preferredLanguages: List<String>,
    ): VideoOption? {
        val prefer = formatPreference(preferredLanguages)
        val atHeight = formats.filter { it.height == height }
        val combined = atHeight
            .filter { it.hasVideo && it.hasAudio }
            .sortedWith(prefer)
            .firstOrNull()
        val videoOnly = atHeight
            .filter { it.hasVideo && !it.hasAudio }
            .sortedWith(prefer)
            .firstOrNull()
        val bestAudio = formats
            .filter { it.hasAudio && !it.hasVideo }
            .sortedWith(prefer)
            .firstOrNull()

        return when {
            combined != null ->
                videoOption(height, null, null, combined, needsMux = false, durationSec)
            videoOnly != null && bestAudio != null ->
                videoOption(height, videoOnly, bestAudio, null, needsMux = true, durationSec)
            videoOnly != null ->
                videoOption(height, videoOnly, null, null, needsMux = false, durationSec)
            else -> null
        }
    }

    private fun videoOption(
        height: Int,
        video: FormatInfo?,
        audio: FormatInfo?,
        combined: FormatInfo?,
        needsMux: Boolean,
        durationSec: Double?,
        label: String = "${height}p",
    ): VideoOption {
        val est = when {
            combined != null -> estimateBytes(combined, durationSec)
            needsMux -> {
                val v = estimateBytes(video, durationSec)
                val a = estimateBytes(audio, durationSec)
                when {
                    v != null && a != null -> v + a
                    v != null -> v
                    else -> null // never fall back to audio-only size for a video row
                }
            }
            else -> estimateBytes(video, durationSec)
        }
        return VideoOption(
            label = label,
            height = height,
            videoFormatId = video?.formatId ?: combined!!.formatId,
            audioFormatId = audio?.formatId,
            combinedFormatId = combined?.formatId,
            estimatedBytes = est,
            needsMux = needsMux,
        )
    }

    /** Prefer known filesize; else tbr (kbps) × duration. */
    internal fun estimateBytes(fmt: FormatInfo?, durationSec: Double?): Long? {
        if (fmt == null) return null
        fmt.filesize?.takeIf { it > 0 }?.let { return it }
        val tbr = fmt.tbr?.takeIf { it > 0 } ?: return null
        val dur = durationSec?.takeIf { it > 0 } ?: return null
        return ((tbr * 1000.0 / 8.0) * dur).toLong()
    }

    /**
     * Higher is better. Exact tag match > primary-language match > untagged > other languages.
     * Index in [preferred] breaks ties (first preferred locale wins).
     */
    internal fun languageScore(language: String?, preferred: List<String>): Int {
        if (preferred.isEmpty()) return 0
        val tag = language?.trim()?.replace('_', '-')?.takeIf { it.isNotEmpty() }
            ?: return 100 // untagged: after matches, before foreign dubs
        val lower = tag.lowercase()
        val primary = lower.substringBefore('-')
        preferred.forEachIndexed { index, raw ->
            val pref = raw.trim().replace('_', '-').lowercase()
            if (pref.isEmpty()) return@forEachIndexed
            val bump = preferred.size - index
            if (lower == pref) return 1000 + bump
            if (primary == pref.substringBefore('-')) return 500 + bump
        }
        return 0
    }

    private fun formatPreference(preferredLanguages: List<String>): Comparator<FormatInfo> =
        compareByDescending<FormatInfo> { languageScore(it.language, preferredLanguages) }
            .thenByDescending { it.isNativeHls }
            .thenByDescending { it.tbr ?: 0.0 }
            .thenByDescending { it.filesize ?: 0L }

    private val FormatInfo.isNativeHls: Boolean
        get() = protocol?.contains("m3u8", ignoreCase = true) == true

    private val FormatInfo.isPlayableStream: Boolean
        get() {
            val proto = protocol.orEmpty().lowercase()
            if (proto.contains("mhtml") || ext.equals("mhtml", ignoreCase = true)) return false
            if (formatNote?.contains("storyboard", ignoreCase = true) == true) return false
            return hasVideo || hasAudio
        }
}
