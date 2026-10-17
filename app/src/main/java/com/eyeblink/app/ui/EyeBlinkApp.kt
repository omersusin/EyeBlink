package com.eyeblink.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eyeblink.app.ui.i18n.Strings
import com.eyeblink.app.ui.kit.Backdrop
import com.eyeblink.app.ui.kit.BlinkTransition
import com.eyeblink.app.ui.kit.BottomSheet
import com.eyeblink.app.ui.kit.Dock
import com.eyeblink.app.ui.kit.DockItem
import com.eyeblink.app.ui.kit.EbTheme
import com.eyeblink.app.ui.kit.Icon
import com.eyeblink.app.ui.kit.Insets
import com.eyeblink.app.ui.kit.S
import com.eyeblink.app.ui.screens.AboutScreen
import com.eyeblink.app.ui.screens.CalibrationScreen
import com.eyeblink.app.ui.screens.DiagnosticsScreen
import com.eyeblink.app.ui.screens.HomeScreen
import com.eyeblink.app.ui.screens.AppPickerSheet
import com.eyeblink.app.ui.screens.AppsScreen
import com.eyeblink.app.ui.screens.MoreScreen
import com.eyeblink.app.ui.screens.NightScreen
import com.eyeblink.app.ui.screens.OnboardingScreen
import com.eyeblink.app.ui.screens.PowerScreen
import com.eyeblink.app.ui.screens.PrivacyScreen
import com.eyeblink.app.ui.screens.RhythmScreen
import com.eyeblink.app.ui.screens.StatsScreen
import com.eyeblink.app.ui.screens.StudioScreen
import com.eyeblink.app.ui.state.Page
import com.eyeblink.app.ui.state.Tab
import com.eyeblink.app.ui.state.UiActions
import com.eyeblink.app.ui.state.UiState

/**
 * The whole app UI. Platform-free: it takes a state and an action sink, so the same code runs
 * inside the Android activity and in the desktop design lab.
 *
 * [backHandler] is supplied by the platform (Android's back gesture); the shared UI only says
 * when back should be intercepted.
 */
@Composable
fun EyeBlinkApp(
    state: UiState,
    actions: UiActions,
    strings: Strings,
    reducedMotion: Boolean = false,
    insets: Insets = Insets(),
    initialTab: Tab = Tab.TODAY,
    initialPage: Page? = null,
    onboardingStep: Int = 0,
    backHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit = { _, _ -> },
) {
    EbTheme(strings, reducedMotion, insets) {
        Backdrop {
            if (!state.settings.onboardingDone) {
                OnboardingScreen(state, actions, initialStep = onboardingStep)
                return@Backdrop
            }
            var tabIndex by rememberSaveable { mutableStateOf(initialTab.ordinal) }
            var page by remember { mutableStateOf(initialPage) }
            var picker by remember { mutableStateOf(false) }
            val tab = Tab.entries[tabIndex]

            backHandler(page != null || picker) { if (picker) picker = false else page = null }

            Box(Modifier.fillMaxSize()) {
                BlinkTransition(target = page ?: tab, modifier = Modifier.fillMaxSize()) { dest ->
                    when (dest) {
                        is Tab -> when (dest) {
                            Tab.TODAY -> HomeScreen(state, actions, onTab = { tabIndex = it.ordinal }, onPage = { page = it })
                            Tab.BLINK -> StudioScreen(state, actions)
                            Tab.RHYTHM -> RhythmScreen(state, actions, onPage = { page = it })
                            Tab.APPS -> AppsScreen(state, actions, onPick = { picker = true })
                            Tab.MORE -> MoreScreen(state, actions, onPage = { page = it })
                            else -> Placeholder(dest.name)
                        }
                        is Page -> when (dest) {
                            Page.CALIBRATION -> CalibrationScreen(state, actions, onBack = { page = null })
                            Page.NIGHT -> NightScreen(state, actions, onBack = { page = null }, onPage = { page = it })
                            Page.POWER -> PowerScreen(state, actions, onBack = { page = null })
                            Page.DIAGNOSTICS -> DiagnosticsScreen(state, actions, onBack = { page = null })
                            Page.STATS -> StatsScreen(state, actions, onBack = { page = null })
                            Page.PRIVACY -> PrivacyScreen(state, actions, onBack = { page = null })
                            Page.ABOUT -> AboutScreen(state, actions, onBack = { page = null })
                        }
                        else -> Unit
                    }
                }

                if (page == null && !picker) {
                    // Content fades into black before it reaches the dock, so nothing shows through it.
                    Box(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(150.dp + insets.bottom)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f), Color.Black))),
                    )
                    val s = S
                    Dock(
                        items = listOf(
                            DockItem(Icon.Eye, s.t("Today", "Bugün")),
                            DockItem(Icon.Sliders, s.t("Blink", "Kırpma")),
                            DockItem(Icon.Pulse, s.t("Rhythm", "Ritim")),
                            DockItem(Icon.Grid, s.t("Apps", "Uygulamalar")),
                            DockItem(Icon.Dots, s.t("More", "Daha")),
                        ),
                        selected = tabIndex,
                        onSelect = { tabIndex = it },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 14.dp + insets.bottom),
                    )
                }

                BottomSheet(visible = picker, onDismiss = { picker = false }) { AppPickerSheet(state, actions) { picker = false } }
            }
        }
    }
}

@Composable
private fun Placeholder(name: String) {
    com.eyeblink.app.ui.kit.EbText(name, com.eyeblink.app.ui.kit.Ink.Headline, modifier = Modifier.padding(30.dp))
}
