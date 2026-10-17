package com.eyeblink.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eyeblink.app.ui.kit.Badge
import com.eyeblink.app.ui.kit.ButtonKind
import com.eyeblink.app.ui.kit.P
import com.eyeblink.app.ui.kit.EbText
import com.eyeblink.app.ui.kit.Flow
import com.eyeblink.app.ui.kit.Card
import com.eyeblink.app.ui.kit.Hairline
import com.eyeblink.app.ui.kit.Hue
import com.eyeblink.app.ui.kit.Ico
import com.eyeblink.app.ui.kit.IconBadge
import com.eyeblink.app.ui.kit.Icon
import com.eyeblink.app.ui.kit.Ink
import com.eyeblink.app.ui.kit.LidSwitch
import com.eyeblink.app.ui.kit.PillButton
import com.eyeblink.app.ui.kit.S
import com.eyeblink.app.ui.kit.SettingRow
import com.eyeblink.app.ui.kit.Tone
import com.eyeblink.app.ui.kit.reveal
import com.eyeblink.app.ui.state.UiActions
import com.eyeblink.app.ui.state.UiState

/**
 * What each access is used for, in "sees / never" pairs. Every line here is a promise the code
 * has to keep: the privacy page is the contract, and the manifest, the accessibility service
 * configuration and the camera pipeline are written to match it.
 */
@Composable
fun PrivacyScreen(state: UiState, actions: UiActions, onBack: () -> Unit) {
    val s = S
    var armedStats by remember { mutableStateOf(false) }
    var armedCalibration by remember { mutableStateOf(false) }

    PageColumn(s.t("Privacy", "Gizlilik"), s.t("Stays on your phone", "Telefonunda kalır"), onBack) {
        // ── the promise ─────────────────────────────────────────
        Card(Modifier.reveal(1).fillMaxWidth(), tint = P.mint) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icon.Shield, P.mint, size = 46.dp)
                EbText(s.t("No network. By design.", "Ağ yok. Bilerek."), Ink.Lead, modifier = Modifier.weight(1f).padding(start = 12.dp))
            }
            Spacer(Modifier.height(10.dp))
            EbText(
                s.t(
                    "EyeBlink does not even ask Android for the Internet permission. There is no account, no analytics, no ads and no server for anything to be sent to. The face model is inside the app.",
                    "EyeBlink, Android'den İnternet iznini bile istemez. Hesap, analiz, reklam ve bir şeyin gönderileceği bir sunucu yok. Yüz modeli uygulamanın içinde.",
                ),
                Ink.Body, color = P.ink,
            )
            Spacer(Modifier.height(12.dp))
            Badge(s.t("no Internet permission", "İnternet izni yok"), Tone.Ok)
        }

        // ── every access ────────────────────────────────────────
        Card(Modifier.reveal(2).fillMaxWidth()) {
            EbText(s.t("Each access, in plain words", "Her erişim, sade bir dille"), Ink.Title)
            Spacer(Modifier.height(6.dp))
            Access(
                Icon.Camera, P.sky, s.t("Camera", "Kamera"),
                sees = s.t("How open your eyes are, as a number, worked out in memory for each frame - only in camera-assisted mode.", "Gözlerinin ne kadar açık olduğu; her kare için bellekte hesaplanan bir sayı olarak - yalnızca kamera destekli modda."),
                never = s.t("Saves, records or sends a picture. A visible indicator shows whenever the camera is on, and the Quick Settings tile switches it off at once.", "Bir görüntüyü kaydetmez, saklamaz ya da göndermez. Kamera açıkken görünür bir gösterge yanar ve Hızlı Ayarlar karosu onu hemen kapatır."),
            )
            Hairline()
            Access(
                Icon.Eye, P.sun, s.t("Accessibility", "Erişilebilirlik"),
                sees = s.t("Which app is in front, and that a finger is on the screen - so a swipe is never interrupted.", "Hangi uygulamanın önde olduğu ve ekranda bir parmak olduğu - böylece bir kaydırma asla bölünmez."),
                never = s.t("Reads what is on your screen, what you type, or where you touch. EyeBlink does not request permission to read window content at all.", "Ekranında ne olduğunu, ne yazdığını ya da nereye dokunduğunu okumaz. EyeBlink pencere içeriğini okuma iznini hiç istemez."),
            )
            Hairline()
            Access(
                Icon.Play, P.grape, s.t("Media sessions", "Medya oturumları"),
                sees = s.t("Whether a player is playing, and a fingerprint of the current item, to tell if you changed videos.", "Bir oynatıcının çalıp çalmadığını ve videoyu değiştirip değiştirmediğini anlamak için geçerli öğenin kısa bir özetini."),
                never = s.t("Reads your notifications or keeps titles. The fingerprint lives in memory for as long as a break lasts.", "Bildirimlerini okumaz, başlıkları saklamaz. Bu özet yalnızca bir mola sürdüğü kadar bellekte kalır."),
            )
            Hairline()
            Access(
                Icon.Terminal, P.coral, s.t("Shizuku & Root", "Shizuku ve Root"),
                sees = s.t("Runs a short, fixed list of commands: reading media sessions, granting EyeBlink its own permissions, switching Night Light.", "Kısa ve sabit bir komut listesi çalıştırır: medya oturumlarını okuma, EyeBlink'e kendi izinlerini verme, Gece Işığı'nı değiştirme."),
                never = s.t("Runs anything it was not built with, downloads code, or touches other apps' data.", "Kendisiyle gelmeyen bir şey çalıştırmaz, kod indirmez, başka uygulamaların verilerine dokunmaz."),
            )
        }

        // ── what is stored ──────────────────────────────────────
        Card(Modifier.reveal(3).fillMaxWidth()) {
            EbText(s.t("What is kept on this phone", "Bu telefonda neler tutulur"), Ink.Title)
            Spacer(Modifier.height(10.dp))
            Kept(s.t("Your settings and per-app rules", "Ayarların ve uygulama başına kuralların"))
            Kept(s.t("Calibration numbers: how often and how long you blink - never images", "Kalibrasyon sayıları: ne sıklıkla ve ne kadar kırptığın - asla görüntü değil"))
            Kept(s.t("Daily counters: breaks shown, pauses checked", "Günlük sayaçlar: gösterilen molalar, doğrulanan duraklatmalar"))
            Kept(s.t("The result of each app's playback test", "Her uygulamanın oynatma testinin sonucu"))
            Hairline(Modifier.padding(top = 6.dp))
            SettingRow(
                s.t("Keep daily counters", "Günlük sayaçları tut"),
                subtitle = s.t("Switch off and nothing is counted from now on.", "Kapatırsan bundan sonra hiçbir şey sayılmaz."),
            ) { LidSwitch(state.settings.privacy.localStats, { v -> actions.update { it.copy(privacy = it.privacy.copy(localStats = v)) } }, description = s.t("Keep counters", "Sayaçları tut")) }
            Hairline()
            Spacer(Modifier.height(12.dp))
            Flow(gap = 10.dp) {
                PillButton(
                    if (armedStats) s.t("Tap again", "Tekrar dokun") else s.t("Clear counters", "Sayaçları sil"),
                    { if (armedStats) { actions.clearStats(); armedStats = false } else armedStats = true },
                    kind = if (armedStats) ButtonKind.Danger else ButtonKind.Ghost, compact = true, icon = Icon.Trash,
                )
                PillButton(
                    if (armedCalibration) s.t("Tap again", "Tekrar dokun") else s.t("Forget calibration", "Kalibrasyonu unut"),
                    { if (armedCalibration) { actions.resetCalibration(); armedCalibration = false } else armedCalibration = true },
                    kind = if (armedCalibration) ButtonKind.Danger else ButtonKind.Ghost, compact = true, icon = Icon.Refresh,
                )
            }
        }

        // ── you stay in charge ──────────────────────────────────
        Card(Modifier.reveal(4).fillMaxWidth()) {
            EbText(s.t("You stay in charge", "Kontrol sende"), Ink.Title)
            Spacer(Modifier.height(10.dp))
            Kept(s.t("The Quick Settings tile suspends EyeBlink - camera included - from anywhere", "Hızlı Ayarlar karosu EyeBlink'i - kamera dahil - her yerden askıya alır"))
            Kept(s.t("If anything goes wrong, whatever EyeBlink drew disappears by itself", "Bir şeyler ters giderse EyeBlink'in çizdiği her şey kendiliğinden kaybolur"))
            Kept(s.t("It never covers calls, the lock screen, payments, sign-in or system settings", "Aramaları, kilit ekranını, ödemeleri, girişi veya sistem ayarlarını asla örtmez"))
            Kept(s.t("Uninstall it and everything is gone - there is no cloud copy", "Kaldırırsan her şey gider - bulutta bir kopya yok"))
        }
    }
}

@Composable
private fun Access(icon: Icon, hue: Hue, title: String, sees: String, never: String) {
    val s = S
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        IconBadge(icon, hue, size = 42.dp)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            EbText(title, Ink.Title)
            Spacer(Modifier.height(8.dp))
            EbText(s.t("Uses", "Kullanır"), Ink.Eyebrow, color = P.mint.text)
            Spacer(Modifier.height(2.dp))
            EbText(sees, Ink.Caption, color = P.ink)
            Spacer(Modifier.height(8.dp))
            EbText(s.t("Never", "Asla"), Ink.Eyebrow, color = P.coral.text)
            Spacer(Modifier.height(2.dp))
            EbText(never, Ink.Caption, color = P.inkSoft)
        }
    }
}

@Composable
private fun Kept(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Ico(Icon.Check, size = 16.dp, color = P.mint.text, weight = 2.6f, modifier = Modifier.padding(top = 2.dp))
        EbText(text, Ink.Caption, color = P.ink, modifier = Modifier.weight(1f).padding(start = 10.dp))
    }
}
