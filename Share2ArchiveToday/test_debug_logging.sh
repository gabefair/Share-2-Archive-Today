#!/bin/bash

# Debug Logging Test Script
# This script helps test that debug logging is working properly

echo "=== Debug Logging Test ==="
echo "Testing Android debug logging configuration..."
echo ""

# Check if device is connected
if ! adb devices | grep -q "device$"; then
    echo "❌ No Android device connected"
    echo "Please connect your device and enable USB debugging"
    exit 1
fi

echo "✅ Android device connected"
echo ""

# Clear existing logs
echo "Clearing existing logs..."
adb logcat -c

echo ""
echo "🔍 Starting log monitoring..."
echo "Now launch your app and perform a video download test"
echo "Press Ctrl+C to stop monitoring"
echo ""

# Monitor logs with filtering
adb logcat -s "Share2ArchiveToday:*" "PythonVideoDownloader:*" "VideoDownloadActivity:*" "BackgroundDownloadService:*" "DebugLogger:*" | while read line; do
    echo "$line"
done
