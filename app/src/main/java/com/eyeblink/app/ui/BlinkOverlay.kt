package com.eyeblink.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.asState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.eyeblink.app.animation.BlinkAnimator
import com.eyeblink.app.core.BlinkConfig

@Composable
fun BlinkOverlay(animator: BlinkAnimator, config: BlinkConfig) {
    val p by animator.progress.asState()
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // full-screen darken
        drawRect(Color.Black.copy(alpha = config.darkenStrength * p))
        // top eyelid
        drawEyelid(top = true, progress = p, curve = config.curve, w = w, h = h)
        // bottom eyelid
        drawEyelid(top = false, progress = p, curve = config.curve, w = w, h = h)
        // fully black when closed
        if (p > 0.99f) drawRect(Color.Black)
    }
}

private fun DrawScope.drawEyelid(top: Boolean, progress: Float, curve: Float, w: Float, h: Float) {
    val lidH = h * 0.55f * progress
    val ctrl = lidH * (1f + curve)
    val path = Path().apply {
        if (top) {
            moveTo(0f, 0f)
            lineTo(w, 0f)
            lineTo(w, lidH)
            quadraticTo(w / 2f, ctrl, 0f, lidH)
            close()
        } else {
            moveTo(0f, h)
            lineTo(w, h)
            lineTo(w, h - lidH)
            quadraticTo(w / 2f, h - ctrl, 0f, h - lidH)
            close()
        }
    }
    drawPath(path, brush = Brush.verticalGradient(
        colors = listOf(Color(0xFF0A0A0A), Color.Black),
        startY = if (top) 0f else h - lidH,
        endY = if (top) lidH else h
    ))
}
