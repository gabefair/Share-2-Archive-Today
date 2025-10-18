# Release-specific ProGuard rules
# This file is only applied to release builds

# Strip all log calls in release builds for performance and security
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

# Strip System.out.println calls
-assumenosideeffects class java.io.PrintStream {
    public void println(%);
    public void println(**);
}

# Strip printStackTrace calls
-assumenosideeffects class java.lang.Exception {
    public void printStackTrace();
}

# Additional optimizations for release builds
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*,!code/allocation/variable
