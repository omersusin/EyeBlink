package com.eyeblink.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eyeblink.app.ui.i18n.fmtMinutes
import com.eyeblink.app.ui.i18n.fmtSeconds
import com.eyeblink.app.ui.kit.Badge
import com.eyeblink.app.ui.kit.ButtonKind
import com.eyeblink.app.ui.kit.Dusk
import com.eyeblink.app.ui.kit.EbText
import com.eyeblink.app.ui.kit.Glass
import com.eyeblink.app.ui.kit.Hairline
import com.eyeblink.app.ui.kit.Horizon
import com.eyeblink.app.ui.kit.Icon
import com.eyeblink.app.ui.kit.Ink
import com.eyeblink.app.ui.kit.LidSwitch
import com.eyeblink.app.ui.kit.PillButton
import com.eyeblink.app.ui.kit.S
import com.eyeblink.app.ui.kit.ScreenColumn
import com.eyeblink.app.ui.kit.SectionHeader
import com.eyeblink.app.ui.kit.SettingRow
import com.eyeblink.app.ui.kit.Stepper
import com.eyeblink.app.ui.kit.Tide
import com.eyeblink.app.ui.kit.Tone
import com.eyeblink.app.ui.kit.reveal
import com.eyeblink.app.ui.state.Page
import com.eyeblink.app.ui.state.UiActions
import com.eyeblink.app.ui.state.UiState
import com.eyeblink.core.engine.TriggerMode
import com.eyeblink.core.model.ConfirmMode
import com.eyeblink.core.model.EyeBlinkSettings
import com.eyeblink.core.model.NoPauseFallback
import com.eyeblink.core.model.RestStyle
import com.eyeblink.core.model.RhythmPreset
import com.eyeblink.core.model.RhythmSettings
import com.eyeblink.core.model.TrackingMode
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** Position (0..1) <-> value on a logarithmic scale, so 5 s and 5 min both get room on one slider. */
internal fun logToValue(x: Float, min: Float, max: Float): Float = min * (max / min).pow(x.coerceIn(0f, 1f))
internal fun logFromValue(v: Float, min: Float, max: Float): Float = (ln(v.coerceIn(min, max) / min) / ln(max / min))

@Composable
fun RhythmScreen(state: UiState, actions: UiActions, onPage: (Page) -> Unit, modifier: Modifier = Modifier) {
    val s = S
    val st = state.settings
    val r = st.rhythm
    fun setR(f: (RhythmSettings) -> RhythmSettings) = actions.update { it.copy(rhythm = f(it.rhythm.copy(preset = RhythmPreset.CUSTOM)).validated()) }
    fun setS(f: (EyeBlinkSettings) -> EyeBlinkSettings) = actions.update { f(it).validated() }

    ScreenColumn(modifier) {
        Box(Modifier.reveal(0)) { SectionHeader(s.t("Rhythm", "Ritim"), s.t("Find your pace", "Kendi temponu bul")) }

        Column(Modifier.reveal(1), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val presetIndex = when (r.preset) { RhythmPreset.GENTLE -> 0; RhythmPreset.BALANCED -> 1; RhythmPreset.ACTIVE -> 2; RhythmPreset.CUSTOM -> -1 }
            Tide(listOf(s.t("Gentle", "Hafif"), s.t("Balanced", "Dengeli"), s.t("Active", "Sık")), presetIndex.coerceAtLeast(0), { i ->
                val p = listOf(RhythmPreset.GENTLE, RhythmPreset.BALANCED, RhythmPreset.ACTIVE)[i]
                setS { it.copy(rhythm = RhythmSettings.preset(p, it.rhythm)) }
            })
            EbText(
                when (r.preset) {
                    RhythmPreset.GENTLE -> s.t("A blink every 90 s, a full rest every 30 min.", "90 sn'de bir göz kırpma, 30 dk'da bir tam mola.")
                    RhythmPreset.BALANCED -> s.t("A blink every 45 s, a full rest every 20 min.", "45 sn'de bir göz kırpma, 20 dk'da bir tam mola.")
                    RhythmPreset.ACTIVE -> s.t("A blink every 25 s, a full rest every 15 min.", "25 sn'de bir göz kırpma, 15 dk'da bir tam mola.")
                    RhythmPreset.CUSTOM -> s.t("Custom: your own numbers.", "Özel: kendi sayıların.")
                },
                Ink.Caption,
            )
            Glass(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 6.dp)) {
                SettingRow(s.t("Fine-tune everything", "Her şeyi ince ayarla"), subtitle = s.t("Simple and automatic by default. Switch on for every control.", "Varsayılan olarak basit ve otomatik. Tüm ayarlar için aç.")) {
                    LidSwitch(st.advanced, { v -> setS { it.copy(advanced = v) } }, description = s.t("Advanced", "Gelişmiş"))
                }
            }
        }

        // ── Micro Blink ─────────────────────────────────────────
        Glass(Modifier.reveal(2).fillMaxWidth(), glow = Dusk.Iris) {
            SectionHeader(s.t("Micro Blink", "Mikro Göz Kırpma"), null)
            Spacer(Modifier.height(6.dp))
            EbText(s.t("The half-second screen blink.", "Yarım saniyelik ekran göz kırpması."), Ink.Caption)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                EbText(s.t("every", "her"), Ink.Eyebrow, caps = false, modifier = Modifier.padding(bottom = 10.dp, end = 8.dp))
                EbText(fmtSeconds(r.microIntervalSec, s), Ink.Headline)
            }
            Horizon(
                logFromValue(r.microIntervalSec.toFloat(), 5f, 600f), { x -> setR { it.copy(microIntervalSec = logToValue(x, 5f, 600f).roundToInt()) } },
                format = { fmtSeconds(logToValue(it, 5f, 600f).roundToInt(), s) }, description = s.t("Micro blink interval", "Mikro göz kırpma aralığı"),
            )
            Hairline()
            SettingRow(
                s.t("Adapt to me", "Bana uyarla"),
                subtitle = s.t("Stretch or shorten the interval from your baseline, what you are doing, how long you have been at it, and - if the camera is on - what it actually sees.", "Aralığı temel çizgine, ne yaptığına, ne kadar süredir kullandığına ve kamera açıksa gerçekten gördüklerine göre uzatır veya kısaltır."),
            ) { LidSwitch(r.adaptive, { v -> setR { it.copy(adaptive = v) } }, description = s.t("Adaptive", "Uyarlanabilir")) }
            if (r.adaptive && state.runtime.factors.size > 1) {
                Spacer(Modifier.height(4.dp))
                EbText(s.t("Right now it is shaped by", "Şu an şunlarla şekilleniyor"), Ink.Eyebrow, caps = false)
                Spacer(Modifier.height(8.dp))
                com.eyeblink.app.ui.kit.Flow(Modifier.fillMaxWidth()) {
                    state.runtime.factors.filter { it.code != com.eyeblink.core.engine.FactorCode.BASE }.take(4).forEach { f ->
                        Badge("${factorName(f.code, s)} ×${"%.2f".format(f.multiplier)}", if (f.observed) Tone.Iris else Tone.Quiet, dot = f.observed)
                    }
                }
                Spacer(Modifier.height(6.dp))
                EbText(s.t("Teal means measured by the camera; grey is an estimate.", "Turkuaz kamerayla ölçüldü demek; gri tahmindir."), Ink.Caption, color = Dusk.TextFaint)
            }
        }

        // ── Smart Rest ──────────────────────────────────────────
        Glass(Modifier.reveal(3).fillMaxWidth(), glow = Dusk.Amber) {
            SettingRow(
                s.t("Smart Rest", "Akıllı Mola"),
                subtitle = s.t("A longer break that asks you to look away - the 20-20-20 idea.", "Uzağa bakmanı isteyen daha uzun bir mola: 20-20-20 fikri."),
            ) { LidSwitch(r.restEnabled, { v -> setR { it.copy(restEnabled = v) } }, description = "Smart Rest") }
            if (r.restEnabled) {
                Hairline()
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    EbText(s.t("every", "her"), Ink.Eyebrow, caps = false, modifier = Modifier.padding(bottom = 10.dp, end = 8.dp))
                    EbText(fmtMinutes(r.restIntervalMin, s), Ink.Headline)
                }
                Horizon(
                    logFromValue(r.restIntervalMin.toFloat(), 5f, 120f), { x -> setR { it.copy(restIntervalMin = logToValue(x, 5f, 120f).roundToInt()) } },
                    format = { fmtMinutes(logToValue(it, 5f, 120f).roundToInt(), s) }, description = s.t("Rest interval", "Mola aralığı"),
                )
                Spacer(Modifier.height(8.dp))
                Tide(listOf(s.t("Distance focus", "Uzağa odak"), s.t("Blackout", "Karartma")), if (st.rest.style == RestStyle.DISTANCE_FOCUS) 0 else 1, { i ->
                    setS { it.copy(rest = it.rest.copy(style = if (i == 0) RestStyle.DISTANCE_FOCUS else RestStyle.BLACKOUT)) }
                })
                Spacer(Modifier.height(6.dp))
                EbText(
                    if (st.rest.style == RestStyle.DISTANCE_FOCUS) s.t("A calm countdown while you look at something far away.", "Uzaktaki bir şeye bakarken sakin bir geri sayım.")
                    else s.t("The screen stays pure black for the whole rest.", "Ekran mola boyunca saf siyah kalır."),
                    Ink.Caption,
                )
                Spacer(Modifier.height(10.dp))
                val dur = st.rest.effectiveDurationSec
                Row(Modifier.fillMaxWidth()) {
                    EbText(s.t("Length", "Süre"), Ink.BodyStrong, modifier = Modifier.weight(1f))
                    EbText(fmtSeconds(dur, s), Ink.MonoSmall, color = Dusk.Amber, caps = false)
                }
                Horizon(
                    ((dur - 5) / 115f).coerceIn(0f, 1f), { x ->
                        val v = (5 + x * 115).roundToInt()
                        setS { it.copy(rest = if (it.rest.style == RestStyle.BLACKOUT) it.rest.copy(blackoutDurationSec = v) else it.rest.copy(durationSec = v)) }
                    },
                    format = { fmtSeconds((5 + it * 115).roundToInt(), s) }, description = s.t("Rest length", "Mola süresi"), accent = Dusk.Amber,
                )
                Spacer(Modifier.height(4.dp))
                EbText(s.t("When it ends", "Bittiğinde"), Ink.Eyebrow, caps = false)
                Spacer(Modifier.height(8.dp))
                Tide(listOf(s.t("Just end", "Kendiliğinden"), s.t("Tap", "Dokun"), s.t("Blink", "Göz kırp")), st.rest.confirm.ordinal, { i ->
                    setS { it.copy(rest = it.rest.copy(confirm = ConfirmMode.entries[i])) }
                })
                Spacer(Modifier.height(6.dp))
                EbText(s.t("Blink needs the camera; without it EyeBlink asks for a tap instead. One blink or one tap is always enough, and you can skip any rest.", "Göz kırpma için kamera gerekir; yoksa EyeBlink dokunma ister. Tek bir kırpma ya da dokunuş her zaman yeter ve her molayı atlayabilirsin."), Ink.Caption, color = Dusk.TextFaint)
            }
        }

        // ── Camera ──────────────────────────────────────────────
        Glass(Modifier.reveal(4).fillMaxWidth()) {
            SectionHeader(s.t("Camera", "Kamera"), null, trailing = {
                Badge(
                    if (state.runtime.triggerMode == TriggerMode.CAMERA_ASSISTED) s.t("observing", "gözlemliyor") else s.t("timer only", "yalnızca zamanlayıcı"),
                    if (state.runtime.triggerMode == TriggerMode.CAMERA_ASSISTED) Tone.Iris else Tone.Quiet,
                )
            })
            Spacer(Modifier.height(12.dp))
            Tide(listOf(s.t("Off", "Kapalı"), s.t("Bursts", "Aralıklı"), s.t("Always", "Sürekli")), st.camera.tracking.ordinal, { i ->
                setS { it.copy(camera = it.camera.copy(tracking = TrackingMode.entries[i])) }
            })
            Spacer(Modifier.height(8.dp))
            EbText(
                when (st.camera.tracking) {
                    TrackingMode.OFF -> s.t("EyeBlink runs on a personal timer. It never claims to see your blinks.", "EyeBlink kişisel bir zamanlayıcıyla çalışır. Göz kırpmalarını gördüğünü asla iddia etmez.")
                    TrackingMode.BURST -> s.t("The front camera looks for a few seconds at a time, a few times a minute. Gentle on the battery.", "Ön kamera dakikada birkaç kez, birkaç saniyeliğine bakar. Pil dostu.")
                    TrackingMode.CONTINUOUS -> s.t("The camera runs while a selected app is open. Most accurate, most power.", "Seçili bir uygulama açıkken kamera çalışır. En doğru, en çok güç.")
                },
                Ink.Caption,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(
                    if (state.personal.isCalibrated) s.t("Recalibrate", "Yeniden kalibre et") else s.t("Calibrate", "Kalibre et"),
                    { onPage(Page.CALIBRATION) }, kind = ButtonKind.Glass, icon = Icon.Eye, compact = true,
                )
            }
            Spacer(Modifier.height(10.dp))
            EbText(s.t("Frames are analysed on this phone and thrown away. No video is saved and nothing is uploaded. A visible indicator shows whenever the camera is on.", "Kareler bu telefonda analiz edilip atılır. Video kaydedilmez, hiçbir şey yüklenmez. Kamera açıkken görünür bir gösterge belirir."), Ink.Caption, color = Dusk.TextFaint)
        }

        // ── Playback ────────────────────────────────────────────
        Glass(Modifier.reveal(5).fillMaxWidth()) {
            SectionHeader(s.t("Your video", "Videon"), null)
            Spacer(Modifier.height(4.dp))
            SettingRow(s.t("Pause during breaks", "Molalarda duraklat"), subtitle = s.t("Only when EyeBlink can prove the pause worked for that app.", "Yalnızca EyeBlink o uygulamada duraklatmanın işlediğini kanıtlayabiliyorsa.")) {
                LidSwitch(st.playback.pauseDuringBreaks, { v -> setS { it.copy(playback = it.playback.copy(pauseDuringBreaks = v)) } }, description = s.t("Pause during breaks", "Molalarda duraklat"))
            }
            Hairline()
            SettingRow(s.t("Resume afterwards", "Sonra devam ettir"), subtitle = s.t("Never if you paused it yourself, switched video, or left the app.", "Kendin durdurduysan, videoyu değiştirdiysen ya da uygulamadan çıktıysan asla.")) {
                LidSwitch(st.playback.resumeAfterBreak, { v -> setS { it.copy(playback = it.playback.copy(resumeAfterBreak = v)) } }, description = s.t("Resume afterwards", "Sonra devam ettir"))
            }
            if (st.advanced) {
                Hairline()
                Spacer(Modifier.height(10.dp))
                EbText(s.t("If an app can't be paused", "Bir uygulama duraklatılamıyorsa"), Ink.Eyebrow, caps = false)
                Spacer(Modifier.height(8.dp))
                Tide(listOf(s.t("Blink anyway", "Yine de kırp"), s.t("Wait a swipe", "Kaydırma bekle"), s.t("Skip app", "Atla")), st.playback.fallback.ordinal, { i ->
                    setS { it.copy(playback = it.playback.copy(fallback = NoPauseFallback.entries[i])) }
                })
                Spacer(Modifier.height(6.dp))
                EbText(s.t("Not every app lets another app pause it. This is what happens when it doesn't.", "Her uygulama başka bir uygulamanın kendini duraklatmasına izin vermez. İzin vermediğinde olacak şey bu."), Ink.Caption, color = Dusk.TextFaint)
            }
        }

        // ── Politeness (advanced) ───────────────────────────────
        if (st.advanced) {
            Glass(Modifier.reveal(6).fillMaxWidth()) {
                SectionHeader(s.t("Good manners", "Nezaket"), null)
                Spacer(Modifier.height(4.dp))
                SettingRow(s.t("Between any two breaks", "İki mola arası en az"), subtitle = null) {
                    Stepper(fmtSeconds(r.cooldownSec, s), { setR { it.copy(cooldownSec = (it.cooldownSec - 5).coerceAtLeast(3)) } }, { setR { it.copy(cooldownSec = it.cooldownSec + 5) } })
                }
                Hairline()
                SettingRow(s.t("Quiet after a full rest", "Tam moladan sonra sessiz"), subtitle = null) {
                    Stepper(fmtMinutes(r.postRestGraceMin, s), { setR { it.copy(postRestGraceMin = (it.postRestGraceMin - 1).coerceAtLeast(0)) } }, { setR { it.copy(postRestGraceMin = it.postRestGraceMin + 1) } })
                }
                Hairline()
                SettingRow(s.t("At most per hour", "Saatte en fazla"), subtitle = null) {
                    Stepper("${r.maxMicroPerHour}", { setR { it.copy(maxMicroPerHour = (it.maxMicroPerHour - 10).coerceAtLeast(6)) } }, { setR { it.copy(maxMicroPerHour = it.maxMicroPerHour + 10) } })
                }
                Hairline()
                SettingRow(s.t("Wait for a swipe", "Kaydırmayı bekle"), subtitle = s.t("When a video can't be paused, blink right after the next swipe.", "Video duraklatılamıyorsa bir sonraki kaydırmanın hemen ardından kırp.")) {
                    LidSwitch(r.preferTransitions, { v -> setR { it.copy(preferTransitions = v) } }, description = s.t("Wait for a swipe", "Kaydırmayı bekle"))
                }
                Hairline()
                SettingRow(s.t("Quiet hours", "Sessiz saatler"), subtitle = if (r.quietHours.enabled) "${hhmm(r.quietHours.startMin)} – ${hhmm(r.quietHours.endMin)}" else s.t("Off", "Kapalı")) {
                    LidSwitch(r.quietHours.enabled, { v -> setR { it.copy(quietHours = it.quietHours.copy(enabled = v)) } }, description = s.t("Quiet hours", "Sessiz saatler"))
                }
                if (r.quietHours.enabled) {
                    SettingRow(s.t("From", "Başlangıç")) { Stepper(hhmm(r.quietHours.startMin), { setR { it.copy(quietHours = it.quietHours.copy(startMin = (it.quietHours.startMin - 30).mod(1440))) } }, { setR { it.copy(quietHours = it.quietHours.copy(startMin = (it.quietHours.startMin + 30).mod(1440))) } }) }
                    SettingRow(s.t("Until", "Bitiş")) { Stepper(hhmm(r.quietHours.endMin), { setR { it.copy(quietHours = it.quietHours.copy(endMin = (it.quietHours.endMin - 30).mod(1440))) } }, { setR { it.copy(quietHours = it.quietHours.copy(endMin = (it.quietHours.endMin + 30).mod(1440))) } }) }
                }
            }
        }
    }
}

internal fun hhmm(min: Int): String = "%02d:%02d".format(min.mod(1440) / 60, min.mod(60))
