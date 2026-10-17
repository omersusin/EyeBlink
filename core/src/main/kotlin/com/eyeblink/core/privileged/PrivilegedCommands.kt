package com.eyeblink.core.privileged

/** Media keys that map to explicit commands. Deliberately no play/pause toggle. */
enum class MediaKey(val arg: String, val keyCode: Int) {
    PLAY("play", 126),
    PAUSE("pause", 127),
}

/**
 * The catalogue of commands EyeBlink is willing to run with elevated rights.
 *
 * Everything is built here from validated pieces, never from free text, so the set of things
 * a privileged shell can be asked to do is small, reviewable, and listed in one file.
 * Builders return null when an argument fails validation rather than producing a command.
 */
object PrivilegedCommands {

    const val DUMP_MEDIA_SESSIONS = "dumpsys media_session"
    const val IDENTITY = "id"
    const val SDK_LEVEL = "getprop ro.build.version.sdk"
    const val DUMP_WINDOW_FOCUS = "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'"
    const val DUMP_RESUMED_ACTIVITY = "dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity'"
    const val ENABLED_ACCESSIBILITY = "settings get secure enabled_accessibility_services"

    /** Dispatch an explicit media key, trying the most targeted tool first. */
    fun dispatchMediaKey(key: MediaKey): List<String> = listOf(
        "cmd media_session dispatch ${key.arg}",
        "media dispatch ${key.arg}",
        "input keyevent ${key.keyCode}",
    )

    fun grantPermission(pkg: String, permission: String): String? =
        if (ShellQuote.isPackage(pkg) && ShellQuote.isPermission(permission)) {
            "pm grant ${ShellQuote.arg(pkg)} ${ShellQuote.arg(permission)}"
        } else null

    fun appOpsAllow(pkg: String, op: String): String? =
        if (ShellQuote.isPackage(pkg) && op.matches(Regex("^[A-Z][A-Z0-9_]*$"))) {
            "appops set ${ShellQuote.arg(pkg)} $op allow"
        } else null

    fun allowNotificationListener(component: String): String? =
        if (ShellQuote.isComponent(component)) "cmd notification allow_listener ${ShellQuote.arg(component)}" else null

    fun putSecure(key: String, value: String): String? =
        if (ShellQuote.isSettingKey(key)) "settings put secure $key ${ShellQuote.arg(value)}" else null

    fun getSecure(key: String): String? =
        if (ShellQuote.isSettingKey(key)) "settings get secure $key" else null

    fun putSystem(key: String, value: String): String? =
        if (ShellQuote.isSettingKey(key)) "settings put system $key ${ShellQuote.arg(value)}" else null

    fun deviceIdleWhitelist(pkg: String): String? =
        if (ShellQuote.isPackage(pkg)) "cmd deviceidle whitelist +${pkg}" else null
}

/** Merging into the colon separated `enabled_accessibility_services` list without clobbering it. */
object AccessibilityList {

    /** @param existing the current value, which may be blank, "null", or a colon separated list */
    fun withService(existing: String?, component: String): String {
        val items = parse(existing)
        return if (component in items) items.joinToString(":") else (items + component).joinToString(":")
    }

    fun contains(existing: String?, component: String): Boolean = component in parse(existing)

    private fun parse(existing: String?): List<String> =
        existing.orEmpty().trim().takeUnless { it.isEmpty() || it == "null" }
            ?.split(':')?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct().orEmpty()
}
