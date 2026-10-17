package com.eyeblink.app.access

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import com.eyeblink.app.engine.engine
import com.eyeblink.app.platform.Signals
import com.eyeblink.app.platform.nowMs
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * EyeBlink's eyes on the system: which app is in front, that the foreground app just scrolled,
 * that a text field took focus, and a window it can draw over everything - status bar included -
 * without the overlay permission.
 *
 * What it deliberately does not do: it does not ask to read window content (see
 * `accessibility_service_config.xml`) and it never calls `event.text`, `event.contentDescription`
 * or `event.source`. From each event it reads the package name, the class name, and for focus
 * events whether the field is a password field. That is the whole of what it learns.
 */
class EyeBlinkAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Signals.accessibility = this
        engine.onAccessibilityChanged(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val now = nowMs()
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString() ?: return
                Signals.onWindowState(packageName, pkg, event.className?.toString(), now)
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val pkg = event.packageName?.toString()
                val front = Signals.foregroundPackage
                if (front == null || pkg == front) Signals.onScroll(now)
            }
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> Signals.onFocus(event.className?.toString(), event.isPassword, now)
        }
    }

    override fun onInterrupt() {
        // Android asks every accessibility service to stop what it is doing: that includes drawing.
        engine.emergencyClear()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        goodbye()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        goodbye()
        super.onDestroy()
    }

    private fun goodbye() {
        if (Signals.accessibility === this) {
            Signals.accessibility = null
            Signals.clearAll()
            // Windows owned by this service die with it; make sure nothing of ours lingers or is expected.
            engine.onAccessibilityChanged(false)
        }
    }

    /**
     * A single tap at the given point, in screen pixels. Only the experimental gesture strategy
     * uses it, and only for a player the compatibility test has proven.
     */
    suspend fun tap(x: Float, y: Float, durationMs: Long = 40L): Boolean = suspendCancellableCoroutine { cont ->
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs)).build()
        val accepted = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) { if (cont.isActive) cont.resume(true) }
                override fun onCancelled(g: GestureDescription?) { if (cont.isActive) cont.resume(false) }
            },
            null,
        )
        if (!accepted && cont.isActive) cont.resume(false)
    }
}
