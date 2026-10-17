package com.eyeblink.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eyeblink.app.ui.kit.ButtonKind
import com.eyeblink.app.ui.kit.Card
import com.eyeblink.app.ui.kit.EbText
import com.eyeblink.app.ui.kit.Hue
import com.eyeblink.app.ui.kit.IconBadge
import com.eyeblink.app.ui.kit.Icon
import com.eyeblink.app.ui.kit.Ink
import com.eyeblink.app.ui.kit.LanguageChip
import com.eyeblink.app.ui.kit.P
import com.eyeblink.app.ui.kit.PillButton
import com.eyeblink.app.ui.kit.S
import com.eyeblink.app.ui.kit.ScreenColumn
import com.eyeblink.app.ui.kit.SectionHeader
import com.eyeblink.app.ui.kit.Tide
import com.eyeblink.app.ui.kit.androidxClickable
import com.eyeblink.app.ui.kit.reveal
import com.eyeblink.app.ui.state.Page
import com.eyeblink.app.ui.state.UiActions
import com.eyeblink.app.ui.state.UiState
import com.eyeblink.core.model.Appearance

private class Tile(val page: Page, val icon: Icon, val hue: Hue, val title: String, val caption: String)

@Composable
fun MoreScreen(state: UiState, actions: UiActions, onPage: (Page) -> Unit, onLanguage: () -> Unit, modifier: Modifier = Modifier) {
    val s = S
    val p = P
    ScreenColumn(modifier) {
        Box(Modifier.reveal(0)) { SectionHeader(s.t("More", "Daha fazla"), s.t("Everything else", "Geri kalan her şey")) }

        val tiles = listOf(
            Tile(Page.CALIBRATION, Icon.Eye, p.sky, s.t("Calibration", "Kalibrasyon"), s.t("Learn your blink", "Kırpmanı öğren")),
            Tile(Page.NIGHT, Icon.Moon, p.grape, s.t("Night", "Gece"), s.t("Warmth & brightness", "Sıcaklık ve parlaklık")),
            Tile(Page.POWER, Icon.Bolt, p.sun, s.t("Power modes", "Güç modları"), s.t("Rootless · Shizuku · Root", "Rootsuz · Shizuku · Root")),
            Tile(Page.DIAGNOSTICS, Icon.Pulse, p.mint, s.t("Diagnostics", "Tanılama"), s.t("Permissions & health", "İzinler ve sağlık")),
            Tile(Page.STATS, Icon.Chart, p.coral, s.t("Statistics", "İstatistikler"), s.t("Only on this phone", "Yalnızca bu telefonda")),
            Tile(Page.PRIVACY, Icon.Shield, p.mint, s.t("Privacy", "Gizlilik"), s.t("What is and isn't kept", "Neler tutulur, neler tutulmaz")),
        )
        tiles.chunked(2).forEachIndexed { row, pair ->
            Row(Modifier.reveal(1 + row).fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                pair.forEach { tile ->
                    Card(Modifier.weight(1f).fillMaxHeight(), tint = tile.hue, onClick = { onPage(tile.page) }, padding = PaddingValues(16.dp)) {
                        IconBadge(tile.icon, tile.hue, size = 46.dp)
                        Spacer(Modifier.height(22.dp))
                        EbText(tile.title, Ink.Title, maxLines = 1)
                        Spacer(Modifier.height(2.dp))
                        EbText(tile.caption, Ink.Caption, maxLines = 2)
                    }
                }
            }
        }

        Card(Modifier.reveal(5).fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    EbText(s.t("Language", "Dil"), Ink.Title)
                    Spacer(Modifier.height(2.dp))
                    EbText(s.t("Tap to pick from the list.", "Listeden seçmek için dokun."), Ink.Caption)
                }
                LanguageChip(languageLabel(state.settings.language, s), onLanguage)
            }
            Spacer(Modifier.height(16.dp))
            EbText(s.t("Appearance", "Görünüm"), Ink.Title)
            Spacer(Modifier.height(2.dp))
            EbText(s.t("Daylight is bright, Moonlit is easy on a dark room.", "Gündüz parlak, Ay ışığı karanlık odada gözü yormaz."), Ink.Caption)
            Spacer(Modifier.height(12.dp))
            val modes = listOf(Appearance.SYSTEM, Appearance.LIGHT, Appearance.DARK)
            Tide(
                listOf(s.t("Auto", "Otomatik"), s.t("Light", "Açık"), s.t("Dark", "Koyu")),
                modes.indexOf(state.settings.appearance),
                { actions.update { st -> st.copy(appearance = modes[it]) } },
            )
        }

        Card(Modifier.reveal(6).fillMaxWidth(), tint = p.coral) {
            SectionHeader(s.t("Emergency stop", "Acil durdurma"), null)
            Spacer(Modifier.height(8.dp))
            EbText(s.t("Instantly switches off every intervention - blinks, rests, camera, brightness changes - and removes anything EyeBlink has drawn. The Quick Settings tile does the same from anywhere.", "Tüm müdahaleleri (göz kırpma, mola, kamera, parlaklık değişiklikleri) anında kapatır ve EyeBlink'in çizdiği her şeyi kaldırır. Hızlı Ayarlar karosu bunu her yerden yapar."), Ink.Caption, color = p.ink)
            Spacer(Modifier.height(14.dp))
            PillButton(s.t("Stop everything", "Her şeyi durdur"), { actions.emergencyStop() }, kind = ButtonKind.Danger, icon = Icon.Close, compact = true)
        }

        Row(Modifier.reveal(7).fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center) {
            EbText(
                s.t("About EyeBlink", "EyeBlink hakkında"), Ink.BodyStrong, color = p.sky.text,
                modifier = Modifier.androidxClickable { onPage(Page.ABOUT) }.padding(10.dp),
            )
        }
    }
}
