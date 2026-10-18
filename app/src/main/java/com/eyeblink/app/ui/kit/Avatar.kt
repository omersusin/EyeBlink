package com.eyeblink.app.ui.kit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText

/** A rounded-square tile with the app's initial on a colour derived from its package name. */
@Composable
fun AppAvatar(label: String, packageName: String, modifier: Modifier = Modifier, size: Dp = 46.dp) {
    val p = P
    val hues = listOf(p.sky, p.sun, p.mint, p.coral, p.grape)
    val hue = hues[(packageName.hashCode() and 0x7fffffff) % hues.size]
    val shape = RoundedCornerShape(size * 0.32f)
    Box(
        modifier.size(size).raised(shape, hue.fill, hue.lip, depth = 2.dp).clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            label.firstOrNull()?.uppercase() ?: "?",
            style = TextStyle(fontFamily = EbFonts.display, fontSize = (size.value * 0.46f).sp, fontWeight = FontWeight.ExtraBold, color = p.onFill),
        )
    }
}
