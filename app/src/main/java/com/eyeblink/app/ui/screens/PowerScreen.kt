package com.eyeblink.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eyeblink.app.ui.i18n.Strings
import com.eyeblink.app.ui.kit.Badge
import com.eyeblink.app.ui.kit.ButtonKind
import com.eyeblink.app.ui.kit.Dusk
import com.eyeblink.app.ui.kit.EbText
import com.eyeblink.app.ui.kit.Glass
import com.eyeblink.app.ui.kit.Hairline
import com.eyeblink.app.ui.kit.Ico
import com.eyeblink.app.ui.kit.Icon
import com.eyeblink.app.ui.kit.Ink
import com.eyeblink.app.ui.kit.PillButton
import com.eyeblink.app.ui.kit.S
import com.eyeblink.app.ui.kit.Tone
import com.eyeblink.app.ui.kit.reveal
import com.eyeblink.app.ui.state.UiActions
import com.eyeblink.app.ui.state.UiState
import com.eyeblink.core.model.OperatingMode
import com.eyeblink.core.privileged.SetupStepId

private enum class Mark { YES, PARTIAL, NO, NA }

private data class Row5(val en: String, val tr: String, val rootless: Mark, val shizuku: Mark, val root: Mark, val lsposed: Mark)

private val matrix = listOf(
    Row5("Draw the blink", "Göz kırpmayı çiz", Mark.YES, Mark.YES, Mark.YES, Mark.NA),
    Row5("Know the app in front", "Öndeki uygulamayı bil", Mark.YES, Mark.YES, Mark.YES, Mark.NA),
    Row5("Pause & resume via media sessions", "Medya oturumuyla duraklat ve sürdür", Mark.PARTIAL, Mark.YES, Mark.YES, Mark.NA),
    Row5("Verify the pause by reading state", "Durumu okuyarak doğrula", Mark.YES, Mark.YES, Mark.YES, Mark.YES),
    Row5("One-tap permission setup", "Tek dokunuşla izin kurulumu", Mark.NO, Mark.YES, Mark.YES, Mark.NO),
    Row5("Control Night Light", "Gece Işığı'nı denetle", Mark.NO, Mark.YES, Mark.YES, Mark.NO),
    Row5("Hook an app's own player", "Uygulamanın kendi oynatıcısına bağlan", Mark.NO, Mark.NO, Mark.NO, Mark.YES),
)

@Composable
fun PowerScreen(state: UiState, actions: UiActions, onBack: () -> Unit) {
    val s = S
    val p = state.power
    val mode = state.settings.mode
    var chosen by remember { mutableStateOf(setOf(SetupStepId.OVERLAY, SetupStepId.NOTIFICATION_LISTENER, SetupStepId.ACCESSIBILITY, SetupStepId.SECURE_SETTINGS, SetupStepId.USAGE_ACCESS)) }

    PageColumn(s.t("Power modes", "Güç modları"), s.t("How deep it reaches", "Ne kadar derine uzanır"), onBack) {
        EbText(
            s.t("EyeBlink works without any special access. Shizuku or Root only make setup one tap and add a few abilities. Nothing here is required.", "EyeBlink hiçbir özel erişim olmadan çalışır. Shizuku veya Root yalnızca kurulumu tek dokunuşa indirir ve birkaç yetenek ekler. Burada hiçbir şey zorunlu değil."),
            Ink.Body, modifier = Modifier.reveal(1),
        )

        // ── Rootless ────────────────────────────────────────────
        ModeCard(Modifier.reveal(2), mode == OperatingMode.ROOTLESS, Icon.Shield, Dusk.Iris, s.t("Rootless", "Rootsuz"), s.t("Accessibility, media sessions and an overlay. Works on any phone.", "Erişilebilirlik, medya oturumları ve bir bindirme. Her telefonda çalışır."), { actions.update { it.copy(mode = OperatingMode.ROOTLESS) } }) {
            Badge(s.t("always available", "her zaman mevcut"), Tone.Ok)
        }

        // ── Shizuku ─────────────────────────────────────────────
        ModeCard(Modifier.reveal(3), mode == OperatingMode.SHIZUKU, Icon.Link, Dusk.Amber, "Shizuku", s.t("Adb-level access without root. Needs the Shizuku app running.", "Root olmadan adb düzeyinde erişim. Shizuku uygulamasının çalışması gerekir."), { actions.update { it.copy(mode = OperatingMode.SHIZUKU) } }) {
            Step(s.t("Shizuku installed", "Shizuku yüklü"), p.shizukuInstalled)
            Step(s.t("Shizuku running", "Shizuku çalışıyor"), p.shizukuRunning)
            Step(s.t("Access granted to EyeBlink", "EyeBlink'e erişim verildi"), p.shizukuGranted)
            if (!p.shizukuGranted) {
                Spacer(Modifier.height(10.dp))
                PillButton(
                    if (!p.shizukuInstalled) s.t("Get Shizuku", "Shizuku'yu edin") else s.t("Grant access", "Erişim ver"),
                    { actions.requestShizuku() }, kind = ButtonKind.Glass, compact = true, icon = Icon.Link,
                )
            }
        }

        // ── Root ────────────────────────────────────────────────
        ModeCard(Modifier.reveal(4), mode == OperatingMode.ROOT, Icon.Terminal, Dusk.Rose, "Root", s.t("Full access through su (Magisk, KernelSU, APatch). You will be asked to grant it.", "su ile tam erişim (Magisk, KernelSU, APatch). Vermen istenecek."), { actions.update { it.copy(mode = OperatingMode.ROOT) } }) {
            when (p.rootAvailable) {
                null -> Step(s.t("Not checked yet", "Henüz denetlenmedi"), false)
                true -> Step(s.t("Root access granted", "Root erişimi verildi"), true)
                false -> Step(s.t("No root found", "Root bulunamadı"), false)
            }
            Spacer(Modifier.height(10.dp))
            PillButton(s.t("Check for root", "Root'u denetle"), { actions.detectRoot() }, kind = ButtonKind.Glass, compact = true, icon = Icon.Terminal)
        }

        // ── one-tap setup ───────────────────────────────────────
        if (p.status.available) {
            Glass(Modifier.reveal(5).fillMaxWidth(), glow = Dusk.Iris) {
                EbText(s.t("Set it up for me", "Benim için kur"), Ink.Headline)
                Spacer(Modifier.height(6.dp))
                EbText(s.t("Using ${p.status.detail ?: "elevated access"}, EyeBlink can grant itself what you would otherwise tap through in Settings. Pick what you want.", "${p.status.detail ?: "Yükseltilmiş erişim"} ile EyeBlink, Ayarlar'da tek tek vereceğin izinleri kendine verebilir. İstediklerini seç."), Ink.Caption)
                Spacer(Modifier.height(10.dp))
                SetupStepId.entries.forEach { id ->
                    val result = p.setup.firstOrNull { it.id == id }
                    val on = id in chosen
                    Row(
                        Modifier.fillMaxWidth().clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { chosen = if (on) chosen - id else chosen + id }.padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Check(on)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            EbText(setupTitle(id, s), Ink.BodyStrong)
                            EbText(setupWhy(id, s), Ink.Caption)
                        }
                        if (result != null) Badge(if (result.ok) s.t("done", "tamam") else s.t("failed", "başarısız"), if (result.ok) Tone.Ok else Tone.Bad)
                    }
                }
                Spacer(Modifier.height(10.dp))
                EbText(s.t("Camera access is never granted this way. That stays your decision, through Android's own dialog.", "Kamera erişimi asla bu yolla verilmez. O karar yalnızca senin, Android'in kendi penceresiyle."), Ink.Caption, color = Dusk.TextFaint)
                Spacer(Modifier.height(12.dp))
                PillButton(if (p.busy) s.t("Working...", "Çalışıyor...") else s.t("Apply", "Uygula"), { actions.runSetup(chosen) }, enabled = !p.busy && chosen.isNotEmpty(), icon = Icon.Check)
            }
        }

        // ── LSPosed ─────────────────────────────────────────────
        Glass(Modifier.reveal(6).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ico(Icon.Sparkle, size = 24.dp, color = Dusk.Violet)
                EbText("LSPosed", Ink.Title, modifier = Modifier.weight(1f).padding(start = 12.dp))
                Badge(if (p.lsposedModuleActive) s.t("module active", "modül etkin") else s.t("optional", "isteğe bağlı"), if (p.lsposedModuleActive) Tone.Ok else Tone.Quiet)
            }
            Spacer(Modifier.height(8.dp))
            EbText(s.t("An optional, separate module that can pause video inside apps whose players expose nothing else. Experimental: it is only used for an app after its own test passes, and it is never needed for anything above.", "Oynatıcısı başka hiçbir şey sunmayan uygulamaların içinde videoyu duraklatabilen isteğe bağlı, ayrı bir modül. Deneysel: yalnızca uygulamanın kendi testi geçildikten sonra kullanılır ve yukarıdakilerin hiçbiri için gerekmez."), Ink.Caption)
            Spacer(Modifier.height(10.dp))
            PillButton(s.t("Open LSPosed", "LSPosed'u aç"), { actions.openLsposed() }, kind = ButtonKind.Glass, compact = true, icon = Icon.Link)
        }

        // ── matrix ──────────────────────────────────────────────
        Glass(Modifier.reveal(7).fillMaxWidth()) {
            EbText(s.t("What each one can do", "Hangisi neler yapabilir"), Ink.Eyebrow, color = Dusk.Amber)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(2.3f))
                // The column heads are the same marks as the cards above, so no label has to be squeezed.
                listOf(Icon.Shield to Dusk.Iris, Icon.Link to Dusk.Amber, Icon.Terminal to Dusk.Rose, Icon.Sparkle to Dusk.Violet).forEach { (icon, tone) ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Ico(icon, size = 20.dp, color = tone) }
                }
            }
            matrix.forEach { r ->
                Hairline()
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    EbText(s.t(r.en, r.tr), Ink.Caption, color = Dusk.Text, modifier = Modifier.weight(2.3f).padding(end = 6.dp))
                    listOf(r.rootless, r.shizuku, r.root, r.lsposed).forEach { m -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { MarkIcon(m) } }
                }
            }
            Spacer(Modifier.height(8.dp))
            EbText(s.t("Partial: needs the one-time media-session permission in Settings.", "Kısmi: Ayarlar'da tek seferlik medya oturumu izni gerekir."), Ink.Caption, color = Dusk.TextFaint)
        }
    }
}

@Composable
private fun ModeCard(modifier: Modifier, selected: Boolean, icon: Icon, tone: Color, title: String, body: String, onSelect: () -> Unit, content: @Composable () -> Unit) {
    Glass(modifier.fillMaxWidth(), glow = if (selected) tone else null, strength = if (selected) 1.2f else 0.8f, onClick = onSelect) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(icon, size = 26.dp, color = tone)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                EbText(title, Ink.Title)
                EbText(body, Ink.Caption)
            }
            Radio(selected, tone)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun Radio(on: Boolean, tone: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
        drawCircle(if (on) tone else Dusk.HairlineStrong, style = androidx.compose.ui.graphics.drawscope.Stroke(1.8.dp.toPx()))
        if (on) drawCircle(tone, radius = 6.dp.toPx())
    }
}

@Composable
private fun Check(on: Boolean) {
    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
            if (on) drawCircle(Dusk.Iris) else drawCircle(Dusk.HairlineStrong, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
        }
        if (on) Ico(Icon.Check, size = 14.dp, color = Color(0xFF04201C), weight = 2.5f)
    }
}

@Composable
private fun Step(text: String, ok: Boolean) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Ico(if (ok) Icon.Check else Icon.Minus, size = 16.dp, color = if (ok) Dusk.Ok else Dusk.TextFaint)
        EbText(text, Ink.Caption, color = if (ok) Dusk.Text else Dusk.TextDim)
    }
}

@Composable
private fun MarkIcon(m: Mark) {
    when (m) {
        Mark.YES -> Ico(Icon.Check, size = 18.dp, color = Dusk.Iris, weight = 2.2f)
        Mark.PARTIAL -> androidx.compose.foundation.Canvas(Modifier.size(12.dp)) { drawCircle(Dusk.Amber) }
        Mark.NO -> Ico(Icon.Minus, size = 16.dp, color = Dusk.TextFaint)
        Mark.NA -> EbText("·", Ink.Caption, color = Dusk.TextFaint)
    }
}

private fun setupTitle(id: SetupStepId, s: Strings) = when (id) {
    SetupStepId.OVERLAY -> s.t("Display over other apps", "Diğer uygulamaların üzerinde göster")
    SetupStepId.USAGE_ACCESS -> s.t("Usage access", "Kullanım erişimi")
    SetupStepId.SECURE_SETTINGS -> s.t("Change Night Light", "Gece Işığı'nı değiştir")
    SetupStepId.NOTIFICATIONS -> s.t("Notifications", "Bildirimler")
    SetupStepId.NOTIFICATION_LISTENER -> s.t("Media session access", "Medya oturumu erişimi")
    SetupStepId.ACCESSIBILITY -> s.t("Accessibility service", "Erişilebilirlik hizmeti")
    SetupStepId.BATTERY_EXEMPT -> s.t("Unrestricted battery", "Kısıtsız pil")
}

private fun setupWhy(id: SetupStepId, s: Strings) = when (id) {
    SetupStepId.OVERLAY -> s.t("Lets EyeBlink draw the blink.", "EyeBlink'in göz kırpmayı çizmesini sağlar.")
    SetupStepId.USAGE_ACCESS -> s.t("A fallback way to know which app is open.", "Hangi uygulamanın açık olduğunu bilmenin yedek yolu.")
    SetupStepId.SECURE_SETTINGS -> s.t("Needed to switch Night Light on and off.", "Gece Işığı'nı açıp kapatmak için gerekli.")
    SetupStepId.NOTIFICATIONS -> s.t("Shows the quiet 'running' notification.", "Sessiz 'çalışıyor' bildirimini gösterir.")
    SetupStepId.NOTIFICATION_LISTENER -> s.t("Lets EyeBlink pause and resume videos safely.", "Videoları güvenle duraklatıp sürdürmesini sağlar.")
    SetupStepId.ACCESSIBILITY -> s.t("Draws over everything and sees which app is in front.", "Her şeyin üstüne çizer ve hangi uygulamanın önde olduğunu görür.")
    SetupStepId.BATTERY_EXEMPT -> s.t("Keeps Android from stopping EyeBlink in the background.", "Android'in EyeBlink'i arka planda durdurmasını engeller.")
}
