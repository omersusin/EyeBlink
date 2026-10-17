# R8 rules for EyeBlink.
#
# AndroidX and Compose ship their own consumer rules, so almost nothing needs
# keeping. These are the app-specific cases only.

# Manifest-declared components are kept automatically by AGP, but be explicit:
# the services are only ever referenced from AndroidManifest.xml.
-keep class com.eyeblink.app.MainActivity { *; }
-keep class com.eyeblink.app.core.BlinkOverlayService { *; }
-keep class com.eyeblink.app.accessibility.EyeBlinkAccessibilityService { *; }

# Blinks are driven by a Config provider lambda and a BlinkAnimator whose
# progress is read from Compose. Keep the animation surface intact.
-keep class com.eyeblink.app.animation.BlinkAnimator { *; }
-keepclassmembers class com.eyeblink.app.animation.BlinkAnimator {
    public <init>(kotlin.jvm.functions.Function0);
}

# DataStore preferences are read by key, not reflection, so no keep is needed.
# BlinkConfig is a plain data class used across the overlay/UI boundary.
-keep class com.eyeblink.app.core.BlinkConfig { *; }

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