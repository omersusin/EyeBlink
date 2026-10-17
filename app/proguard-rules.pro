# R8 rules for EyeBlink.
#
# AndroidX, Compose, kotlinx.serialization and MediaPipe ship their own consumer rules, so almost
# nothing needs keeping. These are the app-specific cases only.

# Manifest-declared components are kept automatically by AGP; they are only ever referenced from
# AndroidManifest.xml, so be explicit about the services Android binds by name.
-keep class com.eyeblink.app.host.MainActivity { *; }
-keep class com.eyeblink.app.host.EyeBlinkApplication { *; }
-keep class com.eyeblink.app.access.EyeBlinkAccessibilityService { *; }
-keep class com.eyeblink.app.access.EyeBlinkListenerService { *; }
-keep class com.eyeblink.app.system.** { *; }

# Shizuku: its simple shell entry point is private since 13.1, and is reached by reflection in
# ShizukuBridge. Without this R8 would strip or rename it and Shizuku mode would silently die.
-keepclassmembers class rikka.shizuku.Shizuku {
    private static *** newProcess(java.lang.String[], java.lang.String[], java.lang.String);
}
-keep class rikka.shizuku.ShizukuRemoteProcess { *; }

# Keep source file + line numbers for readable production stack traces, but
# hide the original source file name from the class file.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin coroutines internals referenced reflectively by the debug agent.
-dontwarn kotlinx.coroutines.debug.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# Strip verbose/debug logging output from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
