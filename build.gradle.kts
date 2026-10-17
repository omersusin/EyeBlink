plugins {
    // AGP 8.5.2 is only tested up to compileSdk 34 and warns on compileSdk 35.
    // 8.7.x is the first line that officially supports 35, and it still pairs
    // with the Gradle 8.9 wrapper. Bumping past 8.7 would force a wrapper bump.
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
