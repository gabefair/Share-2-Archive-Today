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

# Keep Jackson classes required by youtubedl-android
-keep class com.fasterxml.jackson.** { *; }
-keep class com.fasterxml.jackson.databind.** { *; }
-keep class com.fasterxml.jackson.core.** { *; }
-keep class com.fasterxml.jackson.annotation.** { *; }

# Keep youtubedl-android classes
-keep class com.yausername.youtubedl_android.** { *; }
-keep class com.yausername.youtubedl_common.** { *; }

# Keep Commons IO and Commons Compress classes
-keep class org.apache.commons.io.** { *; }
-keep class org.apache.commons.compress.** { *; }

# Additional Jackson-specific rules to prevent issues
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.JsonProperty *;
    @com.fasterxml.jackson.annotation.JsonIgnore *;
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties *;
}

# Keep Jackson module classes
-keep class com.fasterxml.jackson.module.** { *; }

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile