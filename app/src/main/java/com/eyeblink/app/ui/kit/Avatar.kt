package com.eyeblink.app.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle

private val avatarPairs = listOf(
    Color(0xFF52E6D2) to Color(0xFF14A9BC),
    Color(0xFFFFBC70) to Color(0xFFFF8A4C),
    Color(0xFFFF6B8E) to Color(0xFFB4456A),
    Color(0xFF8E83FF) to Color(0xFF5B52C9),
    Color(0xFF5CE6A6) to Color(0xFF2DAA78),
)

/** A rounded-square badge with the app's initial on a colour derived from its package name. */
@Composable
fun AppAvatar(label: String, packageName: String, modifier: Modifier = Modifier, size: Dp = 46.dp) {
    val (a, b) = avatarPairs[(packageName.hashCode() and 0x7fffffff) % avatarPairs.size]
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.34f)).background(Brush.linearGradient(listOf(a.copy(alpha = 0.95f), b.copy(alpha = 0.9f)))),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            label.firstOrNull()?.uppercase() ?: "?",
            style = TextStyle(fontFamily = EbFonts.serif, fontSize = (size.value * 0.5f).sp, fontWeight = FontWeight.Normal, color = Color(0xFF0A0D14)),
        )
    }
}
