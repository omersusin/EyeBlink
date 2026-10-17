package com.eyeblink.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eyeblink.app.ui.kit.ButtonKind
import com.eyeblink.app.ui.kit.Dusk
import com.eyeblink.app.ui.kit.EbText
import com.eyeblink.app.ui.kit.Glass
import com.eyeblink.app.ui.kit.Hairline
import com.eyeblink.app.ui.kit.Ico
import com.eyeblink.app.ui.kit.Icon
import com.eyeblink.app.ui.kit.Ink
import com.eyeblink.app.ui.kit.PillButton
import com.eyeblink.app.ui.kit.androidxClickable
import com.eyeblink.app.ui.kit.S
import com.eyeblink.app.ui.kit.ScreenColumn
import com.eyeblink.app.ui.kit.SectionHeader
import com.eyeblink.app.ui.kit.Tide
import com.eyeblink.app.ui.kit.reveal
import com.eyeblink.app.ui.state.Page
import com.eyeblink.app.ui.state.UiActions
import com.eyeblink.app.ui.state.UiState
import com.eyeblink.core.model.Language

@Composable
fun MoreScreen(state: UiState, actions: UiActions, onPage: (Page) -> Unit, modifier: Modifier = Modifier) {
    val s = S
    ScreenColumn(modifier) {
        Box(Modifier.reveal(0)) { SectionHeader(s.t("More", "Daha fazla"), s.t("Everything else", "Geri kalan her şey")) }

        val tiles = listOf(
            Triple(Page.CALIBRATION, Icon.Eye to Dusk.Iris, s.t("Calibration", "Kalibrasyon") to s.t("Learn your blink", "Kırpmanı öğren")),
            Triple(Page.NIGHT, Icon.Moon to Dusk.Violet, s.t("Night", "Gece") to s.t("Warmth & brightness", "Sıcaklık ve parlaklık")),
            Triple(Page.POWER, Icon.Bolt to Dusk.Amber, s.t("Power modes", "Güç modları") to s.t("Rootless · Shizuku · Root", "Rootsuz · Shizuku · Root")),
            Triple(Page.DIAGNOSTICS, Icon.Pulse to Dusk.Ok, s.t("Diagnostics", "Tanılama") to s.t("Permissions & health", "İzinler ve sağlık")),
            Triple(Page.STATS, Icon.Chart to Dusk.Iris, s.t("Statistics", "İstatistikler") to s.t("Only on this phone", "Yalnızca bu telefonda")),
            Triple(Page.PRIVACY, Icon.Shield to Dusk.Rose, s.t("Privacy", "Gizlilik") to s.t("What is and isn't kept", "Neler tutulur, neler tutulmaz")),
        )
        tiles.chunked(2).forEachIndexed { row, pair ->
            Row(Modifier.reveal(1 + row).fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { (page, iconColor, texts) ->
                    Glass(Modifier.weight(1f).fillMaxHeight(), glow = iconColor.second, onClick = { onPage(page) }, padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                        Ico(iconColor.first, size = 28.dp, color = iconColor.second)
                        Spacer(Modifier.height(26.dp))
                        EbText(texts.first, Ink.Title, maxLines = 1)
                        Spacer(Modifier.height(2.dp))
                        EbText(texts.second, Ink.Caption, maxLines = 2)
                    }
                }
            }
        }

        Glass(Modifier.reveal(5).fillMaxWidth()) {
            SectionHeader(s.t("Language", "Dil"), null)
            Spacer(Modifier.height(12.dp))
            Tide(listOf(s.t("System", "Sistem"), "English", "Türkçe"), state.settings.language.ordinal, { i ->
                actions.update { it.copy(language = Language.entries[i]) }
            })
        }

        Glass(Modifier.reveal(6).fillMaxWidth(), glow = Dusk.Bad) {
            SectionHeader(s.t("Emergency stop", "Acil durdurma"), null)
            Spacer(Modifier.height(8.dp))
            EbText(s.t("Instantly switches off every intervention - blinks, rests, camera, brightness changes - and removes anything EyeBlink has drawn. The Quick Settings tile does the same from anywhere.", "Tüm müdahaleleri (göz kırpma, mola, kamera, parlaklık değişiklikleri) anında kapatır ve EyeBlink'in çizdiği her şeyi kaldırır. Hızlı Ayarlar karosu bunu her yerden yapar."), Ink.Caption)
            Spacer(Modifier.height(14.dp))
            PillButton(s.t("Stop everything", "Her şeyi durdur"), { actions.emergencyStop() }, kind = ButtonKind.Danger, icon = Icon.Close, compact = true)
        }

        Row(Modifier.reveal(7).fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center) {
            EbText(
                s.t("About EyeBlink", "EyeBlink hakkında"), Ink.Caption, color = Dusk.Iris,
                modifier = Modifier.clickableNoRipple { onPage(Page.ABOUT) },
            )
        }
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.then(
    Modifier.androidxClickable(onClick),
)
