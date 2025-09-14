# Development Guide - Share2ArchiveToday

## Project Overview

Share2ArchiveToday is an Android app that extends the system share menu to provide three powerful URL processing options:

1. **Archive Page** - Saves webpages to archive.today for permanent preservation
2. **Copy Clean URL** - Removes tracking parameters and copies cleaned URLs to clipboard
3. **Download Video** - Downloads media from web pages using yt-dlp integration

## Architecture & Design Philosophy

### Core Principles

- **Local Module Integration**: Direct integration of youtubedl-android source code for maximum control and latest features
- **Modern Build System**: Uses latest Gradle practices with version catalogs and modern Android SDK
- **Code Compatibility**: Maintains compatibility with latest Android versions while supporting older devices
- **Direct Source Control**: Uses absolute latest source code from upstream projects, not outdated JitPack releases

### Technical Architecture

```
Share2ArchiveToday/
├── app/                          # Main application module
│   ├── src/main/java/org/gnosco/share2archivetoday/
│   │   ├── MainActivity.kt      # Base activity with URL processing logic
│   │   ├── ClipboardActivity.kt # Clean URL copying functionality
│   │   ├── VideoDownloadActivity.kt # Video download implementation
│   │   ├── ClearUrlsRulesManager.kt # URL cleaning rules engine
│   │   └── QRCodeScanner.kt     # QR code processing
│   └── build.gradle.kts         # Main app build configuration
├── youtubedl-android/           # Local youtubedl-android modules
│   ├── common/                  # Shared utilities and preferences
│   ├── library/                 # Core YouTube-DL functionality
│   ├── ffmpeg/                  # FFmpeg media processing support
│   └── aria2c/                  # Aria2c downloader support
├── gradle/                      # Version catalog and wrapper
├── build.gradle.kts             # Root project configuration
└── settings.gradle.kts          # Module inclusion and project structure
```

## Local Module Integration Strategy

### Why Local Modules?

Instead of using JitPack dependencies, this project integrates youtubedl-android directly as local modules:

- **Latest Source Code**: Always use the most recent commits from the upstream repository
- **Build Control**: Full control over compilation flags and optimizations
- **Dependency Management**: No external dependency on potentially outdated releases
- **Easy Updates**: Simple git pull to get latest features and bug fixes

### Module Structure

```kotlin
// settings.gradle.kts
include(":youtubedl-common")
include(":youtubedl-library") 
include(":youtubedl-ffmpeg")
include(":youtubedl-aria2c")

project(":youtubedl-common").projectDir = file("youtubedl-android/common")
project(":youtubedl-library").projectDir = file("youtubedl-android/library")
project(":youtubedl-ffmpeg").projectDir = file("youtubedl-android/ffmpeg")
project(":youtubedl-aria2c").projectDir = file("youtubedl-android/aria2c")
```

### Package Mapping

The project maintains compatibility by mapping packages:
- **From**: `com.github.yausername.youtubedl`
- **To**: `com.yausername.youtubedl_android`

This ensures all imports work correctly while using local modules.

## Build System Modernization

### Gradle Configuration

- **AGP Version**: 8.12.0 (latest stable)
- **Kotlin Version**: 1.9.0
- **Compile SDK**: 36 (Android 14)
- **Target SDK**: 36
- **Min SDK**: 24 (Android 7.0)

### Version Catalog Usage

```toml
# gradle/libs.versions.toml
[versions]
agp = "8.12.0"
kotlin = "1.9.0"
coreKtx = "1.10.1"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
jetbrains-kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
```

### Modern Build Features

- **View Binding**: Enabled for type-safe view access
- **Proguard**: Optimized for release builds
- **NDK Version**: Explicitly specified (25.2.9519653) instead of deprecated ndk block
- **Resource Shrinking**: Enabled for release builds to reduce APK size

## Video Download Implementation

### Core Components

The video download feature is implemented in `VideoDownloadActivity.kt`:

```kotlin
class VideoDownloadActivity : MainActivity() {
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Initialize youtubedl-android with FFmpeg support
        YoutubeDL.getInstance().init(this)
        FFmpeg.getInstance().init(this)
    }
    
    private fun downloadVideo(url: String) {
        coroutineScope.launch {
            // Get video info first
            val videoInfo = withContext(Dispatchers.IO) {
                YoutubeDL.getInstance().getInfo(url)
            }
            
            // Start download with progress tracking
            startDownload(url, videoInfo.title ?: "video")
        }
    }
}
```

### Download Process

1. **URL Analysis**: Check if URL likely contains video content
2. **Video Info Extraction**: Use yt-dlp to get metadata and available formats
3. **Format Selection**: Choose best quality available (bestvideo + bestaudio)
4. **Progress Tracking**: Real-time download progress with ETA
5. **File Management**: Save to Downloads/Share2Archive folder
6. **Sharing**: Automatically share completed downloads

### Supported Platforms

The yt-dlp integration supports video extraction from:
- YouTube, Vimeo, Dailymotion
- Social media: Twitter/X, Instagram, Facebook, TikTok
- Content platforms: Reddit, Tumblr, Pinterest
- And 1000+ other sites

## Download Configuration & Decisions

### Core Download Strategy

The app implements a sophisticated download strategy with multiple fallback options:

```kotlin
// Primary format selection strategy
request.addOption("-f", "bestvideo[ext=${format}]+bestaudio[ext=m4a]/best[ext=${format}]/best")
```

**Format Selection Priority:**
1. **Best Video + Best Audio**: Separate streams for maximum quality
2. **Best Combined**: Single file with best overall quality
3. **Fallback**: Any available format if specific ones fail

### Download Options & Flags

```kotlin
// Essential download flags
request.addOption("--no-mtime")           // Don't preserve modification time
request.addOption("--no-playlist")        // Download single video, not playlists
request.addOption("--no-warnings")        // Suppress warning messages
request.addOption("--ignore-errors")      // Continue on non-fatal errors
request.addOption("--extract-audio")      // Enable audio extraction capability
request.addOption("--audio-format", "mp3") // Prefer MP3 for audio
request.addOption("--audio-quality", "0")  // Best audio quality (0 = best)
```

**Why These Options:**
- **`--no-mtime`**: Prevents timestamp preservation issues on Android
- **`--no-playlist`**: Ensures single video downloads for better UX
- **`--ignore-errors`**: Graceful handling of minor format issues
- **`--extract-audio`**: Enables audio-only downloads when video fails

### Quality Selection Logic

The app uses yt-dlp's intelligent format selection:

```kotlin
// Format string breakdown:
// "bestvideo[ext=${format}]+bestaudio[ext=m4a]/best[ext=${format}]/best"
// 1. bestvideo[ext=mp4] - Best video in MP4 format
// 2. +bestaudio[ext=m4a] - Best audio in M4A format  
// 3. /best[ext=mp4] - Best combined MP4 if separate streams fail
// 4. /best - Any best format as final fallback
```

**Benefits:**
- **Maximum Quality**: Separate streams often provide better quality
- **Format Consistency**: Prefers MP4 for broad compatibility
- **Fallback Safety**: Multiple fallback levels ensure downloads succeed
- **Audio Quality**: M4A audio provides excellent quality-to-size ratio

### Download Process Management

```kotlin
// Unique process identification
currentProcessId = "download_${System.currentTimeMillis()}"

// Progress tracking with detailed status
val progressText = when {
    progress < 0f -> "Preparing download..."
    progress == 0f -> "Starting download..."
    progress < 10f -> "Downloading: ${progress}% (ETA: ${etaInSeconds}s)"
    progress < 50f -> "Downloading: ${progress}% (ETA: ${etaInSeconds}s)"
    progress < 90f -> "Downloading: ${progress}% (ETA: ${etaInSeconds}s)"
    progress < 100f -> "Finalizing: ${progress}% (ETA: ${etaInSeconds}s)"
    else -> "Download complete!"
}
```

**Process Management Features:**
- **Unique IDs**: Prevents conflicts with multiple downloads
- **Progress Stages**: Clear user feedback at each download phase
- **ETA Display**: Real-time estimated completion time
- **Error Handling**: Graceful handling of download failures

## File Organization & Naming

### Directory Structure

The app creates a dedicated download directory structure:

```kotlin
// Primary download location
val downloadDir = File(
    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), 
    "Share2Archive"
)
```

**Directory Hierarchy:**
```
/sdcard/Download/
└── Share2Archive/           # App-specific download folder
    ├── video_title_1.mp4    # Individual video files
    ├── video_title_2.webm   # Various formats supported
    ├── audio_title_1.mp3    # Audio-only downloads
    └── ...
```

**Why This Structure:**
- **User Visibility**: Downloads appear in standard Downloads folder
- **App Isolation**: Dedicated subfolder prevents conflicts
- **Easy Access**: Users can find files in familiar location
- **System Integration**: Works with Android's download manager

### Filename Generation

The app implements intelligent filename sanitization:

```kotlin
// Safe filename generation
val safeTitle = title.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(100)
val filename = "${safeTitle}.${format}"
val outputPath = "${downloadDir.absolutePath}/${filename}"
```

**Filename Sanitization Rules:**
- **Character Replacement**: Invalid characters become underscores
- **Length Limitation**: Maximum 100 characters to prevent filesystem issues
- **Format Preservation**: Original file extension maintained
- **Uniqueness**: Timestamp-based process IDs prevent conflicts

**Example Transformations:**
```
Original: "My Video Title (2024) - Special Edition!"
Sanitized: "My_Video_Title_2024___Special_Edition.mp4"

Original: "Video with /invalid\characters: < > | ? *"
Sanitized: "Video_with__invalid_characters_____.mp4"
```

### File Format Support

The app supports multiple media formats with intelligent MIME type detection:

```kotlin
// MIME type determination
val mimeType = when (videoFile.extension.lowercase()) {
    "mp4" -> "video/mp4"
    "webm" -> "video/webm"
    "mkv" -> "video/x-matroska"
    "avi" -> "video/x-msvideo"
    "mov" -> "video/quicktime"
    "mp3" -> "audio/mpeg"
    "m4a" -> "audio/mp4"
    "wav" -> "audio/wav"
    else -> "video/*"
}
```

**Supported Formats:**
- **Video**: MP4, WebM, MKV, AVI, MOV
- **Audio**: MP3, M4A, WAV
- **Fallback**: Generic MIME types for unknown formats

### File Sharing & Integration

Completed downloads are automatically shared using Android's intent system:

```kotlin
// File sharing with proper permissions
val shareIntent = Intent(Intent.ACTION_SEND).apply {
    type = mimeType
    putExtra(Intent.EXTRA_STREAM, videoUri)
    putExtra(Intent.EXTRA_SUBJECT, "Media downloaded with Share2Archive")
    putExtra(Intent.EXTRA_TEXT, "Check out this media I downloaded!")
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
```

**Sharing Features:**
- **Automatic Sharing**: Files shared immediately after download
- **Proper Permissions**: FileProvider implementation for Android 7+
- **MIME Type Accuracy**: Correct media type for recipient apps
- **User Choice**: Android's share sheet for app selection

## Dependencies & Libraries

### Comprehensive Dependency Table

The project uses a carefully selected set of dependencies for optimal functionality. Below is a complete table of all dependencies with their project information, versions, and last update dates.

| Dependency | Group/Organization | Project Page | Source Repository | Version | Last Update | Release Date |
|------------|-------------------|--------------|-------------------|---------|-------------|--------------|
| **Android Gradle Plugin** | Google | [Android Developer](https://developer.android.com/studio/releases/gradle-plugin) | [GitHub](https://github.com/android/gradle-plugin) | 8.12.0 | 2024-12-19 | 2024-12-19 |
| **Kotlin** | JetBrains | [Kotlin](https://kotlinlang.org/) | [GitHub](https://github.com/JetBrains/kotlin) | 1.9.0 | 2023-07-06 | 2023-07-06 |
| **AndroidX Core KTX** | Google | [AndroidX](https://developer.android.com/jetpack/androidx/releases/core) | [GitHub](https://github.com/android/android-ktx) | 1.10.1 | 2023-08-16 | 2023-08-16 |
| **AndroidX AppCompat** | Google | [AndroidX](https://developer.android.com/jetpack/androidx/releases/appcompat) | [GitHub](https://github.com/androidx/androidx) | 1.6.1 | 2023-06-07 | 2023-06-07 |
| **AndroidX Lifecycle Runtime KTX** | Google | [AndroidX](https://developer.android.com/jetpack/androidx/releases/lifecycle) | [GitHub](https://github.com/androidx/androidx) | 2.6.1 | 2023-06-07 | 2023-06-07 |
| **AndroidX Activity Compose** | Google | [AndroidX](https://developer.android.com/jetpack/androidx/releases/activity) | [GitHub](https://github.com/androidx/androidx) | 1.8.0 | 2023-08-16 | 2023-08-16 |
| **AndroidX Compose BOM** | Google | [AndroidX](https://developer.android.com/jetpack/androidx/releases/compose) | [GitHub](https://github.com/androidx/androidx) | 2024.04.01 | 2024-04-01 | 2024-04-01 |
| **AndroidX Compose UI** | Google | [AndroidX](https://developer.android.com/jetpack/androidx/releases/compose) | [GitHub](https://github.com/androidx/androidx) | BOM | 2024-04-01 | 2024-04-01 |
| **AndroidX Compose Material3** | Google | [AndroidX](https://developer.android.com/jetpack/androidx/releases/compose) | [GitHub](https://github.com/androidx/androidx) | BOM | 2024-04-01 | 2024-04-01 |
| **ZXing Core** | Google | [ZXing](https://github.com/zxing/zxing) | [GitHub](https://github.com/zxing/zxing) | 3.5.3 | 2023-12-19 | 2023-12-19 |
| **ML Kit Barcode Scanning** | Google | [ML Kit](https://developers.google.com/ml-kit/vision/barcode-scanning) | [GitHub](https://github.com/googlesamples/mlkit) | 18.3.1 | 2024-01-16 | 2024-01-16 |
| **Google Play Services Tasks** | Google | [Google Play Services](https://developers.google.com/android/guides/setup) | [GitHub](https://github.com/googlesamples/google-services) | 18.2.0 | 2024-01-16 | 2024-01-16 |
| **Kotlin Coroutines Android** | JetBrains | [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines) | [GitHub](https://github.com/Kotlin/kotlinx.coroutines) | 1.7.3 | 2023-07-18 | 2023-07-18 |
| **Apache Commons IO** | Apache | [Apache Commons IO](https://commons.apache.org/proper/commons-io/) | [GitHub](https://github.com/apache/commons-io) | 2.15.1 | 2023-12-19 | 2023-12-19 |
| **Apache Commons Compress** | Apache | [Apache Commons Compress](https://commons.apache.org/proper/commons-compress/) | [GitHub](https://github.com/apache/commons-compress) | 1.25.0 | 2024-01-16 | 2024-01-16 |
| **Jackson Databind** | FasterXML | [Jackson](https://github.com/FasterXML/jackson) | [GitHub](https://github.com/FasterXML/jackson) | 2.16.1 | 2024-01-16 | 2024-01-16 |
| **Jackson Annotations** | FasterXML | [Jackson](https://github.com/FasterXML/jackson) | [GitHub](https://github.com/FasterXML/jackson) | 2.16.1 | 2024-01-16 | 2024-01-16 |
| **JUnit** | JUnit Team | [JUnit](https://junit.org/) | [GitHub](https://github.com/junit-team/junit4) | 4.13.2 | 2020-10-14 | 2020-10-14 |
| **JUnit Jupiter** | JUnit Team | [JUnit 5](https://junit.org/junit5/) | [GitHub](https://github.com/junit-team/junit5) | 5.8.1 | 2021-12-03 | 2021-12-03 |
| **AndroidX Test JUnit** | Google | [AndroidX Test](https://developer.android.com/jetpack/androidx/releases/test) | [GitHub](https://github.com/androidx/androidx) | 1.1.5 | 2023-06-07 | 2023-06-07 |
| **AndroidX Test Espresso** | Google | [AndroidX Test](https://developer.android.com/jetpack/androidx/releases/test) | [GitHub](https://github.com/androidx/androidx) | 3.5.1 | 2023-06-07 | 2023-06-07 |
| **youtubedl-android Library** | yausername | [youtubedl-android](https://github.com/yausername/youtubedl-android) | [GitHub](https://github.com/yausername/youtubedl-android) | Local (0.14.0-67-g671d51a) | 2025-04-26 | Local Source |
| **youtubedl-android FFmpeg** | yausername | [youtubedl-android](https://github.com/yausername/youtubedl-android) | [GitHub](https://github.com/yausername/youtubedl-android) | Local (0.14.0-67-g671d51a) | 2025-04-26 | Local Source |
| **youtubedl-android Aria2c** | yausername | [youtubedl-android](https://github.com/yausername/youtubedl-android) | [GitHub](https://github.com/yausername/youtubedl-android) | Local (0.14.0-67-g671d51a) | 2025-04-26 | Local Source |
| **youtubedl-android Common** | yausername | [youtubedl-android](https://github.com/yausername/youtubedl-android) | [GitHub](https://github.com/yausername/youtubedl-android) | Local (0.14.0-67-g671d51a) | 2025-04-26 | Local Source |

### Local Module Dependencies

#### youtubedl-android Module Dependencies

The project integrates youtubedl-android directly as local modules for maximum control and latest features. Since these are Git submodules, the version information shows the actual commit hash and date from the upstream repository:

**Current Submodule Status**: `0.14.0-67-g671d51a` (67 commits ahead of tag 0.14.0, at commit 671d51a from 2025-04-26)

**Common Module (`:youtubedl-common`)**
- **Purpose**: Shared utilities and common functionality across all modules
- **Key Features**: File operations, compression support, preferences management
- **Dependencies**: AndroidX AppCompat, Core KTX, Apache Commons IO, Apache Commons Compress

**Library Module (`:youtubedl-library`)**
- **Purpose**: Core yt-dlp integration logic and download engine
- **Key Features**: Video info extraction, format detection, download management
- **Dependencies**: Common module, AndroidX AppCompat, Core KTX, Jackson Databind, Jackson Annotations, Apache Commons IO

**FFmpeg Module (`:youtubedl-ffmpeg`)**
- **Purpose**: Media processing and format conversion support
- **Key Features**: Video/audio codec support, post-processing capabilities
- **Dependencies**: Common module, Library module, AndroidX AppCompat, Core KTX, Apache Commons IO

**Aria2c Module (`:youtubedl-aria2c`)**
- **Purpose**: Download acceleration and multi-connection support
- **Key Features**: Multi-connection downloading, resume support, bandwidth management
- **Dependencies**: Common module, Library module, AndroidX AppCompat, Core KTX, Apache Commons IO

#### FFmpeg Submodule Integration

**FFmpeg Kit Android (`:ffmpeg-kit-android-lib`)**
- **Purpose**: Native FFmpeg integration for media processing and format conversion
- **Source**: Git submodule from [moizhassankh/ffmpeg-kit-android-16KB](https://github.com/moizhassankh/ffmpeg-kit-android-16KB)
- **Key Features**: 
  - Native FFmpeg libraries (libavcodec, libavformat, libavfilter, etc.)
  - Support for multiple architectures (arm64-v8a, armeabi-v7a, x86, x86_64)
  - Media format conversion and post-processing capabilities
  - Optimized 16KB variant for Android applications
- **Dependencies**: Smart Exception Java library
- **Integration**: Direct project dependency, no external repository required
- **Build Configuration**: Compatible with project's compileSdk 36 and NDK 25.2.9519653

### External Library Dependencies

#### Core Functionality Libraries

**ZXing Core (3.5.3)**
- **Purpose**: QR code and barcode scanning functionality
- **Why Used**: Lightweight, mature, self-contained without external dependencies
- **Last Update**: December 19, 2023

**ML Kit Barcode Scanning (18.3.1)**
- **Purpose**: Enhanced barcode scanning with machine learning
- **Why Used**: Better accuracy than ZXing alone, optional integration
- **Last Update**: January 16, 2024

**Google Play Services Tasks (18.2.0)**
- **Purpose**: Task management for ML Kit integration
- **Why Used**: Required for ML Kit functionality, latest stable version
- **Last Update**: January 16, 2024

#### Async Processing Libraries

**Kotlin Coroutines Android (1.7.3)**
- **Purpose**: Asynchronous operations and non-blocking UI operations
- **Why Used**: Native Android lifecycle support, structured concurrency
- **Last Update**: July 18, 2023

#### Data Processing Libraries

**Apache Commons IO (2.15.1)**
- **Purpose**: Robust file operations and utilities
- **Why Used**: Mature library, comprehensive file handling capabilities
- **Last Update**: December 19, 2023

**Apache Commons Compress (1.25.0)**
- **Purpose**: Archive format support and compression
- **Why Used**: Wide format support, active maintenance
- **Last Update**: January 16, 2024

**Jackson Databind (2.16.1)**
- **Purpose**: JSON processing and data binding
- **Why Used**: High performance, comprehensive feature set
- **Last Update**: January 16, 2024

**Jackson Annotations (2.16.1)**
- **Purpose**: Jackson annotation support
- **Why Used**: Required companion to Jackson Databind
- **Last Update**: January 16, 2024

#### Testing Libraries

**JUnit (4.13.2)**
- **Purpose**: Unit testing framework
- **Why Used**: Standard testing framework, stable version
- **Last Update**: October 14, 2020

**JUnit Jupiter (5.8.1)**
- **Purpose**: JUnit 5 testing framework
- **Why Used**: Modern testing features, parallel execution support
- **Last Update**: December 3, 2021

**AndroidX Test Libraries**
- **JUnit Extension (1.1.5)**: Android-specific JUnit testing
- **Espresso Core (3.5.1)**: UI testing and automation
- **Last Updates**: June 7, 2023

### Version Catalog Management

The project uses Gradle's version catalog for centralized dependency management:

```toml
# gradle/libs.versions.toml
[versions]
agp = "8.12.0"                    # Android Gradle Plugin
kotlin = "1.9.0"                  # Kotlin compiler
coreKtx = "1.10.1"                # AndroidX Core
commonsIo = "2.15.1"              # Apache Commons IO
commonsCompress = "1.25.0"        # Apache Commons Compress
jackson = "2.16.1"                # Jackson JSON processing

[libraries]
commons-io = { group = "org.apache.commons", name = "commons-io", version.ref = "commonsIo" }
commons-compress = { group = "org.apache.commons", name = "commons-compress", version.ref = "commonsCompress" }
jackson-databind = { group = "com.fasterxml.jackson.core", name = "jackson-databind", version.ref = "jackson" }
```

**Benefits:**
- **Centralized Management**: Single source of truth for versions
- **Easy Updates**: Update versions in one location
- **Consistency**: Ensures all modules use same versions
- **Maintenance**: Simplified dependency maintenance

### Dependency Architecture Philosophy

#### Minimal External Dependencies
The project minimizes external dependencies to:
- **Reduce APK Size**: Smaller final application size
- **Improve Security**: Fewer potential security vulnerabilities
- **Enhance Stability**: Less dependency on external services
- **Simplify Maintenance**: Fewer libraries to update and maintain

#### Local Module Priority
Local modules are preferred because:
- **Source Control**: Direct access to source code
- **Customization**: Ability to modify functionality as needed
- **Version Control**: No dependency on external release schedules
- **Quality Assurance**: Full control over code quality and testing

#### Strategic Library Selection
Each external library is chosen for:
- **Specific Functionality**: Addresses a specific technical need
- **Mature Stability**: Well-tested and reliable
- **Active Maintenance**: Regular updates and security patches
- **Performance**: Efficient implementation for the use case

### Dependency Update Strategy

#### Regular Updates
- **Security Patches**: Immediate updates for security vulnerabilities
- **Feature Updates**: Regular updates for new functionality
- **Bug Fixes**: Updates for critical bug fixes
- **Performance**: Updates for performance improvements

#### Update Process
1. **Dependency Audit**: Regular security vulnerability scanning
2. **Version Testing**: Test new versions in development environment
3. **Compatibility Check**: Ensure compatibility with existing code
4. **Gradual Rollout**: Update dependencies incrementally

#### Rollback Strategy
- **Version Pinning**: Specific version numbers for stability
- **Fallback Versions**: Previous stable versions as fallbacks
- **Testing**: Comprehensive testing before production deployment
- **Monitoring**: Post-update monitoring for issues

## Development Setup

### Prerequisites

- **Android Studio**: Latest stable version (Hedgehog or newer)
- **JDK**: Version 17 or 18 (LTS versions recommended)
- **Android SDK**: API level 36 (Android 14)
- **NDK**: Version 25.2.9519653
- **Physical Device**: For testing video downloads (emulators may have limitations)

### Initial Setup

1. **Clone the repository**:
   ```bash
   git clone <repository-url>
   cd Share2ArchiveToday
   ```

2. **Initialize youtubedl-android submodules**:
   ```bash
   # If youtubedl-android directory doesn't exist
   git clone https://github.com/yausername/youtubedl-android.git
   ```

3. **Sync Gradle project** in Android Studio

### Build Commands

```bash
# Clean build
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run tests
./gradlew test

# Install on connected device
./gradlew installDebug
```

## Maintenance & Updates

### Updating youtubedl-android

The project now uses a single, comprehensive script that handles all youtubedl-android management tasks:

```bash
# Comprehensive youtubedl-android management script
./build-youtubedl-local.sh                    # Full build (init + update + build)
./build-youtubedl-local.sh --init-only        # Only initialize submodules
./build-youtubedl-local.sh --update-only      # Only update submodules
./build-youtubedl-local.sh --build-only       # Only build (assume submodules ready)
./build-youtubedl-local.sh --help             # Show usage options

# Legacy update script (still available)
./update-youtubedl.sh
```

**Script Features:**
- **Automatic submodule management**: Initializes and updates Git submodules automatically
- **Flexible operation modes**: Choose to initialize, update, or build independently
- **Error handling**: Comprehensive error checking with helpful error messages
- **Colored output**: Easy-to-read status messages with color coding
- **Fallback support**: Handles both Git repository and direct clone scenarios

### Submodule Management

Since the project now uses Git submodules for youtubedl-android and FFmpeg:

```bash
# Option 1: Clone with submodules (recommended)
git clone --recursive <repository-url>

# Option 2: Clone then initialize submodules
git clone <repository-url>
cd Share2ArchiveToday
./build-youtubedl-local.sh --init-only

# Option 3: Let Gradle handle it automatically
./gradlew build  # Will auto-initialize submodules if needed
```

#### FFmpeg Submodule Updates

The FFmpeg library is included as a Git submodule and can be updated independently:

```bash
# Update FFmpeg to latest version
cd ffmpeg-kit-android
git fetch origin
git checkout main
git pull origin main
cd ..

# Rebuild project to use updated FFmpeg
./gradlew clean build
```

**Note**: FFmpeg updates are typically stable and don't require frequent updates. The current version (6.0.0) provides comprehensive media processing capabilities.

### Manual Update Process

1. **Navigate to youtubedl-android directory**:
   ```bash
   cd youtubedl-android
   ```

2. **Pull latest changes**:
   ```bash
   git fetch origin
   git checkout master
   git pull origin master
   ```

3. **Rebuild project**:
   ```bash
   cd ..
   ./gradlew clean build
   ```

### Dependency Management

- **Local Modules**: Always use local module dependencies
- **External Libraries**: Minimize external dependencies, prefer AndroidX
- **Version Updates**: Regular updates through version catalog
- **Security**: Regular dependency vulnerability scanning

## Testing & Debugging

### Device Testing

- **Physical Device Required**: Video downloads work best on real devices
- **ADB Access**: Ensure `~/Library/Android/sdk/platform-tools/adb` is in PATH
- **Storage Permissions**: Grant necessary storage permissions for downloads
- **Network Testing**: Test on various network conditions

### Debug Features

- **Logging**: Comprehensive logging throughout video download process
- **Progress Tracking**: Real-time download progress with Toast notifications
- **Error Handling**: Graceful fallbacks and user-friendly error messages
- **Network Diagnostics**: Connectivity testing and validation

### Common Issues

1. **Permission Denied**: Ensure storage permissions are granted
2. **Network Errors**: Check internet connectivity and firewall settings
3. **FFmpeg Issues**: FFmpeg is optional, app continues without it
4. **Format Errors**: Some video formats may not be supported on all devices

## Contributing

### Code Style

- **Kotlin**: Use modern Kotlin features and idioms
- **Coroutines**: Prefer coroutines over callbacks for async operations
- **Error Handling**: Comprehensive exception handling with user feedback
- **Documentation**: Clear inline documentation for complex logic

### Testing Guidelines

- **Unit Tests**: Test business logic independently
- **Integration Tests**: Test module interactions
- **UI Tests**: Test user interactions and flows
- **Device Testing**: Always test on physical devices for video features

### Pull Request Process

1. **Feature Branch**: Create feature branch from master
2. **Code Review**: Ensure code follows project standards
3. **Testing**: Verify functionality on physical device
4. **Documentation**: Update relevant documentation
5. **Merge**: Merge after approval and testing

## Build Variants

### Debug Build

- **Optimizations**: Disabled for faster builds and debugging
- **Logging**: Full logging enabled
- **Testing**: Unit test coverage enabled
- **Signing**: Debug signing key

### Release Build

- **Optimizations**: Full ProGuard optimization
- **Resource Shrinking**: Enabled to reduce APK size
- **Logging**: Minimal logging for production
- **Signing**: Release signing key required

## Performance Considerations

### Memory Management

- **Coroutine Scopes**: Properly scoped coroutines to prevent memory leaks
- **Resource Cleanup**: Proper cleanup in onDestroy methods
- **Large File Handling**: Efficient handling of video files

### Network Optimization

- **Connection Pooling**: Reuse HTTP connections
- **Progress Tracking**: Real-time progress without blocking UI
- **Error Recovery**: Graceful handling of network failures

### Storage Management

- **Download Organization**: Structured folder hierarchy
- **File Naming**: Safe filename generation
- **Storage Quotas**: Respect device storage limitations

## Security Considerations

### Permissions

- **Minimal Permissions**: Only request necessary permissions
- **Runtime Permissions**: Proper handling of Android 6.0+ permission model
- **Storage Access**: Proper FileProvider implementation for file sharing

### Network Security

- **HTTPS**: Prefer secure connections
- **Certificate Pinning**: Consider for critical endpoints
- **Input Validation**: Validate all user inputs and URLs

### Data Privacy

- **Local Storage**: User data stays on device
- **No Analytics**: No user tracking or analytics
- **Transparent**: Open source with clear data handling

## Future Enhancements

### Planned Features

- **Batch Downloads**: Multiple video downloads
- **Format Selection**: User choice of video quality
- **Download Queue**: Background download management
- **Cloud Integration**: Save to cloud storage services

### Technical Improvements

- **Jetpack Compose**: Modern UI framework migration
- **Material Design 3**: Latest design system
- **Performance Monitoring**: Built-in performance metrics
- **Accessibility**: Enhanced accessibility features

## Support & Resources


### External Resources

- **youtubedl-android**: [GitHub Repository](https://github.com/yausername/youtubedl-android)
- **yt-dlp**: [GitHub Repository](https://github.com/yt-dlp/yt-dlp)
- **Android Developer**: [Official Documentation](https://developer.android.com/)
- **Kotlin**: [Official Documentation](https://kotlinlang.org/)

---

This development guide provides comprehensive information for developers working on the Share2ArchiveToday project. For specific questions or clarifications, please refer to the codebase or create an issue in the project repository.
