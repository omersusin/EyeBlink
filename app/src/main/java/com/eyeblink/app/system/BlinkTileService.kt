package com.eyeblink.app.system

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.eyeblink.app.engine.engine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * The instant suspend from anywhere: one tap and nothing is drawn and the camera is off; another and
 * EyeBlink carries on. When it is not running at all the tap switches it on - or opens the app if it
 * still needs a permission to draw.
 */
class BlinkTileService : TileService() {

    private var scope: CoroutineScope? = null
    private var job: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = s
        job = s.launch { engine.ui.collect { sync() } }
        sync()
    }

    override fun onStopListening() {
        job?.cancel()
        scope?.cancel()
        scope = null
        job = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val e = engine
        if (!e.ui.value.runtime.running && !e.windows.available()) {
            openApp()
            return
        }
        e.toggleSuspended()
    }

    private fun sync() {
        val tile = qsTile ?: return
        val ui = engine.ui.value
        val s = engine.strings()
        val on = ui.runtime.running && ui.runtime.idle != com.eyeblink.core.engine.Reason.SUSPENDED
        tile.label = "EyeBlink"
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = when {
                !ui.runtime.running -> s.t("Off", "Kapalı")
                ui.runtime.idle == com.eyeblink.core.engine.Reason.SUSPENDED -> s.t("Suspended", "Askıda")
                else -> s.t("On", "Açık")
            }
        }
        tile.updateTile()
    }

    private fun openApp() {
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION") startActivityAndCollapse(launch)
        }
    }
}
