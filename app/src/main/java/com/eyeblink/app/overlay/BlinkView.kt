package com.eyeblink.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import com.eyeblink.app.platform.Signals
import com.eyeblink.app.platform.nowMs
import com.eyeblink.core.anim.BlinkFrame
import com.eyeblink.core.anim.BlinkSpec
import com.eyeblink.core.anim.EyelidGeometry
import kotlin.math.roundToInt

/**
 * The blink itself, drawn straight onto the window's canvas: whole-screen darkening, feathered
 * almond-shaped lids, and the pure `#000000` cover.
 *
 * It is the platform twin of `drawBlinkFrame` in the design kit (the painter behind the live
 * preview in Blink Studio). Both read the same numbers from `EyelidGeometry` and `BlinkTimeline`
 * and draw them the same way, so what the preview shows is what the overlay does. The overlay uses
 * a plain [View] rather than a Compose view so that a blink costs a few path fills and nothing
 * else: no composition, no recomposer, no first-frame delay.
 */
class BlinkView(context: Context) : View(context) {

    var frame: BlinkFrame = BlinkFrame.Idle
    var spec: BlinkSpec = BlinkSpec()

    private val black = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.FILL }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val lid = Path()

    override fun onDraw(canvas: Canvas) {
        val f = frame
        if (f.isIdle) return
        val w = width.toFloat()
        val h = height.toFloat()

        if (f.dim > 0f) fill(canvas, w, h, f.dim)

        if (f.sweep && f.closure > 0f) {
            for (layer in EyelidGeometry.topLayers(f.closure, h, spec.curvature, spec.feather)) {
                val m = layer.margin
                black.alpha = alpha(layer.alpha)
                lid.reset()
                lid.moveTo(0f, 0f); lid.lineTo(w, 0f); lid.lineTo(w, m.edge)
                lid.quadTo(w / 2f, m.control, 0f, m.edge); lid.close()
                canvas.drawPath(lid, black)
                lid.reset()
                lid.moveTo(0f, h); lid.lineTo(w, h); lid.lineTo(w, h - m.edge)
                lid.quadTo(w / 2f, h - m.control, 0f, h - m.edge); lid.close()
                canvas.drawPath(lid, black)
            }
            if (spec.rimLight > 0f && f.closure < 0.98f) {
                val m = EyelidGeometry.margin(f.closure, h, spec.curvature)
                rim.color = Color.argb(alpha(0.55f * spec.rimLight * (1f - f.closure)), 255, 188, 112)
                rim.strokeWidth = 2f * resources.displayMetrics.density
                lid.reset(); lid.moveTo(0f, m.edge); lid.quadTo(w / 2f, m.control, w, m.edge)
                canvas.drawPath(lid, rim)
                lid.reset(); lid.moveTo(0f, h - m.edge); lid.quadTo(w / 2f, h - m.control, w, h - m.edge)
                canvas.drawPath(lid, rim)
            }
        }

        if (f.solid > 0f) fill(canvas, w, h, f.solid)
    }

    private fun fill(canvas: Canvas, w: Float, h: Float, a: Float) {
        black.alpha = alpha(a)
        canvas.drawRect(0f, 0f, w, h, black)
    }

    private fun alpha(a: Float): Int = (a.coerceIn(0f, 1f) * 255f).roundToInt()
}

/**
 * One pixel in a corner that is told when a finger goes down anywhere else on the screen.
 * Android delivers that as `ACTION_OUTSIDE`, deliberately without the coordinates, so EyeBlink
 * learns that you touched the screen - enough to never interrupt a swipe - and nothing about where.
 */
class TouchSentinel(context: Context) : View(context) {
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) Signals.onTouchDown(nowMs())
        return false
    }
}
