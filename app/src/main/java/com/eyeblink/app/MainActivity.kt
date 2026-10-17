package com.eyeblink.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.eyeblink.app.core.BlinkConfig
import com.eyeblink.app.core.BlinkOverlayService
import com.eyeblink.app.core.PreferencesStore
import com.eyeblink.app.ui.SettingsScreen
import com.eyeblink.app.ui.theme.EyeBlinkColors
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val store by lazy { PreferencesStore(applicationContext) }

    /** Returns from the system overlay-permission screen -> re-check, then start if granted. */
    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            startServiceIfPermitted()
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* FGS runs either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var config by remember { mutableStateOf(BlinkConfig()) }
            var running by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                store.config.collect { config = it }
            }
            LaunchedEffect(Unit) {
                running = isServiceRunning()
            }

            Box(Modifier.fillMaxSize().background(EyeBlinkColors.bg)) {
                SettingsScreen(
                    config = config,
                    onConfigChange = { updated -> scope.launch { store.update { updated } } },
                    serviceRunning = running,
                    onToggleService = { toggleService { running = it } },
                )
            }
        }

        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        // The permission screen can be dismissed without a result callback in some OEM builds.
        startServiceIfPermitted()
    }

    private fun toggleService(onResult: (Boolean) -> Unit) {
        if (isServiceRunning()) {
            stopService(Intent(this, BlinkOverlayService::class.java))
            onResult(false)
        } else {
            if (hasOverlayPermission()) {
                startService(Intent(this, BlinkOverlayService::class.java))
                onResult(true)
            } else {
                onResult(false)
                overlayPermissionLauncher.launch(overlaySettingsIntent())
            }
        }
    }

    private fun startServiceIfPermitted() {
        if (hasOverlayPermission()) {
            startService(Intent(this, BlinkOverlayService::class.java))
        }
    }

    private fun hasOverlayPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun overlaySettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == BlinkOverlayService::class.java.name }
    }
}