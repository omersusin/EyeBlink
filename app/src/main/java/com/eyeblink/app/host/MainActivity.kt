package com.eyeblink.app.host

import android.Manifest
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.eyeblink.app.engine.engine
import com.eyeblink.app.platform.Access
import com.eyeblink.app.ui.EyeBlinkApp
import com.eyeblink.app.ui.i18n.Strings
import com.eyeblink.app.ui.kit.Insets
import com.eyeblink.app.ui.state.UiActions
import java.util.Locale

/** The settings screen: a thin Compose host around the platform-free UI, plus the two runtime permission dialogs. */
class MainActivity : ComponentActivity() {

    private var cameraCallback: ((Boolean) -> Unit)? = null

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraCallback?.invoke(granted)
        cameraCallback = null
        if (!granted && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            // "Don't ask again": the only way left is the app's own settings page.
            startActivity(Access.appDetailsIntent(this))
        }
        engine.onUiVisible(true)
    }

    private val notificationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        engine.onUiVisible(true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val actions = AndroidActions(this)
        setContent { Root(actions) }
    }

    override fun onStart() {
        super.onStart()
        engine.onUiVisible(true)
    }

    override fun onStop() {
        engine.onUiVisible(false)
        super.onStop()
    }

    fun requestCamera(then: (Boolean) -> Unit) {
        if (Access.camera(this)) { then(true); return }
        cameraCallback = then
        cameraLauncher.launch(Manifest.permission.CAMERA)
    }

    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !Access.notifications(this)) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun Root(actions: UiActions) {
    val context = LocalContext.current
    val state by context.engine.ui.collectAsState()
    val strings = remember(state.settings.language) { Strings.resolve(state.settings.language, Locale.getDefault().toLanguageTag()) }
    val bars = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    val reduced = remember(state.nowMs / 10_000L) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    EyeBlinkApp(
        state = state,
        actions = actions,
        strings = strings,
        reducedMotion = reduced,
        insets = Insets(bars.calculateTopPadding(), bars.calculateBottomPadding()),
        backHandler = { enabled, onBack -> BackHandler(enabled = enabled, onBack = onBack) },
    )
}
