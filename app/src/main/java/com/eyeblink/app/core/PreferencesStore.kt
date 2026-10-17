package com.eyeblink.app.core

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "eyeblink_prefs")

class PreferencesStore(private val context: Context) {
    private object Keys {
        val enabled = booleanPreferencesKey("enabled")
        val closeMs = longPreferencesKey("close_ms")
        val holdMs = longPreferencesKey("hold_ms")
        val openMs = longPreferencesKey("open_ms")
        val reducedMotion = booleanPreferencesKey("reduced_motion")
    }

    val config: Flow<BlinkConfig> = context.dataStore.data.map { p ->
        BlinkConfig(
            enabled = p[Keys.enabled] ?: true,
            closeMs = p[Keys.closeMs] ?: 140,
            holdMs = p[Keys.holdMs] ?: 150,
            openMs = p[Keys.openMs] ?: 165,
            reducedMotion = p[Keys.reducedMotion] ?: false,
        )
    }

    suspend fun update(transform: (BlinkConfig) -> BlinkConfig) {
        // simplified: write fields
        context.dataStore.edit { prefs ->
            val c = transform(BlinkConfig(
                enabled = prefs[Keys.enabled] ?: true,
                closeMs = prefs[Keys.closeMs] ?: 140,
                holdMs = prefs[Keys.holdMs] ?: 150,
                openMs = prefs[Keys.openMs] ?: 165,
                reducedMotion = prefs[Keys.reducedMotion] ?: false,
            ))
            prefs[Keys.enabled] = c.enabled
            prefs[Keys.closeMs] = c.closeMs
            prefs[Keys.holdMs] = c.holdMs
            prefs[Keys.openMs] = c.openMs
            prefs[Keys.reducedMotion] = c.reducedMotion
        }
    }
}
EOF
echo done