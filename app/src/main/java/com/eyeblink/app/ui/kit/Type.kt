package com.eyeblink.app.ui.kit

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

/**
 * The three typefaces, injected by the platform so this file stays free of Android resources:
 * Android loads them from `res/font`, the desktop design lab from the same files on disk.
 * Until a platform sets them the generic families are used, so nothing ever crashes.
 */
object EbFonts {
    var serif: FontFamily = FontFamily.Serif
    var serifItalic: FontFamily = FontFamily.Serif
    var sans: FontFamily = FontFamily.SansSerif
    var mono: FontFamily = FontFamily.Monospace
}

enum class Ink { Display, DisplayItalic, Headline, Title, Body, BodyStrong, Caption, Eyebrow, Mono, MonoSmall, Code, Button }

/**
 * Instrument Serif for the big, human moments (numerals, titles); Instrument Sans for reading;
 * DM Mono for the small technical readouts. Tabular numerals everywhere numbers change.
 */
@Immutable
class EbTypography {
    fun style(ink: Ink, color: Color = Dusk.Text): TextStyle = when (ink) {
        Ink.Display -> TextStyle(
            fontFamily = EbFonts.serif, fontSize = 84.sp, lineHeight = 84.sp, letterSpacing = (-2.5).sp, color = color,
            fontFeatureSettings = "tnum",
        )
        Ink.DisplayItalic -> TextStyle(
            fontFamily = EbFonts.serifItalic, fontStyle = FontStyle.Italic, fontSize = 40.sp, lineHeight = 44.sp,
            letterSpacing = (-0.8).sp, color = color,
        )
        Ink.Headline -> TextStyle(
            fontFamily = EbFonts.serif, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.6).sp, color = color,
        )
        Ink.Title -> TextStyle(
            fontFamily = EbFonts.sans, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp,
            letterSpacing = (-0.1).sp, color = color,
        )
        Ink.Body -> TextStyle(
            fontFamily = EbFonts.sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp, color = color,
        )
        Ink.BodyStrong -> TextStyle(
            fontFamily = EbFonts.sans, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 22.sp, color = color,
        )
        Ink.Caption -> TextStyle(
            fontFamily = EbFonts.sans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, color = color,
        )
        Ink.Eyebrow -> TextStyle(
            fontFamily = EbFonts.mono, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp,
            letterSpacing = 1.6.sp, color = color,
        )
        Ink.Mono -> TextStyle(
            fontFamily = EbFonts.mono, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp, color = color,
            fontFeatureSettings = "tnum",
        )
        Ink.MonoSmall -> TextStyle(
            fontFamily = EbFonts.mono, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, color = color,
            fontFeatureSettings = "tnum",
        )
        // For commands people copy: sized so a 40-character permission name fits a card without wrapping.
        Ink.Code -> TextStyle(
            fontFamily = EbFonts.mono, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 16.sp, color = color,
        )
        Ink.Button -> TextStyle(
            fontFamily = EbFonts.sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp,
            letterSpacing = 0.1.sp, color = color,
        )
    }
}

/** The only text composable the UI uses. */
@Composable
fun EbText(
    text: String,
    ink: Ink = Ink.Body,
    color: Color = if (ink == Ink.Caption || ink == Ink.Eyebrow) Dusk.TextDim else Dusk.Text,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    caps: Boolean = ink == Ink.Eyebrow,
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
    color: Color = Dusk.Text,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Start,
) {
    BasicText(text = text, modifier = modifier, style = LocalTypography.current.style(ink, color).copy(textAlign = align))
}
