package com.eyeblink.app.platform

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import com.eyeblink.app.access.EyeBlinkAccessibilityService
import com.eyeblink.app.access.EyeBlinkListenerService
import com.eyeblink.app.ui.state.PermissionKind

/** What Android currently lets EyeBlink do, and the screens where the user can change that. */
object Access {

    fun accessibilityComponent(ctx: Context) = ComponentName(ctx, EyeBlinkAccessibilityService::class.java)
    fun listenerComponent(ctx: Context) = ComponentName(ctx, EyeBlinkListenerService::class.java)

    /** The user switched the accessibility service on (it may not have connected yet). */
    fun accessibilityEnabled(ctx: Context): Boolean =
        listed(Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), accessibilityComponent(ctx))

    fun listenerEnabled(ctx: Context): Boolean =
        listed(Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners"), listenerComponent(ctx))

    private fun listed(setting: String?, component: ComponentName): Boolean {
        if (setting.isNullOrEmpty()) return false
        val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(setting) }
        while (splitter.hasNext()) {
            if (ComponentName.unflattenFromString(splitter.next()) == component) return true
        }
        return false
    }

    fun overlay(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun usageStats(ctx: Context): Boolean {
        val ops = ctx.systemService<AppOpsManager>()
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun camera(ctx: Context): Boolean = ctx.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    fun notifications(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun writeSettings(ctx: Context): Boolean = Settings.System.canWrite(ctx)

    fun secureSettings(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun batteryExempt(ctx: Context): Boolean =
        ctx.systemService<PowerManager>().isIgnoringBatteryOptimizations(ctx.packageName)

    fun granted(ctx: Context): Set<PermissionKind> = buildSet {
        if (Signals.accessibility != null || accessibilityEnabled(ctx)) add(PermissionKind.ACCESSIBILITY)
        if (Signals.listenerConnected || listenerEnabled(ctx)) add(PermissionKind.NOTIFICATION_LISTENER)
        if (overlay(ctx)) add(PermissionKind.OVERLAY)
        if (usageStats(ctx)) add(PermissionKind.USAGE_ACCESS)
        if (camera(ctx)) add(PermissionKind.CAMERA)
        if (notifications(ctx)) add(PermissionKind.NOTIFICATIONS)
        if (writeSettings(ctx)) add(PermissionKind.WRITE_SETTINGS)
        if (batteryExempt(ctx)) add(PermissionKind.BATTERY_OPTIMIZATION)
    }

    /**
     * The settings screen for [kind], or null when the permission is granted through Android's own
     * runtime dialog instead (camera, notifications), which the activity shows.
     */
    fun settingsIntent(ctx: Context, kind: PermissionKind): Intent? {
        val pkgUri = Uri.parse("package:${ctx.packageName}")
        return when (kind) {
            PermissionKind.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri)
            PermissionKind.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            PermissionKind.NOTIFICATION_LISTENER ->
                if (Build.VERSION.SDK_INT >= 30) {
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                        .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listenerComponent(ctx).flattenToString())
                } else {
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                }
            PermissionKind.USAGE_ACCESS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, pkgUri)
            PermissionKind.WRITE_SETTINGS -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkgUri)
            PermissionKind.BATTERY_OPTIMIZATION -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            PermissionKind.CAMERA, PermissionKind.NOTIFICATIONS -> null
        }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun appDetailsIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
