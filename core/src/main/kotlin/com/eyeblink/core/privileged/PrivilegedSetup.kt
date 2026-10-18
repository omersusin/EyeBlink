package com.eyeblink.core.privileged

enum class SetupStepId {
    OVERLAY, USAGE_ACCESS, SECURE_SETTINGS, NOTIFICATIONS, NOTIFICATION_LISTENER, ACCESSIBILITY, BATTERY_EXEMPT,
}

data class SetupStep(val id: SetupStepId, val ok: Boolean, val detail: String? = null)

/** The component names of the services EyeBlink needs enabled. */
data class ServiceComponents(
    val packageName: String,
    val notificationListener: String,
    val accessibility: String,
)

/**
 * "Set it up for me": uses a privileged shell to grant the permissions the user would
 * otherwise tap through in Settings. Each step is separate and reported separately, so the UI
 * can let the user pick, and a failure in one never hides the others.
 *
 * Camera is deliberately absent. Camera access stays an explicit decision made through
 * Android's own permission dialog, whatever privileges EyeBlink has.
 */
class PrivilegedSetup(private val shell: ShellExecutor, private val svc: ServiceComponents) {

    suspend fun run(steps: Set<SetupStepId>, sdk: Int?): List<SetupStep> {
        val results = mutableListOf<SetupStep>()
        for (id in SetupStepId.entries) {
            if (id !in steps) continue
            results += try { apply(id, sdk) } catch (e: Exception) { SetupStep(id, false, e.message) }
        }
        return results
    }

    private suspend fun apply(id: SetupStepId, sdk: Int?): SetupStep = when (id) {
        SetupStepId.OVERLAY -> exec(id, PrivilegedCommands.appOpsAllow(svc.packageName, "SYSTEM_ALERT_WINDOW"))
        SetupStepId.USAGE_ACCESS -> exec(id, PrivilegedCommands.appOpsAllow(svc.packageName, "GET_USAGE_STATS"))
        SetupStepId.SECURE_SETTINGS -> exec(id, PrivilegedCommands.grantPermission(svc.packageName, "android.permission.WRITE_SECURE_SETTINGS"))
        SetupStepId.NOTIFICATIONS ->
            if (sdk != null && sdk < 33) SetupStep(id, true, "not needed before Android 13")
            else exec(id, PrivilegedCommands.grantPermission(svc.packageName, "android.permission.POST_NOTIFICATIONS"))
        SetupStepId.NOTIFICATION_LISTENER -> exec(id, PrivilegedCommands.allowNotificationListener(svc.notificationListener))
        SetupStepId.ACCESSIBILITY -> enableAccessibility()
        SetupStepId.BATTERY_EXEMPT -> exec(id, PrivilegedCommands.deviceIdleWhitelist(svc.packageName))
    }

    private suspend fun exec(id: SetupStepId, command: String?): SetupStep {
        if (command == null) return SetupStep(id, false, "refused an invalid argument")
        val r = shell.run(command, STEP_TIMEOUT_MS)
        return SetupStep(id, r.ok, r.output.take(200).ifBlank { null })
    }

    private suspend fun enableAccessibility(): SetupStep {
        val id = SetupStepId.ACCESSIBILITY
        val read = PrivilegedCommands.getSecure("enabled_accessibility_services")
            ?: return SetupStep(id, false, "invalid setting key")
        val current = shell.run(read, STEP_TIMEOUT_MS)
        if (!current.ok) return SetupStep(id, false, current.output.take(200))

        // Merge, never overwrite: the user's other accessibility services must stay enabled.
        val merged = AccessibilityList.withService(current.output, svc.accessibility)
        val write = PrivilegedCommands.putSecure("enabled_accessibility_services", merged)
            ?: return SetupStep(id, false, "invalid setting key")
        val on = PrivilegedCommands.putSecure("accessibility_enabled", "1")
            ?: return SetupStep(id, false, "invalid setting key")

        val w = shell.run(write, STEP_TIMEOUT_MS)
        if (!w.ok) return SetupStep(id, false, w.output.take(200))
        val e = shell.run(on, STEP_TIMEOUT_MS)
        return SetupStep(id, e.ok, e.output.take(200).ifBlank { null })
    }

    private companion object {
        const val STEP_TIMEOUT_MS = 8_000L
    }
}
