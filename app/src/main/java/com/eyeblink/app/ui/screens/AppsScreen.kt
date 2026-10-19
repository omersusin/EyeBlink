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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eyeblink.app.ui.i18n.Strings
import com.eyeblink.app.ui.i18n.fmtAgo
import com.eyeblink.app.ui.kit.AppAvatar
import com.eyeblink.app.ui.kit.Badge
import com.eyeblink.app.ui.kit.ButtonKind
import com.eyeblink.app.ui.kit.P
import com.eyeblink.app.ui.kit.EbText
import com.eyeblink.app.ui.kit.Flow
import com.eyeblink.app.ui.kit.Card
import com.eyeblink.app.ui.kit.CheckDot
import com.eyeblink.app.ui.kit.IconBadge
import com.eyeblink.app.ui.kit.Hairline
import com.eyeblink.app.ui.kit.Horizon
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
            RoundIconButton(Icon.Plus, s.t("Add an app", "Uygulama ekle"), onPick, hue = P.sun)
        }
        EbText(
            s.t("EyeBlink only ever interrupts the apps on this list. Anywhere else - calls, payments, sign-in screens, your camera - it stays out of the way.", "EyeBlink yalnızca bu listedeki uygulamalara müdahale eder. Başka her yerde (aramalar, ödemeler, giriş ekranları, kameran) yoldan çekilir."),
            Ink.Caption, modifier = Modifier.reveal(1),
        )

        if (state.settings.apps.isEmpty()) {
            Card(Modifier.reveal(2).fillMaxWidth(), tint = P.sky) {
                Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    IconBadge(Icon.Grid, P.sky, size = 58.dp)
                    Spacer(Modifier.height(14.dp))
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
                s.t(
                    "A way of pausing is called \"verified\" only after it has worked on this phone: pausing, confirming that the video paused, resuming, and confirming that too. A way that can read the player back (a media session, through notification access, Shizuku or Root) is tried at an app's first break and remembered; every other way needs the test below first.",
                    "Bir duraklatma yolu, bu telefonda çalıştıktan sonra \"doğrulandı\" sayılır: duraklat, videonun durduğunu doğrula, devam ettir, onu da doğrula. Oynatıcıyı geri okuyabilen bir yol (bildirim erişimi, Shizuku ya da Root ile medya oturumu) uygulamanın ilk molasında denenir ve hatırlanır; diğer yollar için önce aşağıdaki test gerekir.",
                ),
                Ink.Caption, color = P.inkFaint, modifier = Modifier.reveal(8).padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun AppCard(profile: AppProfile, compat: CompatUi, nowMs: Long, expanded: Boolean, onToggle: () -> Unit, actions: UiActions, modifier: Modifier) {
    val s = S
    fun set(f: (AppProfile) -> AppProfile) = actions.update { cur -> cur.withApp(f(profile).validated()) }
    Card(modifier.fillMaxWidth().alpha(if (profile.enabled) 1f else 0.7f), onClick = onToggle, padding = PaddingValues(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppAvatar(profile.label.ifBlank { profile.packageName }, profile.packageName)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                EbText(profile.label.ifBlank { profile.packageName }, Ink.Title, maxLines = 1)
                Spacer(Modifier.height(5.dp))
                CompatBadge(compat.status, trying = compat.trial != null, noSession = compat.noSession)
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
                    Ink.NumSmall, color = P.sky.text, caps = false,
                )
            }
            Horizon(
                (profile.intervalScalePercent - 50) / 150f, { x -> set { it.copy(intervalScalePercent = (50 + x * 150).roundToInt()) } },
                format = { "${(50 + it * 150).roundToInt()}%" }, steps = 15, description = s.t("Break frequency", "Mola sıklığı"),
            )
            SettingRow(s.t("Smart Rest here", "Burada Akıllı Mola"), subtitle = null) { LidSwitch(profile.restEnabled, { v -> set { it.copy(restEnabled = v) } }, description = "Smart Rest") }
            Hairline()
            SettingRow(s.t("Pause its video", "Videosunu duraklat"), subtitle = s.t("Only a video EyeBlink has seen playing, and only if a way of pausing it works (see below).", "Yalnızca EyeBlink'in oynadığını gördüğü video, ve yalnızca duraklatmanın bir yolu çalışıyorsa (aşağıya bak).")) { LidSwitch(profile.pauseVideo, { v -> set { it.copy(pauseVideo = v) } }, description = s.t("Pause its video", "Videosunu duraklat")) }
            Spacer(Modifier.height(6.dp))
            Hairline()
            Spacer(Modifier.height(12.dp))
            CompatSection(profile, compat, nowMs, actions)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(s.t("Open", "Aç"), { actions.openApp(profile.packageName) }, kind = ButtonKind.Soft, compact = true, icon = Icon.Play)
                PillButton(s.t("Remove", "Kaldır"), { actions.removeApp(profile.packageName) }, kind = ButtonKind.Ghost, compact = true, icon = Icon.Trash)
            }
        }
    }
}

@Composable
internal fun CompatBadge(status: CompatStatus, trying: Boolean = false, noSession: Boolean = false) {
    val s = S
    when (status) {
        CompatStatus.VERIFIED -> Badge(s.t("pause verified", "duraklatma doğrulandı"), Tone.Ok)
        CompatStatus.UNTESTED ->
            if (noSession) Badge(s.t("no media session", "medya oturumu yok"), Tone.Quiet)
            else if (trying) Badge(s.t("will try", "denenecek"), Tone.Sky)
            else Badge(s.t("no way yet", "yol henüz yok"), Tone.Quiet)
        CompatStatus.FAILING -> Badge(s.t("can't pause", "duraklatılamıyor"), Tone.Bad)
        CompatStatus.RETEST_ADVISED -> Badge(s.t("retest advised", "yeniden test önerilir"), Tone.Warn)
    }
}

@Composable
private fun CompatSection(profile: AppProfile, compat: CompatUi, nowMs: Long, actions: UiActions) {
    val s = S
    // None of the experimental ways is on (they are off until switched on under Rhythm -> Your video).
    val otherWaysOff = compat.available.none { it.experimental }
    val otherWays = s.t(
        " Other ways - audio focus, a media key, a tap gesture - can pause some of these apps. They are experimental and off: switch them on under Rhythm → Your video → Other ways to pause (Fine-tune everything), then run the test below.",
        " Başka yollar - ses odağı, medya tuşu, dokunma hareketi - bu uygulamalardan bazılarını duraklatabilir. Deneysel ve kapalıdır: Ritim → Videon → Duraklatmanın öbür yolları altında aç (her şeyi ince ayarla), sonra aşağıdaki testi çalıştır.",
    )
    EbText(s.t("Playback control", "Oynatma kontrolü"), Ink.Eyebrow, color = P.sky.text)
    Spacer(Modifier.height(8.dp))
    EbText(
        when (compat.status) {
            CompatStatus.VERIFIED -> s.t("Pauses and resumes through ${strategyName(compat.strategy ?: Strategy.MEDIA_SESSION, s).lowercase()}. Last worked ${fmtAgo(nowMs - compat.lastTestedMs, s)}.", "${strategyName(compat.strategy ?: Strategy.MEDIA_SESSION, s).lowercase()} ile duraklatır ve sürdürür. En son ${fmtAgo(nowMs - compat.lastTestedMs, s)} çalıştı.")
            CompatStatus.UNTESTED ->
                if (compat.noSession) s.t(
                    "This app shows no media session, so EyeBlink cannot see or pause what it plays. Blinks happen over the video, or follow the choice under Rhythm → Your video. If that changes (an update, a new permission), it looks again by itself.",
                    "Bu uygulama medya oturumu göstermiyor; EyeBlink neyin oynadığını göremez ve duraklatamaz. Göz kırpmalar videonun üstünde olur ya da Ritim → Videon altındaki seçime uyar. Bu değişirse (güncelleme, yeni izin) kendiliğinden yeniden bakar.",
                ) + (if (otherWaysOff) otherWays else "") else if (compat.trial != null) s.t(
                    "Not proven yet. At the next break EyeBlink will try to pause this app's video through ${strategyName(compat.trial, s).lowercase()}, check that it really paused, and remember how it went.",
                    "Henüz kanıtlanmadı. Bir sonraki molada EyeBlink bu uygulamanın videosunu ${strategyName(compat.trial, s).lowercase()} ile duraklatmayı dener, gerçekten durduğunu kontrol eder ve sonucu hatırlar.",
                ) else s.t(
                    "EyeBlink has no way to pause this app's video yet. Allow notification access (More → Diagnostics → Permissions), or use Shizuku or Root (More → Power modes), and it will try at the next break. Until then it blinks over the video.",
                    "EyeBlink'in bu uygulamanın videosunu duraklatacak bir yolu henüz yok. Bildirim erişimine izin ver (Diğer → Tanılama → İzinler) ya da Shizuku veya Root kullan (Diğer → Güç modları); sonraki molada dener. O zamana dek videonun üstünde göz kırpar.",
                )
            CompatStatus.FAILING -> s.t("No pause method worked here. Blinks happen over the playing video - nothing is paused or resumed.", "Burada hiçbir duraklatma yöntemi çalışmadı. Göz kırpmalar oynayan videonun üstünde olur; hiçbir şey duraklatılmaz ya da sürdürülmez.") + (if (otherWaysOff) otherWays else "")
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
                        CompatOutcome.NO_SESSION -> s.t("no media session", "medya oturumu yok")
                        CompatOutcome.UNREADABLE -> s.t("could not read it", "okunamadı")
                        CompatOutcome.PAUSE_NOT_VERIFIED -> s.t("pause not confirmed", "duraklatma doğrulanmadı")
                        CompatOutcome.RESUME_FAILED -> s.t("resume failed", "devam başarısız")
                        CompatOutcome.REFUSED -> s.t("stopped safely", "güvenle durdu")
                        CompatOutcome.ERROR -> s.t("error", "hata")
                    },
                    if (r.passed) Tone.Ok else if (r.outcome.sawNothing) Tone.Quiet else Tone.Bad,
                )
            }
        }
        // The technical reason, as the strategy reported it, for whoever needs to see why a way did not work.
        compat.results.filter { !it.passed && !it.note.isNullOrBlank() && it.outcome != CompatOutcome.REFUSED }.forEach { r ->
            Spacer(Modifier.height(4.dp))
            EbText("${strategyName(r.strategy, s)}: ${r.note}", Ink.Caption, color = P.inkFaint, caps = false)
        }
    }
    Spacer(Modifier.height(12.dp))
    if (compat.testing) {
        EbText(s.t("Testing... keep a video playing in the app.", "Test ediliyor... uygulamada bir video oynat."), Ink.Caption, color = P.sky.text)
    } else {
        PillButton(s.t("Test compatibility", "Uyumluluğu test et"), { actions.testCompat(profile.packageName) }, icon = Icon.Sparkle, compact = true)
        Spacer(Modifier.height(8.dp))
        EbText(s.t("Open the app, start a video, then tap. EyeBlink pauses it, checks it paused, resumes it and checks that too.", "Uygulamayı aç, bir video başlat, sonra dokun. EyeBlink duraklatır, durduğunu doğrular, devam ettirir ve onu da doğrular."), Ink.Caption, color = P.inkFaint)
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
        EbText(s.t("Suggested", "Önerilenler"), Ink.Eyebrow, color = P.sky.text)
        known.forEach { PickRow(it.label, it.packageName, it.packageName in selected, actions) }
    }
    if (others.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        EbText(s.t("Installed", "Yüklü"), Ink.Eyebrow, color = P.sky.text)
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
            EbText(pkg, Ink.NumSmall, color = P.inkFaint, caps = false, maxLines = 1)
        }
        CheckDot(on)
    }
}

