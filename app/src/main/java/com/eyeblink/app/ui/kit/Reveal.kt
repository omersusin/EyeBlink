package com.eyeblink.app.ui.kit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val LocalRevealClock = compositionLocalOf<State<Float>> { mutableStateOf(10_000f) }

private const val REVEAL_STAGGER_MS = 62f
private const val REVEAL_DURATION_MS = 520f
private const val REVEAL_TOTAL_MS = 2_200f

/**
 * Wraps a screen so that its parts can rise in one after another. One clock serves the whole
 * screen and stops once everything has arrived, so a settled screen costs no animation work.
 */
@Composable
fun RevealScope(content: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    var running by remember { mutableStateOf(!reduced) }
    val clock = rememberClockMs(running = running)
    val settled = remember { mutableStateOf(10_000f) }
    if (running && clock.value > REVEAL_TOTAL_MS) running = false
    CompositionLocalProvider(LocalRevealClock provides (if (reduced) settled else clock), content = content)
}

/** The i-th element of a screen fades up into place, slightly after the one before it. */
fun Modifier.reveal(index: Int): Modifier = composed {
    val clock = LocalRevealClock.current.value
    val t = ((clock - 90f - index * REVEAL_STAGGER_MS) / REVEAL_DURATION_MS).coerceIn(0f, 1f)
    val eased = 1f - (1f - t) * (1f - t) * (1f - t)
    graphicsLayer {
        alpha = eased
        translationY = (1f - eased) * 26.dp.toPx()
    }
}

/** Standard page: scrolling column with generous side margins and room for the dock. */
@Composable
fun ScreenColumn(
    modifier: Modifier = Modifier,
    bottomInset: Dp = 118.dp,
    topInset: Dp = 18.dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val insets = LocalInsets.current
    RevealScope {
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = topInset + insets.top, bottom = bottomInset + insets.bottom),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}
