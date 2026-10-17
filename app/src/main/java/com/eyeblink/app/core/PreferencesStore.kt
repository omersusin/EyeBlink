package com.eyeblink.app.core

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "eyeblink_prefs")

private val DEFAULT = BlinkConfig()

class PreferencesStore(private val context: Context) {

    private object Keys {
        val enabled = booleanPreferencesKey("enabled")
        val closeMs = longPreferencesKey("close_ms")
        val holdMs = longPreferencesKey("hold_ms")
        val openMs = longPreferencesKey("open_ms")
        val reducedMotion = booleanPreferencesKey("reduced_motion")
        val curve = floatPreferencesKey("curve")
        val darkenStrength = floatPreferencesKey("darken_strength")
    }

    /** Single read path so every default lives in exactly one place. */
    private fun read(p: Preferences): BlinkConfig = BlinkConfig(
        enabled = p[Keys.enabled] ?: DEFAULT.enabled,
        closeMs = p[Keys.closeMs] ?: DEFAULT.closeMs,
        holdMs = p[Keys.holdMs] ?: DEFAULT.holdMs,
        openMs = p[Keys.openMs] ?: DEFAULT.openMs,
        reducedMotion = p[Keys.reducedMotion] ?: DEFAULT.reducedMotion,
        curve = p[Keys.curve] ?: DEFAULT.curve,
        darkenStrength = p[Keys.darkenStrength] ?: DEFAULT.darkenStrength,
    )

    private fun write(p: MutablePreferences, c: BlinkConfig) {
        p[Keys.enabled] = c.enabled
        p[Keys.closeMs] = c.closeMs
        p[Keys.holdMs] = c.holdMs
        p[Keys.openMs] = c.openMs
        p[Keys.reducedMotion] = c.reducedMotion
        p[Keys.curve] = c.curve
        p[Keys.darkenStrength] = c.darkenStrength
    }

    val config: Flow<BlinkConfig> = context.dataStore.data.map(::read)

    /** Read-modify-write in one transaction so concurrent edits don't clobber fields. */
    suspend fun update(transform: (BlinkConfig) -> BlinkConfig) {
        context.dataStore.edit { prefs -> write(prefs, transform(read(prefs))) }
    }
}