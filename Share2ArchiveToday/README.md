# Share 2 Archive Today

An Android app that adds an icon to the share menu to archive URLs using the archive.today service.

## Features

The app provides three options when sharing a URL:

1. **Archive Page** - Saves the webpage to archive.today
2. **Copy Clean URL** - Copies the cleaned URL (with tracking parameters removed)
3. **Download Video** - Downloads videos from web pages using yt-dlp

## Video Download Feature

The video download option uses [yt-dlp](https://github.com/yt-dlp/yt-dlp), a powerful and active fork of youtube-dl that can extract videos from many platforms including:

- YouTube
- Vimeo
- Dailymotion
- Twitter/X
- Instagram
- Facebook
- Reddit
- And many more

### How it works

1. When you select "Download Video" from the share menu, the app analyzes the webpage
2. It uses yt-dlp to extract video information and available formats
3. Downloads the best quality video available
4. Saves the video to your Downloads folder

### Fallback methods

If yt-dlp extraction fails, the app will:
1. Try to find direct video URLs in the page HTML
2. Look for video source tags and iframe embeds
3. Attempt direct download of found video URLs

## Permissions

The app requires the following permissions:
- `INTERNET` - To access web pages and download videos
- `WRITE_EXTERNAL_STORAGE` - To save downloaded videos
- `READ_EXTERNAL_STORAGE` - To access the downloads folder
- `READ_MEDIA_VIDEO` - To access video files (Android 13+)

## Installation

1. Build the project using Android Studio
2. Install the APK on your device
3. The app will appear in your share menu when sharing URLs

## Usage

1. Find a webpage with a video you want to download
2. Use the share menu in your browser or app
3. Select "Share 2 Archive Today"
4. Choose "Download Video" from the options
5. The video will be downloaded to your Downloads folder

## Technical Details

- Built with Kotlin
- Uses yt-dlp for video extraction
- Implements coroutines for async operations
- Uses OkHttp for network requests
- Supports multiple video formats and qualities

## Dependencies

- `com.github.yausername:yt-dlp-android:0.14.5` - Video extraction engine
- `com.squareup.okhttp3:okhttp:4.12.0` - HTTP client
- `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3` - Async operations

## License

This project uses yt-dlp which is licensed under the Unlicense. Please respect the terms of service of the platforms you're downloading from.
