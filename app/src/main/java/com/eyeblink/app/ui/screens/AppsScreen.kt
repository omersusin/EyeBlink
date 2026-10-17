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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eyeblink.app.ui.i18n.Strings
import com.eyeblink.app.ui.i18n.fmtAgo
import com.eyeblink.app.ui.kit.AppAvatar
import com.eyeblink.app.ui.kit.Badge
import com.eyeblink.app.ui.kit.ButtonKind
import com.eyeblink.app.ui.kit.Dusk
import com.eyeblink.app.ui.kit.EbText
import com.eyeblink.app.ui.kit.Flow
import com.eyeblink.app.ui.kit.Glass
import com.eyeblink.app.ui.kit.Hairline
import com.eyeblink.app.ui.kit.Horizon
import com.eyeblink.app.ui.kit.Ico
import com.eyeblink.app.ui.kit.Icon
import com.eyeblink.app.ui.kit.Ink
import com.eyeblink.app.ui.kit.LidSwitch
import com.eyeblink.app.ui.kit.PillButton
import com.eyeblink.app.ui.kit.RoundIconButton
import com.eyeblink.app.ui.kit.S
import com.eyeblink.app.ui.kit.ScreenColumn
import com.eyeblink.app.ui.kit.SectionHeader
import com.eyeblink.app.ui.kit.SettingRow
import com.eyeblink.app.ui.kit.Tone
import com.eyeblink.app.ui.kit.reveal
import com.eyeblink.app.ui.state.CompatUi
import com.eyeblink.app.ui.state.UiActions
import com.eyeblink.app.ui.state.UiState
import com.eyeblink.core.model.AppProfile
import com.eyeblink.core.playback.CompatOutcome
import com.eyeblink.core.playback.CompatStatus
import com.eyeblink.core.playback.Strategy
import kotlin.math.roundToInt

internal fun strategyName(st: Strategy, s: Strings) = when (st) {
    Strategy.LSPOSED_HOOK -> s.t("In-app hook (LSPosed)", "Uygulama içi kanca (LSPosed)")
    Strategy.MEDIA_SESSION -> s.t("Media session", "Medya oturumu")
    Strategy.PRIVILEGED_SESSION -> s.t("Media session via Shizuku/Root", "Shizuku/Root ile medya oturumu")
    Strategy.MEDIA_KEY -> s.t("Media key", "Medya tuşu")
    Strategy.AUDIO_FOCUS -> s.t("Audio focus", "Ses odağı")
    Strategy.A11Y_NODE -> s.t("Play/pause button", "Oynat/duraklat düğmesi")
    Strategy.A11Y_GESTURE -> s.t("Tap gesture", "Dokunma hareketi")
}

@Composable
fun AppsScreen(state: UiState, actions: UiActions, onPick: () -> Unit, modifier: Modifier = Modifier) {
    val s = S
    var open by remember { mutableStateOf<String?>(null) }

    ScreenColumn(modifier) {
        Row(Modifier.fillMaxWidth().reveal(0), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.weight(1f)) { SectionHeader(s.t("Apps", "Uygulamalar"), s.t("Where it works", "Nerede çalışır")) }
            RoundIconButton(Icon.Plus, s.t("Add an app", "Uygulama ekle"), onPick, glow = Dusk.Amber)
        }
        EbText(
            s.t("EyeBlink only ever interrupts the apps on this list. Anywhere else - calls, payments, sign-in screens, your camera - it stays out of the way.", "EyeBlink yalnızca bu listedeki uygulamalara müdahale eder. Başka her yerde (aramalar, ödemeler, giriş ekranları, kameran) yoldan çekilir."),
            Ink.Caption, modifier = Modifier.reveal(1),
        )

        if (state.settings.apps.isEmpty()) {
            Glass(Modifier.reveal(2).fillMaxWidth(), glow = Dusk.Iris) {
                Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Ico(Icon.Grid, size = 36.dp, color = Dusk.Iris)
                    Spacer(Modifier.height(12.dp))
                    EbText(s.t("No apps yet", "Henüz uygulama yok"), Ink.Headline, align = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    EbText(s.t("Pick the apps where you scroll or watch for long stretches.", "Uzun süre kaydırdığın ya da izlediğin uygulamaları seç."), Ink.Caption, align = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    PillButton(s.t("Add an app", "Uygulama ekle"), onPick, icon = Icon.Plus)
                }
            }
        }

        state.settings.apps.forEachIndexed { i, profile ->
            AppCard(
                profile = profile, compat = state.compat[profile.packageName] ?: CompatUi(),
                nowMs = state.nowMs, expanded = open == profile.packageName,
                onToggle = { open = if (open == profile.packageName) null else profile.packageName },
                actions = actions, modifier = Modifier.reveal(2 + i),
            )
        }

        if (state.settings.apps.isNotEmpty()) {
            EbText(
                s.t("A pause method is only called \"verified\" after a real test on this phone passes - pausing, confirming it paused, resuming, and confirming that too. Until then EyeBlink blinks over the app without touching playback.", "Bir duraklatma yöntemi, bu telefonda gerçek bir test geçilmeden \"doğrulandı\" sayılmaz: duraklat, durduğunu doğrula, devam ettir, onu da doğrula. O zamana dek EyeBlink oynatmaya dokunmadan uygulamanın üstünde göz kırpar."),
                Ink.Caption, color = Dusk.TextFaint, modifier = Modifier.reveal(8).padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun AppCard(profile: AppProfile, compat: CompatUi, nowMs: Long, expanded: Boolean, onToggle: () -> Unit, actions: UiActions, modifier: Modifier) {
    val s = S
    fun set(f: (AppProfile) -> AppProfile) = actions.update { cur -> cur.withApp(f(profile).validated()) }
    Glass(modifier.fillMaxWidth(), glow = if (profile.enabled) Dusk.Iris else null, strength = if (profile.enabled) 1f else 0.6f, onClick = onToggle, padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppAvatar(profile.label.ifBlank { profile.packageName }, profile.packageName)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                EbText(profile.label.ifBlank { profile.packageName }, Ink.Title, maxLines = 1)
                Spacer(Modifier.height(5.dp))
                CompatBadge(compat.status)
            }
            LidSwitch(profile.enabled, { v -> set { it.copy(enabled = v) } }, description = profile.label)
        }
        if (expanded) {
            Spacer(Modifier.height(14.dp))
            Hairline()
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                EbText(s.t("Break frequency", "Mola sıklığı"), Ink.BodyStrong, modifier = Modifier.weight(1f))
                EbText(
                    when {
                        profile.intervalScalePercent < 98 -> s.t("more often", "daha sık")
                        profile.intervalScalePercent > 102 -> s.t("less often", "daha seyrek")
                        else -> s.t("normal", "normal")
                    } + " · ${profile.intervalScalePercent}%",
                    Ink.MonoSmall, color = Dusk.Iris, caps = false,
                )
            }
            Horizon(
                (profile.intervalScalePercent - 50) / 150f, { x -> set { it.copy(intervalScalePercent = (50 + x * 150).roundToInt()) } },
                format = { "${(50 + it * 150).roundToInt()}%" }, steps = 15, description = s.t("Break frequency", "Mola sıklığı"),
            )
            SettingRow(s.t("Smart Rest here", "Burada Akıllı Mola"), subtitle = null) { LidSwitch(profile.restEnabled, { v -> set { it.copy(restEnabled = v) } }, description = "Smart Rest") }
            Hairline()
            SettingRow(s.t("Pause its video", "Videosunu duraklat"), subtitle = s.t("Only if a pause method is verified below.", "Yalnızca aşağıda doğrulanmış bir yöntem varsa.")) { LidSwitch(profile.pauseVideo, { v -> set { it.copy(pauseVideo = v) } }, description = s.t("Pause its video", "Videosunu duraklat")) }
            Spacer(Modifier.height(6.dp))
            Hairline()
            Spacer(Modifier.height(12.dp))
            CompatSection(profile, compat, nowMs, actions)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(s.t("Open", "Aç"), { actions.openApp(profile.packageName) }, kind = ButtonKind.Glass, compact = true, icon = Icon.Play)
                PillButton(s.t("Remove", "Kaldır"), { actions.removeApp(profile.packageName) }, kind = ButtonKind.Ghost, compact = true, icon = Icon.Trash)
            }
        }
    }
}

@Composable
internal fun CompatBadge(status: CompatStatus) {
    val s = S
    when (status) {
        CompatStatus.VERIFIED -> Badge(s.t("pause verified", "duraklatma doğrulandı"), Tone.Ok)
        CompatStatus.UNTESTED -> Badge(s.t("not tested", "denenmedi"), Tone.Quiet)
        CompatStatus.FAILING -> Badge(s.t("can't pause", "duraklatılamıyor"), Tone.Bad)
        CompatStatus.RETEST_ADVISED -> Badge(s.t("retest advised", "yeniden test önerilir"), Tone.Warn)
    }
}

@Composable
private fun CompatSection(profile: AppProfile, compat: CompatUi, nowMs: Long, actions: UiActions) {
    val s = S
    EbText(s.t("Playback control", "Oynatma kontrolü"), Ink.Eyebrow, color = Dusk.Amber)
    Spacer(Modifier.height(8.dp))
    EbText(
        when (compat.status) {
            CompatStatus.VERIFIED -> s.t("Pauses and resumes through ${strategyName(compat.strategy ?: Strategy.MEDIA_SESSION, s).lowercase()}. Tested ${fmtAgo(nowMs - compat.lastTestedMs, s)}.", "${strategyName(compat.strategy ?: Strategy.MEDIA_SESSION, s).lowercase()} ile duraklatır ve sürdürür. ${fmtAgo(nowMs - compat.lastTestedMs, s)} test edildi.")
            CompatStatus.UNTESTED -> s.t("Not tested yet. Until a test passes, EyeBlink blinks over this app without touching its video.", "Henüz denenmedi. Bir test geçilene dek EyeBlink videosuna dokunmadan bu uygulamanın üstünde göz kırpar.")
            CompatStatus.FAILING -> s.t("No pause method worked here. Blinks happen over the playing video - nothing is paused or resumed.", "Burada hiçbir duraklatma yöntemi çalışmadı. Göz kırpmalar oynayan videonun üstünde olur; hiçbir şey duraklatılmaz ya da sürdürülmez.")
            CompatStatus.RETEST_ADVISED -> s.t("The app updated since the last test, so some results may no longer apply.", "Uygulama son testten beri güncellendi; bazı sonuçlar artık geçerli olmayabilir.")
        },
        Ink.Caption,
    )
    if (compat.results.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Flow(Modifier.fillMaxWidth()) {
            compat.results.forEach { r ->
                Badge(
                    "${strategyName(r.strategy, s)}: " + when (r.outcome) {
                        CompatOutcome.PASSED -> s.t("passed", "geçti")
                        CompatOutcome.NOT_PLAYING -> s.t("nothing playing", "oynayan yok")
                        CompatOutcome.PAUSE_NOT_VERIFIED -> s.t("pause not confirmed", "duraklatma doğrulanmadı")
                        CompatOutcome.RESUME_FAILED -> s.t("resume failed", "devam başarısız")
                        CompatOutcome.REFUSED -> s.t("stopped safely", "güvenle durdu")
                        CompatOutcome.ERROR -> s.t("error", "hata")
                    },
                    if (r.passed) Tone.Ok else if (r.outcome == CompatOutcome.NOT_PLAYING) Tone.Quiet else Tone.Bad,
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    if (compat.testing) {
        EbText(s.t("Testing... keep a video playing in the app.", "Test ediliyor... uygulamada bir video oynat."), Ink.Caption, color = Dusk.Iris)
    } else {
        PillButton(s.t("Test compatibility", "Uyumluluğu test et"), { actions.testCompat(profile.packageName) }, icon = Icon.Sparkle, compact = true)
        Spacer(Modifier.height(8.dp))
        EbText(s.t("Open the app, start a video, then tap. EyeBlink pauses it, checks it paused, resumes it and checks that too.", "Uygulamayı aç, bir video başlat, sonra dokun. EyeBlink duraklatır, durduğunu doğrular, devam ettirir ve onu da doğrular."), Ink.Caption, color = Dusk.TextFaint)
    }
}

/** The picker sheet: known apps first, then everything else installed. */
@Composable
fun AppPickerSheet(state: UiState, actions: UiActions, onDone: () -> Unit) {
    val s = S
    val selected = state.settings.apps.map { it.packageName }.toSet()
    EbText(s.t("Choose apps", "Uygulama seç"), Ink.Headline)
    Spacer(Modifier.height(4.dp))
    EbText(s.t("Tap to add or remove. You can fine-tune each one afterwards.", "Eklemek veya kaldırmak için dokun. Sonra her birini ince ayarlayabilirsin."), Ink.Caption)
    Spacer(Modifier.height(16.dp))
    val known = state.apps.filter { it.known }
    val others = state.apps.filterNot { it.known }
    if (known.isNotEmpty()) {
        EbText(s.t("Suggested", "Önerilenler"), Ink.Eyebrow, color = Dusk.Amber)
        known.forEach { PickRow(it.label, it.packageName, it.packageName in selected, actions) }
    }
    if (others.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        EbText(s.t("Installed", "Yüklü"), Ink.Eyebrow, color = Dusk.Amber)
        others.forEach { PickRow(it.label, it.packageName, it.packageName in selected, actions) }
    }
    Spacer(Modifier.height(18.dp))
    PillButton(s.t("Done", "Tamam"), onDone, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun PickRow(label: String, pkg: String, on: Boolean, actions: UiActions) {
    Row(
        Modifier.fillMaxWidth().clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { if (on) actions.removeApp(pkg) else actions.addApp(pkg) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppAvatar(label, pkg, size = 42.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            EbText(label, Ink.BodyStrong, maxLines = 1)
            EbText(pkg, Ink.MonoSmall, color = Dusk.TextFaint, caps = false, maxLines = 1)
        }
        Box(
            Modifier.size(26.dp).clip(CircleShape).let { if (on) it.then(Modifier) else it },
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.Canvas(Modifier.size(26.dp)) {
                if (on) {
                    drawCircle(Dusk.Iris)
                } else {
                    drawCircle(Dusk.HairlineStrong, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                }
            }
            if (on) Ico(Icon.Check, size = 16.dp, color = Color(0xFF04201C), weight = 2.4f)
        }
    }
}

