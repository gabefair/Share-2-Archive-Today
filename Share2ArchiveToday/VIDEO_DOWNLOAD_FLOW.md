# Video Download Flow Diagram

## Complete Method Call Flow from Share Menu to Download Completion

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        USER INITIATES SHARE                              │
│                                                                           │
│  User shares video URL from browser/app → Android Share Menu            │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    1. VideoDownloadActivity                              │
│                                                                           │
│  onCreate()                                                              │
│    │                                                                      │
│    ├─► Initialize Components:                                           │
│    │   ├─ PythonVideoDownloader                                         │
│    │   ├─ PermissionManager                                             │
│    │   ├─ NetworkMonitor                                                │
│    │   ├─ DownloadHistoryManager                                        │
│    │   └─ VideoFormatSelector                                           │
│    │                                                                      │
│    ├─► testPythonFunctionality()                                        │
│    │   └─ Verify yt-dlp is working                                      │
│    │                                                                      │
│    └─► handleShareIntent(intent)                                        │
│         │                                                                 │
│         ├─ Extract URL from Intent                                      │
│         │  └─ ACTION_SEND with text/plain                               │
│         │                                                                 │
│         └─ Check Permissions                                             │
│             │                                                             │
│             ├─ If NOT Granted ─────────────┐                            │
│             │                                │                            │
│             │                                ▼                            │
│             │              Request Permissions (Storage/Network)         │
│             │                                │                            │
│             │                                └─► onRequestPermissionsResult()
│             │                                     │                       │
│             │                                     └─ If Denied → Finish  │
│             │                                                             │
│             └─ If Granted ─────────────────┐                            │
│                                             │                             │
└─────────────────────────────────────────────┼─────────────────────────────┘
                                              │
                                              ▼
┌─────────────────────────────────────────────────────────────────────────┐
│               2. VideoDownloadCoordinator                                │
│                                                                           │
│  startDownloadProcess(url)                                               │
│    │                                                                      │
│    ├─► Block YouTube URLs (Play Store compliance)                       │
│    │   └─ If YouTube → Show error & return                              │
│    │                                                                      │
│    └─► checkExistingDownloadAndProceed(url)                             │
│         │                                                                 │
│         ├─► downloadHistoryManager.findSuccessfulDownload(url)          │
│         │   │                                                             │
│         │   ├─ If Found ──────────────────────┐                         │
│         │   │                                   ▼                         │
│         │   │            ┌────────────────────────────────────┐         │
│         │   │            │  VideoDownloadDialogManager        │         │
│         │   │            │  showAlreadyDownloadedDialog()     │         │
│         │   │            │    ├─ Open Existing File           │         │
│         │   │            │    ├─ Download Again → Continue    │         │
│         │   │            │    └─ Cancel → Finish              │         │
│         │   │            └────────────────────────────────────┘         │
│         │   │                                                             │
│         │   └─ If NOT Found ────────────┐                               │
│         │                                │                                │
│         └────────────────────────────────┘                               │
│                                           │                                │
│         showQualitySelection(url) ◄───────┘                              │
│           │                                                                │
│           ├─► dialogManager.showLoadingDialog()                          │
│           │   └─ "Analyzing Video..."                                    │
│           │                                                                │
│           ├─► Launch Coroutine (IO)                                      │
│           │   │                                                            │
│           │   ├─► pythonDownloader.getVideoInfo(url)                    │
│           │   │   └─ Calls Python/yt-dlp to get formats                 │
│           │   │                                                            │
│           │   ├─► videoFormatSelector.getRecommendedFormats()           │
│           │   │   ├─ Analyzes device screen resolution                  │
│           │   │   ├─ Checks available space                             │
│           │   │   └─ Filters compatible formats                         │
│           │   │                                                            │
│           │   └─► dialogManager.showQualitySelectionDialog()            │
│           │       └─ User selects quality ──────────┐                   │
│           │                                          │                    │
│           └─► dismissLoadingDialog()                │                    │
│                                                       │                    │
└───────────────────────────────────────────────────┼───────────────────────┘
                                                    │
                     User selects quality          │
                                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│               3. Check Data Usage & Start Service                        │
│                                                                           │
│  executeDownloadWithQuality(formatOption)                                │
│    │                                                                      │
│    ├─► Check if on Mobile Data                                          │
│    │   │                                                                  │
│    │   ├─ If Mobile Data ────────────────────────┐                      │
│    │   │                                           ▼                      │
│    │   │              dialogManager.showDataUsageWarning()              │
│    │   │                ├─ Continue → Proceed                           │
│    │   │                └─ Cancel → Finish                              │
│    │   │                                                                  │
│    │   └─ If WiFi → Continue                                            │
│    │                                                                      │
│    └─► BackgroundDownloadService.startDownload()                        │
│         └─ Pass: url, title, uploader, quality, formatId               │
│                                                                           │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│              4. BackgroundDownloadService (Foreground)                   │
│                                                                           │
│  onStartCommand(intent)                                                  │
│    │                                                                      │
│    ├─► Create Foreground Notification                                   │
│    │   └─ "Starting download..."                                        │
│    │                                                                      │
│    ├─► Initialize All Managers:                                         │
│    │   ├─ PythonVideoDownloader                                         │
│    │   ├─ NotificationHelper                                            │
│    │   ├─ FileProcessor                                                 │
│    │   ├─ MediaStoreHelper                                              │
│    │   ├─ StorageHelper                                                 │
│    │   ├─ DownloadOrchestrator                                          │
│    │   ├─ DownloadPrerequisitesChecker                                  │
│    │   ├─ DownloadResultHandler                                         │
│    │   └─ ProgressHandler                                               │
│    │                                                                      │
│    └─► Launch Coroutine → startDownloadProcess()                        │
│         │                                                                 │
│         ├─► Extract parameters from Intent                              │
│         │   (url, title, uploader, quality, formatId)                   │
│         │                                                                 │
│         ├─► Generate downloadId = UUID                                  │
│         │                                                                 │
│         └─► downloadResumptionManager.startDownload()                   │
│             └─ Save download metadata for resumption                    │
│                                                                           │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│             5. DownloadPrerequisitesChecker                              │
│                                                                           │
│  checkPrerequisites(displayTitle, url, downloadId)                      │
│    │                                                                      │
│    ├─► checkNetwork()                                                   │
│    │   ├─ networkMonitor.isConnected()                                 │
│    │   │  └─ If NO → Show error & STOP                                 │
│    │   │                                                                  │
│    │   └─ networkMonitor.shouldWarnAboutDataUsage()                    │
│    │      └─ Update notification with warning                           │
│    │                                                                      │
│    └─► checkStorage()                                                   │
│        ├─ storageHelper.getAvailableStorageSpace()                     │
│        └─ If < 50MB → Show error & STOP                                │
│                                                                           │
│  Return: PrerequisiteCheckResult(passed = true/false)                   │
│                                                                           │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                  6. DownloadOrchestrator                                 │
│                                                                           │
│  executeDownload(url, downloadDir, quality, formatId, progressCallback) │
│    │                                                                      │
│    ├─► determineDisplayTitle(videoInfo, title, url)                    │
│    │   └─ Choose best title from available sources                     │
│    │                                                                      │
│    ├─► Route based on quality:                                          │
│    │   │                                                                  │
│    │   ├─ If audio_* ────────────────────────┐                         │
│    │   │                                       ▼                          │
│    │   │        pythonDownloader.downloadAudio()                        │
│    │   │          └─ Download as MP3/AAC                                │
│    │   │                                                                  │
│    │   └─ Else (video) ──────────────────────┐                         │
│    │                                           ▼                          │
│    │        pythonDownloader.downloadVideo()                            │
│    │          ├─ quality parameter                                      │
│    │          ├─ formatId (if specified)                                │
│    │          └─ progressCallback                                       │
│    │                                                                      │
│    └─► Monitor Progress via ProgressHandler                             │
│                                                                           │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                7. PythonVideoDownloader (Python/yt-dlp)                  │
│                                                                           │
│  downloadVideo(url, outputDir, quality, progressCallback)                │
│    │                                                                      │
│    ├─► Initialize Python if not already done                            │
│    │   └─ Python.start(new AndroidPlatform(context))                   │
│    │                                                                      │
│    ├─► Get Python module: ytdlp_downloader.py                           │
│    │                                                                      │
│    ├─► Call Python function: download_video()                           │
│    │   │                                                                  │
│    │   └─► yt-dlp.YoutubeDL().download([url])                          │
│    │       │                                                              │
│    │       ├─ Resolve URL                                               │
│    │       ├─ Fetch video metadata                                      │
│    │       ├─ Select format based on quality                            │
│    │       ├─ Download video stream                                     │
│    │       ├─ Download audio stream (if separate)                       │
│    │       └─ Call progress_hook for updates ─────────┐                │
│    │                                                    │                 │
│    └─► Progress Hook Callback ◄─────────────────────────┘               │
│        │                                                                  │
│        └─► progressCallback(ProgressInfo)                               │
│             └─ Sent to ProgressHandler                                  │
│                                                                           │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                  8. ProgressHandler (During Download)                    │
│                                                                           │
│  onProgress(progressInfo)                                                │
│    │                                                                      │
│    ├─► Parse progress status:                                           │
│    │   ├─ "downloading" → Update progress bar                           │
│    │   ├─ "finished" → Show completion                                  │
│    │   ├─ "error" → Show error                                          │
│    │   └─ "retrying" → Show retry attempt                               │
│    │                                                                      │
│    ├─► Build progress text:                                             │
│    │   ├─ Downloaded/Total bytes                                        │
│    │   ├─ Download speed                                                │
│    │   ├─ ETA                                                            │
│    │   └─ Percentage                                                     │
│    │                                                                      │
│    ├─► notificationHelper.updateProgress()                              │
│    │   └─ Update foreground notification                                │
│    │                                                                      │
│    └─► downloadResumptionManager.updateProgress()                       │
│        └─ Save progress for potential resumption                        │
│                                                                           │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
                   Download Complete
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│              9. Post-Download Processing (DownloadOrchestrator)          │
│                                                                           │
│  Process DownloadResult                                                  │
│    │                                                                      │
│    ├─► processAudioIfNeeded(result, quality)                            │
│    │   │                                                                  │
│    │   └─ If audio download or separate audio:                          │
│    │       ├─ Check if needs remuxing (AudioRemuxer)                    │
│    │       └─ fileProcessor.processAudioFile()                          │
│    │           └─ Convert to proper format                              │
│    │                                                                      │
│    ├─► processSeparateStreams(result)                                   │
│    │   │                                                                  │
│    │   ├─ If separate video + audio:                                    │
│    │   │   └─ fileProcessor.mergeVideoAudio()                           │
│    │   │       └─ Use FFmpeg to merge                                   │
│    │   │                                                                  │
│    │   └─ If video needs audio extraction:                              │
│    │       └─ fileProcessor.extractAudioFromVideo()                     │
│    │           └─ Extract audio track                                   │
│    │                                                                      │
│    ├─► moveToMediaStore(filePath, displayTitle)                         │
│    │   │                                                                  │
│    │   └─► mediaStoreHelper.moveToMediaStore()                          │
│    │       ├─ Create MediaStore entry                                   │
│    │       ├─ Copy file to Downloads collection                         │
│    │       ├─ Set metadata (title, date, size)                          │
│    │       └─ Return content:// URI                                     │
│    │                                                                      │
│    └─► Get final file size                                              │
│        └─ storageHelper.getFileSize()                                   │
│                                                                           │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│              10. DownloadResultHandler (Success/Failure)                 │
│                                                                           │
│  handleResult(result)                                                    │
│    │                                                                      │
│    ├─► If result.success = true:                                        │
│    │   │                                                                  │
│    │   ├─► handleSuccessfulDownload()                                   │
│    │   │   │                                                              │
│    │   │   ├─ downloadHistoryManager.addDownload()                      │
│    │   │   │   ├─ url, title, uploader                                  │
│    │   │   │   ├─ quality, filePath                                     │
│    │   │   │   ├─ fileSize, success=true                                │
│    │   │   │   └─ timestamp                                             │
│    │   │   │                                                              │
│    │   │   ├─ downloadResumptionManager.completeDownload()             │
│    │   │   │   └─ Mark as completed, remove from active list           │
│    │   │   │                                                              │
│    │   │   ├─ notificationHelper.showSuccessNotification()             │
│    │   │   │   ├─ Title: "Download Complete"                           │
│    │   │   │   ├─ Action: Open File                                     │
│    │   │   │   └─ Action: Share                                         │
│    │   │   │                                                              │
│    │   │   └─ Clean up temp files                                       │
│    │   │                                                                  │
│    │   └─ stopForeground() → Dismiss notification (or persistent)      │
│    │                                                                      │
│    └─► If result.success = false:                                       │
│        │                                                                  │
│        └─► handleFailedDownload()                                       │
│            │                                                              │
│            ├─ Parse error with ErrorMessageParser                       │
│            │   └─ Convert technical errors to user-friendly            │
│            │                                                              │
│            ├─ downloadHistoryManager.addDownload()                      │
│            │   └─ Save with success=false, error message               │
│            │                                                              │
│            ├─ downloadResumptionManager.failDownload()                  │
│            │   └─ Mark as failed for potential retry                    │
│            │                                                              │
│            ├─ notificationHelper.showErrorNotification()                │
│            │   └─ Show user-friendly error                              │
│            │                                                              │
│            └─ Clean up partial files                                    │
│                                                                           │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    11. Service Cleanup                                   │
│                                                                           │
│  onDestroy()                                                             │
│    ├─► Cancel all coroutines                                            │
│    ├─► Cleanup Python resources                                         │
│    ├─► Release network monitor                                          │
│    └─► Stop foreground service                                          │
│                                                                           │
│  VideoDownloadActivity.finish()                                          │
│    └─► Activity closes                                                  │
│                                                                           │
└─────────────────────────────────────────────────────────────────────────┘

```

## Key Components Summary

### Main Classes
1. **VideoDownloadActivity** - Entry point, handles share intent and permissions
2. **VideoDownloadCoordinator** - Orchestrates the entire download workflow
3. **VideoDownloadDialogManager** - All UI dialogs (quality, warnings, errors)
4. **BackgroundDownloadService** - Foreground service for downloads
5. **DownloadPrerequisitesChecker** - Validates network and storage
6. **DownloadOrchestrator** - Executes download and post-processing
7. **PythonVideoDownloader** - Python/yt-dlp interface
8. **ProgressHandler** - Real-time progress updates
9. **DownloadResultHandler** - Success/failure handling

### Helper Classes
- **NotificationHelper** - Foreground notification management
- **FileProcessor** - Audio/video processing (merge, extract, remux)
- **MediaStoreHelper** - Android MediaStore operations
- **StorageHelper** - Storage space and directory management
- **AudioRemuxer** - Audio format conversion
- **VideoFormatSelector** - Device-compatible format selection
- **ErrorMessageParser** - User-friendly error messages
- **DownloadHistoryManager** - Persistent download history
- **DownloadResumptionManager** - Resume interrupted downloads
- **NetworkMonitor** - Network connectivity checks

## Flow Variations

### Audio Download Flow
```
VideoDownloadActivity 
  → VideoDownloadCoordinator.showQualitySelection() 
  → User selects "Audio Only (MP3/AAC)"
  → PythonVideoDownloader.downloadAudio()
  → DownloadOrchestrator.processAudioIfNeeded()
    → AudioRemuxer checks format
    → FileProcessor.processAudioFile() (if needed)
  → MediaStore (Music collection)
  → Success notification
```

### Failed Download Flow
```
Download fails during Python/yt-dlp execution
  → PythonVideoDownloader returns DownloadResult(success=false)
  → DownloadResultHandler.handleFailedDownload()
    → ErrorMessageParser converts error
    → Save to history with error
    → DownloadResumptionManager.failDownload()
    → Show error notification
    → Clean up partial files
```

### Already Downloaded Flow
```
VideoDownloadCoordinator.checkExistingDownloadAndProceed()
  → downloadHistoryManager.findSuccessfulDownload()
  → Found existing download
  → VideoDownloadDialogManager.showAlreadyDownloadedDialog()
    → User clicks "Open File"
      → openVideoFile() with FileProvider
      → Launch video player
    → User clicks "Download Again"
      → Continue to quality selection
```

## Threading Model

- **Main Thread**: UI interactions, dialogs, activity lifecycle
- **IO Dispatcher**: Network requests, file operations, Python calls
- **Service Scope**: Background download execution
- **Foreground Service**: Ensures download continues in background

## State Management

- **Active Downloads**: DownloadResumptionManager tracks in SharedPreferences
- **Download History**: DownloadHistoryManager persists all attempts
- **Progress Updates**: Real-time via NotificationHelper
- **Resumption Data**: Download metadata saved for crash recovery


