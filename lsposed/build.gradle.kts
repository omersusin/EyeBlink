import java.util.Properties

plugins {
    id("com.android.application")
}

// The optional LSPosed module. It is a separate, tiny APK so that nobody who does not want a hooking
// framework anywhere near their video apps ever has to install it, and so the main app carries no
// Xposed metadata. Plain Java on purpose: it runs inside *other apps'* processes, where a Kotlin
// runtime would be one more thing that could clash.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

fun secret(key: String, env: String): String? =
    (keystoreProps.getProperty(key) ?: System.getenv(env))?.takeIf { it.isNotBlank() }

android {
    namespace = "com.eyeblink.lsposed"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.eyeblink.lsposed"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }

    signingConfigs {
        // The bridge permission is signature-level, so the module and the app must be signed with the
        // same key. Debug builds share the debug key automatically; release builds share this one.
        create("release") {
            enableV2Signing = true
            enableV3Signing = true
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
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")?.takeIf { it.storeFile?.exists() == true }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

dependencies {
    // Provided by the framework inside the hooked process; never packaged.
    compileOnly(project(":xposed-api"))
}
