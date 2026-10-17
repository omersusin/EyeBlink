package com.eyeblink.app.ui.kit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * One family of colour. Everything in the app that is coloured is one of five of these, so a screen
 * never needs more than a handful of hues and they always go together.
 */
@Immutable
class Hue(
    /** The bright colour: buttons, marks, big shapes. Text on it is always [Palette.onFill]. */
    val fill: Color,
    /** The darker edge under a raised [fill]. */
    val lip: Color,
    /** A pale tint for card faces. */
    val soft: Color,
    /** The edge under a card painted with [soft]. */
    val softLip: Color,
    /** The same hue, dark enough to read as small text on a card or on [soft]. */
    val text: Color,
)

/**
 * EyeBlink's colours. Two themes share one structure:
 *
 *  - **Daylight**: a sky-washed white page, white cards, five sunny hues, deep-navy ink.
 *  - **Moonlit**: a deep indigo page (not black - the black belongs to the blink itself),
 *    indigo cards, the same five hues turned up for a dark room.
 *
 * Raised things (cards, buttons, the dock) carry a *lip*, a darker edge a few dp thick underneath;
 * pressing one sinks it onto its lip, like a lid closing. Nothing glows and nothing is translucent.
 * Text on a bright fill is always [onFill], so contrast never depends on which hue it is.
 */
@Immutable
class Palette(
    val dark: Boolean,
    /** The page. */
    val bg: Color,
    /** A shade below the page: handles, wells, the deepest layer. */
    val bgDeep: Color,
    /** The lids that close over the screen when you move between tabs. */
    val lid: Color,
    val card: Color,
    val cardLip: Color,
    /** Sunken areas: the groove of a switch, the track of a segmented control. */
    val sunk: Color,
    /** Hairlines. */
    val line: Color,
    /** The empty part of a meter or a slider: a veil that shows on a white card and on a tinted one alike. */
    val track: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkFaint: Color,
    val onFill: Color,
    /** What dims the page behind a sheet. */
    val scrim: Color,
    val sun: Hue,
    val sky: Hue,
    val mint: Hue,
    val coral: Hue,
    val grape: Hue,
) {
    val ok: Hue get() = mint
    val warn: Hue get() = sun
    val bad: Hue get() = coral
}

private fun hue(fill: Long, lip: Long, soft: Long, softLip: Long, text: Long) =
    Hue(Color(fill), Color(lip), Color(soft), Color(softLip), Color(text))

val Daylight = Palette(
    dark = false,
    bg = Color(0xFFF3F8FF), bgDeep = Color(0xFFE2EDF9), lid = Color(0xFFFFF1BF),
    card = Color(0xFFFFFFFF), cardLip = Color(0xFFCCDDF0),
    sunk = Color(0xFFE6EFF9), line = Color(0xFFDAE6F4), track = Color(0x2416254A),
    ink = Color(0xFF16254A), inkSoft = Color(0xFF4A5A7E), inkFaint = Color(0xFF5F6E92),
    onFill = Color(0xFF16254A), scrim = Color(0x7316254A),
    sun = hue(0xFFFFC62E, 0xFFE3A10C, 0xFFFFF1BF, 0xFFF2DB8A, 0xFF8A5800),
    sky = hue(0xFF3AA4FF, 0xFF1C83DE, 0xFFDDF0FF, 0xFFB7DAF5, 0xFF0B5EB3),
    mint = hue(0xFF35CF92, 0xFF17A471, 0xFFD6F7E8, 0xFFA9E6CC, 0xFF0A7050),
    coral = hue(0xFFFF6F5E, 0xFFDE4D3D, 0xFFFFE3DE, 0xFFF6BDB4, 0xFFBE2F20),
    grape = hue(0xFF8F80FF, 0xFF6D5DE3, 0xFFE9E5FF, 0xFFCBC4F7, 0xFF5242C7),
)

val Moonlit = Palette(
    dark = true,
    bg = Color(0xFF11143B), bgDeep = Color(0xFF0A0C2A), lid = Color(0xFF0A0C2A),
    card = Color(0xFF1C2051), cardLip = Color(0xFF0A0C2A),
    sunk = Color(0xFF14183F), line = Color(0xFF2D336F), track = Color(0x66060822),
    ink = Color(0xFFF6F4FF), inkSoft = Color(0xFFC0C5EE), inkFaint = Color(0xFF9199CF),
    onFill = Color(0xFF11143B), scrim = Color(0x99070920),
    sun = hue(0xFFFFD25C, 0xFFD79F1E, 0xFF5A4415, 0xFF3F2F0E, 0xFFFFD25C),
    sky = hue(0xFF66B8FF, 0xFF3A8CDB, 0xFF263E7A, 0xFF182B58, 0xFF8CCBFF),
    mint = hue(0xFF4FDEA5, 0xFF27B07E, 0xFF1B4A57, 0xFF10343E, 0xFF6FE8B9),
    coral = hue(0xFFFF8575, 0xFFD95C4D, 0xFF4F2B45, 0xFF381D31, 0xFFFF9D90),
    grape = hue(0xFFAA9EFF, 0xFF7E70E3, 0xFF3A3578, 0xFF28255A, 0xFFC0B7FF),
)

val LocalPalette = staticCompositionLocalOf { Daylight }

/** Shorthand used everywhere: the palette of the theme the UI is in. */
val P: Palette @Composable @ReadOnlyComposable get() = LocalPalette.current

/** Always-dark values for the places that sit on pure black no matter the theme (the rest scene, the blink). */
object OnBlack {
    val Void = Color(0xFF000000)
    val ink = Moonlit.ink
    val inkSoft = Moonlit.inkSoft
    val inkFaint = Moonlit.inkFaint
    val sun = Moonlit.sun
    val sky = Moonlit.sky
    val mint = Moonlit.mint
    val coral = Moonlit.coral
    val grape = Moonlit.grape
}
