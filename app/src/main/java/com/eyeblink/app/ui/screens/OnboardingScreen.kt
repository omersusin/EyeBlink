package com.eyeblink.app.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eyeblink.app.ui.eye.EyeHero
import com.eyeblink.app.ui.kit.AppAvatar
import com.eyeblink.app.ui.kit.Badge
import com.eyeblink.app.ui.kit.BlinkTransition
import com.eyeblink.app.ui.kit.ButtonKind
import com.eyeblink.app.ui.kit.Chip
import com.eyeblink.app.ui.kit.P
import com.eyeblink.app.ui.kit.EbText
import com.eyeblink.app.ui.kit.Card
import com.eyeblink.app.ui.kit.CheckDot
import com.eyeblink.app.ui.kit.Hue
import com.eyeblink.app.ui.kit.IconBadge
import com.eyeblink.app.ui.kit.Tide
import com.eyeblink.app.ui.kit.Icon
import com.eyeblink.app.ui.kit.Ink
import com.eyeblink.app.ui.kit.LocalInsets
import com.eyeblink.app.ui.kit.PhoneFrame
import com.eyeblink.app.ui.kit.PillButton
import com.eyeblink.app.ui.kit.RevealScope
import com.eyeblink.app.ui.kit.S
import com.eyeblink.app.ui.kit.LanguageChip
import com.eyeblink.app.ui.kit.Tone
import com.eyeblink.app.ui.kit.reveal
import com.eyeblink.app.ui.state.PermissionKind
import com.eyeblink.app.ui.state.UiActions
import com.eyeblink.app.ui.state.UiState
import com.eyeblink.core.model.Appearance

/** First run: who EyeBlink is, how it behaves, which apps, what to allow. Every step can be skipped past. */
@Composable
fun OnboardingScreen(state: UiState, actions: UiActions, onLanguage: () -> Unit, initialStep: Int = 0, modifier: Modifier = Modifier) {
    val s = S
    var step by rememberSaveable { mutableStateOf(initialStep.coerceIn(0, LAST_STEP)) }
    val insets = LocalInsets.current

    Column(modifier.fillMaxSize().padding(top = insets.top, bottom = insets.bottom)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            BlinkTransition(target = step, modifier = Modifier.fillMaxSize()) { shown ->
                RevealScope {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        when (shown) {
                            0 -> Welcome(state, actions, onLanguage)
                            1 -> HowItWorks(state)
                            2 -> PickApps(state, actions)
                            3 -> Allow(state, actions)
                            else -> Ready(state)
                        }
                    }
                }
            }
            // Content fades out before the footer instead of being cut by it.
            val bg = P.bg
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(40.dp)
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(bg.copy(alpha = 0f), bg.copy(alpha = 0.95f)))),
            )
        }

        // ── footer ──────────────────────────────────────────────
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Dots(step)
            Spacer(Modifier.height(18.dp))
            if (step < LAST_STEP) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (step > 0) PillButton(s.t("Back", "Geri"), { step -= 1 }, kind = ButtonKind.Ghost, compact = true)
                    PillButton(
                        if (step == 0) s.t("Begin", "Başla") else s.t("Next", "İleri"), { step += 1 },
                        modifier = Modifier.weight(1f), icon = if (step == 0) Icon.Eye else null,
                    )
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton(s.t("Not yet", "Şimdi değil"), { actions.finishOnboarding() }, kind = ButtonKind.Ghost, compact = true)
                    PillButton(s.t("Start EyeBlink", "EyeBlink'i başlat"), { actions.setRunning(true); actions.finishOnboarding() }, modifier = Modifier.weight(1f), icon = Icon.Eye)
                }
            }
        }
    }
}

private const val LAST_STEP = 4

@Composable
private fun Dots(step: Int) {
    val p = P
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0..LAST_STEP) {
            val w by animateDpAsState(if (i == step) 28.dp else 9.dp, androidx.compose.animation.core.tween(220), label = "dot")
            Box(
                Modifier.width(w).height(9.dp).clip(RoundedCornerShape(50))
                    .background(if (i == step) p.sun.fill else if (i < step) p.sun.lip.copy(alpha = 0.6f) else p.line),
            )
        }
    }
}

// ── 0 · welcome ─────────────────────────────────────────────────

@Composable
private fun ColumnScope.Welcome(state: UiState, actions: UiActions, onLanguage: () -> Unit) {
    val s = S
    Spacer(Modifier.height(4.dp))
    Box(Modifier.reveal(0).fillMaxWidth(), contentAlignment = Alignment.Center) { EyeHero(Modifier.fillMaxWidth(), description = s.t("The EyeBlink eye. Tap it to blink.", "EyeBlink gözü. Kırpması için dokun.")) }
    Column(Modifier.reveal(1).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        EbText(s.t("Welcome", "Hoş geldin"), Ink.Eyebrow, color = P.sky.text)
        Spacer(Modifier.height(8.dp))
        EbText(s.t("Give your eyes the pause back.", "Gözlerine duraklamayı geri ver."), Ink.Headline, align = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        EbText(
            s.t(
                "We blink far less while we stare at a screen. EyeBlink brings the blink back: a soft half-second lid over your app now and then, and a proper rest once in a while.",
                "Ekrana bakarken çok daha az kırpıyoruz. EyeBlink kırpmayı geri getirir: arada bir uygulamanın üstünden geçen yumuşak, yarım saniyelik bir göz kapağı ve ara sıra gerçek bir mola.",
            ),
            Ink.Body, color = P.inkSoft, align = TextAlign.Center,
        )
    }
    Card(Modifier.reveal(2).fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            EbText(s.t("Language", "Dil"), Ink.BodyStrong, modifier = Modifier.weight(1f))
            LanguageChip(languageLabel(state.settings.language, s), onLanguage)
        }
        Spacer(Modifier.height(14.dp))
        EbText(s.t("Appearance", "Görünüm"), Ink.BodyStrong)
        Spacer(Modifier.height(10.dp))
        val modes = listOf(Appearance.SYSTEM, Appearance.LIGHT, Appearance.DARK)
        Tide(
            listOf(s.t("Auto", "Otomatik"), s.t("Light", "Açık"), s.t("Dark", "Koyu")),
            modes.indexOf(state.settings.appearance),
            { actions.update { st -> st.copy(appearance = modes[it]) } },
        )
    }
}

// ── 1 · how it works ────────────────────────────────────────────

@Composable
private fun ColumnScope.HowItWorks(state: UiState) {
    val s = S
    Box(Modifier.reveal(0).fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
        PhoneFrame(state.settings.blink, description = s.t("A preview of the blink over a video. Tap to blink.", "Bir videonun üzerindeki göz kırpmanın önizlemesi. Kırpmak için dokun."))
    }
    Column(Modifier.reveal(1)) {
        EbText(s.t("How it works", "Nasıl çalışır"), Ink.Eyebrow, color = P.sky.text)
        Spacer(Modifier.height(6.dp))
        EbText(s.t("Small, regular, kind to your eyes", "Küçük, düzenli, gözlerine nazik"), Ink.Headline)
    }
    Beat(2, Icon.Eye, P.sky, s.t("A blink, every so often", "Arada bir kırpma"), s.t("A soft lid sweeps over your app for half a second, then it is gone. About every 45 seconds to start with - you set the pace.", "Yumuşak bir göz kapağı uygulamanın üzerinden yarım saniye geçer, sonra kaybolur. Başlangıçta yaklaşık 45 saniyede bir - temposunu sen belirlersin."))
    Beat(3, Icon.Sun, P.sun, s.t("A proper rest", "Gerçek bir mola"), s.t("Every 20 minutes or so, look at something far away for 20 seconds. Skip it whenever you like.", "Yaklaşık her 20 dakikada bir, 20 saniye uzaktaki bir şeye bak. İstediğin an atla."))
    Beat(4, Icon.Play, P.grape, s.t("Videos wait for you", "Videolar seni bekler"), s.t("If it can confirm the pause, your video stops for the break and carries on after. If it cannot, it says so instead of guessing.", "Duraklatmayı doğrulayabilirse videon mola için durur, sonra devam eder. Doğrulayamazsa tahmin etmek yerine bunu söyler."))
}

@Composable
private fun Beat(index: Int, icon: Icon, hue: Hue, title: String, body: String) {
    Card(Modifier.reveal(index).fillMaxWidth(), tint = hue, padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        Row {
            IconBadge(icon, hue, size = 46.dp)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                EbText(title, Ink.Title)
                Spacer(Modifier.height(4.dp))
                EbText(body, Ink.Caption, color = P.ink)
            }
        }
    }
}

// ── 2 · apps ────────────────────────────────────────────────────

@Composable
private fun ColumnScope.PickApps(state: UiState, actions: UiActions) {
    val s = S
    val selected = state.settings.apps.map { it.packageName }.toSet()
    val shown = (state.apps.filter { it.known || it.packageName in selected }).take(9)
    Column(Modifier.reveal(0)) {
        EbText(s.t("Your apps", "Uygulamaların"), Ink.Eyebrow, color = P.sky.text)
        Spacer(Modifier.height(6.dp))
        EbText(s.t("Where should it blink?", "Nerede kırpsın?"), Ink.Headline)
        Spacer(Modifier.height(8.dp))
        EbText(
            s.t("EyeBlink only ever interrupts the apps you choose. Start with the ones you scroll the most - you can change this any time.", "EyeBlink yalnızca seçtiğin uygulamaları böler. En çok kaydırdıklarınla başla - bunu istediğin an değiştirebilirsin."),
            Ink.Body, color = P.inkSoft,
        )
    }
    Card(Modifier.reveal(1).fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
        if (shown.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            EbText(s.t("No familiar apps found on this phone. You can add any app later from the Apps tab.", "Bu telefonda tanıdık uygulama bulunamadı. Daha sonra Uygulamalar sekmesinden istediğini ekleyebilirsin."), Ink.Caption)
            Spacer(Modifier.height(10.dp))
        }
        shown.forEach { app ->
            val on = app.packageName in selected
            Row(
                Modifier.fillMaxWidth()
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { if (on) actions.removeApp(app.packageName) else actions.addApp(app.packageName) }
                    .padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppAvatar(app.label, app.packageName, size = 42.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { EbText(app.label, Ink.BodyStrong, maxLines = 1) }
                CheckDot(on)
            }
        }
    }
    if (selected.isNotEmpty()) {
        EbText(s.t("${selected.size} selected", "${selected.size} seçili"), Ink.BodyStrong, color = P.sky.text)
    }
}

// ── 3 · permissions ─────────────────────────────────────────────

@Composable
private fun ColumnScope.Allow(state: UiState, actions: UiActions) {
    val s = S
    Column(Modifier.reveal(0)) {
        EbText(s.t("Permissions", "İzinler"), Ink.Eyebrow, color = P.sky.text)
        Spacer(Modifier.height(6.dp))
        EbText(s.t("Allow what it needs", "Gerekenlere izin ver"), Ink.Headline)
        Spacer(Modifier.height(8.dp))
        EbText(
            s.t("Only the first is required. The rest make it better, and each one can be changed later in Diagnostics.", "Yalnızca ilki zorunlu. Gerisi onu daha iyi yapar ve her biri sonradan Tanılama'dan değiştirilebilir."),
            Ink.Body, color = P.inkSoft,
        )
    }
    val kinds = listOf(PermissionKind.ACCESSIBILITY, PermissionKind.NOTIFICATION_LISTENER, PermissionKind.NOTIFICATIONS)
    kinds.forEachIndexed { i, kind ->
        val granted = state.perms.has(kind) || (kind == PermissionKind.ACCESSIBILITY && state.perms.has(PermissionKind.OVERLAY))
        Card(Modifier.reveal(1 + i).fillMaxWidth(), tint = if (granted) P.mint else if (i == 0) P.sun else null, padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                IconBadge(permissionIcon(kind), if (granted) P.mint else if (i == 0) P.sun else P.sky, size = 44.dp)
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EbText(permissionTitle(kind, s), Ink.Title, modifier = Modifier.weight(1f, fill = false))
                        if (i == 0) Badge(s.t("required", "gerekli"), Tone.Sun, dot = false) else Badge(s.t("optional", "isteğe bağlı"), Tone.Quiet, dot = false)
                    }
                    Spacer(Modifier.height(4.dp))
                    EbText(permissionWhy(kind, s), Ink.Caption, color = P.ink)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (granted) Badge(s.t("allowed", "izin verildi"), Tone.Ok)
            else PillButton(s.t("Allow", "İzin ver"), { actions.request(kind) }, kind = if (i == 0) ButtonKind.Primary else ButtonKind.Soft, compact = true)
        }
    }
    Card(Modifier.reveal(4).fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            IconBadge(Icon.Camera, P.grape, size = 40.dp)
            Column(Modifier.padding(start = 14.dp)) {
                EbText(s.t("What about the camera?", "Peki ya kamera?"), Ink.BodyStrong)
                Spacer(Modifier.height(3.dp))
                EbText(
                    s.t("It is optional and comes later, in calibration. Without it EyeBlink runs on a smart timer and never claims to have seen you blink.", "İsteğe bağlıdır ve sonra, kalibrasyonda sorulur. O olmadan EyeBlink akıllı bir zamanlayıcıyla çalışır ve göz kırptığını gördüğünü asla iddia etmez."),
                    Ink.Caption,
                )
            }
        }
    }
}

// ── 4 · ready ───────────────────────────────────────────────────

@Composable
private fun ColumnScope.Ready(state: UiState) {
    val s = S
    Spacer(Modifier.height(10.dp))
    Box(Modifier.reveal(0).fillMaxWidth(), contentAlignment = Alignment.Center) { EyeHero(Modifier.fillMaxWidth(), orbit = 0.7f) }
    Column(Modifier.reveal(1).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        EbText(s.t("All set", "Hazır"), Ink.Eyebrow, color = P.sky.text)
        Spacer(Modifier.height(8.dp))
        EbText(s.t("Go and look at something.", "Git bir şeylere bak."), Ink.Headline, align = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        EbText(
            s.t("Open one of your apps. The first blink arrives in about a minute.", "Uygulamalarından birini aç. İlk kırpma yaklaşık bir dakika içinde gelir."),
            Ink.Body, color = P.inkSoft, align = TextAlign.Center,
        )
    }
    Card(Modifier.reveal(2).fillMaxWidth(), tint = P.sky, padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            IconBadge(Icon.Sparkle, P.sky, size = 40.dp)
            Column(Modifier.padding(start = 14.dp)) {
                EbText(s.t("Make it yours", "Sana göre ayarla"), Ink.BodyStrong)
                Spacer(Modifier.height(3.dp))
                EbText(
                    s.t("Calibrate once in More, then Calibration. It takes about two minutes and teaches EyeBlink how often you really blink.", "Daha, ardından Kalibrasyon'dan bir kez kalibre et. Yaklaşık iki dakika sürer ve EyeBlink'e gerçekte ne sıklıkla kırptığını öğretir."),
                    Ink.Caption,
                )
            }
        }
    }
}
