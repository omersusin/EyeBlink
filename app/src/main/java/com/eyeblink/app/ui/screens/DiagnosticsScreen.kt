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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eyeblink.app.ui.i18n.fmtDecimal
import com.eyeblink.app.ui.kit.Badge
import com.eyeblink.app.ui.kit.ButtonKind
import com.eyeblink.app.ui.kit.Chip
import com.eyeblink.app.ui.kit.P
import com.eyeblink.app.ui.kit.EbText
import com.eyeblink.app.ui.kit.Card
import com.eyeblink.app.ui.kit.Hairline
import com.eyeblink.app.ui.kit.Ico
import com.eyeblink.app.ui.kit.IconBadge
import com.eyeblink.app.ui.kit.Icon
import com.eyeblink.app.ui.kit.Ink
import com.eyeblink.app.ui.kit.Meter
import com.eyeblink.app.ui.kit.PillButton
import com.eyeblink.app.ui.kit.S
import com.eyeblink.app.ui.kit.Tone
import com.eyeblink.app.ui.kit.reveal
import com.eyeblink.app.ui.state.PermissionKind
import com.eyeblink.app.ui.state.UiActions
import com.eyeblink.app.ui.state.UiState
import com.eyeblink.core.model.OperatingMode
import com.eyeblink.core.model.TrackingMode
import com.eyeblink.core.perf.DrainEstimate
import com.eyeblink.core.privileged.PowerKind

/** Frame budget the camera pipeline is judged against: two frames at 30 fps. */
private const val FRAME_BUDGET_MS = 33L

/**
 * Plain facts about whether EyeBlink can do its job right now, and what it costs. Everything is
 * measured on this phone; a number that has not been measured says so instead of showing zero.
 */
@Composable
fun DiagnosticsScreen(state: UiState, actions: UiActions, onBack: () -> Unit) {
    val s = S
    val d = state.diag
    val rt = state.runtime
    val power = state.power

    val camera = state.settings.camera.tracking
    val healthy = d.serviceRunning && (d.accessibilityConnected || state.perms.has(PermissionKind.OVERLAY))

    PageColumn(s.t("Diagnostics", "Tanılama"), s.t("How it is doing", "Nasıl gidiyor"), onBack) {
        // ── health ──────────────────────────────────────────────
        Card(Modifier.reveal(1).fillMaxWidth(), tint = if (healthy) P.mint else P.sun) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                EbText(s.t("Health", "Sağlık"), Ink.Title, modifier = Modifier.weight(1f))
                Badge(if (healthy) s.t("all good", "her şey yolunda") else s.t("needs attention", "ilgi bekliyor"), if (healthy) Tone.Ok else Tone.Warn)
            }
            Spacer(Modifier.height(8.dp))
            Health(
                if (d.serviceRunning) true else null, s.t("Engine", "Motor"),
                if (d.serviceRunning) s.t("running", "çalışıyor") else s.t("stopped", "durdu"),
            )
            Health(
                d.accessibilityConnected, s.t("Accessibility", "Erişilebilirlik"),
                if (d.accessibilityConnected) s.t("connected", "bağlı") else s.t("not connected", "bağlı değil"),
            )
            Health(
                d.listenerConnected, s.t("Media sessions", "Medya oturumları"),
                if (d.listenerConnected) s.t("connected", "bağlı") else s.t("not connected", "bağlı değil"),
            )
            Health(
                if (d.overlayKind.isNotEmpty()) true else null, s.t("Overlay", "Katman"),
                d.overlayKind.ifEmpty { s.t("none yet", "henüz yok") },
            )
            Health(
                if (camera == TrackingMode.OFF) null else rt.cameraActive, s.t("Camera", "Kamera"),
                when {
                    camera == TrackingMode.OFF -> s.t("off (camera-free)", "kapalı (kamerasız)")
                    rt.cameraActive -> s.t("tracking now", "şu an izliyor")
                    else -> s.t("idle between bursts", "atımlar arasında boşta")
                },
            )
            Health(
                when {
                    state.settings.mode == OperatingMode.ROOTLESS -> null
                    power.status.available -> true
                    else -> false
                },
                s.t("Power mode", "Güç modu"),
                when {
                    state.settings.mode == OperatingMode.ROOTLESS -> s.t("rootless", "rootsuz")
                    power.status.available -> if (power.status.kind == PowerKind.ROOT) "root" else "Shizuku"
                    else -> s.t("${state.settings.mode.name.lowercase()} unavailable - using rootless", "${state.settings.mode.name.lowercase()} yok - rootsuz kullanılıyor")
                },
            )
        }

        // ── permissions ─────────────────────────────────────────
        Card(Modifier.reveal(2).fillMaxWidth()) {
            EbText(s.t("Permissions", "İzinler"), Ink.Title)
            Spacer(Modifier.height(4.dp))
            EbText(s.t("EyeBlink needs Accessibility or Display over other apps. Everything else is optional.", "EyeBlink'in Erişilebilirlik veya Diğer uygulamaların üzerinde göster iznine ihtiyacı var. Geri kalan her şey isteğe bağlı."), Ink.Caption)
            Spacer(Modifier.height(6.dp))
            PermissionKind.entries.forEachIndexed { i, kind ->
                if (i > 0) Hairline()
                val granted = state.perms.has(kind)
                val core = kind == PermissionKind.ACCESSIBILITY || kind == PermissionKind.OVERLAY
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(permissionIcon(kind), if (granted) P.mint else P.sky, size = 38.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        EbText(permissionTitle(kind, s), Ink.BodyStrong)
                        EbText(permissionWhy(kind, s), Ink.Caption)
                    }
                    if (granted) Badge(s.t("on", "açık"), Tone.Ok)
                    else Chip(s.t("Grant", "Ver"), selected = core, onClick = { actions.request(kind) })
                }
            }
        }

        // ── performance ─────────────────────────────────────────
        Card(Modifier.reveal(3).fillMaxWidth()) {
            EbText(s.t("Performance", "Performans"), Ink.Title)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                EbText(s.t("Frame analysis", "Kare analizi"), Ink.BodyStrong, modifier = Modifier.weight(1f))
                EbText(
                    if (d.frameP95Ms > 0) s.t("p50 ${d.frameP50Ms} ms · p95 ${d.frameP95Ms} ms", "p50 ${d.frameP50Ms} ms · p95 ${d.frameP95Ms} ms")
                    else s.t("not measured", "ölçülmedi"),
                    Ink.NumSmall, color = P.inkSoft, caps = false, maxLines = 1,
                )
            }
            Spacer(Modifier.height(8.dp))
            Meter(
                (d.frameP95Ms.toFloat() / FRAME_BUDGET_MS).coerceIn(0f, 1f),
                tone = when {
                    d.frameP95Ms <= FRAME_BUDGET_MS / 2 -> P.mint
                    d.frameP95Ms <= FRAME_BUDGET_MS -> P.sun
                    else -> P.coral
                },
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                EbText(s.t("Camera share of active time", "Kameranın aktif süre payı"), Ink.BodyStrong, modifier = Modifier.weight(1f))
                EbText("${d.cameraDutyPercent}%", Ink.NumSmall, color = P.inkSoft, caps = false)
            }
            Spacer(Modifier.height(8.dp))
            Meter(d.cameraDutyPercent / 100f, tone = P.sky)

            Hairline(Modifier.padding(vertical = 14.dp))
            EbText(s.t("Battery cost of the camera", "Kameranın pil maliyeti"), Ink.BodyStrong)
            Spacer(Modifier.height(8.dp))
            Drain(s.t("Camera-assisted", "Kamera destekli"), d.drain.cameraAssisted)
            Drain(s.t("Camera-free", "Kamerasız"), d.drain.cameraFree)
            Spacer(Modifier.height(8.dp))
            val extra = d.drain.extraPerHour
            EbText(
                if (extra != null) s.t("On this phone the camera costs about ${fmtDecimal(extra, s)} % per hour more.", "Bu telefonda kamera saatte yaklaşık %${fmtDecimal(extra, s)} daha fazla pil harcıyor.")
                else s.t("Needs a few hours of each mode with the screen on, unplugged. Until then EyeBlink will not pretend to know.", "Her mod için ekran açık ve şarjsız birkaç saat gerekir. O zamana kadar EyeBlink biliyormuş gibi yapmaz."),
                Ink.Caption, color = P.inkFaint,
            )
        }

        // ── device ──────────────────────────────────────────────
        Card(Modifier.reveal(4).fillMaxWidth()) {
            EbText(s.t("This phone", "Bu telefon"), Ink.Title)
            Spacer(Modifier.height(8.dp))
            Fact(s.t("EyeBlink", "EyeBlink"), d.appVersion.ifEmpty { "-" })
            Fact("Android", if (d.androidSdk > 0) "API ${d.androidSdk}" else "-")
            Fact(s.t("Device", "Cihaz"), d.device.ifEmpty { "-" })
            Fact(s.t("Battery", "Pil"), "${d.batteryPercent}%" + if (d.charging) s.t(" · charging", " · şarjda") else "")
            Fact(s.t("Heat", "Isı"), d.thermal)
            Fact(s.t("Battery saver", "Pil tasarrufu"), if (d.powerSave) s.t("on", "açık") else s.t("off", "kapalı"))
        }

        Column(Modifier.reveal(5).fillMaxWidth()) {
            PillButton(s.t("Copy report", "Raporu kopyala"), { actions.copyReport() }, kind = ButtonKind.Soft, icon = Icon.Link, compact = true)
            Spacer(Modifier.height(8.dp))
            EbText(s.t("Plain text: versions, permissions and counters. No app list, no content, nothing personal.", "Düz metin: sürümler, izinler ve sayaçlar. Uygulama listesi, içerik, kişisel hiçbir şey yok."), Ink.Caption, color = P.inkFaint)
        }
    }
}

/** One line of the health list. [ok] null means "not applicable" and is drawn quietly. */
@Composable
private fun Health(ok: Boolean?, title: String, detail: String) {
    val tone: Color = when (ok) { true -> P.mint.text; false -> P.sun.text; null -> P.inkFaint }
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Ico(when (ok) { true -> Icon.Check; false -> Icon.Warning; null -> Icon.Minus }, size = 18.dp, color = tone, weight = 2.4f)
        EbText(title, Ink.BodyStrong, modifier = Modifier.weight(1.25f).padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Clip)
        EbText(detail, Ink.NumSmall, color = if (ok == false) P.sun.text else P.inkSoft, caps = false, align = TextAlign.End, modifier = Modifier.weight(1f), maxLines = 2)
    }
}

@Composable
private fun Drain(label: String, e: DrainEstimate?) {
    val s = S
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        EbText(label, Ink.Caption, color = P.ink, modifier = Modifier.weight(1f))
        EbText(
            if (e == null) s.t("not measured", "ölçülmedi")
            else s.t("${fmtDecimal(e.percentPerHour, s)} % / h over ${fmtDecimal(e.hoursObserved, s)} h", "${fmtDecimal(e.percentPerHour, s)} % / sa, ${fmtDecimal(e.hoursObserved, s)} sa boyunca") +
                if (e.trustworthy) "" else s.t(" (too short)", " (çok kısa)"),
            Ink.NumSmall, color = if (e?.trustworthy == true) P.ink else P.inkSoft, caps = false, maxLines = 1,
        )
    }
}

@Composable
private fun Fact(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        EbText(key, Ink.Caption, modifier = Modifier.weight(1f))
        EbText(value, Ink.NumSmall, caps = false, align = TextAlign.End, modifier = Modifier.weight(1.6f), maxLines = 1)
    }
}
