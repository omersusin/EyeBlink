package com.eyeblink.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eyeblink.app.ui.i18n.Strings
import com.eyeblink.app.ui.kit.Dusk
import com.eyeblink.app.ui.kit.EbText
import com.eyeblink.app.ui.kit.Ico
import com.eyeblink.app.ui.kit.Icon
import com.eyeblink.app.ui.kit.Ink
import com.eyeblink.app.ui.kit.S
import com.eyeblink.app.ui.state.UiActions
import com.eyeblink.app.ui.state.UiState
import com.eyeblink.core.model.Language

/** What a language is called on the chip. A language always names itself in its own words. */
fun languageLabel(language: Language, s: Strings): String = when (language) {
    Language.SYSTEM -> s.t("Auto", "Otomatik")
    Language.EN -> "English"
    Language.TR -> "Türkçe"
}

/**
 * The list behind the language chip. Adding a language means adding one line here and one to
 * [Language]; nothing else about the picker has to change.
 */
@Composable
fun LanguageSheet(state: UiState, actions: UiActions, onDone: () -> Unit) {
    val s = S
    val chosen = state.settings.language
    EbText(s.t("Language", "Dil"), Ink.Headline)
    Spacer(Modifier.height(4.dp))
    EbText(s.t("Choose the language EyeBlink speaks. It changes at once.", "EyeBlink'in hangi dilde konuşacağını seç. Hemen değişir."), Ink.Caption)
    Spacer(Modifier.height(12.dp))
    val options = listOf(
        Triple(Language.EN, "English", s.t("English", "İngilizce")),
        Triple(Language.TR, "Türkçe", s.t("Turkish", "Türkçe")),
        Triple(Language.SYSTEM, s.t("Same as my phone", "Telefonumla aynı"), s.t("Uses English unless your phone is set to Turkish", "Telefonun Türkçeyse Türkçe, değilse İngilizce kullanır")),
    )
    options.forEach { (language, title, subtitle) ->
        Row(
            Modifier.fillMaxWidth()
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    actions.update { it.copy(language = language) }
                    onDone()
                }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                EbText(title, Ink.Title)
                Spacer(Modifier.height(2.dp))
                EbText(subtitle, Ink.Caption)
            }
            Tick(language == chosen)
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Tick(on: Boolean) {
    androidx.compose.foundation.layout.Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(28.dp)) {
            if (on) drawCircle(Dusk.Iris)
            else drawCircle(Dusk.HairlineStrong, style = androidx.compose.ui.graphics.drawscope.Stroke(1.8.dp.toPx()))
        }
        if (on) Ico(Icon.Check, size = 16.dp, color = androidx.compose.ui.graphics.Color(0xFF04201C), weight = 2.6f)
    }
}
