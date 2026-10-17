package com.eyeblink.app.night

import android.content.Context
import android.provider.Settings

class NightDisplayController(private val context: Context) {
    fun isNightLightEnabled(): Boolean = try {
        Settings.Secure.getInt(context.contentResolver, "night_display_activated", 0) == 1
    } catch (e: Exception) { false }

    fun setNightLight(enabled: Boolean) {
        try {
            Settings.Secure.putInt(context.contentResolver, "night_display_activated", if (enabled) 1 else 0)
        } catch (e: SecurityException) {
            // requires WRITE_SECURE_SETTINGS; no-op fallback
        }
    }
}
