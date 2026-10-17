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

# MediaPipe (the face model) calls back into Java by name from its native code, and its protobuf messages are read by
# reflection. Shrinking or renaming either one makes the model fail to load - in a release build only, which is exactly
# where nobody looks. CI starts a minified build on an emulator to keep this honest.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
# Keeping every method also keeps two in MediaPipe's framework that mention generated proto classes the library does not
# ship (the graph profiler and graph templates, which a face landmarker never touches). R8 rejects a keep that points at a
# missing class unless told it is expected.
-dontwarn com.google.mediapipe.proto.**

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
