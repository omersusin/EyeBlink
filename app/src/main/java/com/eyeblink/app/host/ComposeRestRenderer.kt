package com.eyeblink.app.host

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.eyeblink.app.overlay.OverlayWindows
import com.eyeblink.app.overlay.RestRenderer
import com.eyeblink.app.overlay.navigationBarHeightPx
import com.eyeblink.app.overlay.statusBarHeightPx
import com.eyeblink.app.platform.nowMs
import com.eyeblink.app.ui.i18n.Strings
import com.eyeblink.app.ui.kit.EbTheme
import com.eyeblink.app.ui.kit.Insets
import com.eyeblink.app.ui.rest.RestScene
import com.eyeblink.core.anim.BlinkSpec
import com.eyeblink.core.rest.RestPhase
import com.eyeblink.core.rest.RestSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The long rest in a window of its own: the same Compose scene the design lab renders, hosted in a
 * [ComposeView] that is attached only for the length of the rest. Unlike the blink it needs touches (to
 * skip or confirm) and a composition, and it can afford them: a rest lasts seconds, not milliseconds.
 */
class ComposeRestRenderer(
    private val app: Application,
    private val windows: OverlayWindows,
    private val strings: () -> Strings,
    private val reducedMotion: () -> Boolean,
) : RestRenderer {

    private var view: ComposeView? = null
    private var owner: OverlayLifecycleOwner? = null

    override suspend fun run(session: RestSession, spec: BlinkSpec) {
        withContext(Dispatchers.Main.immediate) {
            val state = mutableStateOf(session.view(nowMs()))
            val lifecycle = OverlayLifecycleOwner().also { it.resume() }
            val density = app.resources.displayMetrics.density
            val insets = Insets((app.statusBarHeightPx() / density).dp, (app.navigationBarHeightPx() / density).dp)
            val s = strings()
            val reduced = reducedMotion() || spec.reducedMotion
            val style = session.request.style

            val compose = ComposeView(app).apply {
                setViewTreeLifecycleOwner(lifecycle)
                setViewTreeSavedStateRegistryOwner(lifecycle)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    EbTheme(s, reduced, insets) {
                        RestScene(state.value, style, onSkip = { session.skip(nowMs()) }, onTap = { session.onTap(nowMs()) })
                    }
                }
            }
            if (!windows.attach(compose, touchable = true, keepScreenOn = true)) {
                lifecycle.destroy()
                error("No window can be shown: grant Accessibility or Display over other apps")
            }
            view = compose
            owner = lifecycle
            try {
                while (true) {
                    val v = session.view(nowMs())
                    state.value = v
                    if (v.phase == RestPhase.DONE) break
                    delay(UPDATE_MS)
                }
                // Let the scene fade out before the window goes.
                delay(FADE_OUT_MS)
            } finally {
                remove()
            }
        }
    }

    override fun clear() = remove()

    private fun remove() {
        view?.let {
            windows.detach(it)
            runCatching { it.disposeComposition() }
        }
        owner?.destroy()
        view = null
        owner = null
    }

    private companion object {
        const val UPDATE_MS = 50L
        const val FADE_OUT_MS = 650L
    }
}
