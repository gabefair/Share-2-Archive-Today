# Strip all log calls
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

# Preserve the special static methods that are required in all enumeration classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep ML Kit classes if they exist (for reflection)
-keep class com.google.mlkit.vision.barcode.BarcodeScanning { *; }
-keep class com.google.mlkit.vision.barcode.BarcodeScannerOptions { *; }
-keep class com.google.mlkit.vision.barcode.BarcodeScanner { *; }
-keep class com.google.mlkit.vision.barcode.common.Barcode { *; }
-keep class com.google.mlkit.vision.common.InputImage { *; }
-keep class com.google.android.gms.tasks.Tasks { *; }
-keep class com.google.android.gms.tasks.Task { *; }

# Keep ZXing classes (fallback)
-keep class com.google.zxing.** { *; }

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
-keep class org.gnosco.share2archivetoday.PythonVideoDownloader { *; }
-keep class org.gnosco.share2archivetoday.PythonVideoDownloader$* { *; }

# Keep video download activity and service
-keep class org.gnosco.share2archivetoday.VideoDownloadActivity { *; }
-keep class org.gnosco.share2archivetoday.BackgroundDownloadService { *; }

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

# Keep download managers and utilities
-keep class org.gnosco.share2archivetoday.DownloadHistoryManager { *; }
-keep class org.gnosco.share2archivetoday.DownloadResumptionManager { *; }
-keep class org.gnosco.share2archivetoday.NetworkMonitor { *; }
-keep class org.gnosco.share2archivetoday.PermissionManager { *; }

# Keep FFmpeg wrapper and media processing classes
-keep class org.gnosco.share2archivetoday.FFmpegWrapper { *; }
-keep class org.gnosco.share2archivetoday.FFmpegWrapper$* { *; }

# Keep advanced feature classes
-keep class org.gnosco.share2archivetoday.BrotliDecoder { *; }
-keep class org.gnosco.share2archivetoday.WebSocketClient { *; }
-keep class org.gnosco.share2archivetoday.AESHLSDecryptor { *; }

# CRITICAL: Keep Python callback lambda interfaces
# Without this, Python callbacks to Kotlin will fail
-keep class kotlin.jvm.functions.Function1 { *; }
-keep class kotlin.jvm.functions.Function2 { *; }
-keepclassmembers class * {
    public <methods>;
    kotlin.jvm.functions.Function1 *;
}

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

##---------------End: Chaquopy Rules ----------