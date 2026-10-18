plugins {
    `java-library`
}

// Compile-time stubs of the *legacy* Xposed API (API 82), which every maintained
// framework (LSPosed, EdXposed, Dreamland) still honours. The real classes are
// provided by the framework inside the hooked process, so this module is consumed
// with compileOnly and is never packaged into an APK - shipping our own copies
// would shadow the framework's and break hooking.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
