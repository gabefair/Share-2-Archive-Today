# FFmpegWrapper API Documentation

## Overview

The `FFmpegWrapper` class provides native Android alternatives to common ffmpeg operations, allowing video/audio processing without bundling the ffmpeg binary. This reduces app size and improves compatibility while maintaining essential media processing capabilities.

## Quick Start

```kotlin
val wrapper = FFmpegWrapper(context)

// Convert video format
val success = wrapper.convertVideoFormat(
    inputPath = "/path/to/input.mp4",
    outputPath = "/path/to/output.webm",
    outputFormat = FFmpegWrapper.OutputFormats.WEBM
)

// Extract audio
val audioSuccess = wrapper.extractAudio(
    inputPath = "/path/to/video.mp4",
    outputPath = "/path/to/audio.aac"
)
```

## API Reference

### Constants

#### OutputFormats
```kotlin
object OutputFormats {
    const val MP4 = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
    const val WEBM = MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
    const val THREE_GPP = MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP
}
```

#### Quality
```kotlin
object Quality {
    const val BEST = "best"
    const val WORST = "worst"
    const val HIGH = "high"
    const val MEDIUM = "medium"
    const val LOW = "low"
}
```

### Video Operations

#### ✅ convertVideoFormat()
Convert video format using Android MediaMuxer.

**Equivalent ffmpeg:** `ffmpeg -i input.mp4 -c copy output.webm`

```kotlin
fun convertVideoFormat(
    inputPath: String, 
    outputPath: String, 
    outputFormat: Int = OutputFormats.WEBM
): Boolean
```

**Parameters:**
- `inputPath`: Path to input video file
- `outputPath`: Path to output video file  
- `outputFormat`: Android MediaMuxer output format (use OutputFormats constants)

**Returns:** `true` if conversion successful, `false` otherwise

**Throws:** `IllegalArgumentException`, `IOException`

#### ✅ trimVideo()
Trim video using Android MediaExtractor and MediaMuxer.

**Equivalent ffmpeg:** `ffmpeg -i input.mp4 -ss start_time -t duration -c copy output.mp4`

```kotlin
fun trimVideo(
    inputPath: String, 
    outputPath: String, 
    startTimeMs: Long, 
    durationMs: Long
): Boolean
```

**Parameters:**
- `inputPath`: Path to input video file
- `outputPath`: Path to output video file
- `startTimeMs`: Start time in milliseconds
- `durationMs`: Duration in milliseconds

**Returns:** `true` if trimming successful, `false` otherwise

#### ⏳ concatenateVideos()
Concatenate multiple videos into one.

**Equivalent ffmpeg:** `ffmpeg -i "concat:input1.mp4|input2.mp4" -c copy output.mp4`

```kotlin
fun concatenateVideos(inputPaths: List<String>, outputPath: String): Boolean
```

**Status:** ⏳ Planned but not yet implemented

#### ⏳ applyVideoFilters()
Apply video filters (resize, crop, rotate, etc.).

**Equivalent ffmpeg:** `ffmpeg -i input.mp4 -vf "scale=640:480" output.mp4`

```kotlin
fun applyVideoFilters(
    inputPath: String, 
    outputPath: String, 
    filters: List<VideoFilter>
): Boolean
```

**Status:** ⏳ Planned but not yet implemented

### Audio Operations

#### ✅ extractAudio()
Extract audio from video using Android MediaExtractor.

**Equivalent ffmpeg:** `ffmpeg -i input.mp4 -vn -acodec copy output.aac`

```kotlin
fun extractAudio(inputPath: String, outputPath: String): Boolean
```

**Parameters:**
- `inputPath`: Path to input video file
- `outputPath`: Path to output audio file

**Returns:** `true` if extraction successful, `false` otherwise

#### ⏳ mixAudio()
Mix multiple audio files into one.

**Equivalent ffmpeg:** `ffmpeg -i input1.wav -i input2.wav -filter_complex amix=inputs=2 output.wav`

```kotlin
fun mixAudio(
    inputPaths: List<String>, 
    outputPath: String, 
    mixType: AudioMixType = AudioMixType.ADD
): Boolean
```

**Status:** ⏳ Planned but not yet implemented

#### ⏳ convertAudioFormat()
Convert audio format.

**Equivalent ffmpeg:** `ffmpeg -i input.wav -acodec mp3 output.mp3`

```kotlin
fun convertAudioFormat(
    inputPath: String, 
    outputPath: String, 
    outputFormat: Int = OutputFormats.MP4
): Boolean
```

**Status:** ⏳ Planned but not yet implemented

### Metadata Operations

#### ✅ getVideoMetadata()
Get video metadata using Android MediaExtractor.

**Equivalent ffmpeg:** `ffprobe -v quiet -print_format json -show_format -show_streams input.mp4`

```kotlin
fun getVideoMetadata(inputPath: String): VideoMetadata?
```

**Parameters:**
- `inputPath`: Path to input video file

**Returns:** `VideoMetadata` object containing video details, `null` if failed

#### ⏳ extractSubtitles()
Extract subtitles from video.

**Equivalent ffmpeg:** `ffmpeg -i input.mp4 -map 0:s:0 subtitles.srt`

```kotlin
fun extractSubtitles(
    inputPath: String, 
    outputPath: String, 
    format: SubtitleFormat = SubtitleFormat.SRT
): Boolean
```

**Status:** ⏳ Planned but not yet implemented

### File Operations

#### ✅ copyFile()
Simple file copy utility.

**Equivalent ffmpeg:** `cp input output`

```kotlin
fun copyFile(inputPath: String, outputPath: String): Boolean
```

**Parameters:**
- `inputPath`: Path to input file
- `outputPath`: Path to output file

**Returns:** `true` if copy successful, `false` otherwise

#### ✅ deleteFile()
Delete file safely.

```kotlin
fun deleteFile(filePath: String): Boolean
```

**Parameters:**
- `filePath`: Path to file to delete

**Returns:** `true` if deletion successful, `false` otherwise

## Data Classes

### VideoMetadata
```kotlin
data class VideoMetadata(
    var duration: Long = 0,
    var width: Int = 0,
    var height: Int = 0,
    var frameRate: Int = 0,
    var sampleRate: Int = 0,
    var channelCount: Int = 0,
    var videoCodec: String? = null,
    var audioCodec: String? = null
)
```

### Enums

#### VideoFilter
```kotlin
enum class VideoFilter {
    SCALE, CROP, ROTATE, BRIGHTNESS, CONTRAST, SATURATION, BLUR, SHARPEN
}
```

#### AudioMixType
```kotlin
enum class AudioMixType {
    ADD, AVERAGE, MAX, MIN
}
```

#### SubtitleFormat
```kotlin
enum class SubtitleFormat {
    SRT, VTT, ASS, SSA, SUB, IDX
}
```

## Implementation Status

| Feature | Status | Description |
|---------|--------|-------------|
| Video format conversion | ✅ Implemented | Uses MediaMuxer for format conversion |
| Audio extraction | ✅ Implemented | Uses MediaExtractor for audio extraction |
| Video trimming | ✅ Implemented | Time-based filtering with MediaExtractor |
| Metadata extraction | ✅ Implemented | Video/audio metadata using MediaFormat |
| File operations | ✅ Implemented | Copy, delete, and basic file management |
| Video concatenation | ⏳ Planned | Multiple video merging |
| Audio mixing | ⏳ Planned | Multiple audio file mixing |
| Video filters | ⏳ Planned | Resize, crop, rotate, effects |
| Subtitle extraction | ⏳ Planned | Extract subtitles from video files |
| Audio format conversion | ⏳ Planned | Convert between audio formats |

## Error Handling

All methods return `Boolean` for success/failure indication and log detailed error information. Methods that can throw exceptions are documented with `@throws` annotations.

## Performance Considerations

- Uses 1MB buffers for optimal performance
- Leverages Android's native media APIs for efficiency
- Minimal memory footprint compared to ffmpeg binary
- Async operations recommended for large files

## Future Enhancements

1. **Video Concatenation**: Implement using MediaMuxer with multiple input sources
2. **Audio Mixing**: Use AudioTrack and MediaCodec for audio processing
3. **Video Filters**: Implement using OpenGL ES or RenderScript
4. **Subtitle Support**: Parse and extract subtitle streams
5. **Batch Operations**: Process multiple files in parallel
6. **Progress Callbacks**: Add progress reporting for long operations

## Migration from ffmpeg

| ffmpeg Command | FFmpegWrapper Method |
|----------------|---------------------|
| `ffmpeg -i input.mp4 -c copy output.webm` | `convertVideoFormat(inputPath, outputPath, OutputFormats.WEBM)` |
| `ffmpeg -i input.mp4 -vn -acodec copy output.aac` | `extractAudio(inputPath, outputPath)` |
| `ffmpeg -i input.mp4 -ss 10 -t 30 -c copy output.mp4` | `trimVideo(inputPath, outputPath, 10000, 30000)` |
| `ffprobe -v quiet -print_format json input.mp4` | `getVideoMetadata(inputPath)` |
| `cp input output` | `copyFile(inputPath, outputPath)` |
