package com.eyeblink.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.eyeblink.app.core.BlinkConfig
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
    }
}
EOF
echo done