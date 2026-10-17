package com.eyeblink.app.overlay

import com.eyeblink.app.platform.systemService
import android.app.Application
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.eyeblink.app.platform.Signals

/**
 * Puts EyeBlink's windows on screen, and takes them off again.
 *
 * Two kinds of window can be used, in this order of preference:
 *  - an accessibility overlay, owned by the accessibility service. It needs no overlay permission
 *    and sits above the status and navigation bars, so the blink really covers the whole screen;
 *  - a classic application overlay, which needs "display over other apps".
 *
 * Every window is created only for as long as it is needed (a blink, a rest). Nothing stays
 * attached between breaks except the one-pixel touch sentinel.
 */
class OverlayWindows(private val app: Application) {

    private data class Attached(val wm: WindowManager, val view: View)

    private val attached = LinkedHashMap<View, Attached>()

    /** "Accessibility overlay", "Application overlay", or empty while nothing has been shown. */
    @Volatile var kind: String = ""
        private set

    /** Whether any kind of window can be shown right now. */
    fun available(): Boolean = Signals.accessibility != null || Settings.canDrawOverlays(app)

    /** Must be called on the main thread. */
    fun attach(view: View, touchable: Boolean, keepScreenOn: Boolean = false): Boolean {
        val (wm, type) = target() ?: return false
        val size = screenSize(wm)
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (keepScreenOn) flags = flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        val lp = WindowManager.LayoutParams(size.width(), size.height(), type, flags, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "EyeBlink"
            if (Build.VERSION.SDK_INT >= 30) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        return add(wm, view, lp, type)
    }

    /** The one-pixel window that is told, without coordinates, that a finger went down somewhere else. */
    fun attachSentinel(view: View): Boolean {
        val (wm, type) = target() ?: return false
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        val lp = WindowManager.LayoutParams(1, 1, type, flags, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "EyeBlink sentinel"
        }
        return add(wm, view, lp, type)
    }

    fun detach(view: View) {
        val a = attached.remove(view) ?: return
        runCatching { a.wm.removeViewImmediate(a.view) }
    }

    fun detachAll() {
        attached.values.toList().forEach { runCatching { it.wm.removeViewImmediate(it.view) } }
        attached.clear()
    }

    private fun add(wm: WindowManager, view: View, lp: WindowManager.LayoutParams, type: Int): Boolean = try {
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        wm.addView(view, lp)
        attached[view] = Attached(wm, view)
        kind = if (type == WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY) KIND_ACCESSIBILITY else KIND_APPLICATION
        true
    } catch (e: Exception) {
        // A revoked permission or a window token that just went away: the caller falls back.
        false
    }

    private fun target(): Pair<WindowManager, Int>? {
        Signals.accessibility?.let { svc ->
            return svc.systemService<WindowManager>() to WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        }
        if (Settings.canDrawOverlays(app)) {
            return app.systemService<WindowManager>() to WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }
        return null
    }

    private fun screenSize(wm: WindowManager): Rect {
        if (Build.VERSION.SDK_INT >= 30) return Rect(wm.maximumWindowMetrics.bounds)
        val m = DisplayMetrics()
        @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m)
        return Rect(0, 0, m.widthPixels, m.heightPixels)
    }

    companion object {
        const val KIND_ACCESSIBILITY = "Accessibility overlay"
        const val KIND_APPLICATION = "Application overlay"
    }
}

/** The status-bar height, for the rest scene's top padding. Zero if the platform does not say. */
fun Context.statusBarHeightPx(): Int {
    val id = resources.getIdentifier("status_bar_height", "dimen", "android")
    return if (id > 0) resources.getDimensionPixelSize(id) else 0
}

fun Context.navigationBarHeightPx(): Int {
    val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
    return if (id > 0) resources.getDimensionPixelSize(id) else 0
}
