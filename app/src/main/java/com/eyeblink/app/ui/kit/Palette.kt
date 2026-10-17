package com.eyeblink.app.ui.kit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Dusk: EyeBlink's own colour language - not Material.
 *
 * The app is about light and its absence, so the base is true black (`#000000`, which on OLED
 * means pixels that emit nothing) and every other surface is a barely-there tint of it. Colour
 * is used like the eye uses it: one cool *iris* (aqua) for things that are awake and alive, one
 * warm *seam* (amber) for the line where the lids meet - rests, emphasis, the primary action.
 *
 * There is deliberately no light theme.
 */
object Dusk {
    val Void = Color(0xFF000000)
    val Ink0 = Color(0xFF05070B)
    val Ink1 = Color(0xFF0A0D14)
    val Ink2 = Color(0xFF10141D)
    val Ink3 = Color(0xFF181D29)

    val Hairline = Color(0x14FFFFFF)
    val HairlineStrong = Color(0x29FFFFFF)

    val Text = Color(0xFFF3EFE8)
    val TextDim = Color(0xFFB4B0BB)
    val TextFaint = Color(0xFF7C7986)

    val Iris = Color(0xFF52E6D2)
    val IrisDeep = Color(0xFF14A9BC)
    val IrisGlow = Color(0xFF9DF7EB)

    val Amber = Color(0xFFFFBC70)
    val Ember = Color(0xFFFF8A4C)
    val Rose = Color(0xFFFF6B8E)
    val Violet = Color(0xFF8E83FF)

    val Ok = Color(0xFF5CE6A6)
    val Warn = Color(0xFFFFC857)
    val Bad = Color(0xFFFF5D73)

    /** Primary action: the amber of a lid seam catching light. */
    fun dawn(start: Offset = Offset.Zero, end: Offset = Offset.Infinite): Brush =
        Brush.linearGradient(listOf(Amber, Ember), start, end)

    fun irisBrush(start: Offset = Offset.Zero, end: Offset = Offset.Infinite): Brush =
        Brush.linearGradient(listOf(IrisGlow, Iris, IrisDeep), start, end)

    /** Amber -> rose -> amber, used along lid margins. */
    fun seamBrush(start: Offset, end: Offset): Brush =
        Brush.linearGradient(listOf(Amber.copy(alpha = 0.15f), Amber, Rose, Amber, Amber.copy(alpha = 0.15f)), start, end)

    fun glass(alpha: Float = 1f): Brush = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = 0.075f * alpha), Color.White.copy(alpha = 0.03f * alpha)),
    )
}
