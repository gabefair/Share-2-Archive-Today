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

### Core Dependencies Overview

The project uses a carefully selected set of dependencies for optimal functionality:

```kotlin
// Main app dependencies (app/build.gradle.kts)
dependencies {
    // QR Code scanning
    implementation("com.google.zxing:core:3.5.3")
    
    // Optional ML Kit integration
    compileOnly("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")
    compileOnly("com.google.android.gms:play-services-tasks:18.2.0")
    
    // Local youtubedl-android modules
    implementation(project(":youtubedl-library"))
    implementation(project(":youtubedl-ffmpeg"))
    implementation(project(":youtubedl-aria2c"))
    
    // Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

### youtubedl-android Module Dependencies

#### Common Module (`:youtubedl-common`)
```kotlin
dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.commons.io)           // File operations
    implementation(libs.commons.compress)     // Archive handling
}
```

**Purpose & Usage:**
- **Shared Utilities**: Common functionality across all modules
- **File Operations**: Apache Commons IO for robust file handling
- **Compression**: Support for various archive formats
- **Preferences**: Shared preferences management

#### Library Module (`:youtubedl-library`)
```kotlin
dependencies {
    implementation(project(":youtubedl-common"))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.jackson.databind)     // JSON parsing
    implementation(libs.jackson.annotations)  // Jackson annotations
    implementation(libs.commons.io)           // File utilities
}
```

**Purpose & Usage:**
- **Core Functionality**: Main yt-dlp integration logic
- **JSON Processing**: Jackson for API response parsing
- **Video Info**: Metadata extraction and format detection
- **Download Engine**: Core download management

#### FFmpeg Module (`:youtubedl-ffmpeg`)
```kotlin
dependencies {
    implementation(project(":youtubedl-common"))
    implementation(project(":youtubedl-library"))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.commons.io)
}
```

**Purpose & Usage:**
- **Media Processing**: Video/audio format conversion
- **Codec Support**: Extended format compatibility
- **Quality Enhancement**: Post-processing capabilities
- **Optional Integration**: App continues without FFmpeg if unavailable

#### Aria2c Module (`:youtubedl-aria2c`)
```kotlin
dependencies {
    implementation(project(":youtubedl-common"))
    implementation(project(":youtubedl-library"))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.commons.io)
}
```

**Purpose & Usage:**
- **Download Acceleration**: Multi-connection downloading
- **Resume Support**: Interrupted download recovery
- **Bandwidth Management**: Efficient network utilization
- **Optional Enhancement**: Standard downloads work without Aria2c

### External Library Dependencies

#### ZXing Core (3.5.3)
```kotlin
implementation("com.google.zxing:core:3.5.3")
```

**Why Used:**
- **QR Code Scanning**: Core barcode/QR code functionality
- **Lightweight**: Minimal dependency footprint
- **Mature Library**: Well-tested and stable
- **No Permissions**: Self-contained without external dependencies

#### ML Kit Barcode Scanning (18.3.1)
```kotlin
compileOnly("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")
```

**Why Used:**
- **Enhanced Scanning**: Better accuracy than ZXing alone
- **Optional Integration**: App works without Google Play Services
- **Modern API**: Latest ML Kit features
- **Fallback Support**: ZXing provides basic functionality

#### Coroutines (1.7.3)
```kotlin
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
```

**Why Used:**
- **Async Operations**: Non-blocking download operations
- **UI Thread Safety**: Proper thread management
- **Error Handling**: Structured concurrency
- **Android Integration**: Native Android lifecycle support

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

The project includes automated update scripts:

```bash
# Update to latest source code
./update-youtubedl.sh

# Build locally from source
./build-youtubedl-local.sh
```

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
