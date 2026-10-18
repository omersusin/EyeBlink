package com.eyeblink.app.ui.eye

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eyeblink.app.ui.eye.EyeArt.drawEye
import com.eyeblink.app.ui.kit.Daylight

/**
 * The EyeBlink logo: the mascot eye on a sky-blue tile. It looks the same in both themes - the
 * logo is a brand colour, not a surface - and it is the same drawing as the launcher icon.
 */
@Composable
fun EyeMark(modifier: Modifier = Modifier, size: Dp = 40.dp, closure: Float = 0f) {
    val colors = remember { EyeColors.of(Daylight) }
    val dpPx = with(LocalDensity.current) { 1.dp.toPx() }
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val lip = w * 0.06f
        val r = CornerRadius(w * 0.30f)
        drawRoundRect(Daylight.sky.lip, Offset(0f, lip), Size(w, w - lip), r)
        drawRoundRect(Daylight.sky.fill, Offset.Zero, Size(w, w - lip), r)
        val eyeW = w * 0.74f
        val eyeH = eyeW * 0.62f
        val top = (w - lip - eyeH) / 2f
        drawEye(Rect(Offset((w - eyeW) / 2f, top), Size(eyeW, eyeH)), EyeLook(closure = closure), colors, dpPx * (w / 40.dp.toPx()), lashes = true, lip = false)
    }
}
