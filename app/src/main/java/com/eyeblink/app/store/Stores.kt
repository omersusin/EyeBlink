package com.eyeblink.app.store

import android.content.Context
import com.eyeblink.core.calibration.PersonalProfile
import com.eyeblink.core.model.CoreJson
import com.eyeblink.core.model.EyeBlinkSettings
import com.eyeblink.core.model.SettingsCodec
import com.eyeblink.core.perf.BatteryLedger
import com.eyeblink.core.playback.CompatRecord
import com.eyeblink.core.stats.UsageLedger
import kotlinx.serialization.encodeToString

/**
 * Everything EyeBlink remembers, in one private preferences file.
 *
 * Values are JSON written with the same configuration the settings use, so a newer build's file
 * still loads in an older one and a damaged value falls back to its default instead of crashing
 * the service at boot. Nothing here is ever sent anywhere: the app does not hold the Internet
 * permission.
 */
class Stores(ctx: Context) {

    private val prefs = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun loadSettings(): EyeBlinkSettings = SettingsCodec.decode(prefs.getString(SETTINGS, null))
    fun saveSettings(s: EyeBlinkSettings) = prefs.edit().putString(SETTINGS, SettingsCodec.encode(s)).apply()

    fun loadProfile(): PersonalProfile = read(PROFILE) { PersonalProfile() }
    fun saveProfile(p: PersonalProfile) = write(PROFILE, p)

    fun loadLedger(): UsageLedger = read(LEDGER) { UsageLedger() }
    fun saveLedger(l: UsageLedger) = write(LEDGER, l)

    fun loadBattery(): BatteryLedger = read(BATTERY) { BatteryLedger() }
    fun saveBattery(b: BatteryLedger) = write(BATTERY, b)

    fun loadCompat(): Map<String, CompatRecord> = read(COMPAT) { emptyMap() }
    fun saveCompat(c: Map<String, CompatRecord>) = write(COMPAT, c)

    /** Whether the master switch was on when the process last ran, and in which boot of the phone. */
    var wasRunning: Boolean
        get() = prefs.getBoolean(WAS_RUNNING, false)
        set(v) { prefs.edit().putBoolean(WAS_RUNNING, v).apply() }

    var runningBootCount: Int
        get() = prefs.getInt(BOOT_COUNT, -1)
        set(v) { prefs.edit().putInt(BOOT_COUNT, v).apply() }

    private inline fun <reified T> read(key: String, default: () -> T): T =
        runCatching { prefs.getString(key, null)?.let { CoreJson.instance.decodeFromString<T>(it) } }.getOrNull() ?: default()

    private inline fun <reified T> write(key: String, value: T) {
        prefs.edit().putString(key, CoreJson.instance.encodeToString(value)).apply()
    }

    private companion object {
        const val FILE = "eyeblink"
        const val SETTINGS = "settings.v1"
        const val PROFILE = "profile.v1"
        const val LEDGER = "ledger.v1"
        const val BATTERY = "battery.v1"
        const val COMPAT = "compat.v1"
        const val WAS_RUNNING = "was_running"
        const val BOOT_COUNT = "boot_count"
    }
}
