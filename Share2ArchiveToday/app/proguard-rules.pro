# ========================================
# Share2ArchiveToday ProGuard Rules
# ========================================

##---------------Begin: Log Stripping ----------
# Strip all log calls in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

##---------------Begin: General Optimization Rules ----------
# Remove all debugging info from all classes
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers
-dontpreverify
-verbose
-dump class_files.txt
-printseeds seeds.txt
-printusage unused.txt
-printmapping mapping.txt
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*



##---------------Begin: Android Core ----------
# Keep all Android framework classes
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.view.View

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep custom View constructors
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep onClick methods referenced in XML
-keepclassmembers class * extends android.content.Context {
    public void *(android.view.View);
    public void *(android.view.MenuItem);
}

# Keep Parcelable implementations
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep Serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

##---------------Begin: ViewBinding ----------
# Keep ViewBinding classes
-keep class * implements androidx.viewbinding.ViewBinding {
    public static *** bind(android.view.View);
    public static *** inflate(android.view.LayoutInflater);
    public static *** inflate(android.view.LayoutInflater, android.view.ViewGroup, boolean);
}

# Keep all generated ViewBinding classes for this app
-keep class org.gnosco.share2archivetoday.databinding.** { *; }

# Keep ML Kit classes if they exist (for reflection)
-keep class com.google.mlkit.vision.barcode.BarcodeScanning { *; }
-keep class com.google.mlkit.vision.barcode.BarcodeScannerOptions { *; }
-keep class com.google.mlkit.vision.barcode.BarcodeScanner { *; }
-keep class com.google.mlkit.vision.barcode.common.Barcode { *; }
-keep class com.google.mlkit.vision.common.InputImage { *; }
-keep class com.google.android.gms.tasks.Tasks { *; }
-keep class com.google.android.gms.tasks.Task { *; }

##---------------Begin: App-Specific Classes ----------
# Keep main activity and its internal methods (some are tested)
-keep class org.gnosco.share2archivetoday.MainActivity {
    public <init>();
    *** onCreate(android.os.Bundle);
    *** onNewIntent(android.content.Intent);
    *** handleURL(java.lang.String);
    *** extractUrl(java.lang.String);
    *** processArchiveUrl(java.lang.String);
    *** applyPlatformSpecificOptimizations(java.lang.String);
    *** cleanTrackingParamsFromUrl(java.lang.String);
    *** cleanUrl(java.lang.String);
    *** removeAnchorsAndTextFragments(java.lang.String);
    *** threeSteps(java.lang.String);
    *** openInBrowser(java.lang.String);
    *** handleImageShare(android.net.Uri);
}

# Keep ClipboardActivity
-keep class org.gnosco.share2archivetoday.ClipboardActivity {
    public <init>();
}

# Keep URL processing classes and their public methods
-keep class org.gnosco.share2archivetoday.UrlExtractor {
    public <init>();
    public *** extractUrl(...);
}

-keep class org.gnosco.share2archivetoday.UrlCleaner {
    public <init>();
    public *** cleanUrl(...);
    public *** removeAnchorsAndTextFragments(...);
}

-keep class org.gnosco.share2archivetoday.UrlOptimizer {
    public <init>();
    public *** applyPlatformSpecificOptimizations(...);
    public *** cleanTrackingParamsFromUrl(...);
}

-keep class org.gnosco.share2archivetoday.ArchiveUrlProcessor {
    public <init>();
    public *** processArchiveUrl(...);
}

-keep class org.gnosco.share2archivetoday.WebURLMatcher {
    public <init>();
    public *** *(...);
}

# Keep QR Code Scanner
-keep class org.gnosco.share2archivetoday.QRCodeScanner {
    public <init>(...);
    public *** extractQRCodeFromImage(...);
}

##---------------Begin: ClearURLs Components ----------
# Keep ClearURLs rules manager and its methods
-keep class org.gnosco.share2archivetoday.ClearUrlsRulesManager {
    public <init>(...);
    public *** clearUrl(...);
    public *** areRulesLoaded();
    public *** isValidUrlMissingProtocol(...);
}

# Keep ClearURLs provider classes
-keep class org.gnosco.share2archivetoday.clearurls.ClearUrlsProvider {
    public <init>(...);
    public *** matchURL(...);
    public *** getRules(...);
    public *** getRawRules(...);
    public *** getRedirection(...);
    public *** isCanceling();
}

-keep class org.gnosco.share2archivetoday.clearurls.ClearUrlsRulesLoader {
    public <init>(...);
    public *** loadRules(...);
    public *** buildDomainProviderMap(...);
}

-keep class org.gnosco.share2archivetoday.clearurls.ClearUrlsUrlProcessor {
    public <init>();
    public *** removeFieldsFromURL(...);
    public *** isValidUrlMissingProtocol(...);
}

# Keep data classes that might be used for JSON parsing
-keepclassmembers class org.gnosco.share2archivetoday.clearurls.** {
    public <init>();
    public <fields>;
    public <methods>;
}

##---------------Begin: Kotlin ----------
# Keep Kotlin metadata for reflection
-keep class kotlin.Metadata { *; }

# Keep Kotlin coroutines (if used)
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Keep Kotlin companion objects
-keepclassmembers class * {
    public static ** Companion;
}

##---------------Begin: ZXing (Required) ----------
# Keep ZXing classes for QR code scanning
-keep class com.google.zxing.** { *; }
-keepclassmembers class com.google.zxing.** { *; }

# Specific ZXing classes that use reflection
-keep class com.google.zxing.client.result.** { *; }
-keep class com.google.zxing.common.** { *; }
-keep class com.google.zxing.qrcode.** { *; }

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

##---------------Begin: Chaquopy Rules ----------
# Keep Chaquopy runtime classes
-keep class com.chaquo.python.** { *; }
-keepclassmembers class com.chaquo.python.** { *; }
-keepclasseswithmembers class com.chaquo.python.** { *; }
-dontwarn com.chaquo.python.**

# Keep Python module references (important for reflection and dynamic loading)
-keepnames class ** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep ALL classes that interact with Python (VideoDownloader and PythonVideoDownloader)
-keep class org.gnosco.share2archivetoday.VideoDownloader { *; }
-keep class org.gnosco.share2archivetoday.VideoDownloader$* { *; }
-keep class org.gnosco.share2archivetoday.download.PythonVideoDownloader { *; }
-keep class org.gnosco.share2archivetoday.download.PythonVideoDownloader$* { *; }

# Keep MainActivity and its lifecycle methods (CRITICAL for app functionality)
-keep class org.gnosco.share2archivetoday.MainActivity { *; }
-keep class org.gnosco.share2archivetoday.MainActivity$* { *; }

# Specifically keep MainActivity methods that are critical for functionality
-keepclassmembers class org.gnosco.share2archivetoday.MainActivity {
    public void onCreate(android.os.Bundle);
    public void onNewIntent(android.content.Intent);
    public void handleShareIntent(android.content.Intent);
    public void threeSteps(java.lang.String);
    public void openInBrowser(java.lang.String);
    public void handleImageShare(android.net.Uri);
    public void finish();
    public java.lang.String extractUrl(java.lang.String);
    public java.lang.String handleURL(java.lang.String);
    public java.lang.String applyPlatformSpecificOptimizations(java.lang.String);
    public java.lang.String cleanTrackingParamsFromUrl(java.lang.String);
    public java.lang.String removeAnchorsAndTextFragments(java.lang.String);
    public java.lang.String cleanUrl(java.lang.String);
}

# Keep video download activity and service
-keep class org.gnosco.share2archivetoday.VideoDownloadActivity { *; }
-keep class org.gnosco.share2archivetoday.BackgroundDownloadService { *; }
-keep class org.gnosco.share2archivetoday.download.VideoDownloadActivity { *; }
-keep class org.gnosco.share2archivetoday.download.BackgroundDownloadService { *; }
-keep class org.gnosco.share2archivetoday.download.BackgroundDownloadService$* { *; }

# Keep download history and resumption activities
-keep class org.gnosco.share2archivetoday.DownloadHistoryActivity { *; }
-keep class org.gnosco.share2archivetoday.DownloadResumptionActivity { *; }

# Remove debug activities from release builds (specific methods only)
-assumenosideeffects class org.gnosco.share2archivetoday.FeatureFlagDebugActivity {
    public void onCreate(android.os.Bundle);
    public void onResume();
    public void onPause();
    public void onDestroy();
}
-assumenosideeffects class org.gnosco.share2archivetoday.debug.FeatureFlagDebugActivity {
    public void onCreate(android.os.Bundle);
    public void onResume();
    public void onPause();
    public void onDestroy();
}
-assumenosideeffects class org.gnosco.share2archivetoday.VideoDownloadTestActivity {
    public void onCreate(android.os.Bundle);
    public void onResume();
    public void onPause();
    public void onDestroy();
}
-assumenosideeffects class org.gnosco.share2archivetoday.debug.VideoDownloadTestActivity {
    public void onCreate(android.os.Bundle);
    public void onResume();
    public void onPause();
    public void onDestroy();
}
-assumenosideeffects class org.gnosco.share2archivetoday.DebugFeatureTester {
    public static void logTestResults(android.content.Context);
    public static void testFeatureFlags(android.content.Context);
}
-assumenosideeffects class org.gnosco.share2archivetoday.debug.DebugFeatureTester {
    public static void logTestResults(android.content.Context);
    public static void testFeatureFlags(android.content.Context);
}

# Prevent obfuscation of data classes used by Python - VideoDownloader
-keep class org.gnosco.share2archivetoday.VideoDownloader$VideoInfo { *; }
-keep class org.gnosco.share2archivetoday.VideoDownloader$DownloadResult { *; }
-keep class org.gnosco.share2archivetoday.VideoDownloader$BatchDownloadResult { *; }
-keep class org.gnosco.share2archivetoday.VideoDownloader$StorageInfo { *; }
-keep class org.gnosco.share2archivetoday.VideoDownloader$FileInfo { *; }
-keep class org.gnosco.share2archivetoday.VideoDownloader$CleanupResult { *; }

# Prevent obfuscation of data classes used by Python - PythonVideoDownloader
-keep class org.gnosco.share2archivetoday.PythonVideoDownloader$ProgressInfo { *; }
-keep class org.gnosco.share2archivetoday.PythonVideoDownloader$ProgressInfo$* { *; }
-keep class org.gnosco.share2archivetoday.PythonVideoDownloader$DownloadResult { *; }
-keep class org.gnosco.share2archivetoday.PythonVideoDownloader$VideoInfo { *; }
-keep class org.gnosco.share2archivetoday.download.PythonVideoDownloader$ProgressInfo { *; }
-keep class org.gnosco.share2archivetoday.download.PythonVideoDownloader$ProgressInfo$* { *; }
-keep class org.gnosco.share2archivetoday.download.PythonVideoDownloader$DownloadResult { *; }
-keep class org.gnosco.share2archivetoday.download.PythonVideoDownloader$VideoInfo { *; }

# Keep download managers and utilities
-keep class org.gnosco.share2archivetoday.DownloadHistoryManager { *; }
-keep class org.gnosco.share2archivetoday.DownloadResumptionManager { *; }
-keep class org.gnosco.share2archivetoday.NetworkMonitor { *; }
-keep class org.gnosco.share2archivetoday.PermissionManager { *; }
-keep class org.gnosco.share2archivetoday.download.DownloadHistoryManager { *; }
-keep class org.gnosco.share2archivetoday.download.DownloadHistoryManager$* { *; }
-keep class org.gnosco.share2archivetoday.download.DownloadResumptionManager { *; }
-keep class org.gnosco.share2archivetoday.download.DownloadResumptionManager$* { *; }
-keep class org.gnosco.share2archivetoday.download.VideoDownloadCoordinator { *; }
-keep class org.gnosco.share2archivetoday.download.VideoDownloadDialogManager { *; }
-keep class org.gnosco.share2archivetoday.download.DownloadOrchestrator { *; }
-keep class org.gnosco.share2archivetoday.download.DownloadPrerequisitesChecker { *; }
-keep class org.gnosco.share2archivetoday.download.DownloadResultHandler { *; }
-keep class org.gnosco.share2archivetoday.download.ProgressHandler { *; }
-keep class org.gnosco.share2archivetoday.network.NetworkMonitor { *; }
-keep class org.gnosco.share2archivetoday.utils.PermissionManager { *; }

# Keep audio processing classes
-keep class org.gnosco.share2archivetoday.AudioRemuxer { *; }
-keep class org.gnosco.share2archivetoday.AudioRemuxer$* { *; }
-keep class org.gnosco.share2archivetoday.media.AudioRemuxer { *; }
-keep class org.gnosco.share2archivetoday.media.AudioRemuxer$* { *; }

# Keep QR code scanner
-keep class org.gnosco.share2archivetoday.QRCodeScanner { *; }
-keep class org.gnosco.share2archivetoday.utils.QRCodeScanner { *; }

# Keep URL processing utilities used by MainActivity
-keep class org.gnosco.share2archivetoday.WebURLMatcher { *; }
-keep class org.gnosco.share2archivetoday.ClearUrlsRulesManager { *; }
-keep class org.gnosco.share2archivetoday.DebugLogger { *; }
-keep class org.gnosco.share2archivetoday.DebugFeatureTester { *; }
-keep class org.gnosco.share2archivetoday.QRCodeScanner { *; }
-keep class org.gnosco.share2archivetoday.network.WebURLMatcher { *; }
-keep class org.gnosco.share2archivetoday.network.ClearUrlsRulesManager { *; }
-keep class org.gnosco.share2archivetoday.debug.DebugLogger { *; }
-keep class org.gnosco.share2archivetoday.debug.DebugFeatureTester { *; }

# Keep memory manager
-keep class org.gnosco.share2archivetoday.MemoryManager { *; }
-keep class org.gnosco.share2archivetoday.MemoryManager$* { *; }

# Keep FFmpeg wrapper and media processing classes
-keep class org.gnosco.share2archivetoday.FFmpegWrapper { *; }
-keep class org.gnosco.share2archivetoday.FFmpegWrapper$* { *; }
-keep class org.gnosco.share2archivetoday.media.FFmpegWrapper { *; }
-keep class org.gnosco.share2archivetoday.media.FFmpegWrapper$* { *; }

# Keep advanced feature classes
-keep class org.gnosco.share2archivetoday.BrotliDecoder { *; }
-keep class org.gnosco.share2archivetoday.WebSocketClient { *; }
-keep class org.gnosco.share2archivetoday.AESHLSDecryptor { *; }
-keep class org.gnosco.share2archivetoday.network.BrotliDecoder { *; }
-keep class org.gnosco.share2archivetoday.network.WebSocketClient { *; }
-keep class org.gnosco.share2archivetoday.network.WebSocketClient$* { *; }
-keep class org.gnosco.share2archivetoday.crypto.AESHLSDecryptor { *; }
-keep class org.gnosco.share2archivetoday.crypto.AESHLSDecryptor$* { *; }
-keep class org.gnosco.share2archivetoday.crypto.AESCipherManager { *; }
-keep class org.gnosco.share2archivetoday.crypto.HLSKeyManager { *; }
-keep class org.gnosco.share2archivetoday.crypto.HLSManifestParser { *; }

# CRITICAL: Keep Python callback lambda interfaces
# Without this, Python callbacks to Kotlin will fail
-keep class kotlin.jvm.functions.Function1 { *; }
-keep class kotlin.jvm.functions.Function2 { *; }
-keep class kotlin.jvm.functions.Function0 { *; }
-keepclassmembers class * {
    public <methods>;
    kotlin.jvm.functions.Function1 *;
    kotlin.jvm.functions.Function2 *;
    kotlin.jvm.functions.Function0 *;
}

# Keep all Kotlin data classes that might be used by Python
-keep class * implements java.io.Serializable { *; }
-keep class * implements kotlin.jvm.internal.KObject { *; }

# Keep coroutines for async operations
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# OkHttp (used for network operations in yt-dlp and WebSocket)
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Bouncy Castle (crypto for AES-128 HLS)
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-keepclassmembers class org.bouncycastle.** { *; }

# Google Brotli (compression support)
-keep class com.google.brotli.** { *; }
-dontwarn com.google.brotli.**
-keepclassmembers class com.google.brotli.** { *; }

# JSON parsing (used for Python-Kotlin communication)
-keep class org.json.** { *; }
-keepclassmembers class org.json.** { *; }

# Keep serialization attributes for data classes
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

# Prevent stripping of methods that Python might call via reflection
-keepclassmembers class * {
    public <methods>;
    public <fields>;
}

# Additional safety rules for Python integration
-dontwarn java.lang.Class
-dontwarn java.lang.reflect.**
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# Keep all utility classes (refactored into subpackages)
-keep class org.gnosco.share2archivetoday.utils.** { *; }
-keep class org.gnosco.share2archivetoday.media.** { *; }
-keep class org.gnosco.share2archivetoday.network.** { *; }
-keep class org.gnosco.share2archivetoday.crypto.** { *; }
-keep class org.gnosco.share2archivetoday.download.** { *; }
-keep class org.gnosco.share2archivetoday.debug.** { *; }

# Keep specific utility classes
-keep class org.gnosco.share2archivetoday.utils.FileProcessor { *; }
-keep class org.gnosco.share2archivetoday.utils.FileUtils { *; }
-keep class org.gnosco.share2archivetoday.utils.StorageHelper { *; }
-keep class org.gnosco.share2archivetoday.utils.NotificationHelper { *; }
-keep class org.gnosco.share2archivetoday.utils.ErrorMessageParser { *; }
-keep class org.gnosco.share2archivetoday.utils.UrlExtractor { *; }

# Keep media processing classes
-keep class org.gnosco.share2archivetoday.media.MediaStoreHelper { *; }
-keep class org.gnosco.share2archivetoday.media.VideoFormatSelector { *; }
-keep class org.gnosco.share2archivetoday.media.VideoFormatSelector$* { *; }

# Keep download history UI classes
-keep class org.gnosco.share2archivetoday.download.DownloadHistoryUIManager { *; }
-keep class org.gnosco.share2archivetoday.download.DownloadHistoryFileManager { *; }
-keep class org.gnosco.share2archivetoday.download.DownloadHistoryDialogManager { *; }
-keep class org.gnosco.share2archivetoday.download.DownloadHistoryDataManager { *; }
-keep class org.gnosco.share2archivetoday.download.DownloadsFolderOpener { *; }
-keep class org.gnosco.share2archivetoday.download.DownloadHistoryItem { *; }

# Keep all classes in the main package (safer approach for Python integration)
-keep class org.gnosco.share2archivetoday.** { *; }

# CRITICAL: Keep Activity lifecycle methods to prevent crashes
-keepclassmembers class * extends android.app.Activity {
    public void onCreate(android.os.Bundle);
    public void onResume();
    public void onPause();
    public void onDestroy();
    public void onNewIntent(android.content.Intent);
    public void finish();
}

# Keep Intent handling methods
-keepclassmembers class * {
    public void handleShareIntent(android.content.Intent);
    public void openInBrowser(java.lang.String);
}

# CRITICAL: Never remove finish() method from Activities
-keepclassmembers class * extends android.app.Activity {
    public void finish();
}

##---------------End: Chaquopy Rules ----------