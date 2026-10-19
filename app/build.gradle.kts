// NOTE: `java` alone resolves to Gradle's JavaPluginExtension, shadowing the
// `java.util` package, so Properties must be imported rather than fully qualified.
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Signing credentials come from keystore.properties (local, git-ignored) or from
// environment variables (CI). Never commit real values.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

fun secret(key: String, env: String): String? =
    (keystoreProps.getProperty(key) ?: System.getenv(env))?.takeIf { it.isNotBlank() }

android {
    namespace = "com.eyeblink.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.eyeblink.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.3.1"

        // The two languages EyeBlink is written in; libraries ship dozens more that would only add weight.
        resourceConfigurations += listOf("en", "tr")
    }

    signingConfigs {
        create("release") {
            // Play requires v2+; v3 adds key rotation support and v4 produces a
            // detached .idsig for incremental delivery. These are properties of
            // the SigningConfig, not of the build type. v1 stays off (minSdk 26).
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true

            val store = secret("storeFile", "KEYSTORE_FILE")
            store?.let {
                storeFile = rootProject.file(it)
                storePassword = secret("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = secret("keyAlias", "KEY_ALIAS")
                keyPassword = secret("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDefault = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Only sign when credentials are actually present, so a fork or a
            // secretless CI run still produces an unsigned release.
            signingConfig = signingConfigs.findByName("release")
                ?.takeIf { it.storeFile?.exists() == true }
        }
        // The release build as R8 leaves it, but signed with the debug key so an emulator can install it. Shrinking, renaming
        // and inlining are where reflection, native callbacks and stack inspection break, and a debug build can never show
        // that. It is deliberately NOT debuggable: R8 optimises a debuggable build less (it does not inline), and the first
        // version of this build type therefore passed CI while the real release failed on a phone. Nothing is published from
        // this one.
        create("minified") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".minified"
            versionNameSuffix = "-minified"
            matchingFallbacks += "release"
        }
    }

    buildFeatures { compose = true }
    // No composeOptions.kotlinCompilerExtensionVersion here: the Kotlin 2.x
    // Compose plugin derives a matching compiler itself, and pinning it
    // manually causes a version-mismatch failure.

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/*.version",
            "DebugProbesKt.bin",
            "kotlin-tooling-metadata.json",
        )
    }

    // MediaPipe ships libmediapipe_tasks_jni.so at ~11 MB for every ABI, which
    // pushed the universal APK to 50 MB. Split per ABI so a device only ever
    // receives the slice it can run. x86_64 is kept so emulators still work.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        // A signature-only permission must never be requested; keep it fatal.
        abortOnError = true
        warningsAsErrors = false
    }
}

dependencies {
    // Scheduling, the playback safety protocol, parsers and calibration math: pure Kotlin, tested on the JVM.
    implementation(project(":core"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui:1.7.5")
    implementation("androidx.compose.ui:ui-graphics:1.7.5")
    implementation("androidx.compose.foundation:foundation:1.7.5")
    implementation("androidx.compose.animation:animation:1.7.5")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Camera: pinned to 1.5.x deliberately. camera-core 1.6.x declares
    // minCompileSdk=36 and minAndroidGradlePluginVersion=8.9.1, which this
    // project does not satisfy; 1.5.3 declares minCompileSdk=35 / AGP 8.6.0.
    implementation("androidx.camera:camera-core:1.5.3")
    implementation("androidx.camera:camera-camera2:1.5.3")
    implementation("androidx.camera:camera-lifecycle:1.5.3")

    // On-device face landmarks. Runs locally: no network, no Play Services.
    implementation("com.google.mediapipe:tasks-vision:0.10.35")

    // Optional Shizuku mode. Both are on Maven Central.
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}

// Reproducible-ish release builds: no debug info in the shipped APK.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}