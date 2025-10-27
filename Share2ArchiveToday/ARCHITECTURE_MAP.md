# Architecture Map - Video Download System

## Component Interaction Diagram

```
┌──────────────────────────────────────────────────────────────────────┐
│                          PRESENTATION LAYER                           │
├──────────────────────────────────────────────────────────────────────┤
│                                                                        │
│  ┌─────────────────────┐          ┌──────────────────────────┐      │
│  │VideoDownloadActivity│─────────►│VideoDownloadDialogManager│      │
│  │  (Entry Point)      │          │  (UI Dialogs)            │      │
│  └──────────┬──────────┘          └──────────────────────────┘      │
│             │                                                          │
│             │ delegates to                                            │
│             ▼                                                          │
│  ┌──────────────────────┐                                            │
│  │VideoDownloadCoordinator│◄────────── orchestrates workflow         │
│  │  (Workflow Control)   │                                            │
│  └───────────┬───────────┘                                            │
│              │                                                          │
└──────────────┼──────────────────────────────────────────────────────┘
               │ starts service
               ▼
┌──────────────────────────────────────────────────────────────────────┐
│                          SERVICE LAYER                                │
├──────────────────────────────────────────────────────────────────────┤
│                                                                        │
│  ┌──────────────────────────┐                                        │
│  │BackgroundDownloadService │                                        │
│  │  (Foreground Service)    │                                        │
│  └───────────┬──────────────┘                                        │
│              │                                                          │
│              │ delegates to                                            │
│              ▼                                                          │
│  ┌───────────────────────────────────────────────────────────┐      │
│  │                    COORDINATORS                            │      │
│  │  ┌─────────────────────┐  ┌────────────────────────────┐ │      │
│  │  │DownloadOrchestrator │  │DownloadPrerequisitesChecker│ │      │
│  │  │  (Main Logic)       │  │  (Validation)              │ │      │
│  │  └──────────┬──────────┘  └─────────────┬──────────────┘ │      │
│  │             │                             │                 │      │
│  │  ┌──────────┴────────┐       ┌──────────┴────────┐        │      │
│  │  │DownloadResultHandler│     │ProgressHandler    │        │      │
│  │  │  (Success/Fail)    │      │  (Updates)        │        │      │
│  │  └────────────────────┘      └───────────────────┘        │      │
│  └───────────────────────────────────────────────────────────┘      │
│                                                                        │
└──────────────────────────────────────────────────────────────────────┘
               │ uses
               ▼
┌──────────────────────────────────────────────────────────────────────┐
│                          BUSINESS LAYER                               │
├──────────────────────────────────────────────────────────────────────┤
│                                                                        │
│  ┌───────────────────────────────────────────────────────────┐      │
│  │                  CORE DOWNLOADERS                          │      │
│  │  ┌──────────────────────┐                                 │      │
│  │  │PythonVideoDownloader │◄────── Python/Chaquopy         │      │
│  │  │  ├─ getVideoInfo()   │                                 │      │
│  │  │  ├─ downloadVideo()  │        ┌────────────────┐      │      │
│  │  │  └─ downloadAudio()  │───────►│yt-dlp (Python) │      │      │
│  │  └──────────────────────┘        └────────────────┘      │      │
│  └───────────────────────────────────────────────────────────┘      │
│                                                                        │
│  ┌───────────────────────────────────────────────────────────┐      │
│  │                  FORMAT & SELECTION                        │      │
│  │  ┌────────────────────┐     ┌────────────────────┐       │      │
│  │  │VideoFormatSelector │     │ErrorMessageParser  │       │      │
│  │  │  (Choose Quality)  │     │  (User Messages)   │       │      │
│  │  └────────────────────┘     └────────────────────┘       │      │
│  └───────────────────────────────────────────────────────────┘      │
│                                                                        │
└──────────────────────────────────────────────────────────────────────┘
               │ uses
               ▼
┌──────────────────────────────────────────────────────────────────────┐
│                          UTILITY LAYER                                │
├──────────────────────────────────────────────────────────────────────┤
│                                                                        │
│  ┌─────────────────────┐  ┌─────────────────────┐                   │
│  │  File Operations    │  │  Media Operations   │                   │
│  │  ┌───────────────┐  │  │  ┌───────────────┐  │                   │
│  │  │FileProcessor  │  │  │  │MediaStoreHelper│ │                   │
│  │  │  ├─ merge     │  │  │  │  ├─ save      │  │                   │
│  │  │  ├─ extract   │  │  │  │  └─ get URI   │  │                   │
│  │  │  └─ remux     │  │  │  └───────────────┘  │                   │
│  │  └───────────────┘  │  │  ┌───────────────┐  │                   │
│  │  ┌───────────────┐  │  │  │AudioRemuxer   │  │                   │
│  │  │FileUtils      │  │  │  │  (Format Fix) │  │                   │
│  │  │  (Helpers)    │  │  │  └───────────────┘  │                   │
│  │  └───────────────┘  │  └─────────────────────┘                   │
│  └─────────────────────┘                                              │
│                                                                        │
│  ┌─────────────────────┐  ┌─────────────────────┐                   │
│  │  Storage            │  │  Network            │                   │
│  │  ┌───────────────┐  │  │  ┌───────────────┐  │                   │
│  │  │StorageHelper  │  │  │  │NetworkMonitor │  │                   │
│  │  │  ├─ space     │  │  │  │  ├─ connected │  │                   │
│  │  │  └─ directory │  │  │  │  └─ data warn │  │                   │
│  │  └───────────────┘  │  │  └───────────────┘  │                   │
│  └─────────────────────┘  └─────────────────────┘                   │
│                                                                        │
│  ┌─────────────────────┐  ┌─────────────────────┐                   │
│  │  Notifications      │  │  URL Processing     │                   │
│  │  ┌───────────────┐  │  │  ┌───────────────┐  │                   │
│  │  │NotificationHelper││ │  │UrlExtractor   │  │                   │
│  │  │  ├─ progress  │  │  │  │  (Parse URL)  │  │                   │
│  │  │  ├─ success   │  │  │  └───────────────┘  │                   │
│  │  │  └─ error     │  │  │  ┌───────────────┐  │                   │
│  │  └───────────────┘  │  │  │QRCodeScanner  │  │                   │
│  └─────────────────────┘  │  │  (Image URL)  │  │                   │
│                            │  └───────────────┘  │                   │
│                            └─────────────────────┘                   │
│                                                                        │
└──────────────────────────────────────────────────────────────────────┘
               │ persists to
               ▼
┌──────────────────────────────────────────────────────────────────────┐
│                          DATA LAYER                                   │
├──────────────────────────────────────────────────────────────────────┤
│                                                                        │
│  ┌────────────────────────┐  ┌─────────────────────────────┐        │
│  │DownloadHistoryManager  │  │DownloadResumptionManager    │        │
│  │  ├─ addDownload()      │  │  ├─ startDownload()         │        │
│  │  ├─ getHistory()       │  │  ├─ updateProgress()        │        │
│  │  └─ findSuccessful()   │  │  ├─ completeDownload()      │        │
│  └─────────┬──────────────┘  │  └─ failDownload()          │        │
│            │                  └──────────┬──────────────────┘        │
│            │                             │                            │
│            └─────────────┬───────────────┘                            │
│                          ▼                                             │
│                ┌──────────────────┐                                   │
│                │ SharedPreferences │                                  │
│                │  (Persistent)    │                                   │
│                └──────────────────┘                                   │
│                                                                        │
└──────────────────────────────────────────────────────────────────────┘
```

## Quick Class Directory

### 📱 Activities & UI
- `VideoDownloadActivity` - Main entry point
- `DownloadHistoryActivity` - View past downloads
- `VideoDownloadDialogManager` - All dialogs
- `DownloadHistoryDialogManager` - History dialogs
- `DownloadHistoryUIManager` - History UI

### ⚙️ Services
- `BackgroundDownloadService` - Foreground download service

### 🎯 Coordinators (Business Logic)
- `VideoDownloadCoordinator` - Download workflow
- `DownloadOrchestrator` - Execution logic
- `DownloadPrerequisitesChecker` - Validation
- `DownloadResultHandler` - Success/failure
- `ProgressHandler` - Progress updates

### 📥 Download Core
- `PythonVideoDownloader` - Python/yt-dlp interface
- `VideoFormatSelector` - Quality selection
- `ErrorMessageParser` - User-friendly errors

### 🗂️ File Operations
- `FileProcessor` - Merge/extract/convert
- `FileUtils` - General file helpers
- `AudioRemuxer` - Audio format fixing
- `MediaStoreHelper` - Android MediaStore
- `StorageHelper` - Storage management

### 🌐 Network
- `NetworkMonitor` - Connectivity checks
- `WebURLMatcher` - URL validation
- `UrlExtractor` - URL parsing
- `QRCodeScanner` - Extract URLs from images
- `ClearUrlsRulesManager` - Remove tracking

### 🔔 Notifications
- `NotificationHelper` - All notifications

### 💾 Data Persistence
- `DownloadHistoryManager` - Download history
- `DownloadResumptionManager` - Resume/retry
- `DownloadHistoryDataManager` - History operations

### 📂 History UI
- `DownloadHistoryFileManager` - File operations
- `DownloadHistoryDialogManager` - History dialogs
- `DownloadsFolderOpener` - Open downloads folder

### 🔐 Permissions & Memory
- `PermissionManager` - Android permissions
- `MemoryManager` - Memory monitoring

## Data Flow

```
URL Input
   │
   ├─► Validation ─────────► NetworkMonitor
   │                          StorageHelper
   │
   ├─► Format Info ────────► PythonVideoDownloader ──► yt-dlp
   │
   ├─► User Selection ─────► VideoFormatSelector
   │
   ├─► Download ───────────► PythonVideoDownloader ──► yt-dlp
   │                                   │
   │                          Progress │
   │                                   ▼
   ├─► Progress Updates ───► ProgressHandler ──► NotificationHelper
   │
   ├─► Post-Process ───────► FileProcessor
   │                          AudioRemuxer
   │
   ├─► Store ──────────────► MediaStoreHelper
   │
   └─► Save Metadata ──────► DownloadHistoryManager
                              DownloadResumptionManager
```

## Dependency Hierarchy

```
Level 1: Data & Utilities (No dependencies on other app code)
  └─ SharedPreferences, File I/O, Network, MediaStore APIs

Level 2: Managers & Helpers (Depend on Level 1)
  └─ DownloadHistoryManager
  └─ StorageHelper
  └─ NetworkMonitor
  └─ NotificationHelper

Level 3: Business Logic (Depend on Level 1 & 2)
  └─ PythonVideoDownloader
  └─ FileProcessor
  └─ VideoFormatSelector

Level 4: Coordinators (Depend on Level 1, 2 & 3)
  └─ DownloadOrchestrator
  └─ DownloadPrerequisitesChecker
  └─ DownloadResultHandler
  └─ ProgressHandler

Level 5: Service (Depends on all lower levels)
  └─ BackgroundDownloadService

Level 6: Coordinators (Depends on Service & lower levels)
  └─ VideoDownloadCoordinator
  └─ VideoDownloadDialogManager

Level 7: Activities (Top level)
  └─ VideoDownloadActivity
  └─ DownloadHistoryActivity
```

## Threading Model

```
┌────────────────────────────────────────────────┐
│ Main Thread (UI)                               │
│  ├─ Activity lifecycle                         │
│  ├─ Dialog interactions                        │
│  └─ Notification creation                      │
└────────────────────────────────────────────────┘

┌────────────────────────────────────────────────┐
│ IO Dispatcher (Background)                     │
│  ├─ Network requests                           │
│  ├─ File operations                            │
│  ├─ Python/yt-dlp calls                        │
│  └─ MediaStore operations                      │
└────────────────────────────────────────────────┘

┌────────────────────────────────────────────────┐
│ Service Scope (Isolated)                       │
│  ├─ Download execution                         │
│  ├─ Progress monitoring                        │
│  └─ Post-processing                            │
└────────────────────────────────────────────────┘
```

## Key Design Patterns

### 1. **Coordinator Pattern**
- `VideoDownloadCoordinator` coordinates activity interactions
- `DownloadOrchestrator` coordinates service operations
- Separates UI from business logic

### 2. **Service Locator**
- Activity/Service creates all managers
- Passes them to coordinators
- No global singletons

### 3. **Strategy Pattern**
- `VideoFormatSelector` chooses download strategy
- `ErrorMessageParser` selects message strategy
- Different behaviors for same interface

### 4. **Observer Pattern**
- Progress callbacks from Python
- Notification updates
- State changes propagate up

### 5. **Factory Pattern**
- `NotificationHelper.createNotification()`
- Different notification types
- Centralized creation

## Communication Paths

```
Activity ←→ Coordinator ←→ Service
   │            │             │
   │            │             ├─► Orchestrator ─► Downloader
   │            │             ├─► Checker ──────► NetworkMonitor
   │            │             ├─► Handler ──────► HistoryManager
   │            │             └─► ProgressHandler → NotificationHelper
   │            │
   │            └─► DialogManager (shows UI)
   │
   └─► User Input
```


