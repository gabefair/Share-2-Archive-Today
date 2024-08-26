# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep the main activity
-keep class org.gnosco.share2archivetoday.MainActivity { *; }

# Keep all the Compose-related classes
-keep class androidx.compose.** { *; }
-keep class androidx.activity.ComponentActivity { *; }

# Keep logging (optional)
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}