package com.eyeblink.app.ui.rest

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eyeblink.app.ui.i18n.fmtClock
import com.eyeblink.app.ui.eye.EyeMark
import com.eyeblink.app.ui.kit.LocalPalette
import com.eyeblink.app.ui.kit.Moonlit
import com.eyeblink.app.ui.kit.OnBlack
import com.eyeblink.app.ui.kit.P
import com.eyeblink.app.ui.kit.EbText
import com.eyeblink.app.ui.kit.Ink
import com.eyeblink.app.ui.kit.LocalInsets
import com.eyeblink.app.ui.kit.LocalReducedMotion
import com.eyeblink.app.ui.kit.S
import com.eyeblink.app.ui.kit.rememberClockMs
import com.eyeblink.core.model.ConfirmMode
import com.eyeblink.core.model.RestStyle
import com.eyeblink.core.rest.RestPhase
import com.eyeblink.core.rest.RestView
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sin

/**
 * The long rest, full screen. It is the same composable in the system overlay and in the design lab.
 *
 * Both styles are mostly black on purpose: on an OLED panel the empty parts emit nothing, and
 * a resting eye does not want a bright screen to look at. [RestStyle.BLACKOUT] is pure `#000000`
 * apart from a dim countdown; [RestStyle.DISTANCE_FOCUS] adds a slow focus pulse that travels
 * outward - the eye is invited to follow it past the screen and out into the room.
 *
 * Skipping is always one tap away. Confirmation is never a test: a single tap, or a single
 * blink when the camera is tracking, and the rest ends by itself after a timeout regardless.
 *
 * The scene always wears the Moonlit colours, whatever theme the app is in: it sits on black.
 */
@Composable
fun RestScene(
    view: RestView,
    style: RestStyle,
    onSkip: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalPalette provides Moonlit) { RestSceneContent(view, style, onSkip, onTap, modifier) }
}

@Composable
private fun RestSceneContent(
    view: RestView,
    style: RestStyle,
    onSkip: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier,
) {
    val reduced = LocalReducedMotion.current
    val enter = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reduced) enter.animateTo(1f, tween(RestSceneTiming.ENTER_MS)) }
    val leaving by animateFloatAsState(if (view.phase == RestPhase.DONE) 0f else 1f, tween(if (reduced) 1 else 600), label = "leave")

    val awaiting = view.awaiting
    Box(
        modifier.fillMaxSize().background(OnBlack.Void).alpha(enter.value * leaving)
            .let { m ->
                // Anywhere on the screen confirms once the rest is over; before that a stray touch does nothing.
                if (awaiting != null) m.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onTap) else m
            },
    ) {
        when (style) {
            RestStyle.BLACKOUT -> Blackout(view)
            RestStyle.DISTANCE_FOCUS -> DistanceFocus(view)
        }
        SkipButton(onSkip, Modifier.align(Alignment.BottomCenter))
    }
}

/** Timings other code (the overlay host) may need to agree with. */
object RestSceneTiming {
    const val ENTER_MS = 700
}

@Composable
private fun SkipButton(onSkip: () -> Unit, modifier: Modifier) {
    val s = S
    val inset = LocalInsets.current.bottom
    Box(
        modifier.padding(bottom = 26.dp + inset)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onSkip)
            .padding(horizontal = 28.dp, vertical = 14.dp),
    ) { EbText(s.t("Skip", "Atla"), Ink.BodyStrong, color = P.inkFaint) }
}

// ── Blackout ────────────────────────────────────────────────────

@Composable
private fun Blackout(view: RestView) {
    val reduced = LocalReducedMotion.current
    val clock by rememberClockMs(running = !reduced)
    val awaiting = view.awaiting
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            awaiting != null -> {
                // A slow breath of dim yellow, so the screen is not mistaken for being off.
                val breath = if (reduced) 0.6f else 0.45f + 0.25f * (0.5f + 0.5f * sin(clock / 1_300f * PI.toFloat()))
                EbText(confirmText(awaiting), Ink.Title, color = P.sun.fill, modifier = Modifier.alpha(breath).padding(horizontal = 36.dp), align = TextAlign.Center)
            }
            view.phase == RestPhase.RESTING -> EbText(
                fmtClock(view.remainingMs), Ink.Num, color = P.inkFaint,
                modifier = Modifier.alpha(0.55f),
            )
            else -> Unit
        }
    }
}

// ── Distance focus ──────────────────────────────────────────────

@Composable
private fun DistanceFocus(view: RestView) {
    val s = S
    val reduced = LocalReducedMotion.current
    val clock by rememberClockMs(running = !reduced)
    val inset = LocalInsets.current
    val resting = view.phase == RestPhase.RESTING || view.phase == RestPhase.SETTLING
    val seconds = ceil(view.remainingMs / 1000f).toInt().coerceAtLeast(0)

    Column(
        Modifier.fillMaxSize().padding(top = 28.dp + inset.top, bottom = 96.dp + inset.bottom),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.7f))
        EbText(
            if (resting) s.t("Look far away", "Uzağa bak") else s.t("Welcome back", "Hoş geldin"),
            Ink.Lead, align = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        EbText(
            if (resting) s.t("Find something about 6 m (20 ft) away and let your eyes soften.", "6 m (20 ft) kadar uzaktaki bir şeye bak ve gözlerinin gevşemesine izin ver.")
            else confirmText(view.awaiting ?: ConfirmMode.AUTO),
            Ink.Body, color = P.inkSoft, align = TextAlign.Center, modifier = Modifier.width(290.dp),
        )
        Spacer(Modifier.height(36.dp))

        val p = P
        Box(Modifier.fillMaxWidth().height(380.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val r = 104.dp.toPx()
                val stroke = 10.dp.toPx()

                // Focus pulses travelling outward and fading: "into the distance".
                if (!reduced) {
                    for (k in 0 until 3) {
                        val t = ((clock / 4_600f) + k / 3f) % 1f
                        val rr = r * (1.12f + 1.45f * t)
                        val a = (1f - t) * (1f - t) * 0.5f
                        drawCircle(p.sky.fill.copy(alpha = a), radius = rr, center = c, style = Stroke(2.dp.toPx()))
                    }
                }

                drawCircle(p.line, radius = r, center = c, style = Stroke(stroke))
                val sweep = 360f * view.progress.coerceIn(0f, 1f)
                if (sweep > 0.5f) {
                    rotate(-90f, c) {
                        drawArc(
                            color = p.sun.fill,
                            startAngle = 0f, sweepAngle = sweep, useCenter = false,
                            topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2f, r * 2f),
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    }
                    // The head of the arc: a small white bead.
                    val ang = (-90f + sweep) * (PI.toFloat() / 180f)
                    val head = Offset(c.x + r * kotlin.math.cos(ang), c.y + r * kotlin.math.sin(ang))
                    drawCircle(p.ink, radius = 3.4.dp.toPx(), center = head)
                }
            }
            if (resting) {
                EbText(seconds.toString(), Ink.Display, align = TextAlign.Center, maxLines = 1)
            } else {
                EyeMark(size = 84.dp)
            }
        }

        Spacer(Modifier.weight(1f))
        EbText(
            if (resting) s.t("Blink naturally. There is nothing to do.", "Doğal şekilde kırp. Yapacak bir şey yok.") else "",
            Ink.Caption, color = P.inkFaint, align = TextAlign.Center,
        )
    }
}

@Composable
private fun confirmText(mode: ConfirmMode): String {
    val s = S
    return when (mode) {
        ConfirmMode.BLINK -> s.t("Blink once to continue, or tap anywhere.", "Devam etmek için bir kez kırp ya da herhangi bir yere dokun.")
        else -> s.t("Tap anywhere to continue.", "Devam etmek için herhangi bir yere dokun.")
    }
}
