package com.eyeblink.app.core

import android.app.ActivityManager
import android.content.Context
import android.content.Intent

/**
 * Whether one of our own services is currently running.
 *
 * `getRunningServices` is deprecated and since Android 8 only reports the
 * caller's own services - which is exactly the question being asked here.
 * For any other app's services this correctly returns false.
 */
internal fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
    @Suppress("DEPRECATION")
    return manager.getRunningServices(Int.MAX_VALUE)
        .any { it.service.className == serviceClass.name }
}

/** Starts a service only if the user has granted the overlay permission. */
internal fun startIfPermitted(context: Context, serviceClass: Class<*>, canDrawOverlays: Boolean): Boolean {
    if (!canDrawOverlays) return false
    context.startService(Intent(context, serviceClass))
    return true
}