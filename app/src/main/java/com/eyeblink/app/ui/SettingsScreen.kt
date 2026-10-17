package com.eyeblink.app.ui

import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
fun SettingsScreen(config: BlinkConfig) {
    var cfg by remember { mutableStateOf(config) }
    val scope = rememberCoroutineScope()
    val animator = remember(cfg) { BlinkAnimator(cfg) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("EyeBlink", style = TextStyle(color = EyeBlinkColors.text, fontSize = 28.sp, fontWeight = FontWeight.Bold))
        Text("Smart eye comfort", style = TextStyle(color = EyeBlinkColors.textDim, fontSize = 14.sp))
        // live preview
        Box(
            Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp))
                .background(EyeBlinkColors.surface)
        ) {
            BlinkOverlay(animator, cfg)
            Box(Modifier.align(Alignment.Center)) {
                PillButton("Preview blink") { scope.launch { animator.blink() } }
            }
        }
        // toggles
        ToggleRow("Enabled", cfg.enabled) { cfg = cfg.copy(enabled = it) }
        ToggleRow("Reduced motion", cfg.reducedMotion) { cfg = cfg.copy(reducedMotion = it) }
        // timings
        NumberRow("Close (ms)", cfg.closeMs) { cfg = cfg.copy(closeMs = it) }
        NumberRow("Hold (ms)", cfg.holdMs) { cfg = cfg.copy(holdMs = it) }
        NumberRow("Open (ms)", cfg.openMs) { cfg = cfg.copy(openMs = it) }
        Spacer(Modifier.height(8.dp))
        Text("Total ≈ ${cfg.totalMs} ms", style = TextStyle(color = EyeBlinkColors.textDim, fontSize = 12.sp))
    }
}

@Composable
private fun Text(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    androidx.compose.foundation.text.BasicText(text = text, style = style, modifier = modifier)
}

@Composable
private fun PillButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp)).background(EyeBlinkColors.accent)
            .clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.text.BasicText(label, style = TextStyle(color = Color.White, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.text.BasicText(label, style = TextStyle(color = EyeBlinkColors.text), modifier = Modifier.weight(1f))
        Box(
            Modifier.width(52.dp).height(28.dp).clip(RoundedCornerShape(999.dp))
                .background(if (value) EyeBlinkColors.accent else EyeBlinkColors.surface2)
                .clickable { onChange(!value) },
            contentAlignment = if (value) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(Modifier.padding(4.dp).width(20.dp).height(20.dp).clip(RoundedCornerShape(999.dp)).background(Color.White))
        }
    }
}

@Composable
private fun NumberRow(label: String, value: Long, onChange: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.text.BasicText(label, style = TextStyle(color = EyeBlinkColors.text), modifier = Modifier.weight(1f))
        PillButton("-") { onChange((value - 10).coerceAtLeast(50)) }
        Spacer(Modifier.width(8.dp))
        androidx.compose.foundation.text.BasicText("$value", style = TextStyle(color = EyeBlinkColors.text, fontWeight = FontWeight.Bold))
        Spacer(Modifier.width(8.dp))
        PillButton("+") { onChange(value + 10) }
    }
}
EOF
echo done