package com.eyeblink.app.system

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.eyeblink.app.engine.engine
import com.eyeblink.app.platform.Signals

/**
 * The foreground service: it keeps EyeBlink's process alive, shows the ongoing notification (which
 * doubles as the visible "EyeBlink is active" marker), and carries the camera type when camera
 * tracking is on. All behaviour lives in the engine; this class only holds the notification up.
 *
 * Android 14 only lets a foreground service pick up the camera type while the app is visible, so
 * after a restart in the background the service comes back without it and camera tracking waits
 * until the app is next opened. Nothing else depends on it.
 */
class EyeBlinkService : Service() {

    private val main = Handler(Looper.getMainLooper())
    private var cameraType = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        engine.attachService(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        enterForeground()
        if (!engine.wantsService()) stopSelfSafely()
        return START_STICKY
    }

    /** Re-applies the notification and the service type after the engine's needs changed. */
    fun refresh() {
        main.post { enterForeground() }
    }

    private fun enterForeground() {
        val e = engine
        val wantCamera = e.wantsCamera() && (Build.VERSION.SDK_INT < 34 || Signals.uiVisible || cameraType)
        val notification = e.notification()
        try {
            start(notification, wantCamera)
            cameraType = wantCamera
        } catch (ex: Exception) {
            Log.w(TAG, "foreground start with camera=$wantCamera failed: ${ex.message}")
            try {
                start(notification, false)
                cameraType = false
            } catch (ex2: Exception) {
                Log.w(TAG, "foreground start failed: ${ex2.message}")
                stopSelf()
            }
        }
    }

    private fun start(n: android.app.Notification, camera: Boolean) {
        if (Build.VERSION.SDK_INT >= 34) {
            val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or if (camera) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0
            startForeground(Notifier.ID_RUNNING, n, type)
        } else {
            startForeground(Notifier.ID_RUNNING, n)
        }
    }

    fun stopSelfSafely() {
        main.post {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        engine.attachService(null)
        super.onDestroy()
    }

    private companion object {
        const val TAG = "EyeBlinkService"
    }
}
