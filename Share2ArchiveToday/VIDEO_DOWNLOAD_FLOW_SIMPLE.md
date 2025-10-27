# Video Download Flow - Simplified Overview

## Quick Reference: From Share Menu to Downloaded Video

```
┌─────────────────────────┐
│   USER SHARES URL       │
│   (Android Share Menu)  │
└───────────┬─────────────┘
            │
            ▼
┌─────────────────────────┐
│  VideoDownloadActivity  │
│  ● Check permissions    │
│  ● Extract URL          │
└───────────┬─────────────┘
            │
            ▼
┌─────────────────────────┐
│ VideoDownloadCoordinator│
│  ● Block YouTube        │
│  ● Check if downloaded  │
│  ● Show quality dialog  │
└───────────┬─────────────┘
            │
            ▼
┌─────────────────────────┐
│BackgroundDownloadService│
│  ● Foreground service   │
│  ● Check network/storage│
└───────────┬─────────────┘
            │
            ▼
┌─────────────────────────┐
│  PythonVideoDownloader  │
│  ● Call yt-dlp          │
│  ● Download streams     │
│  ● Progress updates     │
└───────────┬─────────────┘
            │
            ▼
┌─────────────────────────┐
│  Post-Processing        │
│  ● Merge audio/video    │
│  ● Convert formats      │
│  ● Move to MediaStore   │
└───────────┬─────────────┘
            │
            ▼
┌─────────────────────────┐
│  Success/Failure        │
│  ● Save to history      │
│  ● Show notification    │
│  ● Open file (success)  │
└─────────────────────────┘
```

## The 11 Main Steps

### 1. **Entry Point** - VideoDownloadActivity
- User shares URL from any app
- Android sends Intent to VideoDownloadActivity
- Check if yt-dlp is working
- Extract URL from Intent

### 2. **Permission Check** - PermissionManager
- Check storage permissions
- Request if not granted
- If denied → Exit

### 3. **URL Validation** - VideoDownloadCoordinator
- Block YouTube URLs (Play Store policy)
- Clean URL parameters
- Validate format

### 4. **Duplicate Check** - DownloadHistoryManager
- Search history for this URL
- If found → Offer to open existing file
- If not found → Continue

### 5. **Format Analysis** - PythonVideoDownloader
- Call yt-dlp to get available formats
- Get video info (title, uploader, duration)
- Analyze device capabilities

### 6. **Quality Selection** - VideoFormatSelector
- Filter formats by device screen resolution
- Check available storage space
- Show user quality options
- Include audio-only option

### 7. **Network/Storage Check** - DownloadPrerequisitesChecker
- Verify internet connection
- Warn if on mobile data
- Ensure sufficient storage (50MB minimum)
- Stop if checks fail

### 8. **Download Execution** - PythonVideoDownloader + yt-dlp
- Start foreground service
- Call Python yt-dlp module
- Download video stream
- Download audio stream (if separate)
- Show real-time progress

### 9. **Post-Processing** - FileProcessor
- Merge separate audio/video (if needed)
- Extract audio (for audio-only downloads)
- Convert audio format (remux if needed)
- Clean up temporary files

### 10. **MediaStore Integration** - MediaStoreHelper
- Create MediaStore entry
- Copy file to Downloads collection
- Set metadata (title, date, size)
- Get content:// URI

### 11. **Completion** - DownloadResultHandler
- Save to download history
- Show success notification
- Offer to open/share file
- Stop foreground service

## Key Decision Points

### YouTube URL?
```
YES → Block download (Play Store policy)
NO  → Continue
```

### Already Downloaded?
```
YES → Show dialog:
      ├─ Open existing file
      └─ Download again
NO  → Continue to quality selection
```

### Mobile Data?
```
YES → Show warning:
      ├─ Continue anyway
      └─ Cancel
NO  → Continue
```

### Separate Audio/Video?
```
YES → Merge using FFmpeg
NO  → Use direct download
```

### Audio Only?
```
YES → Check format:
      ├─ Needs remuxing? → Convert
      └─ Good format? → Keep
NO  → Standard video processing
```

## File Flow

```
1. Download → /Download/temp/[filename].[ext]
              ↓
2. Process  → Merge/Extract/Convert (if needed)
              ↓
3. Move     → MediaStore (content://media/external/downloads/[id])
              ↓
4. Cleanup  → Delete temp files
              ↓
5. Access   → User can open via content:// URI
```

## Error Handling

Every step has error handling that:
1. Converts technical errors to user-friendly messages
2. Saves failure to history
3. Shows notification with error
4. Cleans up partial files
5. Allows retry from download history

## Progress Updates

Throughout download:
- Notification shows: percentage, speed, ETA, file size
- Updates every ~500ms
- Persisted for crash recovery
- Can be viewed in notification shade

## Background Operation

Service runs as **Foreground Service**:
- Ensures Android doesn't kill it
- Shows persistent notification
- Continues even if app is closed
- Can be cancelled by user
- Automatically stops when complete

## File Opening After Download

When user taps "Open" notification:
```
Intent.ACTION_VIEW
  ├─ Set data: content:// URI
  ├─ Set type: video/mp4 (or audio/*)
  └─ Grant READ_URI_PERMISSION
      → Android picks best app to open
```

## Architecture Benefits

**Separation of Concerns:**
- UI (Activity) separate from business logic (Coordinator)
- Download logic (Service) separate from UI
- File operations isolated in helpers
- Easy to test individual components

**Modularity:**
- Each class has single responsibility
- Can be refactored independently
- Easy to add features (e.g., pause/resume)

**Robustness:**
- Download can survive activity destruction
- Progress saved for crash recovery
- Comprehensive error handling
- User-friendly error messages


