package com.eyeblink.app.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.eyeblink.app.animation.BlinkAnimator
import com.eyeblink.app.context.ContextEngine
import com.eyeblink.app.playback.PlaybackController
import com.eyeblink.app.ui.BlinkOverlay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BlinkOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private var prefsJob: Job? = null

    /** Live config, fed by PreferencesStore so overlay timing follows the settings screen. */
    private val configState = mutableStateOf(BlinkConfig())
    private val config get() = configState.value

    private val animator = BlinkAnimator { config }

    private val controller by lazy {
        BlinkController(
            scope = lifecycleScope,
            configProvider = { config },
            animator = animator,
            playback = PlaybackController(this),
            contextEngine = ContextEngine(this),
            onBlink = {},
        )
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundCompat()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        observePreferences()

        if (!showOverlay()) {
            // Overlay permission was revoked while running -> tear the service down cleanly.
            Log.w(TAG, "overlay not permitted; stopping service")
            stopSelf()
            return
        }
        controller.start()
    }

    private fun observePreferences() {
        val store = PreferencesStore(applicationContext)
        prefsJob = lifecycleScope.launch {
            store.config.collectLatest { configState.value = it }
        }
    }

    /** @return true if the overlay view was attached. */
    private fun showOverlay(): Boolean {
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@BlinkOverlayService)
            setViewTreeSavedStateRegistryOwner(this@BlinkOverlayService)
            setContent { BlinkOverlay(animator, config) }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }

        return try {
            windowManager.addView(view, params)
            overlayView = view
            true
        } catch (e: Exception) {
            // BadTokenException / permission revoked / service not allowed to draw.
            Log.w(TAG, "addView failed: ${e.message}")
            false
        }
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val channelId = CHANNEL_ID
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "EyeBlink", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return Notification.Builder(this, channelId)
            .setContentTitle("EyeBlink active")
            .setContentText("Brief visual rest breaks on a timer.")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        controller.stop()
        prefsJob?.cancel()
        overlayView?.let { view ->
            runCatching { windowManager.removeView(view) }
                .onFailure { Log.w(TAG, "removeView failed: ${it.message}") }
        }
        overlayView = null
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val TAG = "BlinkOverlayService"
        const val CHANNEL_ID = "eyeblink"
        const val NOTIFICATION_ID = 1
    }
}