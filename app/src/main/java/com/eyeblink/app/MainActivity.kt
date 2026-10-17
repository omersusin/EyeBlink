package com.eyeblink.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import com.eyeblink.app.core.BlinkConfig
import com.eyeblink.app.core.BlinkOverlayService
import com.eyeblink.app.ui.SettingsScreen
import com.eyeblink.app.ui.theme.EyeBlinkColors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val config = remember { BlinkConfig() }
            Box(Modifier.fillMaxSize().background(EyeBlinkColors.bg)) {
                SettingsScreen(config)
            }
        }
        ensureOverlayPermission()
    }

    private fun ensureOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } else {
            startService(Intent(this, BlinkOverlayService::class.java))
        }
    }
}
EOF
echo done