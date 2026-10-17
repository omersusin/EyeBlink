package com.eyeblink.app.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The room the whole app sits in: the pale sky-washed page by day, deep indigo by night. Nothing
 * else - no gradients, no grain, no lights drifting about; the colour lives in the cards.
 */
@Composable
fun Backdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(P.bg)) { content() }
}
