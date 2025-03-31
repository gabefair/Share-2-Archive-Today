##---------------Begin: App Specific Rules ----------
# Keep only essential parts of MainActivity
-keep class org.gnosco.share2archivetoday.MainActivity {
    void onCreate(android.os.Bundle);
    void onNewIntent(android.content.Intent);
}

# Only keep necessary methods in ClearUrlsRulesManager
-keep class org.gnosco.share2archivetoday.ClearUrlsRulesManager {
    public <init>(...);
    public boolean areRulesLoaded();
    public java.lang.String clearUrl(java.lang.String);
}

##---------------Begin: Stripping Logs ----------
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

##---------------Begin: ZXing Library Rules ----------
# Keep only necessary ZXing classes
-keep class com.google.zxing.BarcodeFormat { *; }
-keep class com.google.zxing.DecodeHintType { *; }
-keep class com.google.zxing.MultiFormatReader { *; }
-keep class com.google.zxing.Result { *; }
-keep class com.google.zxing.BinaryBitmap { *; }
-keep class com.google.zxing.RGBLuminanceSource { *; }
-keep class com.google.zxing.common.HybridBinarizer { *; }
-keep class com.google.zxing.NotFoundException { *; }

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

# Keep necessary Android components
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Keep legacy extension methods
-keepclassmembers class org.gnosco.share2archivetoday.* {
    public static * legacy*(...);
}

# Keep the application's entry points
-keepattributes *Annotation*

# Remove unused code, resources, attributes in XMLs
-keepattributes SourceFile,LineNumberTable