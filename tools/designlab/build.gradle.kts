plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    application
}

// The design lab renders EyeBlink's real Compose UI on the desktop JVM with Skia, headlessly.
// It compiles the *same source files* as the Android app (app/src/main/java/.../ui), so a
// screenshot here is the actual UI code, not a mock-up. Only Maven Central artifacts are used.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll("-Xjvm-default=all", "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi")
    }
    sourceSets.named("main") {
        // The platform-free half of the app's UI: design kit, screens, state.
        kotlin.srcDir("../../app/src/main/java/com/eyeblink/app/ui")
    }
}

val cmp = "1.5.12"

// JetBrains' non-Android builds still declare a dependency on Google's androidx.annotation,
// which lives only on Google Maven. Those are source-retention style annotations that the
// desktop artifacts already carry their own copy of, so they are excluded here.
configurations.configureEach {
    exclude(group = "androidx.annotation")
}

dependencies {
    implementation(project(":core"))
    implementation("org.jetbrains.compose.runtime:runtime-desktop:$cmp")
    implementation("org.jetbrains.compose.ui:ui-desktop:$cmp")
    implementation("org.jetbrains.compose.ui:ui-graphics-desktop:$cmp")
    implementation("org.jetbrains.compose.ui:ui-text-desktop:$cmp")
    implementation("org.jetbrains.compose.ui:ui-unit-desktop:$cmp")
    implementation("org.jetbrains.compose.ui:ui-geometry-desktop:$cmp")
    implementation("org.jetbrains.compose.ui:ui-util-desktop:$cmp")
    implementation("org.jetbrains.compose.foundation:foundation-desktop:$cmp")
    implementation("org.jetbrains.compose.foundation:foundation-layout-desktop:$cmp")
    implementation("org.jetbrains.compose.animation:animation-desktop:$cmp")
    implementation("org.jetbrains.compose.animation:animation-core-desktop:$cmp")
    implementation("org.jetbrains.skiko:skiko-awt-runtime-linux-x64:0.7.85.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
}

application {
    mainClass.set("eyeblink.designlab.MainKt")
    applicationDefaultJvmArgs = listOf("-Djava.awt.headless=true", "-Dskiko.renderApi=SOFTWARE")
}
