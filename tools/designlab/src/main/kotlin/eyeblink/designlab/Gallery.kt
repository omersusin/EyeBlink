package eyeblink.designlab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eyeblink.app.ui.kit.Badge
import com.eyeblink.app.ui.kit.ButtonKind
import com.eyeblink.app.ui.kit.Card
import com.eyeblink.app.ui.kit.Chip
import com.eyeblink.app.ui.kit.CurveThumb
import com.eyeblink.app.ui.kit.Dock
import com.eyeblink.app.ui.kit.DockItem
import com.eyeblink.app.ui.kit.EbText
import com.eyeblink.app.ui.kit.Flow
import com.eyeblink.app.ui.kit.Horizon
import com.eyeblink.app.ui.kit.Ico
import com.eyeblink.app.ui.kit.Icon
import com.eyeblink.app.ui.kit.IconBadge
import com.eyeblink.app.ui.kit.Ink
import com.eyeblink.app.ui.kit.LidSwitch
import com.eyeblink.app.ui.kit.Meter
import com.eyeblink.app.ui.kit.MiniBars
import com.eyeblink.app.ui.kit.P
import com.eyeblink.app.ui.kit.PillButton
import com.eyeblink.app.ui.kit.PulseDot
import com.eyeblink.app.ui.kit.RoundIconButton
import com.eyeblink.app.ui.kit.SectionHeader
import com.eyeblink.app.ui.kit.SettingRow
import com.eyeblink.app.ui.kit.Stepper
import com.eyeblink.app.ui.kit.Tide
import com.eyeblink.app.ui.kit.TimelineEditor
import com.eyeblink.app.ui.kit.Tone
import com.eyeblink.core.anim.BlinkSpec
import com.eyeblink.core.anim.EaseKind

/** Every component of the kit on one page, for judging the look at a glance. */
@Composable
fun KitGallery() {
    var on by remember { mutableStateOf(true) }
    var v by remember { mutableStateOf(0.55f) }
    var tide by remember { mutableStateOf(1) }
    var spec by remember { mutableStateOf(BlinkSpec()) }
    val p = P
    com.eyeblink.app.ui.kit.Backdrop {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionHeader("Kit", "Components")
            Card {
                SettingRow("Lid switch", subtitle = "A switch that is a small eye") { LidSwitch(on, { on = it }) }
                SettingRow("Off state") { LidSwitch(false, {}) }
                Horizon(v, { v = it }, steps = 10, description = "demo")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Start EyeBlink", {}, icon = Icon.Eye)
                PillButton("Rest", {}, kind = ButtonKind.Soft)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Ghost", {}, kind = ButtonKind.Ghost, compact = true)
                PillButton("Stop", {}, kind = ButtonKind.Danger, compact = true)
                RoundIconButton(Icon.Moon, "moon", {})
                RoundIconButton(Icon.Bolt, "bolt", {}, hue = p.sun)
            }
            Tide(listOf("Gentle", "Balanced", "Active"), tide, { tide = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Instagram", true, {}, icon = Icon.Eye)
                Chip("TikTok", false, {})
                Chip("YouTube", false, {})
            }
            Flow(Modifier.fillMaxWidth()) {
                Badge("Verified", Tone.Ok); Badge("Untested", Tone.Quiet); Badge("Failing", Tone.Bad); Badge("Retest", Tone.Warn)
                PulseDot(p.coral.fill)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Card(Modifier.weight(1f), tint = p.sky) { EbText("0:38", Ink.Lead); EbText("Next blink", Ink.Caption) }
                Card(Modifier.weight(1f), tint = p.mint) { EbText("82%", Ink.Lead); EbText("Rested", Ink.Caption) }
                Card(Modifier.weight(1f), tint = p.coral) { EbText("3", Ink.Lead); EbText("Skipped", Ink.Caption) }
            }
            Card {
                Meter(0.62f)
                Spacer(Modifier.height(12.dp))
                MiniBars(listOf(12f, 18f, 9f, 22f, 31f, 17f, 26f), listOf("M", "T", "W", "T", "F", "S", "S"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CurveThumb(EaseKind.NATURAL_CLOSE, "natural", true, {})
                CurveThumb(EaseKind.CUBIC_IN_OUT, "cubic", false, {})
                CurveThumb(EaseKind.EXPO_OUT, "expo", false, {})
                CurveThumb(EaseKind.BACK_OUT, "back", false, {})
            }
            Card { TimelineEditor(spec, { c, h, o -> spec = spec.copy(closeMs = c, holdMs = h, openMs = o) }, playhead = 210f) }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconBadge(Icon.Eye, p.sky); IconBadge(Icon.Moon, p.grape); IconBadge(Icon.Bolt, p.sun); IconBadge(Icon.Pulse, p.mint); IconBadge(Icon.Chart, p.coral)
                Stepper("45 s", {}, {})
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (i in listOf(Icon.Eye, Icon.EyeClosed, Icon.Sliders, Icon.Pulse, Icon.Grid, Icon.Dots, Icon.Moon, Icon.Bolt, Icon.Shield, Icon.Chart)) Ico(i, size = 24.dp, color = p.ink)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (i in listOf(Icon.Camera, Icon.Check, Icon.Play, Icon.Sun, Icon.Clock, Icon.Lock, Icon.Info, Icon.Warning, Icon.Refresh, Icon.Search)) Ico(i, size = 24.dp, color = p.sky.text)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (i in listOf(Icon.Globe, Icon.Terminal, Icon.Link, Icon.Sparkle, Icon.Battery, Icon.Hand, Icon.Trash, Icon.Apps, Icon.Signal, Icon.Film, Icon.Book, Icon.Wave)) Ico(i, size = 24.dp, color = p.coral.text)
            }
            Dock(listOf(DockItem(Icon.Eye, "Today"), DockItem(Icon.Sliders, "Blink"), DockItem(Icon.Pulse, "Rhythm"), DockItem(Icon.Grid, "Apps"), DockItem(Icon.Dots, "More")), 1, {})
        }
    }
}
