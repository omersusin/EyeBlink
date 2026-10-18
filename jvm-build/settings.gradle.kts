// A second, minimal Gradle root for machines WITHOUT the Android SDK or access to Google's
// Maven repository. It builds only the pure JVM modules from the same sources:
//
//   ./gradlew -p jvm-build :core:test
//   ./gradlew -p jvm-build :tools:designlab:run
//
// The Android modules (:app, :lsposed) are built from the repository root.
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.0.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { mavenCentral() }
}

rootProject.name = "EyeBlink-jvm"

include(":core", ":xposed-api", ":tools", ":tools:designlab")
project(":core").projectDir = file("../core")
project(":xposed-api").projectDir = file("../xposed-api")
project(":tools").projectDir = file("../tools")
project(":tools:designlab").projectDir = file("../tools/designlab")
