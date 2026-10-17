package com.eyeblink.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eyeblink.app.ui.eye.EyeArt.drawEye
import com.eyeblink.app.ui.eye.EyeColors
import com.eyeblink.app.ui.eye.EyeLook
import com.eyeblink.app.ui.i18n.Strings
import com.eyeblink.app.ui.i18n.fmtClock
import com.eyeblink.app.ui.kit.AmbientReel
import com.eyeblink.app.ui.kit.Badge
import com.eyeblink.app.ui.kit.ButtonKind
import com.eyeblink.app.ui.kit.P
import com.eyeblink.app.ui.kit.EbText
import com.eyeblink.app.ui.kit.Flow
import com.eyeblink.app.ui.kit.Card
import com.eyeblink.app.ui.kit.Hairline
import com.eyeblink.app.ui.kit.Hue
import com.eyeblink.app.ui.kit.IconBadge
import com.eyeblink.app.ui.kit.Icon
import com.eyeblink.app.ui.kit.Ink
import com.eyeblink.app.ui.kit.Meter
import com.eyeblink.app.ui.kit.PillButton
import com.eyeblink.app.ui.kit.PulseDot
import com.eyeblink.app.ui.kit.S
import com.eyeblink.app.ui.kit.Tone
import com.eyeblink.app.ui.kit.reveal
import com.eyeblink.app.ui.state.CalibrationStage
import com.eyeblink.app.ui.state.CalibrationUi
import com.eyeblink.app.ui.state.PermissionKind
import com.eyeblink.app.ui.state.UiActions
import com.eyeblink.app.ui.state.UiState
import com.eyeblink.core.calibration.CalibrationSession.Phase
import com.eyeblink.core.calibration.ContextProfile
import com.eyeblink.core.calibration.Provenance
import com.eyeblink.core.calibration.QualityIssue
import kotlin.math.roundToInt

@Composable
fun CalibrationScreen(state: UiState, actions: UiActions, onBack: () -> Unit) {
    val s = S
    val c = state.calibration
    PageColumn(s.t("Calibration", "Kalibrasyon"), s.t("Meet your blink", "Kırpmanla tanış"), onBack = {
        // A session in progress is dropped on the way out, and so is a result with nothing in it to keep; a result that can
        // still be saved stays until it is.
        val nothingToKeep = c.stage == CalibrationStage.RESULT && c.result?.hasEnoughSpontaneous != true
        if (c.stage == CalibrationStage.RUNNING || nothingToKeep) actions.cancelCalibration()
        onBack()
    }) {
        when (c.stage) {
            CalibrationStage.INTRO -> Intro(state, actions)
            CalibrationStage.RUNNING -> Running(c, actions)
            CalibrationStage.RESULT -> Result(state, actions)
        }
    }
}

@Composable
private fun Intro(state: UiState, actions: UiActions) {
    val s = S
    val c = state.calibration
    EbText(
        s.t("A short, guided session so EyeBlink knows how you blink - not how an average person does.", "EyeBlink'in ortalama bir insanın değil, senin nasıl göz kırptığını bilmesi için kısa, rehberli bir oturum."),
        Ink.Body, modifier = Modifier.reveal(1),
    )
    Column(Modifier.reveal(2), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StepCard(Icon.Film, P.sky, s.t("Watch", "İzle"), s.t("A calm moving picture while EyeBlink notes your natural blinks.", "Sakin bir görüntü; EyeBlink doğal kırpmalarını not eder."), "${state.settings.calibration.videoSec} ${s.t("s", "sn")}")
        StepCard(Icon.Book, P.sun, s.t("Read", "Oku"), s.t("A short passage, because reading changes how often we blink.", "Kısa bir metin; çünkü okumak göz kırpma sıklığını değiştirir."), "${state.settings.calibration.readingSec} ${s.t("s", "sn")}")
        StepCard(Icon.Eye, P.coral, s.t("Blink on purpose", "Bilerek kırp"), s.t("A few full, deliberate blinks so it learns what a closed eye looks like on you.", "Kapalı gözün sende nasıl göründüğünü öğrenmesi için birkaç tam, bilinçli kırpma."), "${state.settings.calibration.deliberateBlinks}×")
    }
    Card(Modifier.reveal(3).fillMaxWidth(), tint = P.sky) {
        Row(verticalAlignment = Alignment.Top) {
            IconBadge(Icon.Shield, P.sky, size = 40.dp)
            Column(Modifier.padding(start = 12.dp)) {
                EbText(s.t("Nothing leaves your phone", "Hiçbir şey telefonundan çıkmaz"), Ink.BodyStrong)
                Spacer(Modifier.height(4.dp))
                EbText(s.t("You will not see yourself on screen. The camera only measures eyelid movement, frames are discarded as they are read, and only a few numbers are kept. You can retry, skip a step, or delete the result at any time.", "Ekranda kendini görmeyeceksin. Kamera yalnızca göz kapağı hareketini ölçer, kareler okunur okunmaz atılır ve yalnızca birkaç sayı tutulur. İstediğin zaman yeniden deneyebilir, bir adımı atlayabilir ya da sonucu silebilirsin."), Ink.Caption)
            }
        }
    }
    if (c.cameraDenied && PermissionKind.CAMERA !in state.perms.granted) {
        ProblemCard(
            s.t("Camera access was not allowed", "Kamera izni verilmedi"),
            s.t(
                "Calibration needs the camera to see your eyelids. Nothing is recorded or sent. Allow it, then press Begin again.",
                "Kalibrasyon, göz kapaklarını görmek için kameraya ihtiyaç duyar. Hiçbir şey kaydedilmez ya da gönderilmez. İzin ver, sonra Başla'ya yeniden bas.",
            ),
            Modifier.reveal(4),
        )
    } else if (!c.hasCameraPermission) {
        EbText(s.t("EyeBlink will ask for camera access when you begin. It is the only thing it asks for here.", "Başlayınca EyeBlink kamera erişimi isteyecek. Burada istediği tek şey bu."), Ink.Caption, color = P.inkSoft, modifier = Modifier.reveal(4))
    }
    c.error?.let { problem ->
        ProblemCard(
            s.t("The camera could not start", "Kamera başlatılamadı"), problem, Modifier.reveal(4),
            hint = s.t("If this keeps happening, copy the report from More → Diagnostics and send it with this message.", "Bu sürerse Diğer → Tanılama'daki raporu kopyalayıp bu mesajla birlikte gönder."),
        )
    }
    Column(Modifier.reveal(5), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PillButton(s.t("Begin", "Başla"), { actions.startCalibration() }, icon = Icon.Eye, modifier = Modifier.fillMaxWidth())
        if (state.personal.isCalibrated) {
            PillButton(s.t("Reset my profile", "Profilimi sıfırla"), { actions.resetCalibration() }, kind = ButtonKind.Ghost, icon = Icon.Trash, modifier = Modifier.fillMaxWidth(), compact = true)
        }
    }
}

/** Something went wrong before the session could begin: what, and what to do about it. */
@Composable
private fun ProblemCard(title: String, body: String, modifier: Modifier = Modifier, hint: String? = null) {
    Card(modifier.fillMaxWidth(), tint = P.coral) {
        Row(verticalAlignment = Alignment.Top) {
            IconBadge(Icon.Warning, P.coral, size = 40.dp)
            Column(Modifier.padding(start = 12.dp)) {
                EbText(title, Ink.BodyStrong)
                Spacer(Modifier.height(4.dp))
                EbText(body, Ink.Caption)
                if (hint != null) {
                    Spacer(Modifier.height(6.dp))
                    EbText(hint, Ink.Caption, color = P.inkSoft)
                }
            }
        }
    }
}

@Composable
private fun StepCard(icon: Icon, hue: Hue, title: String, body: String, meta: String) {
    Card(Modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, hue, size = 46.dp)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                EbText(title, Ink.Title)
                EbText(body, Ink.Caption)
            }
            EbText(meta, Ink.NumSmall, color = hue.text)
        }
    }
}

@Composable
private fun Running(c: CalibrationUi, actions: UiActions) {
    val s = S
    // step indicator
    Row(Modifier.fillMaxWidth().reveal(1), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(Phase.VIDEO to s.t("Watch", "İzle"), Phase.READING to s.t("Read", "Oku"), Phase.DELIBERATE to s.t("Blink", "Kırp")).forEach { (p, label) ->
            val done = c.phase.ordinal > p.ordinal
            val now = c.phase == p
            Column(Modifier.weight(1f)) {
                Meter(if (done) 1f else if (now) (if (p == Phase.DELIBERATE) (1f - c.deliberateLeft / c.deliberateTotal.coerceAtLeast(1).toFloat()) else c.phaseProgress) else 0f, tone = if (now) P.sun else P.sky)
                Spacer(Modifier.height(6.dp))
                EbText(label, Ink.Eyebrow, color = if (now) P.sun.text else P.inkFaint)
            }
        }
    }
    Row(Modifier.fillMaxWidth().reveal(2), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PulseDot(P.coral.fill)
            EbText(s.t("Camera on - analysed on this phone, nothing saved", "Kamera açık - bu telefonda analiz ediliyor, hiçbir şey kaydedilmiyor"), Ink.Caption, color = P.inkSoft)
        }
    }

    when (c.phase) {
        Phase.VIDEO -> {
            Box(Modifier.reveal(3)) { AmbientReel() }
            EbText(s.t("Just watch. Blink the way you normally do.", "Sadece izle. Her zamanki gibi göz kırp."), Ink.Title, align = TextAlign.Center, modifier = Modifier.reveal(4).fillMaxWidth())
        }
        Phase.READING -> {
            Card(Modifier.reveal(3).fillMaxWidth(), tint = P.sun, padding = PaddingValues(22.dp)) {
                EbText(passage(s), Ink.Body)
            }
            EbText(s.t("Read at your own pace.", "Kendi hızında oku."), Ink.Title, align = TextAlign.Center, modifier = Modifier.reveal(4).fillMaxWidth())
        }
        Phase.DELIBERATE, Phase.COMPLETE -> {
            Card(Modifier.reveal(3).fillMaxWidth(), tint = P.sky) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    LiveEye(c.liveOpenness)
                    Spacer(Modifier.height(10.dp))
                    val p = P
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(c.deliberateTotal) { i ->
                            val lit = i < c.deliberateTotal - c.deliberateLeft
                            Canvas(Modifier.size(18.dp)) {
                                if (lit) {
                                    drawCircle(p.sun.lip, center = center.copy(y = center.y + 1.5.dp.toPx()))
                                    drawCircle(p.sun.fill)
                                } else drawCircle(p.card)
                            }
                        }
                    }
                }
            }
            EbText(
                if (c.deliberateLeft > 0) s.t("Blink fully, on purpose - ${c.deliberateLeft} to go.", "Tam ve bilerek kırp - ${c.deliberateLeft} kaldı.")
                else s.t("That's all I need.", "İhtiyacım olan bu kadar."),
                Ink.Title, align = TextAlign.Center, modifier = Modifier.reveal(4).fillMaxWidth(),
            )
        }
    }

    val tips = c.quality?.issues.orEmpty()
    if (tips.isNotEmpty()) {
        Flow(Modifier.fillMaxWidth().reveal(5)) {
            tips.take(2).forEach { Badge(qualityTip(it, s), Tone.Warn) }
        }
    }

    if (c.remainingSec > 0 && c.phase != Phase.DELIBERATE) {
        EbText(fmtClock(c.remainingSec * 1000L), Ink.Display, align = TextAlign.Center, modifier = Modifier.fillMaxWidth().reveal(5))
    }

    Row(Modifier.fillMaxWidth().reveal(6), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PillButton(s.t("Skip this step", "Bu adımı atla"), { actions.skipCalibrationPhase() }, kind = ButtonKind.Soft, compact = true, modifier = Modifier.weight(1f))
        PillButton(s.t("Cancel", "Vazgeç"), { actions.cancelCalibration() }, kind = ButtonKind.Ghost, compact = true, modifier = Modifier.weight(1f))
    }
}

/** A small eye that mirrors what the camera sees: open when your eye is open, closed when it blinks. */
@Composable
private fun LiveEye(openness: Float?) {
    val p = P
    val colors = remember(p) { EyeColors.of(p) }
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val w = size.width * 0.5f
        val h = w * 0.62f
        val closure = if (openness == null) 0.55f else (1f - openness).coerceIn(0f, 1f)
        drawEye(
            Rect(Offset((size.width - w) / 2f, (size.height - h) / 2f), Size(w, h)),
            EyeLook(closure = closure, awake = if (openness == null) 0.3f else 1f),
            colors, dp = density,
        )
    }
}

private fun qualityTip(i: QualityIssue, s: Strings) = when (i) {
    QualityIssue.NO_FACE -> s.t("I can't see you - face the screen", "Seni göremiyorum - ekrana dön")
    QualityIssue.TOO_DARK -> s.t("Too dark - find more light", "Çok karanlık - biraz ışık bul")
    QualityIssue.FACE_TOO_SMALL -> s.t("A little closer, please", "Biraz daha yaklaş")
    QualityIssue.FACE_TOO_CLOSE -> s.t("A little further back", "Biraz geri çekil")
    QualityIssue.TURNED_AWAY -> s.t("Look toward the screen", "Ekrana doğru bak")
    QualityIssue.TILTED_VERTICALLY -> s.t("Hold the phone a bit straighter", "Telefonu biraz daha dik tut")
}

private fun passage(s: Strings) = s.t(
    "The night market never really closes. Somewhere beyond the bridge a vendor lowers a lantern, and the street shifts from gold to blue. You walk without hurrying, counting nothing, noticing how every window holds a smaller, quieter evening. A kettle begins to sing. A cat crosses the light and is gone. For a moment the whole city seems to hold its breath - and then, softly, it goes on.",
    "Gece pazarı hiçbir zaman tam kapanmaz. Köprünün ötesinde bir satıcı fenerini indirir, sokak altından maviye döner. Acele etmeden, hiçbir şey saymadan yürürsün; her pencerenin daha küçük, daha sessiz bir akşam taşıdığını fark edersin. Bir çaydanlık ıslık çalmaya başlar. Bir kedi ışığın içinden geçip kaybolur. Bir an bütün şehir nefesini tutmuş gibidir - sonra yumuşakça devam eder.",
)

@Composable
private fun Result(state: UiState, actions: UiActions) {
    val s = S
    val r = state.calibration.result
    if (r == null || !r.hasEnoughSpontaneous) {
        Card(Modifier.reveal(1).fillMaxWidth(), tint = P.sun) {
            EbText(s.t("Not enough to learn from yet", "Henüz öğrenecek kadar veri yok"), Ink.Headline)
            Spacer(Modifier.height(8.dp))
            EbText(s.t("The camera didn't see enough of your natural blinks - often because of dim light or a face too far from the lens. Nothing was saved. Try again in a brighter spot.", "Kamera doğal kırpmalarını yeterince göremedi; çoğunlukla loş ışık ya da lensten uzak bir yüz yüzünden. Hiçbir şey kaydedilmedi. Daha aydınlık bir yerde tekrar dene."), Ink.Body)
        }
        Column(Modifier.reveal(2), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(s.t("Try again", "Tekrar dene"), { actions.startCalibration() }, icon = Icon.Refresh, modifier = Modifier.fillMaxWidth())
        }
        return
    }
    val p = state.personal.takeIf { it.isCalibrated } ?: r.profile
    EbText(s.t("Here is what I learned", "Öğrendiklerim"), Ink.Lead, modifier = Modifier.reveal(1))

    Row(Modifier.reveal(2).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(ContextProfile.VIDEO to s.t("Watching", "İzlerken"), ContextProfile.READING to s.t("Reading", "Okurken")).forEach { (ctx, label) ->
            val b = r.profile.byContext[ctx]
            val hue = if (ctx == ContextProfile.VIDEO) P.sky else P.sun
            Card(Modifier.weight(1f), tint = hue) {
                EbText(label, Ink.Eyebrow, color = hue.text)
                Spacer(Modifier.height(8.dp))
                EbText(b?.blinksPerMinute?.let { "%.1f".format(it) } ?: "—", Ink.Display)
                EbText(s.t("blinks / min", "kırpma / dk"), Ink.Caption)
            }
        }
    }

    Card(Modifier.reveal(3).fillMaxWidth()) {
        val b = r.baseline
        DetailRow(s.t("Typical gap between blinks", "Kırpmalar arası tipik süre"), "${"%.1f".format(b.medianIbiMs / 1000f)} ${s.t("s", "sn")}")
        Hairline()
        DetailRow(s.t("Typical blink length", "Tipik kırpma süresi"), "${b.blinkDurationMs} ms")
        Hairline()
        DetailRow(s.t("Full blinks", "Tam kırpmalar"), "${(b.completeBlinkRatio * 100).roundToInt()}%")
        Hairline()
        DetailRow(s.t("Natural blinks seen", "Görülen doğal kırpma"), "${r.spontaneousCount}")
        Hairline()
        DetailRow(s.t("Camera confidence", "Kamera güveni"), "${(r.meanConfidence * 100).roundToInt()}%")
        Hairline()
        DetailRow(s.t("Detector fitted to you", "Sana göre ayarlanan algılayıcı"), if (r.profile.thresholds.personalised) s.t("yes", "evet") else s.t("defaults", "varsayılan"))
    }

    Card(Modifier.reveal(4).fillMaxWidth()) {
        EbText(s.t("What is measured and what is guessed", "Neler ölçüldü, neler tahmin"), Ink.Title)
        Spacer(Modifier.height(10.dp))
        Flow(Modifier.fillMaxWidth()) {
            ContextProfile.entries.forEach { ctx ->
                val pr = p.baselineFor(ctx)
                Badge(
                    "${contextShort(ctx, s)}: " + when (pr.provenance) { Provenance.OBSERVED -> s.t("measured", "ölçüldü"); Provenance.ESTIMATED -> s.t("estimated", "tahmin"); Provenance.NONE -> s.t("unknown", "bilinmiyor") },
                    when (pr.provenance) { Provenance.OBSERVED -> Tone.Sky; Provenance.ESTIMATED -> Tone.Quiet; Provenance.NONE -> Tone.Quiet },
                    dot = pr.provenance == Provenance.OBSERVED,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        EbText(s.t("Contexts you haven't been measured in are estimated from your general profile. EyeBlink keeps refining the measured ones, slowly, from reliable camera moments only.", "Ölçülmediğin bağlamlar genel profilinden tahmin edilir. EyeBlink ölçülenleri yalnızca güvenilir kamera anlarından, yavaş yavaş iyileştirmeye devam eder."), Ink.Caption, color = P.inkFaint)
    }

    Column(Modifier.reveal(5), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PillButton(s.t("Save my profile", "Profilimi kaydet"), { actions.saveCalibration() }, icon = Icon.Check, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(s.t("Retry", "Tekrar dene"), { actions.startCalibration() }, kind = ButtonKind.Soft, compact = true, icon = Icon.Refresh, modifier = Modifier.weight(1f))
            PillButton(s.t("Discard", "Çöpe at"), { actions.resetCalibration() }, kind = ButtonKind.Ghost, compact = true, modifier = Modifier.weight(1f))
        }
    }
}

private fun contextShort(c: ContextProfile, s: Strings) = when (c) {
    ContextProfile.VIDEO -> s.t("video", "video")
    ContextProfile.READING -> s.t("reading", "okuma")
    ContextProfile.SOCIAL -> s.t("scrolling", "kaydırma")
    ContextProfile.LOW_LIGHT -> s.t("low light", "loş ışık")
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        EbText(label, Ink.Body, modifier = Modifier.weight(1f))
        EbText(value, Ink.Num, maxLines = 1)
    }
}
