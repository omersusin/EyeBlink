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
import androidx.compose.ui.text.style.TextAlign
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
import com.eyeblink.app.ui.kit.Ico
import com.eyeblink.app.ui.kit.Icon
import com.eyeblink.app.ui.kit.Ink
import com.eyeblink.app.ui.kit.LidSwitch
import com.eyeblink.app.ui.kit.NightDial
import com.eyeblink.app.ui.kit.PillButton
import com.eyeblink.app.ui.kit.S
import com.eyeblink.app.ui.kit.SettingRow
import com.eyeblink.app.ui.kit.Stepper
import com.eyeblink.app.ui.kit.Tone
import com.eyeblink.app.ui.kit.reveal
import com.eyeblink.app.ui.state.Page
import com.eyeblink.app.ui.state.PermissionKind
import com.eyeblink.app.ui.state.UiActions
import com.eyeblink.app.ui.state.UiState
import com.eyeblink.core.model.NightSettings
import com.eyeblink.core.night.NightReason
import kotlin.math.roundToInt

@Composable
fun NightScreen(state: UiState, actions: UiActions, onBack: () -> Unit, onPage: (Page) -> Unit) {
    val s = S
    val n = state.settings.night
    val ui = state.night
    fun set(f: (NightSettings) -> NightSettings) = actions.update { it.copy(night = f(it.night).validated()) }
    val nowMin = state.clock.split(":").let { (it.getOrNull(0)?.toIntOrNull() ?: 21) * 60 + (it.getOrNull(1)?.toIntOrNull() ?: 0) }

    PageColumn(s.t("Night", "Gece"), s.t("Ease into the dark", "Karanlığa yumuşak geçiş"), onBack) {
        Glass(Modifier.reveal(1).fillMaxWidth(), glow = Dusk.Violet) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(6.dp))
                NightDial(n.startMin, n.endMin, nowMin, active = n.enabled && ui.target?.active == true, center = {
                    EbText(state.clock.ifBlank { "--:--" }, Ink.Display.let { Ink.Headline }, align = TextAlign.Center)
                    Spacer(Modifier.height(2.dp))
                    EbText(
                        when {
                            !n.enabled -> s.t("off", "kapalı")
                            ui.target?.active == true -> s.t("night mode on", "gece modu açık")
                            ui.target?.reason == NightReason.MANUAL_OVERRIDE -> s.t("hands off", "dokunmuyor")
                            else -> s.t("waiting for ${hhmm(n.startMin)}", "${hhmm(n.startMin)} bekleniyor")
                        },
                        Ink.Eyebrow, caps = false, color = Dusk.Violet,
                    )
                })
                Spacer(Modifier.height(8.dp))
            }
        }

        Glass(Modifier.reveal(2).fillMaxWidth()) {
            SettingRow(s.t("Smart night mode", "Akıllı gece modu"), subtitle = s.t("Eases warmth and brightness in at night, gradually, on your terms.", "Geceleri sıcaklığı ve parlaklığı, senin kurallarınla yavaş yavaş devreye alır.")) {
                LidSwitch(n.enabled, { v -> set { it.copy(enabled = v) } }, description = s.t("Smart night mode", "Akıllı gece modu"))
            }
        }

        if (n.enabled) {
            Glass(Modifier.reveal(3).fillMaxWidth()) {
                SettingRow(s.t("Starts", "Başlar")) { Stepper(hhmm(n.startMin), { set { it.copy(startMin = (it.startMin - 30).mod(1440)) } }, { set { it.copy(startMin = (it.startMin + 30).mod(1440)) } }) }
                Hairline()
                SettingRow(s.t("Ends", "Biter")) { Stepper(hhmm(n.endMin), { set { it.copy(endMin = (it.endMin - 30).mod(1440)) } }, { set { it.copy(endMin = (it.endMin + 30).mod(1440)) } }) }
                Hairline()
                SettingRow(
                    s.t("Follow the room's light", "Odanın ışığını izle"),
                    subtitle = (ui.lux?.let { s.t("The room reads about ${it.roundToInt()} lux. ", "Oda yaklaşık ${it.roundToInt()} lüks. ") } ?: "") +
                        s.t("A very dark room counts as night even before the hour.", "Çok karanlık bir oda, saat gelmeden de gece sayılır."),
                ) { LidSwitch(n.adaptToAmbientLight, { v -> set { it.copy(adaptToAmbientLight = v) } }, description = s.t("Follow the room's light", "Odanın ışığını izle")) }
            }

            Glass(Modifier.reveal(4).fillMaxWidth(), glow = Dusk.Amber) {
                SettingRow(s.t("Night Light", "Gece Işığı"), subtitle = s.t("Android's warm screen tint.", "Android'in sıcak ekran tonu.")) {
                    LidSwitch(n.manageNightLight, { v -> set { it.copy(manageNightLight = v) } }, description = s.t("Night Light", "Gece Işığı"))
                }
                if (n.manageNightLight) {
                    if (!ui.canWriteSecureSettings) {
                        Spacer(Modifier.height(6.dp))
                        Badge(s.t("needs one-time access", "tek seferlik erişim gerekli"), Tone.Warn)
                        Spacer(Modifier.height(8.dp))
                        EbText(s.t("Android only lets EyeBlink change Night Light after a one-time grant. Shizuku or Root can do it in a tap; otherwise run this once from a computer:", "Android, Gece Işığı'nı değiştirme iznini yalnızca tek seferlik bir onayla verir. Shizuku veya Root bunu tek dokunuşla yapar; yoksa bir bilgisayardan şunu bir kez çalıştır:"), Ink.Caption)
                        Spacer(Modifier.height(8.dp))
                        // Two deliberate lines: a command that wraps mid-word is a command nobody can copy by eye.
                        EbText("adb shell pm grant com.eyeblink.app \\\nandroid.permission.WRITE_SECURE_SETTINGS", Ink.Code, color = Dusk.Amber, caps = false, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(10.dp))
                        PillButton(s.t("Power modes", "Güç modları"), { onPage(Page.POWER) }, kind = ButtonKind.Glass, compact = true, icon = Icon.Bolt)
                    } else {
                        Hairline()
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth()) {
                            EbText(s.t("Warmth", "Sıcaklık"), Ink.BodyStrong, modifier = Modifier.weight(1f))
                            EbText("${n.warmthKelvin} K", Ink.MonoSmall, color = Dusk.Amber, caps = false)
                        }
                        Horizon(
                            1f - (n.warmthKelvin - 2300) / 4200f, { x -> set { it.copy(warmthKelvin = (2300 + (1f - x) * 4200).roundToInt()) } },
                            format = { "${(2300 + (1f - it) * 4200).roundToInt()} K" }, description = s.t("Warmth", "Sıcaklık"), accent = Dusk.Amber,
                        )
                        EbText(s.t("Warmer colour is not a cure for eye strain - pick whatever you find comfortable.", "Daha sıcak renk göz yorgunluğunun çaresi değildir; sana rahat gelen neyse onu seç."), Ink.Caption, color = Dusk.TextFaint)
                    }
                }
            }

            Glass(Modifier.reveal(5).fillMaxWidth()) {
                SettingRow(s.t("Brightness", "Parlaklık"), subtitle = s.t("Dim the screen at night. Off by default.", "Geceleri ekranı kısar. Varsayılan olarak kapalı.")) {
                    LidSwitch(n.manageBrightness, { v -> set { it.copy(manageBrightness = v) } }, description = s.t("Brightness", "Parlaklık"))
                }
                if (n.manageBrightness) {
                    if (!ui.canWriteSettings) {
                        Spacer(Modifier.height(6.dp))
                        Badge(s.t("needs permission", "izin gerekli"), Tone.Warn)
                        Spacer(Modifier.height(8.dp))
                        PillButton(s.t("Allow changing brightness", "Parlaklığı değiştirmeye izin ver"), { actions.request(PermissionKind.WRITE_SETTINGS) }, kind = ButtonKind.Glass, compact = true)
                    } else {
                        Hairline()
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth()) {
                            EbText(s.t("At night", "Geceleyin"), Ink.BodyStrong, modifier = Modifier.weight(1f))
                            EbText("${n.nightBrightnessPercent}%", Ink.MonoSmall, color = Dusk.Iris, caps = false)
                        }
                        Horizon(n.nightBrightnessPercent / 100f, { x -> set { it.copy(nightBrightnessPercent = (x * 100).roundToInt()) } }, description = s.t("Night brightness", "Gece parlaklığı"))
                        Row(Modifier.fillMaxWidth()) {
                            EbText(s.t("Never dimmer than", "Asla bundan kısma"), Ink.BodyStrong, modifier = Modifier.weight(1f))
                            EbText("${n.brightnessFloorPercent}%", Ink.MonoSmall, color = Dusk.Iris, caps = false)
                        }
                        Horizon(n.brightnessFloorPercent / 50f, { x -> set { it.copy(brightnessFloorPercent = (x * 50).roundToInt().coerceAtLeast(1)) } }, format = { "${(it * 50).roundToInt()}%" }, description = s.t("Brightness floor", "Parlaklık tabanı"))
                    }
                }
            }

            Glass(Modifier.reveal(6).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth()) {
                    EbText(s.t("Change over", "Geçiş süresi"), Ink.BodyStrong, modifier = Modifier.weight(1f))
                    EbText(fmtSeconds(n.rampSec, s), Ink.MonoSmall, color = Dusk.Iris, caps = false)
                }
                Horizon(
                    com.eyeblink.app.ui.screens.logFromValue(n.rampSec.toFloat(), 2f, 300f), { x -> set { it.copy(rampSec = com.eyeblink.app.ui.screens.logToValue(x, 2f, 300f).roundToInt()) } },
                    format = { fmtSeconds(com.eyeblink.app.ui.screens.logToValue(it, 2f, 300f).roundToInt(), s) }, description = s.t("Transition length", "Geçiş süresi"),
                )
                Hairline()
                SettingRow(s.t("Back off after I change it", "Ben değiştirince çekil"), subtitle = s.t("If you adjust brightness yourself, EyeBlink stops touching it for a while.", "Parlaklığı kendin ayarlarsan EyeBlink bir süre ona dokunmaz.")) {
                    Stepper(fmtMinutes(n.manualOverrideBackoffMin, s), { set { it.copy(manualOverrideBackoffMin = (it.manualOverrideBackoffMin - 5).coerceAtLeast(1)) } }, { set { it.copy(manualOverrideBackoffMin = it.manualOverrideBackoffMin + 5) } })
                }
            }
        }

        EbText(s.t("You stay in control: EyeBlink only does what you set here, gradually, and steps back the moment you change something yourself.", "Kontrol sende: EyeBlink yalnızca burada belirlediğini, kademeli olarak yapar ve sen bir şeyi değiştirdiğin anda geri çekilir."), Ink.Caption, color = Dusk.TextFaint, modifier = Modifier.reveal(7).padding(horizontal = 4.dp))
    }
}
