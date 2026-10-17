// Plugin versions live in settings.gradle.kts (pluginManagement), so a module only
// resolves the plugins it actually applies. That is what lets the JVM-only modules
// configure without the Android Gradle Plugin being reachable.

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
