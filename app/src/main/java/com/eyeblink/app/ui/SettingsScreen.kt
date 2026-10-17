package com.eyeblink.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eyeblink.app.animation.BlinkAnimator
import com.eyeblink.app.core.BlinkConfig
import com.eyeblink.app.ui.theme.EyeBlinkColors
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    config: BlinkConfig,
    onConfigChange: (BlinkConfig) -> Unit,
    serviceRunning: Boolean,
    onToggleService: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val animator = remember { BlinkAnimator { config } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BasicText(
            "EyeBlink",
            style = TextStyle(color = EyeBlinkColors.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        )
        BasicText(
            "Smart eye comfort",
            style = TextStyle(color = EyeBlinkColors.textDim, fontSize = 14.sp)
        )

        // live preview
        Box(
            Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp))
                .background(EyeBlinkColors.surface)
        ) {
            BlinkOverlay(animator, config)
            Box(Modifier.align(Alignment.Center)) {
                PillButton(if (config.enabled) "Preview blink" else "Disabled") {
                    if (config.enabled) scope.launch { animator.blink() }
                }
            }
        }

        PillButton(if (serviceRunning) "Stop EyeBlink" else "Start EyeBlink", onToggleService)

        ToggleRow("Enabled", config.enabled) { onConfigChange(config.copy(enabled = it)) }
        ToggleRow("Reduced motion", config.reducedMotion) { onConfigChange(config.copy(reducedMotion = it)) }

        Spacer(Modifier.height(4.dp))
        BasicText("Timing", style = TextStyle(color = EyeBlinkColors.textDim, fontSize = 13.sp))

        NumberRow("Close (ms)", config.closeMs) { onConfigChange(config.copy(closeMs = it)) }
        NumberRow("Hold (ms)", config.holdMs) { onConfigChange(config.copy(holdMs = it)) }
        NumberRow("Open (ms)", config.openMs) { onConfigChange(config.copy(openMs = it)) }

        Spacer(Modifier.height(4.dp))
        BasicText("Appearance", style = TextStyle(color = EyeBlinkColors.textDim, fontSize = 13.sp))

        SliderRow("Eyelid curve", config.curve) {
            onConfigChange(config.copy(curve = it))
        }
        SliderRow("Darken", config.darkenStrength) {
            onConfigChange(config.copy(darkenStrength = it))
        }

        Spacer(Modifier.height(8.dp))
        BasicText(
            "Total = ${config.totalMs} ms",
            style = TextStyle(color = EyeBlinkColors.textDim, fontSize = 12.sp)
        )
        BasicText(
            "Not a medical device. This inserts visual breaks; it does not treat dry eye.",
            style = TextStyle(color = EyeBlinkColors.textDim, fontSize = 11.sp)
        )
    }
}

@Composable
private fun PillButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp)).background(EyeBlinkColors.accent)
            .clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            label,
            style = TextStyle(color = Color.White, fontWeight = FontWeight.SemiBold)
        )
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            label,
            style = TextStyle(color = EyeBlinkColors.text),
            modifier = Modifier.weight(1f)
        )
        Box(
            Modifier.width(52.dp).height(28.dp).clip(RoundedCornerShape(999.dp))
                .background(if (value) EyeBlinkColors.accent else EyeBlinkColors.surface2)
                .clickable { onChange(!value) },
            contentAlignment = if (value) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                Modifier.padding(4.dp).width(20.dp).height(20.dp)
                    .clip(RoundedCornerShape(999.dp)).background(Color.White)
            )
        }
    }
}

@Composable
private fun NumberRow(label: String, value: Long, onChange: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            label,
            style = TextStyle(color = EyeBlinkColors.text),
            modifier = Modifier.weight(1f)
        )
        PillButton("-") { onChange((value - 10).coerceIn(MIN_MS, MAX_MS)) }
        Spacer(Modifier.width(8.dp))
        BasicText("$value", style = TextStyle(color = EyeBlinkColors.text, fontWeight = FontWeight.Bold))
        Spacer(Modifier.width(8.dp))
        PillButton("+") { onChange((value + 10).coerceIn(MIN_MS, MAX_MS)) }
    }
}

/** Minimal 0..1 stepper: coarse then fine, so both ends are reachable without a slider dep. */
@Composable
private fun SliderRow(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            label,
            style = TextStyle(color = EyeBlinkColors.text),
            modifier = Modifier.weight(1f)
        )
        PillButton("-") { onChange((value - 0.05f).coerceIn(0f, 1f)) }
        Spacer(Modifier.width(8.dp))
        BasicText(
            "${(value * 100).toInt()}%",
            style = TextStyle(color = EyeBlinkColors.text, fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.width(8.dp))
        PillButton("+") { onChange((value + 0.05f).coerceIn(0f, 1f)) }
    }
}

private const val MIN_MS = 50L
private const val MAX_MS = 2000L