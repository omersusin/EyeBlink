package com.eyeblink.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eyeblink.app.ui.eye.EyeHero
import com.eyeblink.app.ui.i18n.Strings
import com.eyeblink.app.ui.i18n.fmtClock
import com.eyeblink.app.ui.i18n.fmtSeconds
import com.eyeblink.app.ui.i18n.label
import com.eyeblink.app.ui.kit.Badge
import com.eyeblink.app.ui.kit.ButtonKind
import com.eyeblink.app.ui.kit.Chip
import com.eyeblink.app.ui.kit.Dusk
import com.eyeblink.app.ui.kit.EbFonts
import com.eyeblink.app.ui.kit.EbText
import com.eyeblink.app.ui.kit.Glass
import com.eyeblink.app.ui.kit.Hairline
import com.eyeblink.app.ui.kit.Ico
import com.eyeblink.app.ui.kit.Icon
import com.eyeblink.app.ui.kit.Ink
import com.eyeblink.app.ui.kit.Meter
import com.eyeblink.app.ui.kit.MiniBars
import com.eyeblink.app.ui.kit.PillButton
import com.eyeblink.app.ui.kit.PulseDot
import com.eyeblink.app.ui.kit.S
import com.eyeblink.app.ui.kit.ScreenColumn
import com.eyeblink.app.ui.kit.SectionHeader
import com.eyeblink.app.ui.kit.Tone
import com.eyeblink.app.ui.kit.reveal
import com.eyeblink.app.ui.state.Page
import com.eyeblink.app.ui.state.PermissionKind
import com.eyeblink.app.ui.state.Tab
import com.eyeblink.app.ui.state.UiActions
import com.eyeblink.app.ui.state.UiState
import com.eyeblink.core.engine.BreakKind
import com.eyeblink.core.engine.BreakOutcome
import com.eyeblink.core.engine.FactorCode
import com.eyeblink.core.engine.PauseSummary
import com.eyeblink.core.engine.ResumeSummary
import com.eyeblink.core.engine.TriggerMode
import com.eyeblink.core.model.OperatingMode

/** The wordmark: "Eye" upright, "Blink" in amber italic - one open, one mid-blink. */
@Composable
fun Wordmark(modifier: Modifier = Modifier, size: Int = 28) {
    val text: AnnotatedString = buildAnnotatedString {
        withStyle(SpanStyle(fontFamily = EbFonts.serif, fontSize = size.sp, color = Dusk.Text, letterSpacing = (-0.5).sp)) { append("Eye") }
        withStyle(SpanStyle(fontFamily = EbFonts.serifItalic, fontStyle = FontStyle.Italic, fontSize = size.sp, color = Dusk.Amber, letterSpacing = (-0.5).sp)) { append("Blink") }
    }
    EbText(text, modifier = modifier)
}

@Composable
fun HomeScreen(
    state: UiState,
    actions: UiActions,
    onTab: (Tab) -> Unit,
    onPage: (Page) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = S
    val rt = state.runtime
    val snoozed = rt.snoozedUntilMs > state.nowMs
    val awake = rt.running && !snoozed
    val ready = state.perms.has(PermissionKind.ACCESSIBILITY) || state.perms.has(PermissionKind.OVERLAY)
    val orbit = if (awake && rt.microInMs != null && rt.intervalMs > 0) (rt.microInMs.toFloat() / rt.intervalMs).coerceIn(0f, 1f) else null

    ScreenColumn(modifier, topInset = 10.dp) {
        // ── top bar ─────────────────────────────────────────────
        Row(Modifier.fillMaxWidth().reveal(0).padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Wordmark(Modifier.weight(1f))
            ModeChip(state.settings.mode, state.power.status.available) { onPage(Page.POWER) }
        }

        // ── hero ────────────────────────────────────────────────
        Box(Modifier.reveal(1).fillMaxWidth()) {
            EyeHero(
                awake = awake, orbit = orbit, echo = rt.echo, spec = state.settings.blink,
                description = if (awake) s.t("EyeBlink is awake", "EyeBlink uyanık") else s.t("EyeBlink is resting", "EyeBlink dinleniyor"),
            )
        }

        // ── countdown ───────────────────────────────────────────
        Column(Modifier.reveal(2).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                !rt.running -> {
                    EbText(s.t("Resting", "Dinleniyor"), Ink.DisplayItalic, color = Dusk.TextDim, align = TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    EbText(
                        if (ready) s.t("EyeBlink is asleep. Wake it when you're ready.", "EyeBlink uyuyor. Hazır olunca uyandır.")
                        else s.t("One quick step first: let EyeBlink draw the blink.", "Önce tek bir adım: EyeBlink'in göz kırpmayı çizmesine izin ver."),
                        Ink.Caption, align = TextAlign.Center,
                    )
                }
                snoozed -> {
                    EbText(s.t("Snoozed", "Ertelendi"), Ink.DisplayItalic, color = Dusk.Amber, align = TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    EbText(s.t("Back in ${fmtClock(rt.snoozedUntilMs - state.nowMs)}", "${fmtClock(rt.snoozedUntilMs - state.nowMs)} sonra geri"), Ink.Caption, align = TextAlign.Center)
                }
                rt.idle != null -> {
                    EbText(s.t("Standing by", "Hazırda"), Ink.DisplayItalic, color = Dusk.TextDim, align = TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    EbText(rt.idle.label(s), Ink.Caption, align = TextAlign.Center)
                }
                else -> {
                    EbText(s.t("Next blink in", "Sonraki göz kırpma"), Ink.Eyebrow, color = Dusk.Amber)
                    EbText(rt.microInMs?.let { fmtClock(it) } ?: "—", Ink.Display, align = TextAlign.Center)
                    if (rt.deferred != null) {
                        Spacer(Modifier.height(2.dp))
                        EbText(s.t("Due, waiting: ${rt.deferred.label(s).lowercase()}", "Zamanı geldi, bekliyor: ${rt.deferred.label(s).lowercase()}"), Ink.Caption, color = Dusk.Amber, align = TextAlign.Center)
                    }
                }
            }
        }

        // ── controls ────────────────────────────────────────────
        Column(Modifier.reveal(3).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton(
                text = when {
                    !rt.running && !ready -> s.t("Finish setup", "Kurulumu tamamla")
                    !rt.running -> s.t("Wake EyeBlink", "EyeBlink'i uyandır")
                    else -> s.t("Let it rest", "Dinlendir")
                },
                onClick = {
                    if (!rt.running && !ready) actions.request(PermissionKind.ACCESSIBILITY) else actions.setRunning(!rt.running)
                },
                kind = if (rt.running) ButtonKind.Glass else ButtonKind.Primary,
                icon = if (rt.running) Icon.EyeClosed else Icon.Eye,
                modifier = Modifier.fillMaxWidth(),
            )
            if (rt.running) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickAction(s.t("Blink", "Kırp"), Icon.Eye, Modifier.weight(1f)) { actions.blinkNow() }
                    QuickAction(s.t("Rest", "Mola"), Icon.Moon, Modifier.weight(1f)) { actions.restNow() }
                    if (snoozed) QuickAction(s.t("Resume", "Sürdür"), Icon.Play, Modifier.weight(1f)) { actions.cancelSnooze() }
                    else QuickAction(s.t("Snooze", "Ertele"), Icon.Clock, Modifier.weight(1f)) { actions.snooze(30) }
                }
            }
        }

        // ── setup checklist ─────────────────────────────────────
        val missing = missingSetup(state)
        if (missing.isNotEmpty()) {
            Glass(Modifier.reveal(4).fillMaxWidth(), glow = Dusk.Amber) {
                SectionHeader(s.t("Setup", "Kurulum"), null)
                Spacer(Modifier.height(10.dp))
                missing.forEachIndexed { i, item ->
                    if (i > 0) Hairline()
                    Row(Modifier.fillMaxWidth().clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { actions.request(item.kind) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(34.dp).clip(CircleShape).then(Modifier), contentAlignment = Alignment.Center) { Ico(item.icon, size = 22.dp, color = Dusk.Amber) }
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            EbText(item.title, Ink.BodyStrong)
                            EbText(item.why, Ink.Caption)
                        }
                        Badge(if (item.required) s.t("needed", "gerekli") else s.t("optional", "isteğe bağlı"), if (item.required) Tone.Amber else Tone.Quiet, dot = false)
                    }
                }
            }
        }

        // ── right now ───────────────────────────────────────────
        Glass(Modifier.reveal(5).fillMaxWidth(), glow = Dusk.Iris) {
            SectionHeader(s.t("Right now", "Şu an"), null)
            Spacer(Modifier.height(10.dp))
            FactRow(s.t("App", "Uygulama"), rt.foregroundLabel ?: "—")
            Hairline()
            FactRow(s.t("Context", "Bağlam"), rt.context?.let { contextName(it, s) } ?: "—")
            Hairline()
            FactRow(s.t("Trigger", "Tetikleyici"), when (rt.triggerMode) {
                TriggerMode.CAMERA_ASSISTED -> s.t("Camera-assisted", "Kamera destekli")
                TriggerMode.CAMERA_FREE -> s.t("Timer", "Zamanlayıcı")
            })
            Hairline()
            FactRow(s.t("Interval", "Aralık"), fmtSeconds((rt.intervalMs / 1000).toInt(), s))
            val shown = rt.factors.filter { it.code != FactorCode.BASE }
            if (shown.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                com.eyeblink.app.ui.kit.Flow(Modifier.fillMaxWidth()) {
                    shown.take(4).forEach { f -> Badge("${factorName(f.code, s)} ×${"%.2f".format(f.multiplier)}", if (f.observed) Tone.Iris else Tone.Quiet, dot = f.observed) }
                }
            }
            Spacer(Modifier.height(12.dp))
            EbText(
                if (rt.triggerMode == TriggerMode.CAMERA_ASSISTED) s.t("Blinks are being observed by the camera. Everything stays on this phone.", "Göz kırpmaların kamerayla gözlemleniyor. Her şey bu telefonda kalıyor.")
                else s.t("EyeBlink is on a timer. It can't tell whether you actually blinked.", "EyeBlink bir zamanlayıcıyla çalışıyor. Gerçekten göz kırpıp kırpmadığını bilemez."),
                Ink.Caption, color = Dusk.TextFaint,
            )
        }

        // ── last break ──────────────────────────────────────────
        rt.lastBreak?.let { br ->
            Glass(Modifier.reveal(6).fillMaxWidth()) {
                SectionHeader(s.t("Last break", "Son mola"), null)
                Spacer(Modifier.height(8.dp))
                EbText(
                    when {
                        br.outcome == BreakOutcome.ABORTED -> s.t("Skipped: ${br.abortedBy?.label(s)?.lowercase() ?: ""}", "Atlandı: ${br.abortedBy?.label(s)?.lowercase() ?: ""}")
                        br.kind == BreakKind.MICRO -> s.t("Micro Blink", "Mikro Göz Kırpma")
                        else -> s.t("Smart Rest", "Akıllı Mola")
                    },
                    Ink.Title,
                )
                Spacer(Modifier.height(4.dp))
                EbText(
                    when {
                        br.pause == PauseSummary.PAUSED && br.resume == ResumeSummary.RESUMED -> s.t("Video paused, verified, and resumed where it was.", "Video duraklatıldı, doğrulandı ve kaldığı yerden devam etti.")
                        br.pause == PauseSummary.PAUSED && br.resume == ResumeSummary.REFUSED -> s.t("Paused, then left alone because you moved on.", "Duraklatıldı; sen devam ettiğin için dokunulmadı.")
                        br.pause == PauseSummary.NOT_PLAYING -> s.t("Nothing was playing, so nothing was touched.", "Oynatılan bir şey yoktu, hiçbir şeye dokunulmadı.")
                        br.pause == PauseSummary.UNVERIFIED -> s.t("The pause could not be verified.", "Duraklatma doğrulanamadı.")
                        br.pause == PauseSummary.NOT_ATTEMPTED && br.outcome != BreakOutcome.ABORTED -> s.t("The video kept playing underneath. No verified pause exists for this app yet.", "Video altta oynamaya devam etti. Bu uygulama için henüz doğrulanmış duraklatma yok.")
                        else -> ""
                    },
                    Ink.Caption,
                )
            }
        }

        // ── today ───────────────────────────────────────────────
        Glass(Modifier.reveal(7).fillMaxWidth()) {
            SectionHeader(s.t("Today", "Bugün"), null, trailing = {
                EbText(s.t("see all", "tümü"), Ink.Caption, color = Dusk.Iris, modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onPage(Page.STATS) })
            })
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat(state.stats.today.microShown.toString(), s.t("blinks", "kırpma"), Modifier.weight(1f))
                Stat(state.stats.today.restsCompleted.toString(), s.t("rests", "mola"), Modifier.weight(1f))
                Stat((state.stats.today.activeMs / 60_000).toString(), s.t("min active", "dk aktif"), Modifier.weight(1f))
            }
            if (state.stats.series.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                MiniBars(state.stats.series.map { (it.microShown + it.restsCompleted * 3).toFloat() }, state.stats.seriesLabels)
            }
        }
    }
}

@Composable
private fun QuickAction(label: String, icon: Icon, modifier: Modifier, onClick: () -> Unit) {
    Chip(text = label, selected = false, onClick = onClick, icon = icon, modifier = modifier)
}

@Composable
private fun ModeChip(mode: OperatingMode, active: Boolean, onClick: () -> Unit) {
    val s = S
    val label = when (mode) {
        OperatingMode.ROOTLESS -> s.t("Rootless", "Rootsuz")
        OperatingMode.SHIZUKU -> "Shizuku"
        OperatingMode.ROOT -> "Root"
    }
    val icon = when (mode) { OperatingMode.ROOTLESS -> Icon.Shield; OperatingMode.SHIZUKU -> Icon.Link; OperatingMode.ROOT -> Icon.Terminal }
    Chip(label, selected = active && mode != OperatingMode.ROOTLESS, onClick = onClick, icon = icon)
}

@Composable
private fun FactRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        EbText(label, Ink.Eyebrow, modifier = Modifier.width(96.dp))
        EbText(value, Ink.BodyStrong, modifier = Modifier.weight(1f), align = TextAlign.End, maxLines = 1)
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        EbText(value, Ink.Headline)
        EbText(label, Ink.Eyebrow, caps = false)
    }
}

internal fun contextName(c: com.eyeblink.core.calibration.ContextProfile, s: Strings) = when (c) {
    com.eyeblink.core.calibration.ContextProfile.VIDEO -> s.t("Watching video", "Video izliyor")
    com.eyeblink.core.calibration.ContextProfile.READING -> s.t("Reading", "Okuyor")
    com.eyeblink.core.calibration.ContextProfile.SOCIAL -> s.t("Scrolling", "Kaydırıyor")
    com.eyeblink.core.calibration.ContextProfile.LOW_LIGHT -> s.t("Low light", "Loş ışık")
}

internal fun factorName(c: FactorCode, s: Strings) = when (c) {
    FactorCode.BASE -> s.t("base", "temel")
    FactorCode.APP_SCALE -> s.t("this app", "bu uygulama")
    FactorCode.PERSONAL_BASELINE -> s.t("your baseline", "temel çizgin")
    FactorCode.READING_CONTEXT -> s.t("reading", "okuma")
    FactorCode.LOW_LIGHT -> s.t("low light", "loş ışık")
    FactorCode.LONG_SESSION -> s.t("long session", "uzun oturum")
    FactorCode.OBSERVED_LOW_RATE -> s.t("fewer blinks seen", "az kırpma görüldü")
    FactorCode.OBSERVED_HEALTHY -> s.t("blinking well", "iyi kırpıyor")
    FactorCode.SKIP_BACKOFF -> s.t("you skipped", "atladın")
    FactorCode.JITTER -> s.t("variation", "çeşitlilik")
}

private data class SetupItem(val kind: PermissionKind, val icon: Icon, val title: String, val why: String, val required: Boolean)

private fun missingSetup(state: UiState): List<SetupItem> {
    val s = if (state.settings.language == com.eyeblink.core.model.Language.TR) Strings.TR else Strings.EN
    val p = state.perms
    val out = mutableListOf<SetupItem>()
    if (!p.has(PermissionKind.ACCESSIBILITY) && !p.has(PermissionKind.OVERLAY)) {
        out += SetupItem(PermissionKind.ACCESSIBILITY, Icon.Eye, s.t("Draw the blink", "Göz kırpmayı çiz"), s.t("Accessibility lets EyeBlink draw over everything, including the status bar.", "Erişilebilirlik, EyeBlink'in durum çubuğu dahil her şeyin üstüne çizmesini sağlar."), true)
    }
    if (!p.has(PermissionKind.NOTIFICATION_LISTENER)) {
        out += SetupItem(PermissionKind.NOTIFICATION_LISTENER, Icon.Play, s.t("Pause and resume videos", "Videoları duraklat ve sürdür"), s.t("Lets EyeBlink see media sessions so it can pause safely and give your video back.", "Medya oturumlarını görerek güvenle duraklatıp videonu geri vermesini sağlar."), false)
    }
    if (!p.has(PermissionKind.NOTIFICATIONS)) {
        out += SetupItem(PermissionKind.NOTIFICATIONS, Icon.Info, s.t("Show that it's running", "Çalıştığını göster"), s.t("A quiet notification with a stop button.", "Durdurma düğmeli sessiz bir bildirim."), false)
    }
    return out
}
