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

# Don't warn about ZXing internals
-dontwarn com.google.zxing.**

##---------------Begin: ML Kit (Optional) ----------
# Keep ML Kit classes if they exist (optional dependency)
-keep class com.google.mlkit.vision.barcode.** { *; }
-keep class com.google.mlkit.vision.common.** { *; }
-keep class com.google.android.gms.tasks.** { *; }
-keep class com.google.android.gms.common.** { *; }

# Keep ML Kit interfaces and callbacks
-keepclassmembers class * {
    @com.google.android.gms.common.annotation.KeepName *;
}

# Don't warn about missing ML Kit classes (it's optional)
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

##---------------Begin: JSON Parsing ----------
# If using Gson or other JSON libraries, keep model classes
# Keep fields for JSON serialization/deserialization
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep generic signatures for JSON parsing
-keepattributes Signature
-keepattributes *Annotation*

##---------------Begin: Debugging ----------
# Keep annotations for debugging
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations
-keepattributes AnnotationDefault

# Keep exception information
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

##---------------Begin: R8 Full Mode Compatibility ----------
# Ensure compatibility with R8 full mode
-allowaccessmodification
-repackageclasses ''