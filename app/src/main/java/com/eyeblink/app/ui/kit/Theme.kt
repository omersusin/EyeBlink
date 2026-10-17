package com.eyeblink.app.ui.kit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eyeblink.app.ui.i18n.Strings

val LocalTypography = staticCompositionLocalOf { EbTypography() }

/** True when the user (or the system's "remove animations") asked for less motion. */
val LocalReducedMotion = compositionLocalOf { false }

val LocalStrings = staticCompositionLocalOf { Strings.EN }

/**
 * Space the system keeps for itself (status bar, cutout, navigation bar). The platform measures it
 * and the UI only pads by it, so the backdrop can run edge to edge while content stays clear.
 */
@Immutable
data class Insets(val top: Dp = 0.dp, val bottom: Dp = 0.dp)

val LocalInsets = compositionLocalOf { Insets() }

/** Wraps everything. [reducedMotion] swaps sweeping motion for fades throughout the UI. */
@Composable
fun EbTheme(strings: Strings = Strings.EN, reducedMotion: Boolean = false, insets: Insets = Insets(), content: @Composable () -> Unit) {
    val typography = remember { EbTypography() }
    CompositionLocalProvider(
        LocalTypography provides typography,
        LocalStrings provides strings,
        LocalReducedMotion provides reducedMotion,
        LocalInsets provides insets,
        content = content,
    )
}

/** Shorthand used by screens. */
val S: Strings @Composable get() = LocalStrings.current
