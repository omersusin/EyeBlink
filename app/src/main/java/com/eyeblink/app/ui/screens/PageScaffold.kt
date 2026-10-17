package com.eyeblink.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eyeblink.app.ui.kit.EbText
import com.eyeblink.app.ui.kit.Icon
import com.eyeblink.app.ui.kit.Ink
import com.eyeblink.app.ui.kit.RoundIconButton
import com.eyeblink.app.ui.kit.S
import com.eyeblink.app.ui.kit.ScreenColumn
import com.eyeblink.app.ui.kit.reveal
import com.eyeblink.app.ui.kit.Dusk

/** A full-screen sub-page: a back button, an eyebrow and a title, then scrolling content. */
@Composable
fun PageColumn(
    eyebrow: String,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: androidx.compose.ui.unit.Dp = 40.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    ScreenColumn(modifier, bottomInset = bottomInset, topInset = 14.dp) {
        Row(Modifier.fillMaxWidth().reveal(0), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            RoundIconButton(Icon.ChevronLeft, S.t("Back", "Geri"), onBack)
            Column(Modifier.weight(1f)) {
                EbText(eyebrow, Ink.Eyebrow, color = Dusk.Amber)
                EbText(title, Ink.Headline, maxLines = 1)
            }
        }
        content()
    }
}
