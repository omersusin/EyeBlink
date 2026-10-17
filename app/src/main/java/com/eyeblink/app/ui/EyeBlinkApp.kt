package com.eyeblink.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Brush
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
import com.eyeblink.app.ui.kit.isDark
import com.eyeblink.app.ui.kit.P
import com.eyeblink.app.ui.kit.S
import com.eyeblink.app.ui.screens.AboutScreen
import com.eyeblink.app.ui.screens.CalibrationScreen
import com.eyeblink.app.ui.screens.DiagnosticsScreen
import com.eyeblink.app.ui.screens.HomeScreen
import com.eyeblink.app.ui.screens.LanguageSheet
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

/** The bottom sheets, so the design lab can render them open. */
enum class Sheet { LANGUAGE, APPS }

/**
 * The whole app UI. Platform-free: it takes a state and an action sink, so the same code runs
 * inside the Android activity and in the desktop design lab.
 *
 * [systemDark] says whether the phone is in dark mode; it only matters while the appearance
 * setting is "Auto". [backHandler] is supplied by the platform (Android's back gesture); the
 * shared UI only says when back should be intercepted.
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
    /** Open one of the sheets from the start - for the design lab; the app itself never sets these. */
    initialSheet: Sheet? = null,
    systemDark: Boolean = false,
    backHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit = { _, _ -> },
) {
    val dark = state.settings.appearance.isDark(systemDark)
    EbTheme(strings, reducedMotion, insets, dark) {
        Backdrop {
            var languageSheet by remember { mutableStateOf(initialSheet == Sheet.LANGUAGE) }
            val openLanguage = { languageSheet = true }

            Box(Modifier.fillMaxSize()) {
                if (!state.settings.onboardingDone) {
                    OnboardingScreen(state, actions, initialStep = onboardingStep, onLanguage = openLanguage)
                } else {
                    MainShell(state, actions, insets, initialTab, initialPage, initialSheet == Sheet.APPS, openLanguage, backHandler)
                }
                // Hosted here, above everything, so the same list serves onboarding and More.
                BottomSheet(visible = languageSheet, onDismiss = { languageSheet = false }) { LanguageSheet(state, actions) { languageSheet = false } }
            }
            backHandler(languageSheet) { languageSheet = false }
        }
    }
}

/** The five tabs, the pages reached from More, the dock and the app picker. */
@Composable
private fun MainShell(
    state: UiState,
    actions: UiActions,
    insets: Insets,
    initialTab: Tab,
    initialPage: Page?,
    initialPicker: Boolean,
    onLanguage: () -> Unit,
    backHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit,
) {
    var tabIndex by rememberSaveable { mutableStateOf(initialTab.ordinal) }
    var page by remember { mutableStateOf(initialPage) }
    var picker by remember { mutableStateOf(initialPicker) }
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
                    Tab.MORE -> MoreScreen(state, actions, onPage = { page = it }, onLanguage = onLanguage)
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
            // Content fades into the page before it reaches the dock, so nothing shows through it.
            val bg = P.bg
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(150.dp + insets.bottom)
                    .background(Brush.verticalGradient(listOf(bg.copy(alpha = 0f), bg.copy(alpha = 0.9f), bg))),
            )
            val s = S
            Dock(
                items = listOf(
                    DockItem(Icon.Eye, s.t("Today", "Bugün")),
                    DockItem(Icon.Sliders, s.t("Blink", "Kırpma")),
                    DockItem(Icon.Pulse, s.t("Rhythm", "Ritim")),
                    DockItem(Icon.Grid, s.t("Apps", "Uygulama")),
                    DockItem(Icon.Dots, s.t("More", "Diğer")),
                ),
                selected = tabIndex,
                onSelect = { tabIndex = it },
                modifier = Modifier.align(Alignment.BottomCenter).padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 14.dp + insets.bottom),
            )
        }

        BottomSheet(visible = picker, onDismiss = { picker = false }) { AppPickerSheet(state, actions) { picker = false } }
    }
}

@Composable
private fun Placeholder(name: String) {
    com.eyeblink.app.ui.kit.EbText(name, com.eyeblink.app.ui.kit.Ink.Headline, modifier = Modifier.padding(30.dp))
}
