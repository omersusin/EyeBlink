package com.eyeblink.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import com.eyeblink.app.ui.eye.EyeHero
import com.eyeblink.app.ui.kit.Badge
import com.eyeblink.app.ui.kit.P
import com.eyeblink.app.ui.kit.EbText
import com.eyeblink.app.ui.kit.Card
import com.eyeblink.app.ui.kit.Hairline
import com.eyeblink.app.ui.kit.Hue
import com.eyeblink.app.ui.kit.IconBadge
import com.eyeblink.app.ui.kit.Icon
import com.eyeblink.app.ui.kit.Ink
import com.eyeblink.app.ui.kit.S
import com.eyeblink.app.ui.kit.Tone
import com.eyeblink.app.ui.kit.reveal
import com.eyeblink.app.ui.state.UiActions
import com.eyeblink.app.ui.state.UiState

/** The mark, what the app believes, how far to trust each part of it, and the people whose work it stands on. */
@Composable
fun AboutScreen(state: UiState, actions: UiActions, onBack: () -> Unit) {
    val s = S
    PageColumn(s.t("About", "Hakkında"), "EyeBlink", onBack) {
        Column(Modifier.reveal(1).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            EyeHero(Modifier.fillMaxWidth(), description = s.t("The EyeBlink eye. Tap it to blink.", "EyeBlink gözü. Kırpması için dokun."))
            Spacer(Modifier.height(14.dp))
            EbText(s.t("Give your eyes the pause back.", "Gözlerine duraklamayı geri ver."), Ink.Headline, align = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            if (state.diag.appVersion.isNotEmpty()) Badge("v${state.diag.appVersion}", Tone.Quiet, dot = false)
        }

        Card(Modifier.reveal(2).fillMaxWidth()) {
            EbText(s.t("What it believes", "İlkeleri"), Ink.Title)
            Spacer(Modifier.height(6.dp))
            Belief(
                Icon.Check, P.mint, s.t("Honest", "Dürüst"),
                s.t("It says a blink was seen only when the camera saw one. An estimate is always called an estimate.", "Bir göz kırpmasını yalnızca kamera gördüyse gördüm der. Tahmin her zaman tahmin diye adlandırılır."),
            )
            Hairline()
            Belief(
                Icon.Pause, P.sun, s.t("Careful", "Dikkatli"),
                s.t("A video is paused only after the pause is confirmed, and handed back only if nothing changed. It never resumes what you paused yourself.", "Bir video yalnızca duraklatma doğrulandıktan sonra durdurulur ve yalnızca hiçbir şey değişmediyse geri verilir. Senin duraklattığını asla sürdürmez."),
            )
            Hairline()
            Belief(
                Icon.Moon, P.grape, s.t("Quiet", "Sessiz"),
                s.t("It waits for a still moment, stays away from sensitive screens and calls, and removes everything it drew if anything goes wrong.", "Sakin bir an bekler, hassas ekranlardan ve aramalardan uzak durur, bir şeyler ters giderse çizdiği her şeyi kaldırır."),
            )
        }

        Card(Modifier.reveal(3).fillMaxWidth(), tint = P.sun) {
            EbText(s.t("What to expect", "Ne beklemeli"), Ink.Title)
            Spacer(Modifier.height(8.dp))
            EbText(
                s.t(
                    "Video apps differ, and they change with every update. So EyeBlink tests each app on your phone - and again when the app updates - before it relies on pausing it. Shizuku, Root and LSPosed paths are experimental and are only used once their test passes.",
                    "Video uygulamaları birbirinden farklıdır ve her güncellemeyle değişir. Bu yüzden EyeBlink, bir uygulamayı duraklatmaya güvenmeden önce onu telefonunda test eder - uygulama güncellenince yeniden. Shizuku, Root ve LSPosed yolları deneyseldir ve yalnızca testleri geçtikten sonra kullanılır.",
                ),
                Ink.Body, color = P.ink,
            )
            Spacer(Modifier.height(10.dp))
            EbText(
                s.t("EyeBlink is not a medical device. It does not treat dry eye; it gives you short, regular pauses.", "EyeBlink tıbbi bir cihaz değildir. Kuru gözü tedavi etmez; sana kısa, düzenli duraklamalar verir."),
                Ink.Caption, color = P.inkSoft,
            )
        }

        Card(Modifier.reveal(4).fillMaxWidth()) {
            EbText(s.t("Made with", "Şunlarla yapıldı"), Ink.Title)
            Spacer(Modifier.height(8.dp))
            Credit("Bricolage Grotesque · Figtree", s.t("typefaces, SIL Open Font License", "yazı tipleri, SIL Açık Yazı Tipi Lisansı"))
            Credit("DM Mono", s.t("typeface, SIL Open Font License", "yazı tipi, SIL Açık Yazı Tipi Lisansı"))
            Credit("MediaPipe Face Landmarker", s.t("on-device face model, Apache 2.0", "cihaz üstü yüz modeli, Apache 2.0"))
            Credit("Shizuku API", s.t("optional adb-level access, Apache 2.0", "isteğe bağlı adb düzeyi erişim, Apache 2.0"))
            Credit("CameraX · Compose · Kotlin", "Apache 2.0")
            Spacer(Modifier.height(8.dp))
            EbText("github.com/omersusin/EyeBlink", Ink.BodyStrong, color = P.sky.text)
        }
    }
}

@Composable
private fun Belief(icon: Icon, hue: Hue, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        IconBadge(icon, hue, size = 42.dp)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            EbText(title, Ink.Title)
            Spacer(Modifier.height(3.dp))
            EbText(body, Ink.Caption)
        }
    }
}

@Composable
private fun Credit(name: String, what: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        EbText(name, Ink.BodyStrong)
        EbText(what, Ink.Caption)
    }
}
