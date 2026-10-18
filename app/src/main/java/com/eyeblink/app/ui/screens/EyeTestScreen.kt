package com.eyeblink.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eyeblink.app.ui.kit.Badge
import com.eyeblink.app.ui.kit.ButtonKind
import com.eyeblink.app.ui.kit.Card
import com.eyeblink.app.ui.kit.EbText
import com.eyeblink.app.ui.kit.Flow
import com.eyeblink.app.ui.kit.Icon
import com.eyeblink.app.ui.kit.Ink
import com.eyeblink.app.ui.kit.Meter
import com.eyeblink.app.ui.kit.P
import com.eyeblink.app.ui.kit.PillButton
import com.eyeblink.app.ui.kit.PulseDot
import com.eyeblink.app.ui.kit.S
import com.eyeblink.app.ui.kit.Tide
import com.eyeblink.app.ui.kit.Tone
import com.eyeblink.app.ui.kit.reveal
import com.eyeblink.app.ui.state.EyeTestUi
import com.eyeblink.app.ui.state.PermissionKind
import com.eyeblink.app.ui.state.UiActions
import com.eyeblink.app.ui.state.UiState
import com.eyeblink.core.model.EyeSensitivity
import kotlin.math.roundToInt

/**
 * "Try your blinks": the camera engine as it is configured right now, shown live. Nothing is recorded; the page
 * exists so that a person can see that EyeBlink sees *their* eyes - however wide they open - and tune the
 * sensitivity by watching it, instead of through a three-minute calibration.
 */
@Composable
fun EyeTestScreen(state: UiState, actions: UiActions, onBack: () -> Unit) {
    val s = S
    val t = state.eyeTest
    // However the page is left (its back arrow, the system's back), the camera goes off with it.
    DisposableEffect(Unit) { onDispose { actions.stopEyeTest() } }
    PageColumn(s.t("Blink test", "Kırpma testi"), s.t("See what EyeBlink sees", "EyeBlink'in gördüğünü gör"), onBack = {
        actions.stopEyeTest()
        onBack()
    }) {
        if (t.running) Live(state, actions) else Intro(state, actions)
    }
}

@Composable
private fun Intro(state: UiState, actions: UiActions) {
    val s = S
    val t = state.eyeTest
    EbText(
        s.t(
            "Look at the screen and blink the way you normally do. The eye on this page moves as your eyelids do, and a counter goes up for every blink EyeBlink sees. Nothing is recorded or sent.",
            "Ekrana bak ve her zamanki gibi göz kırp. Bu sayfadaki göz, göz kapaklarınla birlikte hareket eder; EyeBlink'in gördüğü her kırpmada bir sayaç artar. Hiçbir şey kaydedilmez ya da gönderilmez.",
        ),
        Ink.Body, modifier = Modifier.reveal(1),
    )
    if (t.cameraDenied && PermissionKind.CAMERA !in state.perms.granted) {
        ProblemCard(
            s.t("Camera access was not allowed", "Kamera izni verilmedi"),
            s.t("The test needs the camera to see your eyelids. Allow it, then press Begin again.", "Test, göz kapaklarını görmek için kameraya ihtiyaç duyar. İzin ver, sonra Başla'ya yeniden bas."),
            Modifier.reveal(2),
        )
    }
    t.error?.let { problem ->
        ProblemCard(
            s.t("The camera could not start", "Kamera başlatılamadı"), problem, Modifier.reveal(2),
            hint = s.t("If this keeps happening, copy the report from More → Diagnostics and send it with this message.", "Bu sürerse Diğer → Tanılama'daki raporu kopyalayıp bu mesajla birlikte gönder."),
        )
    }
    Column(Modifier.reveal(3), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PillButton(s.t("Begin", "Başla"), { actions.startEyeTest() }, icon = Icon.Eye, modifier = Modifier.fillMaxWidth())
    }
    EbText(
        if (state.personal.thresholds.fitted) s.t("Using the detector fitted to you by your calibration.", "Kalibrasyonunun sana göre ayarladığı algılayıcı kullanılıyor.")
        else s.t("You have not calibrated yet, so this uses the general settings: every eye is still measured against its own open level.", "Henüz kalibre etmedin; bu yüzden genel ayarlar kullanılıyor: yine de her göz kendi açık hâline göre ölçülür."),
        Ink.Caption, color = P.inkFaint, modifier = Modifier.reveal(4),
    )
}

@Composable
private fun Live(state: UiState, actions: UiActions) {
    val s = S
    val t = state.eyeTest
    Row(Modifier.fillMaxWidth().reveal(1), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PulseDot(P.coral.fill)
        EbText(s.t("Camera on - analysed on this phone, nothing saved", "Kamera açık - bu telefonda analiz ediliyor, hiçbir şey kaydedilmiyor"), Ink.Caption, color = P.inkSoft)
    }

    Card(Modifier.reveal(2).fillMaxWidth(), tint = P.sky) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            LiveEye(t.openness)
            Spacer(Modifier.height(10.dp))
            val closure = 1f - (t.openness ?: 1f)
            EbText(s.t("Eyelid movement", "Göz kapağı hareketi"), Ink.Eyebrow, color = P.inkSoft)
            Spacer(Modifier.height(6.dp))
            Meter(closure, tone = if (t.openness != null && closure >= t.countsAt && t.countsAt > 0f) P.sun else P.sky)
            Spacer(Modifier.height(4.dp))
            EbText(
                s.t("It counts as a blink once the bar is past ${(t.countsAt * 100).roundToInt()}%.", "Çubuk %${(t.countsAt * 100).roundToInt()} üzerine çıkınca kırpma sayılır."),
                Ink.Caption, color = P.inkSoft, align = TextAlign.Center,
            )
        }
    }

    Card(Modifier.reveal(3).fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                EbText(s.t("Blinks seen", "Görülen kırpma"), Ink.Eyebrow, color = P.inkSoft)
                EbText("${t.blinks}", Ink.Display)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                EbText(s.t("Last blink", "Son kırpma"), Ink.Eyebrow, color = P.inkSoft)
                val last = t.lastDepth
                if (last == null) {
                    EbText("-", Ink.Title)
                } else {
                    EbText("${(last * 100).roundToInt()}% · ${t.lastDurationMs} ${s.t("ms", "ms")}", Ink.Title)
                    Spacer(Modifier.height(4.dp))
                    Badge(if (t.lastComplete) s.t("full", "tam") else s.t("partial", "kısmi"), if (t.lastComplete) Tone.Sky else Tone.Quiet)
                }
            }
        }
    }

    val tips = t.quality?.issues.orEmpty()
    if (tips.isNotEmpty() || t.openness == null) {
        Flow(Modifier.fillMaxWidth().reveal(4)) {
            if (t.openness == null) Badge(s.t("I can't see you - face the screen", "Seni göremiyorum - ekrana dön"), Tone.Warn)
            tips.filter { it != com.eyeblink.core.calibration.QualityIssue.NO_FACE }.take(2).forEach { Badge(qualityTip(it, s), Tone.Warn) }
        }
    }

    Card(Modifier.reveal(5).fillMaxWidth()) {
        EbText(s.t("Eye sensitivity", "Göz hassasiyeti"), Ink.Eyebrow, caps = false)
        Spacer(Modifier.height(8.dp))
        Tide(listOf(s.t("Auto", "Otomatik"), s.t("Higher", "Yüksek"), s.t("Lower", "Düşük")), state.settings.camera.sensitivity.ordinal, { i ->
            actions.update { it.copy(camera = it.camera.copy(sensitivity = EyeSensitivity.entries[i])) }
        })
        Spacer(Modifier.height(6.dp))
        EbText(
            s.t(
                "Blinks missed? Try Higher and watch the counter. Blinks counted that you did not make? Try Lower.",
                "Kırpmalar kaçıyor mu? Yüksek'i dene ve sayacı izle. Yapmadığın kırpmalar sayılıyor mu? Düşük'ü dene.",
            ),
            Ink.Caption, color = P.inkFaint,
        )
    }

    Column(Modifier.reveal(6).padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PillButton(s.t("Stop", "Durdur"), { actions.stopEyeTest() }, kind = ButtonKind.Soft, modifier = Modifier.fillMaxWidth())
    }
}
