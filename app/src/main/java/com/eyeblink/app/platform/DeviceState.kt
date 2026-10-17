package com.eyeblink.app.platform

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import com.eyeblink.core.engine.AudioMode
import com.eyeblink.core.engine.Thermal
import java.util.concurrent.ConcurrentHashMap

/** Battery, heat, call state, screen and camera availability, read from the platform on demand. */
class DeviceState(private val ctx: Context) {

    private val power = ctx.systemService<PowerManager>()
    private val keyguard = ctx.systemService<KeyguardManager>()
    private val audio = ctx.systemService<AudioManager>()
    private val cameras = ctx.systemService<CameraManager>()

    fun screenOn(): Boolean = power.isInteractive
    fun locked(): Boolean = keyguard.isKeyguardLocked
    fun powerSave(): Boolean = power.isPowerSaveMode

    fun audioMode(): AudioMode = when (audio.mode) {
        AudioManager.MODE_RINGTONE -> AudioMode.RINGTONE
        AudioManager.MODE_IN_CALL -> AudioMode.IN_CALL
        AudioManager.MODE_IN_COMMUNICATION -> AudioMode.IN_COMMUNICATION
        else -> AudioMode.NORMAL
    }

    /** Coarse "is any music/video audio playing": the only playback fact available without a media session. */
    fun audioActive(): Boolean = audio.isMusicActive

    fun thermal(): Thermal {
        if (Build.VERSION.SDK_INT < 29) return Thermal.NONE
        return when (power.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_LIGHT -> Thermal.LIGHT
            PowerManager.THERMAL_STATUS_MODERATE -> Thermal.MODERATE
            PowerManager.THERMAL_STATUS_SEVERE -> Thermal.SEVERE
            PowerManager.THERMAL_STATUS_CRITICAL, PowerManager.THERMAL_STATUS_EMERGENCY, PowerManager.THERMAL_STATUS_SHUTDOWN -> Thermal.CRITICAL
            else -> Thermal.NONE
        }
    }

    data class Battery(val percent: Int, val charging: Boolean)

    fun battery(): Battery {
        val i: Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val pct = if (level < 0 || scale <= 0) 100 else (level * 100 / scale)
        return Battery(pct, charging)
    }

    // ── camera availability ─────────────────────────────────────

    private val unavailable = ConcurrentHashMap.newKeySet<String>()
    private var watching = false

    private val frontId: String? by lazy {
        runCatching {
            cameras.cameraIdList.firstOrNull { cameras.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT }
        }.getOrNull()
    }

    private val callback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraUnavailable(cameraId: String) { unavailable += cameraId }
        override fun onCameraAvailable(cameraId: String) { unavailable -= cameraId }
    }

    fun watchCameras() {
        if (watching) return
        watching = true
        runCatching { cameras.registerAvailabilityCallback(callback, Handler(Looper.getMainLooper())) }
    }

    fun unwatchCameras() {
        if (!watching) return
        watching = false
        runCatching { cameras.unregisterAvailabilityCallback(callback) }
        unavailable.clear()
    }

    /**
     * Some other app is using a camera. EyeBlink's own use of the front camera makes that camera
     * "unavailable" too, so while [ownOpen] it is not counted against anyone else.
     */
    fun cameraBusyElsewhere(ownOpen: Boolean): Boolean {
        if (!watching) return false
        val others = if (ownOpen) unavailable.filter { it != frontId } else unavailable.toList()
        return others.isNotEmpty()
    }
}

/** Ambient light in lux, smoothed, from the light sensor while it is being listened to. */
class AmbientLight(ctx: Context) : SensorEventListener {

    private val sensors = ctx.systemService<SensorManager>()
    private val sensor: Sensor? = sensors.getDefaultSensor(Sensor.TYPE_LIGHT)
    private var started = false

    @Volatile var lux: Float? = null
        private set

    val available: Boolean get() = sensor != null

    fun start() {
        if (started || sensor == null) return
        started = true
        // One reading a second, delivered in batches: this sensor is cheap, but not free.
        sensors.registerListener(this, sensor, 1_000_000, 4_000_000)
    }

    fun stop() {
        if (!started) return
        started = false
        sensors.unregisterListener(this)
        lux = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values.firstOrNull() ?: return
        lux = lux?.let { it * 0.7f + v * 0.3f } ?: v
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
