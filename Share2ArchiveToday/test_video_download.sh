#!/bin/bash

# Test script for video download functionality
# This script helps test the video download feature implementation

echo "🎬 Share2ArchiveToday Video Download Test Script"
echo "================================================"

# Check if we're in the right directory
if [ ! -f "app/build.gradle.kts" ]; then
    echo "❌ Error: Please run this script from the project root directory"
    exit 1
fi

# Check if ADB is available
if ! command -v adb &> /dev/null; then
    echo "❌ Error: ADB not found. Please install Android SDK tools"
    exit 1
fi

# Check if device is connected
if ! adb devices | grep -q "device$"; then
    echo "❌ Error: No Android device connected. Please connect a device and enable USB debugging"
    exit 1
fi

echo "✅ Android device connected"

# Build the app
echo "🔨 Building app..."
if ./gradlew assembleDebug; then
    echo "✅ Build successful"
else
    echo "❌ Build failed"
    exit 1
fi

# Install the app
echo "📱 Installing app..."
if adb install -r app/build/outputs/apk/debug/app-debug.apk; then
    echo "✅ App installed successfully"
else
    echo "❌ App installation failed"
    exit 1
fi

# Clear logs
echo "🧹 Clearing logs..."
adb logcat -c

echo ""
echo "🧪 Testing Instructions:"
echo "========================"
echo "1. Open the app and look for 'Test Video Download' in the app list"
echo "2. Tap it to run automated tests"
echo "3. Or share a YouTube URL and select 'Download Video/Media'"
echo ""
echo "📊 Monitoring logs:"
echo "==================="
echo "Run this command in another terminal to monitor logs:"
echo "adb logcat | grep -E '(VideoDownload|FFmpegWrapper|Chaquopy)'"
echo ""
echo "🔍 Debug commands:"
echo "=================="
echo "Check Python initialization: adb logcat | grep 'Python initialized'"
echo "Check download progress: adb logcat | grep 'Download completed'"
echo "Check errors: adb logcat | grep -E '(Error|Exception)'"
echo ""

# Start monitoring logs
echo "📋 Starting log monitoring (Press Ctrl+C to stop)..."
echo "=================================================="
adb logcat | grep -E "(VideoDownload|FFmpegWrapper|Chaquopy|Python|yt-dlp)"
