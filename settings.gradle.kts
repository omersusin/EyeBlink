pluginManagement {
    repositories {
        // Google Maven only hosts the Android/AndroidX/MediaPipe groups; restricting it keeps
        // every other lookup on Maven Central.
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

include(":app")
include(":lsposed")

// Renders the real Compose UI headlessly: screenshots and the README animation.
// `./gradlew :tools:designlab:run`
include(":tools:designlab")
