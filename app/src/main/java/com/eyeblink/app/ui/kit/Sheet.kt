package com.eyeblink.app.ui.kit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

/** A modal sheet rising from the bottom over a black scrim. Closed sheets cost nothing: they are not composed. */
@Composable
fun BottomSheet(visible: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val progress by animateFloatAsState(if (visible) 1f else 0f, Motion.Settle, label = "sheet")
    if (!visible && progress < 0.01f) return
    var height by remember { mutableStateOf(1) }
    val bottomInset = LocalInsets.current.bottom

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().alpha(progress).background(Color.Black.copy(alpha = 0.74f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onDismiss),
        )
        Column(
            modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .offset { IntOffset(0, ((1f - progress) * height).roundToInt()) }
                .onSizeChanged { height = it.height }
                .clip(RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp))
                .background(Dusk.Ink1)
                .glass(RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp), strength = 0.9f)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                .padding(horizontal = 22.dp).padding(top = 12.dp, bottom = 28.dp + bottomInset)
                .heightIn(max = 760.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Dusk.HairlineStrong))
            Box(Modifier.height(18.dp))
            Column(Modifier.verticalScroll(rememberScrollState()), content = content)
        }
    }
}
