package com.eyeblink.core.privileged

import com.eyeblink.core.model.OperatingMode

enum class Capability {
    /** Can run shell commands at all. */
    SHELL,

    /** Can read every app's media sessions and dispatch media keys. */
    MEDIA_CONTROL,

    /** Can grant EyeBlink its own permissions (overlay, usage access, secure settings). */
    SELF_GRANT,

    /** Can enable the accessibility service and the notification listener without Settings. */
    ENABLE_SERVICES,

    /** Can write Night Light / brightness style secure settings. */
    DISPLAY_SETTINGS,

    /** Can read the foreground app without Usage Access or accessibility. */
    FOREGROUND_QUERY,
}

enum class PowerKind { NONE, SHIZUKU, ROOT }

/** What a privileged backend can really do right now - measured, not assumed. */
data class PowerStatus(
    val kind: PowerKind,
    val available: Boolean,
    val uid: Int? = null,
    val sdk: Int? = null,
    val capabilities: Set<Capability> = emptySet(),
    val detail: String? = null,
) {
    fun can(c: Capability) = available && c in capabilities

    companion object {
        fun unavailable(kind: PowerKind, detail: String? = null) = PowerStatus(kind, false, detail = detail)
        val None = PowerStatus(PowerKind.NONE, false)
    }
}

/**
 * Works out what a shell can do by asking it, instead of assuming that "root" or "Shizuku" is
 * a blanket yes. A Shizuku server started over ADB is uid 2000 (shell), which can do a lot but
 * not everything root can; the probe records the difference.
 */
object PowerProbe {

    private val UID = Regex("""uid=(\d+)""")

    fun uidOf(idOutput: String): Int? = UID.find(idOutput)?.groupValues?.get(1)?.toIntOrNull()

    suspend fun probe(kind: PowerKind, shell: ShellExecutor): PowerStatus {
        val id = shell.run(PrivilegedCommands.IDENTITY, FIRST_USE_TIMEOUT_MS)
        if (!id.ok) {
            val why = if (id.timedOut) "timed out waiting for a grant" else id.output.ifBlank { "no shell" }
            return PowerStatus.unavailable(kind, why)
        }
        val uid = uidOf(id.output) ?: return PowerStatus.unavailable(kind, "unrecognised identity: ${id.output.take(80)}")
        if (uid != ROOT_UID && uid != SHELL_UID) {
            return PowerStatus.unavailable(kind, "running as uid $uid, expected root (0) or shell (2000)")
        }

        val sdk = shell.run(PrivilegedCommands.SDK_LEVEL).output.trim().toIntOrNull()

        val caps = mutableSetOf(Capability.SHELL)
        val media = shell.run("${PrivilegedCommands.DUMP_MEDIA_SESSIONS} | head -n 5")
        if (media.ok && media.output.contains("media", ignoreCase = true)) caps += Capability.MEDIA_CONTROL

        // Root and the shell user can both manage packages, services and secure settings.
        caps += Capability.SELF_GRANT
        caps += Capability.ENABLE_SERVICES
        caps += Capability.DISPLAY_SETTINGS
        val focus = shell.run(PrivilegedCommands.DUMP_WINDOW_FOCUS)
        if (focus.ok && focus.output.isNotBlank()) caps += Capability.FOREGROUND_QUERY

        return PowerStatus(kind, true, uid, sdk, caps, if (uid == ROOT_UID) "root" else "shell (adb-level)")
    }

    fun modeFor(kind: PowerKind): OperatingMode = when (kind) {
        PowerKind.ROOT -> OperatingMode.ROOT
        PowerKind.SHIZUKU -> OperatingMode.SHIZUKU
        PowerKind.NONE -> OperatingMode.ROOTLESS
    }

    const val ROOT_UID = 0
    const val SHELL_UID = 2000

    /** The first `su` call may wait on a Superuser grant dialog, so allow a human to answer. */
    const val FIRST_USE_TIMEOUT_MS = 30_000L
}
