package com.eyeblink.app.ui.kit

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

/**
 * The typefaces, injected by the platform so this file stays free of Android resources:
 * Android loads them from `res/font`, the desktop design lab from the same files on disk.
 * Until a platform sets them the generic families are used, so nothing ever crashes.
 */
object EbFonts {
    var display: FontFamily = FontFamily.SansSerif
    var sans: FontFamily = FontFamily.SansSerif
    var mono: FontFamily = FontFamily.Monospace
}

enum class Ink { Display, Headline, Lead, Title, Body, BodyStrong, Caption, Eyebrow, Chip, Num, NumSmall, Code, Button }

/**
 * Bricolage Grotesque for the loud, friendly moments (numerals, titles, buttons); Figtree for
 * reading; DM Mono only where a command is shown. Tabular numerals wherever numbers change.
 */
@Immutable
class EbTypography {
    fun style(ink: Ink, color: Color): TextStyle = when (ink) {
        Ink.Display -> TextStyle(
            fontFamily = EbFonts.display, fontWeight = FontWeight.ExtraBold, fontSize = 76.sp, lineHeight = 80.sp,
            letterSpacing = (-2.2).sp, color = color, fontFeatureSettings = "tnum",
        )
        Ink.Headline -> TextStyle(
            fontFamily = EbFonts.display, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 34.sp,
            letterSpacing = (-0.6).sp, color = color,
        )
        Ink.Lead -> TextStyle(
            fontFamily = EbFonts.display, fontWeight = FontWeight.Bold, fontSize = 23.sp, lineHeight = 28.sp,
            letterSpacing = (-0.3).sp, color = color,
        )
        Ink.Title -> TextStyle(
            fontFamily = EbFonts.sans, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp, color = color,
        )
        Ink.Body -> TextStyle(
            fontFamily = EbFonts.sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp, color = color,
        )
        Ink.BodyStrong -> TextStyle(
            fontFamily = EbFonts.sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 22.sp, color = color,
        )
        Ink.Caption -> TextStyle(
            fontFamily = EbFonts.sans, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, color = color,
        )
        Ink.Eyebrow -> TextStyle(
            fontFamily = EbFonts.sans, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 16.sp,
            letterSpacing = 0.1.sp, color = color,
        )
        Ink.Chip -> TextStyle(
            fontFamily = EbFonts.sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp, color = color,
        )
        Ink.Num -> TextStyle(
            fontFamily = EbFonts.sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp,
            color = color, fontFeatureSettings = "tnum",
        )
        Ink.NumSmall -> TextStyle(
            fontFamily = EbFonts.sans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp,
            color = color, fontFeatureSettings = "tnum",
        )
        // For commands people copy: sized so a 40-character permission name fits a card without wrapping.
        Ink.Code -> TextStyle(
            fontFamily = EbFonts.mono, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 16.sp, color = color,
        )
        Ink.Button -> TextStyle(
            fontFamily = EbFonts.display, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.sp,
            letterSpacing = 0.sp, color = color,
        )
    }
}

/** The only text composable the UI uses. */
@Composable
fun EbText(
    text: String,
    ink: Ink = Ink.Body,
    color: Color = if (ink == Ink.Caption || ink == Ink.Eyebrow) P.inkSoft else P.ink,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    caps: Boolean = false,
) {
    val shown = if (caps) text.uppercase() else text
    BasicText(
        text = shown,
        modifier = modifier,
        style = LocalTypography.current.style(ink, color).copy(textAlign = align),
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
fun EbText(
    text: AnnotatedString,
    ink: Ink = Ink.Body,
    color: Color = P.ink,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Start,
) {
    BasicText(text = text, modifier = modifier, style = LocalTypography.current.style(ink, color).copy(textAlign = align))
}
