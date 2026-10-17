package com.eyeblink.app.context

import android.app.KeyguardManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager

/**
 * Foreground-app and device-state awareness used to decide *when it is safe*
 * to interrupt. Conservative by design: when state cannot be determined we
 * report "not safe" rather than blinking over a call or a text field.
 */
class ContextEngine(private val context: Context) {

    private val powerManager: PowerManager? =
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private val keyguardManager: KeyguardManager? =
        context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager

    /**
     * Null unless the user granted Usage Access. Requires the user to enable
     * it in Settings > Special app access > Usage access.
     */
    fun foregroundPackage(): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val now = System.currentTimeMillis()
        val stats = runCatching {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60_000, now)
        }.getOrNull() ?: return null
        return stats?.maxByOrNull { it.lastTimeUsed }?.packageName
    }

    /** False when the screen is off or the device is locked. */
    fun isScreenOn(): Boolean = powerManager?.isInteractive ?: false

    fun isDeviceUnlocked(): Boolean = keyguardManager?.isDeviceUnlocked ?: false

    /**
     * Safe to interrupt only when: screen on, device unlocked, and no blocking
     * foreground app detected. Unknown foreground state is treated as unsafe so
     * we never overlay a call, payment or auth screen we could not identify.
     */
    fun isInteractive(): Boolean {
        if (!isScreenOn()) return false
        if (!isDeviceUnlocked()) return false
        val fg = foregroundPackage() ?: return false // no Usage Access -> stay quiet
        return fg !in BLOCKING_PACKAGES
    }

    private companion object {
        /** Never overlay these. */
        val BLOCKING_PACKAGES = setOf(
            "com.android.server.telecom", // in-call UI
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.systemui", // keyguard, recents, notifications
            "com.android.settings",
        )
    }
}