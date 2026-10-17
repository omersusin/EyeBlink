package com.eyeblink.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import com.eyeblink.app.ui.i18n.fmtActive
import com.eyeblink.app.ui.i18n.fmtDecimal
import com.eyeblink.app.ui.i18n.fmtMinutes
import com.eyeblink.app.ui.i18n.label
import com.eyeblink.app.ui.kit.Badge
import com.eyeblink.app.ui.kit.ButtonKind
import com.eyeblink.app.ui.kit.Dusk
import com.eyeblink.app.ui.kit.EbText
import com.eyeblink.app.ui.kit.Glass
import com.eyeblink.app.ui.kit.Hairline
import com.eyeblink.app.ui.kit.Icon
import com.eyeblink.app.ui.kit.Ink
import com.eyeblink.app.ui.kit.Meter
import com.eyeblink.app.ui.kit.MiniBars
import com.eyeblink.app.ui.kit.PillButton
import com.eyeblink.app.ui.kit.S
import com.eyeblink.app.ui.kit.SplitMeter
import com.eyeblink.app.ui.kit.Tone
import com.eyeblink.app.ui.kit.reveal
import com.eyeblink.app.ui.state.UiActions
import com.eyeblink.app.ui.state.UiState
import com.eyeblink.core.engine.Reason

/**
 * What EyeBlink did, and what it could and could not verify. Counted on the phone, shown only
 * on the phone. "Observed" numbers come from the camera and are labelled as such; nothing is
 * ever estimated and presented as if it had been seen.
 */
@Composable
fun StatsScreen(state: UiState, actions: UiActions, onBack: () -> Unit) {
    val s = S
    val st = state.stats
    val today = st.today
    val week = st.week
    var armed by remember { mutableStateOf(false) }

    PageColumn(s.t("Statistics", "İstatistikler"), s.t("Only on this phone", "Yalnızca bu telefonda"), onBack) {
        if (!state.settings.privacy.localStats) {
            Glass(Modifier.reveal(1).fillMaxWidth(), glow = Dusk.Amber) {
                EbText(s.t("Counting is switched off", "Sayım kapalı"), Ink.Title)
                Spacer(Modifier.height(4.dp))
                EbText(s.t("Nothing is being recorded. Turn it on in Privacy if you would like a record of your breaks.", "Hiçbir şey kaydedilmiyor. Molalarının kaydını istersen Gizlilik'ten aç."), Ink.Caption)
            }
        }

        // ── today ───────────────────────────────────────────────
        Glass(Modifier.reveal(1).fillMaxWidth(), glow = Dusk.Iris) {
            EbText(s.t("Today", "Bugün"), Ink.Eyebrow, color = Dusk.Amber)
            Spacer(Modifier.height(14.dp))
            val (activeValue, activeUnit) = fmtActive(today.activeMs, s)
            Row(Modifier.fillMaxWidth()) {
                Figure(Modifier.weight(1f), today.microShown.toString(), s.t("blinks", "kırpma"))
                Figure(Modifier.weight(1f), today.restsCompleted.toString(), s.t("rests", "mola"))
                Figure(Modifier.weight(1f), activeValue, s.t("$activeUnit active", "$activeUnit aktif"))
            }
        }

        // ── the week ────────────────────────────────────────────
        Glass(Modifier.reveal(2).fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                EbText(s.t("Last 7 days", "Son 7 gün"), Ink.Eyebrow, color = Dusk.Amber, modifier = Modifier.weight(1f))
                EbText(s.t("${week.microShown} blinks · ${week.restsCompleted} rests", "${week.microShown} kırpma · ${week.restsCompleted} mola"), Ink.MonoSmall, color = Dusk.TextDim, caps = false, maxLines = 1)
            }
            Spacer(Modifier.height(14.dp))
            if (st.series.isEmpty()) {
                EbText(s.t("Nothing yet. Use a selected app for a while and this fills in.", "Henüz bir şey yok. Seçtiğin bir uygulamayı biraz kullan, burası dolar."), Ink.Caption)
            } else {
                MiniBars(st.series.map { it.microShown.toFloat() }, st.seriesLabels)
            }
        }

        // ── what the camera saw ─────────────────────────────────
        Glass(Modifier.reveal(3).fillMaxWidth()) {
            val observed = week.observedBlinksPerMinute
            val calibrated = state.personal.general.blinksPerMinute
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                EbText(s.t("What the camera saw", "Kameranın gördüğü"), Ink.Eyebrow, color = Dusk.Amber, modifier = Modifier.weight(1f))
                Badge(if (observed != null) s.t("observed", "gözlenen") else s.t("no data", "veri yok"), if (observed != null) Tone.Iris else Tone.Quiet)
            }
            Spacer(Modifier.height(10.dp))
            if (observed != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    EbText(fmtDecimal(observed, s), Ink.Headline)
                    EbText(s.t("blinks / min", "kırpma / dk"), Ink.Caption, modifier = Modifier.padding(start = 8.dp, bottom = 6.dp))
                }
                Spacer(Modifier.height(4.dp))
                EbText(s.t("Counted over ${fmtMinutes((week.observedTrackedMs / 60_000L).toInt(), s)} with the camera tracking you.", "Kamera seni izlerken ${fmtMinutes((week.observedTrackedMs / 60_000L).toInt(), s)} boyunca sayıldı."), Ink.Caption)
                if (calibrated != null) {
                    Hairline(Modifier.padding(vertical = 10.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        EbText(s.t("From your calibration", "Kalibrasyonundan"), Ink.Caption, modifier = Modifier.weight(1f))
                        EbText(s.t("${fmtDecimal(calibrated, s)} / min", "${fmtDecimal(calibrated, s)} / dk"), Ink.Mono, caps = false)
                    }
                }
            } else {
                EbText(s.t("Camera-assisted mode counts the blinks it really sees and shows the rate here. Without the camera EyeBlink does not guess one.", "Kamera destekli mod gerçekten gördüğü göz kırpmalarını sayar ve oranı burada gösterir. Kamera olmadan EyeBlink bir oran tahmin etmez."), Ink.Caption)
            }
        }

        // ── playback safety ─────────────────────────────────────
        Glass(Modifier.reveal(4).fillMaxWidth()) {
            EbText(s.t("Playback safety", "Oynatma güvenliği"), Ink.Eyebrow, color = Dusk.Amber)
            Spacer(Modifier.height(4.dp))
            EbText(s.t("A pause only counts once EyeBlink has seen the player stop. A resume only happens if nothing changed in the meantime.", "Duraklatma, EyeBlink oynatıcının durduğunu görmeden sayılmaz. Sürdürme, bu arada hiçbir şey değişmediyse yapılır."), Ink.Caption)
            Spacer(Modifier.height(14.dp))
            Outcome(
                s.t("Pauses", "Duraklatmalar"),
                listOf(
                    Triple(week.pausesVerified, s.t("verified", "doğrulandı"), Dusk.Ok),
                    Triple(week.pausesUnverified, s.t("unverified", "doğrulanamadı"), Dusk.Warn),
                    Triple(week.pausesFailed, s.t("failed", "başarısız"), Dusk.Bad),
                ),
            )
            Spacer(Modifier.height(16.dp))
            Outcome(
                s.t("Resumes", "Sürdürmeler"),
                listOf(
                    Triple(week.resumesOk, s.t("resumed", "sürdürüldü"), Dusk.Ok),
                    Triple(week.resumesRefused, s.t("left to you", "sana bırakıldı"), Dusk.Iris),
                    Triple(week.resumesFailed, s.t("failed", "başarısız"), Dusk.Bad),
                ),
            )
            if (week.resumesRefused > 0) {
                Spacer(Modifier.height(10.dp))
                EbText(s.t("\"Left to you\" means you had taken over - you paused it, switched videos or left the app - so EyeBlink did not touch it.", "\"Sana bırakıldı\", kontrolü sen almışsın demektir - kendin duraklattın, videoyu değiştirdin ya da uygulamadan çıktın - EyeBlink dokunmadı."), Ink.Caption, color = Dusk.TextFaint)
            }
        }

        // ── why a break waited ─────────────────────────────────
        val waits = week.deferredByReason.entries.sortedByDescending { it.value }.take(5)
        if (waits.isNotEmpty()) {
            Glass(Modifier.reveal(5).fillMaxWidth()) {
                EbText(s.t("Why a break waited", "Mola neden bekledi"), Ink.Eyebrow, color = Dusk.Amber)
                Spacer(Modifier.height(4.dp))
                EbText(s.t("EyeBlink waits for a calm moment instead of interrupting a gesture.", "EyeBlink bir hareketi bölmek yerine sakin bir an bekler."), Ink.Caption)
                Spacer(Modifier.height(12.dp))
                val top = waits.first().value.coerceAtLeast(1)
                waits.forEach { (key, count) ->
                    val label = Reason.entries.firstOrNull { it.name == key }?.label(s) ?: key
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            EbText(label, Ink.Caption, color = Dusk.Text, modifier = Modifier.weight(1f))
                            EbText(count.toString(), Ink.MonoSmall, caps = false)
                        }
                        Spacer(Modifier.height(6.dp))
                        Meter(count.toFloat() / top, tone = Dusk.Violet)
                    }
                }
            }
        }

        // ── clear ───────────────────────────────────────────────
        Box(Modifier.reveal(6).fillMaxWidth()) {
            PillButton(
                if (armed) s.t("Tap again to erase", "Silmek için tekrar dokun") else s.t("Clear statistics", "İstatistikleri temizle"),
                { if (armed) { actions.clearStats(); armed = false } else armed = true },
                kind = if (armed) ButtonKind.Danger else ButtonKind.Ghost, icon = Icon.Trash, compact = true,
            )
        }
    }
}

@Composable
private fun Figure(modifier: Modifier, value: String, label: String) {
    Column(modifier) {
        EbText(value, Ink.Headline, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        EbText(label, Ink.Caption, maxLines = 1)
    }
}

/** A titled split bar with a coloured legend of counts underneath. */
@Composable
private fun Outcome(title: String, parts: List<Triple<Int, String, androidx.compose.ui.graphics.Color>>) {
    Column(Modifier.fillMaxWidth()) {
        EbText(title, Ink.BodyStrong)
        Spacer(Modifier.height(8.dp))
        SplitMeter(parts.map { it.first.toFloat() to it.third })
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            parts.forEach { (count, label, color) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(color))
                    EbText("$count $label", Ink.Caption, maxLines = 1)
                }
            }
        }
    }
}
