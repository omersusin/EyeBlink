pluginManagement {
    repositories {
        // Google Maven only hosts the Android/AndroidX/MediaPipe groups. Restricting it
        // keeps every other lookup on Maven Central, which also lets the pure-JVM
        // modules build on a machine that has no route to Google's repository.
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "8.7.3"
        id("com.android.library") version "8.7.3"
        id("org.jetbrains.kotlin.android") version "2.0.21"
        id("org.jetbrains.kotlin.jvm") version "2.0.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "EyeBlink"

// Platform-independent brain: scheduling, safety protocol, parsers, calibration math.
// Pure Kotlin/JVM, so it is built and unit tested without an Android SDK.
include(":core")

// Compile-only stubs of the legacy Xposed API (never packaged).
include(":xposed-api")

// -Peyeblink.jvmOnly=true builds only the JVM modules (core + design lab). This is what
// you want on a machine without the Android SDK; CI and Android Studio build everything.
val jvmOnly = providers.gradleProperty("eyeblink.jvmOnly").orNull == "true"

if (!jvmOnly) {
    include(":app")
    include(":lsposed")
}

// The design lab renders the real Compose UI headlessly (screenshots, README animation).
if (jvmOnly || providers.gradleProperty("eyeblink.designlab").orNull == "true") {
    include(":tools:designlab")
}
