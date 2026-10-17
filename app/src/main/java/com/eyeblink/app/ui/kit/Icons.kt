package com.eyeblink.app.ui.kit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * EyeBlink's own icon set: hand-drawn 24x24 line glyphs, round caps, one stroke weight.
 * Each is an SVG path string, so the same shapes can be reused for vector assets.
 * `dots` are filled circles given as `cx,cy,r` triples.
 */
enum class Icon(val paths: List<String>, val dots: List<Triple<Float, Float, Float>> = emptyList(), val filled: Boolean = false) {
    Eye(
        listOf("M2.5 12C5 7.4 8.4 5.2 12 5.2C15.6 5.2 19 7.4 21.5 12C19 16.6 15.6 18.8 12 18.8C8.4 18.8 5 16.6 2.5 12Z"),
        dots = listOf(Triple(12f, 12f, 2.9f)),
    ),
    EyeClosed(
        listOf(
            "M3 11.2C5.4 14.6 8.6 16.2 12 16.2C15.4 16.2 18.6 14.6 21 11.2",
            "M7.2 14.8L5.6 17.2", "M12 16.2V19", "M16.8 14.8L18.4 17.2",
        ),
    ),
    Sliders(
        listOf("M4 7H20", "M4 12H20", "M4 17H20"),
        dots = listOf(Triple(9f, 7f, 2.3f), Triple(15f, 12f, 2.3f), Triple(8f, 17f, 2.3f)),
    ),
    Pulse(listOf("M2.5 12.5H6.2L8.6 6.2L12.2 18.4L14.8 10.6L16.6 12.5H21.5")),
    Grid(
        listOf(
            "M5 5H10.2V10.2H5Z", "M13.8 5H19V10.2H13.8Z", "M5 13.8H10.2V19H5Z", "M13.8 13.8H19V19H13.8Z",
        ),
    ),
    Dots(listOf(), dots = listOf(Triple(5.5f, 12f, 1.7f), Triple(12f, 12f, 1.7f), Triple(18.5f, 12f, 1.7f))),
    Moon(listOf("M19.5 14.6A8 8 0 0 1 9.4 4.5A8 8 0 1 0 19.5 14.6Z")),
    Bolt(listOf("M13.2 3L5.5 13.4H11.6L10.8 21L18.5 10.6H12.4L13.2 3Z")),
    Shield(listOf("M12 3L19 5.8V11.4C19 15.6 16.2 19 12 20.8C7.8 19 5 15.6 5 11.4V5.8L12 3Z", "M9 12L11.2 14.2L15.2 9.8")),
    Chart(listOf("M5 19V11", "M10 19V5", "M15 19V9", "M20 19V14")),
    Camera(
        listOf("M4 8.2H7.4L9 6H15L16.6 8.2H20V18H4V8.2Z"),
        dots = listOf(Triple(12f, 13f, 3f)),
    ),
    Check(listOf("M5 12.6L10 17.4L19 7")),
    ChevronRight(listOf("M9.5 5.5L16 12L9.5 18.5")),
    ChevronLeft(listOf("M14.5 5.5L8 12L14.5 18.5")),
    ChevronDown(listOf("M5.5 9.5L12 16L18.5 9.5")),
    Close(listOf("M6 6L18 18", "M18 6L6 18")),
    Plus(listOf("M12 5V19", "M5 12H19")),
    Minus(listOf("M5 12H19")),
    Play(listOf("M8 5.5V18.5L18.5 12L8 5.5Z")),
    Pause(listOf("M8.5 5.5V18.5", "M15.5 5.5V18.5")),
    Sun(
        listOf("M12 3V5", "M12 19V21", "M3 12H5", "M19 12H21", "M5.6 5.6L7 7", "M17 17L18.4 18.4", "M18.4 5.6L17 7", "M7 17L5.6 18.4"),
        dots = emptyList(),
    ),
    Clock(listOf("M12 7.2V12L15.2 13.8"), dots = emptyList()),
    Lock(listOf("M6.5 10.5H17.5V19.5H6.5Z", "M8.7 10.5V8A3.3 3.3 0 0 1 15.3 8V10.5")),
    Info(listOf("M12 11V16.2", "M12 7.6V7.7")),
    Warning(listOf("M12 4L21 19.5H3L12 4Z", "M12 10V14", "M12 16.8V16.9")),
    Refresh(listOf("M19.5 12A7.5 7.5 0 1 1 17.3 6.7", "M19.6 4.6V8.4H15.8")),
    Search(listOf("M16.5 16.5L20.5 20.5"), dots = emptyList()),
    Globe(listOf("M3.5 12H20.5", "M12 3.5C14.4 6 15.4 9 15.4 12C15.4 15 14.4 18 12 20.5C9.6 18 8.6 15 8.6 12C8.6 9 9.6 6 12 3.5Z")),
    Terminal(listOf("M5 7.5L10 12L5 16.5", "M12.5 17H19")),
    Link(listOf("M10.2 13.8A3.6 3.6 0 0 0 15.3 13.8L18.4 10.7A3.6 3.6 0 0 0 13.3 5.6L12.4 6.5", "M13.8 10.2A3.6 3.6 0 0 0 8.7 10.2L5.6 13.3A3.6 3.6 0 0 0 10.7 18.4L11.6 17.5")),
    Sparkle(listOf("M12 3.5C12.6 8 14 9.4 18.5 10C14 10.6 12.6 12 12 16.5C11.4 12 10 10.6 5.5 10C10 9.4 11.4 8 12 3.5Z", "M18.6 15.2C18.8 16.8 19.2 17.2 20.8 17.4C19.2 17.6 18.8 18 18.6 19.6C18.4 18 18 17.6 16.4 17.4C18 17.2 18.4 16.8 18.6 15.2Z")),
    Battery(listOf("M4.5 8.5H17.5V15.5H4.5Z", "M20 10.5V13.5", "M7 11V13", "M9.7 11V13")),
    Hand(listOf("M8.5 12V6.2A1.4 1.4 0 0 1 11.3 6.2V11", "M11.3 10.2V4.8A1.4 1.4 0 0 1 14.1 4.8V11", "M14.1 10.4V6.2A1.4 1.4 0 0 1 16.9 6.2V13.4C16.9 17.2 14.6 19.5 11.8 19.5C9.6 19.5 8.2 18.2 6.8 15.8L5.2 12.6A1.3 1.3 0 0 1 7.5 11.4L8.5 13")),
    Trash(listOf("M5 7.5H19", "M9.5 7.5V5H14.5V7.5", "M7 7.5L8 19H16L17 7.5", "M10.3 11V15.5", "M13.7 11V15.5")),
    Apps(listOf("M12 4.5L19 8.2V15.8L12 19.5L5 15.8V8.2L12 4.5Z", "M12 12V19.5", "M5 8.2L12 12L19 8.2")),
    Signal(listOf("M5 15.5A10 10 0 0 1 19 15.5", "M8 17.5A6 6 0 0 1 16 17.5", "M11 19.5A2 2 0 0 1 13 19.5")),
    Swipe(listOf("M12 19V6", "M7.5 10.5L12 6L16.5 10.5")),
    Film(listOf("M4.5 5.5H19.5V18.5H4.5Z", "M9.5 5.5V18.5", "M14.5 5.5V18.5", "M4.5 10H9.5", "M4.5 14H9.5", "M14.5 10H19.5", "M14.5 14H19.5")),
    Book(listOf("M12 6.5C10 5 7.4 4.6 4.5 5V18C7.4 17.6 10 18 12 19.5C14 18 16.6 17.6 19.5 18V5C16.6 4.6 14 5 12 6.5Z", "M12 6.5V19.5")),
    Wave(listOf("M2.5 12C4 8 5.5 8 7 12S10 16 11.5 12S14.5 8 16 12S19 16 21.5 12")),
}

/** Draws [icon] in a [size] box. Strokes scale with the size so every icon keeps the same optical weight. */
@Composable
fun Ico(
    icon: Icon,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    color: Color = Dusk.Text,
    weight: Float = 1.75f,
    description: String? = null,
) {
    val parsed = remember(icon) {
        icon.paths.map { PathParser().parsePathString(it).toPath() }
    }
    val semantic = if (description != null) Modifier.semantics { contentDescription = description } else Modifier
    Canvas(modifier.then(semantic).size(size)) {
        val s = this.size.width / 24f
        scale(s, s, pivot = Offset.Zero) {
            val stroke = Stroke(width = weight, cap = StrokeCap.Round, join = StrokeJoin.Round)
            parsed.forEach { drawPath(it, color, style = stroke) }
            if (icon == Icon.Search) {
                drawCircle(color, radius = 6f, center = Offset(11f, 11f), style = stroke)
            }
            if (icon == Icon.Sun) drawCircle(color, radius = 3.6f, center = Offset(12f, 12f), style = stroke)
            if (icon == Icon.Clock) drawCircle(color, radius = 8.5f, center = Offset(12f, 12f), style = stroke)
            if (icon == Icon.Info || icon == Icon.Warning) {
                // the "dot" of i and ! is a tiny stroked segment already; nothing more to add
            }
            icon.dots.forEach { (cx, cy, r) ->
                val filledDot = icon == Icon.Dots || icon == Icon.Sliders
                if (filledDot) drawCircle(color, radius = r, center = Offset(cx, cy), style = Fill)
                else drawCircle(color, radius = r, center = Offset(cx, cy), style = stroke)
            }
            if (icon == Icon.Eye) {
                drawCircle(color, radius = 1.0f, center = Offset(12f, 12f), style = Fill)
            }
        }
    }
}
