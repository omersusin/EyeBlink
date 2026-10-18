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
import androidx.compose.ui.unit.dp
import com.eyeblink.app.ui.i18n.Strings
import com.eyeblink.app.ui.i18n.reason
import com.eyeblink.app.ui.kit.Badge
import com.eyeblink.app.ui.kit.ButtonKind
import com.eyeblink.app.ui.kit.P
import com.eyeblink.app.ui.kit.EbText
import com.eyeblink.app.ui.kit.Card
import com.eyeblink.app.ui.kit.Hairline
import com.eyeblink.app.ui.kit.CheckDot
import com.eyeblink.app.ui.kit.Hue
import com.eyeblink.app.ui.kit.Ico
import com.eyeblink.app.ui.kit.IconBadge
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
        ModeCard(Modifier.reveal(2), mode == OperatingMode.ROOTLESS, Icon.Shield, P.sky, s.t("Rootless", "Rootsuz"), s.t("Accessibility, media sessions and an overlay. Works on any phone.", "Erişilebilirlik, medya oturumları ve bir bindirme. Her telefonda çalışır."), { actions.update { it.copy(mode = OperatingMode.ROOTLESS) } }) {
            Badge(s.t("always available", "her zaman mevcut"), Tone.Ok)
        }

        // ── Shizuku ─────────────────────────────────────────────
        ModeCard(Modifier.reveal(3), mode == OperatingMode.SHIZUKU, Icon.Link, P.sun, "Shizuku", s.t("Adb-level access without root. Needs the Shizuku app running.", "Root olmadan adb düzeyinde erişim. Shizuku uygulamasının çalışması gerekir."), { actions.update { it.copy(mode = OperatingMode.SHIZUKU) } }) {
            Step(s.t("Shizuku installed", "Shizuku yüklü"), p.shizukuInstalled)
            Step(s.t("Shizuku running", "Shizuku çalışıyor"), p.shizukuRunning)
            Step(s.t("Access granted to EyeBlink", "EyeBlink'e erişim verildi"), p.shizukuGranted)
            if (p.shizukuGranted && mode != OperatingMode.SHIZUKU) NotSelected()
            if (!p.shizukuGranted) {
                Spacer(Modifier.height(10.dp))
                PillButton(
                    if (!p.shizukuInstalled) s.t("Get Shizuku", "Shizuku'yu indir") else s.t("Grant access", "Erişim ver"),
                    { actions.requestShizuku() }, kind = ButtonKind.Soft, compact = true, icon = Icon.Link,
                )
            }
        }

        // ── Root ────────────────────────────────────────────────
        ModeCard(Modifier.reveal(4), mode == OperatingMode.ROOT, Icon.Terminal, P.coral, "Root", s.t("Full access through su (Magisk, KernelSU, APatch). You will be asked to grant it.", "su ile tam erişim (Magisk, KernelSU, APatch). Vermen istenecek."), { actions.update { it.copy(mode = OperatingMode.ROOT) } }) {
            when (p.rootAvailable) {
                null -> Step(s.t("Not checked yet", "Henüz denetlenmedi"), false)
                true -> Step(s.t("Root access granted", "Root erişimi verildi"), true)
                false -> Step(s.t("No root found", "Root bulunamadı"), false)
            }
            if (p.rootAvailable == true && mode != OperatingMode.ROOT) NotSelected()
            if (mode == OperatingMode.ROOT && p.rootAvailable == true && !p.status.available) {
                EbText(
                    s.t("Root is granted but the shell did not start: ${p.status.detail ?: "no answer"}.", "Root verildi ama kabuk başlamadı: ${s.reason(p.status.detail) ?: "yanıt yok"}."),
                    Ink.Caption, color = P.sun.text,
                )
            }
            Spacer(Modifier.height(10.dp))
            PillButton(s.t("Check for root", "Root'u denetle"), { actions.detectRoot() }, kind = ButtonKind.Soft, compact = true, icon = Icon.Terminal)
        }

        // ── one-tap setup ───────────────────────────────────────
        if (p.status.available) {
            Card(Modifier.reveal(5).fillMaxWidth(), tint = P.sky) {
                EbText(s.t("Set it up for me", "Benim için kur"), Ink.Headline)
                Spacer(Modifier.height(6.dp))
                EbText(s.t("Using ${p.status.detail ?: "elevated access"}, EyeBlink can grant itself what you would otherwise tap through in Settings. Pick what you want.", "${s.reason(p.status.detail) ?: "Yükseltilmiş erişim"} ile EyeBlink, Ayarlar'da tek tek vereceğin izinleri kendine verebilir. İstediklerini seç."), Ink.Caption)
                Spacer(Modifier.height(10.dp))
                SetupStepId.entries.forEach { id ->
                    val result = p.setup.firstOrNull { it.id == id }
                    val on = id in chosen
                    Row(
                        Modifier.fillMaxWidth().clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { chosen = if (on) chosen - id else chosen + id }.padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CheckDot(on, size = 26.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            EbText(setupTitle(id, s), Ink.BodyStrong)
                            EbText(setupWhy(id, s), Ink.Caption)
                        }
                        if (result != null) Badge(if (result.ok) s.t("done", "tamam") else s.t("failed", "başarısız"), if (result.ok) Tone.Ok else Tone.Bad)
                    }
                }
                Spacer(Modifier.height(10.dp))
                EbText(s.t("Camera access is never granted this way. That stays your decision, through Android's own dialog.", "Kamera erişimi asla bu yolla verilmez. O karar yalnızca senin, Android'in kendi penceresiyle."), Ink.Caption, color = P.inkFaint)
                Spacer(Modifier.height(12.dp))
                PillButton(if (p.busy) s.t("Working...", "Çalışıyor...") else s.t("Apply", "Uygula"), { actions.runSetup(chosen) }, enabled = !p.busy && chosen.isNotEmpty(), icon = Icon.Check)
            }
        }

        // ── LSPosed ─────────────────────────────────────────────
        Card(Modifier.reveal(6).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icon.Sparkle, P.grape, size = 40.dp)
                EbText("LSPosed", Ink.Title, modifier = Modifier.weight(1f).padding(start = 12.dp))
                Badge(if (p.lsposedModuleActive) s.t("module active", "modül etkin") else s.t("optional", "isteğe bağlı"), if (p.lsposedModuleActive) Tone.Ok else Tone.Quiet)
            }
            Spacer(Modifier.height(8.dp))
            EbText(s.t("An optional, separate module that can pause video inside apps whose players expose nothing else. Experimental: it is only used for an app after its own test passes, and it is never needed for anything above.", "Oynatıcısı başka hiçbir şey sunmayan uygulamaların içinde videoyu duraklatabilen isteğe bağlı, ayrı bir modül. Deneysel: yalnızca uygulamanın kendi testi geçildikten sonra kullanılır ve yukarıdakilerin hiçbiri için gerekmez."), Ink.Caption)
            Spacer(Modifier.height(10.dp))
            PillButton(s.t("Open LSPosed", "LSPosed'u aç"), { actions.openLsposed() }, kind = ButtonKind.Soft, compact = true, icon = Icon.Link)
        }

        // ── matrix ──────────────────────────────────────────────
        Card(Modifier.reveal(7).fillMaxWidth()) {
            EbText(s.t("What each one can do", "Hangisi neler yapabilir"), Ink.Title)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(2.3f))
                // The column heads are the same marks as the cards above, so no label has to be squeezed.
                listOf(Icon.Shield to P.sky, Icon.Link to P.sun, Icon.Terminal to P.coral, Icon.Sparkle to P.grape).forEach { (icon, hue) ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { IconBadge(icon, hue, size = 32.dp) }
                }
            }
            matrix.forEach { r ->
                Hairline()
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    EbText(s.t(r.en, r.tr), Ink.Caption, color = P.ink, modifier = Modifier.weight(2.3f).padding(end = 6.dp))
                    listOf(r.rootless, r.shizuku, r.root, r.lsposed).forEach { m -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { MarkIcon(m) } }
                }
            }
            Spacer(Modifier.height(8.dp))
            EbText(s.t("Partial: needs the one-time media-session permission in Settings.", "Kısmi: Ayarlar'da tek seferlik medya oturumu izni gerekir."), Ink.Caption, color = P.inkFaint)
        }
    }
}

@Composable
private fun ModeCard(modifier: Modifier, selected: Boolean, icon: Icon, hue: Hue, title: String, body: String, onSelect: () -> Unit, content: @Composable () -> Unit) {
    Card(modifier.fillMaxWidth(), tint = if (selected) hue else null, onClick = onSelect) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, hue, size = 46.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                EbText(title, Ink.Title)
                EbText(body, Ink.Caption, color = if (selected) P.ink else P.inkSoft)
            }
            CheckDot(selected)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

/** Access that works but is not the chosen mode does nothing; say so where the person is looking. */
@Composable
private fun NotSelected() {
    val s = S
    Spacer(Modifier.height(4.dp))
    EbText(
        s.t("Ready, but this mode is not selected yet. Tap this card to use it.", "Hazır ama bu mod henüz seçili değil. Kullanmak için bu karta dokun."),
        Ink.Caption, color = P.sun.text,
    )
}

@Composable
private fun Step(text: String, ok: Boolean) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Ico(if (ok) Icon.Check else Icon.Minus, size = 16.dp, color = if (ok) P.mint.text else P.inkFaint, weight = 2.4f)
        EbText(text, Ink.Caption, color = if (ok) P.ink else P.inkSoft)
    }
}

@Composable
private fun MarkIcon(m: Mark) {
    when (m) {
        Mark.YES -> Ico(Icon.Check, size = 18.dp, color = P.mint.text, weight = 2.6f)
        Mark.PARTIAL -> { val c = P.sun.fill; androidx.compose.foundation.Canvas(Modifier.size(12.dp)) { drawCircle(c) } }
        Mark.NO -> Ico(Icon.Minus, size = 16.dp, color = P.inkFaint, weight = 2.2f)
        Mark.NA -> EbText("·", Ink.Caption, color = P.inkFaint)
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
