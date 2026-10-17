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
        versionCode = 1
        versionName = "0.1.0"

        resourceConfigurations += listOf("en")
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui:1.7.5")
    implementation("androidx.compose.ui:ui-graphics:1.7.5")
    implementation("androidx.compose.foundation:foundation:1.7.5")
    implementation("androidx.compose.animation:animation:1.7.5")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

// Reproducible-ish release builds: no debug info in the shipped APK.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}