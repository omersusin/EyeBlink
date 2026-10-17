package com.eyeblink.app.calibration

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.calibrationStore by preferencesDataStore(name = "eyeblink_calibration")

/**
 * Local, on-device storage for the personal baseline.
 *
 * Calibration data is biometric-derived, so it never leaves the device: there
 * is no upload path here, by design.
 */
class CalibrationStore(private val context: Context) {

    private object Keys {
        val medianIbiMs = longPreferencesKey("median_ibi_ms")
        val blinkDurationMs = longPreferencesKey("blink_duration_ms")
        val completeBlinkRatio = floatPreferencesKey("complete_blink_ratio")
        val sampleCount = intPreferencesKey("sample_count")
        val calibratedAtMs = longPreferencesKey("calibrated_at_ms")
    }

    /** Emits the stored baseline, or null when the user has never calibrated. */
    val baseline: Flow<BlinkBaseline?> = context.calibrationStore.data.map { p ->
        val count = p[Keys.sampleCount] ?: 0
        if (count <= 0) null
        else BlinkBaseline(
            medianIbiMs = p[Keys.medianIbiMs] ?: 0L,
            blinkDurationMs = p[Keys.blinkDurationMs] ?: 0L,
            completeBlinkRatio = p[Keys.completeBlinkRatio] ?: 0f,
            sampleCount = count,
        )
    }

    val calibratedAtMs: Flow<Long> = context.calibrationStore.data.map { it[Keys.calibratedAtMs] ?: 0L }

    suspend fun save(baseline: BlinkBaseline) {
        context.calibrationStore.edit { p ->
            p[Keys.medianIbiMs] = baseline.medianIbiMs
            p[Keys.blinkDurationMs] = baseline.blinkDurationMs
            p[Keys.completeBlinkRatio] = baseline.completeBlinkRatio
            p[Keys.sampleCount] = baseline.sampleCount
            p[Keys.calibratedAtMs] = System.currentTimeMillis()
        }
    }

    /** Wipes the profile. The user must be able to reset calibration. */
    suspend fun clear() {
        context.calibrationStore.edit { it.clear() }
    }
}