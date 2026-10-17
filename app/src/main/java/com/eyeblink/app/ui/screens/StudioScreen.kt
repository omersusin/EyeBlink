package com.eyeblink.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eyeblink.app.ui.kit.ButtonKind
import com.eyeblink.app.ui.kit.Chip
import com.eyeblink.app.ui.kit.CurveThumb
import com.eyeblink.app.ui.kit.P
import com.eyeblink.app.ui.kit.EbText
import com.eyeblink.app.ui.kit.Card
import com.eyeblink.app.ui.kit.Hairline
import com.eyeblink.app.ui.kit.Horizon
import com.eyeblink.app.ui.kit.Icon
import com.eyeblink.app.ui.kit.Ink
import com.eyeblink.app.ui.kit.LidSwitch
import com.eyeblink.app.ui.kit.PhoneFrame
import com.eyeblink.app.ui.kit.PillButton
import com.eyeblink.app.ui.kit.S
import com.eyeblink.app.ui.kit.ScreenColumn
import com.eyeblink.app.ui.kit.SectionHeader
import com.eyeblink.app.ui.kit.SettingRow
import com.eyeblink.app.ui.kit.TimelineEditor
import com.eyeblink.app.ui.kit.reveal
import com.eyeblink.app.ui.state.UiActions
import com.eyeblink.app.ui.state.UiState
import com.eyeblink.core.anim.BlinkSpec
import com.eyeblink.core.anim.EaseKind
import kotlin.math.roundToInt

private data class Preset(val id: String, val en: String, val tr: String, val spec: BlinkSpec)

private val presets = listOf(
    Preset("natural", "Natural", "Doğal", BlinkSpec()),
    Preset("soft", "Soft", "Yumuşak", BlinkSpec(closeMs = 190, holdMs = 120, openMs = 270, curvature = 0.5f, feather = 0.85f, darken = 0.5f)),
    Preset("snappy", "Snappy", "Çevik", BlinkSpec(closeMs = 90, holdMs = 70, openMs = 110, curvature = 0.2f, feather = 0.2f, darken = 0.7f)),
    Preset("drift", "Slow drift", "Yavaş süzülme", BlinkSpec(closeMs = 280, holdMs = 220, openMs = 400, curvature = 0.45f, feather = 0.9f, darken = 0.55f)),
)

private fun easeName(k: EaseKind, s: com.eyeblink.app.ui.i18n.Strings) = when (k) {
    EaseKind.LINEAR -> s.t("linear", "doğrusal")
    EaseKind.SINE_IN_OUT -> s.t("sine", "sinüs")
    EaseKind.QUAD_OUT -> s.t("quad out", "kare çıkış")
    EaseKind.CUBIC_IN -> s.t("cubic in", "kübik giriş")
    EaseKind.CUBIC_OUT -> s.t("cubic out", "kübik çıkış")
    EaseKind.CUBIC_IN_OUT -> s.t("cubic", "kübik")
    EaseKind.EXPO_OUT -> s.t("expo out", "üstel çıkış")
    EaseKind.BACK_OUT -> s.t("settle", "oturma")
    EaseKind.NATURAL_CLOSE -> s.t("natural ↓", "doğal ↓")
    EaseKind.NATURAL_OPEN -> s.t("natural ↑", "doğal ↑")
}

@Composable
fun StudioScreen(state: UiState, actions: UiActions, modifier: Modifier = Modifier) {
    val s = S
    val spec = state.settings.blink
    fun set(f: (BlinkSpec) -> BlinkSpec) = actions.update { it.copy(blink = f(it.blink).validated()) }

    ScreenColumn(modifier) {
        Box(Modifier.reveal(0)) { SectionHeader(s.t("Blink Studio", "Kırpma Stüdyosu"), s.t("Shape the blink", "Göz kırpmayı şekillendir")) }

        Column(Modifier.reveal(1).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.height(380.dp), contentAlignment = Alignment.Center) {
                PhoneFrame(spec, description = s.t("Live preview of the blink", "Göz kırpmanın canlı önizlemesi"))
            }
            Spacer(Modifier.height(12.dp))
            EbText(
                "${spec.totalMs} ms",
                Ink.Headline, align = TextAlign.Center,
            )
            EbText(s.t("tap the phone to blink it", "göz kırpmak için telefona dokun"), Ink.Eyebrow, caps = false)
        }

        Row(Modifier.reveal(2).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { p ->
                val selected = spec.closeMs == p.spec.closeMs && spec.holdMs == p.spec.holdMs && spec.openMs == p.spec.openMs && kotlin.math.abs(spec.curvature - p.spec.curvature) < 0.01f
                Chip(s.t(p.en, p.tr), selected, onClick = { set { cur -> p.spec.copy(reducedMotion = cur.reducedMotion, closeEase = cur.closeEase, openEase = cur.openEase) } })
            }
        }

        Card(Modifier.reveal(3).fillMaxWidth()) {
            SectionHeader(s.t("Timing", "Zamanlama"), null)
            Spacer(Modifier.height(14.dp))
            TimelineEditor(
                spec, { c, h, o -> set { it.copy(closeMs = c, holdMs = h, openMs = o) } },
                labels = Triple(s.t("close", "kapanma"), s.t("black", "siyah"), s.t("open", "açılma")),
            )
            Spacer(Modifier.height(10.dp))
            EbText(s.t("Drag the white handles. The black part is a pure #000000 hold: on OLED screens those pixels emit no light at all.", "Beyaz tutamaçları sürükle. Siyah bölüm saf #000000 beklemedir: OLED ekranlarda bu pikseller hiç ışık yaymaz."), Ink.Caption, color = P.inkFaint)
        }

        Card(Modifier.reveal(4).fillMaxWidth()) {
            SectionHeader(s.t("Curves", "Eğriler"), null)
            Spacer(Modifier.height(12.dp))
            EbText(s.t("Closing", "Kapanırken"), Ink.Eyebrow, color = P.sky.text)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EaseKind.entries.forEach { k -> CurveThumb(k, easeName(k, s), spec.closeEase == k, { set { it.copy(closeEase = k) } }, Modifier.width(92.dp)) }
            }
            Spacer(Modifier.height(14.dp))
            EbText(s.t("Opening", "Açılırken"), Ink.Eyebrow, color = P.sun.text)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EaseKind.entries.forEach { k -> CurveThumb(k, easeName(k, s), spec.openEase == k, { set { it.copy(openEase = k) } }, Modifier.width(92.dp)) }
            }
        }

        Card(Modifier.reveal(5).fillMaxWidth()) {
            SectionHeader(s.t("Look", "Görünüm"), null)
            Spacer(Modifier.height(6.dp))
            LookSlider(s.t("Eyelid curve", "Göz kapağı kavisi"), spec.curvature, s.t("How strongly the lids bow into an almond", "Kapakların badem şeklini ne kadar alacağı")) { v -> set { it.copy(curvature = v) } }
            Hairline()
            LookSlider(s.t("Soft edge", "Yumuşak kenar"), spec.feather, null) { v -> set { it.copy(feather = v) } }
            Hairline()
            LookSlider(s.t("Darkening", "Kararma"), spec.darken, s.t("Subtle dimming that builds while the lids close", "Kapaklar kapanırken artan hafif kararma")) { v -> set { it.copy(darken = v) } }
            Hairline()
            LookSlider(s.t("Rim light", "Kenar ışığı"), spec.rimLight, s.t("A warm line along the lid. Lit pixels weaken the OLED effect, so it is off by default.", "Kapak boyunca sıcak bir çizgi. Yanan pikseller OLED etkisini azaltır, bu yüzden varsayılan kapalı.")) { v -> set { it.copy(rimLight = v) } }
        }

        Card(Modifier.reveal(6).fillMaxWidth()) {
            SettingRow(
                s.t("Reduced motion", "Azaltılmış hareket"),
                subtitle = s.t("Fade to black instead of sweeping lids. Also applied automatically when Android's animations are off.", "Süpüren kapaklar yerine siyaha geçiş. Android animasyonları kapalıysa otomatik uygulanır."),
            ) { LidSwitch(spec.reducedMotion, { v -> set { it.copy(reducedMotion = v) } }, description = s.t("Reduced motion", "Azaltılmış hareket")) }
        }

        PillButton(
            s.t("Reset to natural", "Doğala sıfırla"), { set { BlinkSpec() } },
            kind = ButtonKind.Soft, icon = Icon.Refresh, modifier = Modifier.reveal(7).fillMaxWidth(),
        )
        EbText(
            s.t("On LCD screens the animation looks the same, but the backlight stays on - EyeBlink never claims otherwise. It is a short visual break, not a replacement for real blinking.", "LCD ekranlarda animasyon aynı görünür ama arka ışık açık kalır; EyeBlink aksini iddia etmez. Kısa bir görsel moladır, gerçek göz kırpmanın yerini tutmaz."),
            Ink.Caption, color = P.inkFaint, modifier = Modifier.reveal(8).padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun LookSlider(title: String, value: Float, hint: String?, onChange: (Float) -> Unit) {
    Column(Modifier.padding(top = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            EbText(title, Ink.BodyStrong, modifier = Modifier.weight(1f))
            EbText("${(value * 100).roundToInt()}%", Ink.NumSmall, color = P.sky.text, caps = false)
        }
        if (hint != null) EbText(hint, Ink.Caption, color = P.inkSoft)
        Horizon(value, onChange, steps = 20, description = title)
    }
}
