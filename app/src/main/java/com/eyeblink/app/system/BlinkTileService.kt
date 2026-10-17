package com.eyeblink.app.system

import android.content.Intent
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.eyeblink.app.core.BlinkOverlayService
import com.eyeblink.app.core.isServiceRunning
import com.eyeblink.app.core.startIfPermitted

/**
 * Quick Settings tile for one-tap suspend/resume.
 *
 * This is the spec's "immediate suspension" escape hatch: the user can stop the
 * overlay without opening the app. If the overlay permission was revoked the
 * tap starts the settings screen instead of silently doing nothing.
 */
class BlinkTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        syncTile()
    }

    override fun onClick() {
        super.onClick()
        val running = isServiceRunning(this, BlinkOverlayService::class.java)
        if (running) {
            stopService(Intent(this, BlinkOverlayService::class.java))
        } else {
            val permitted = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M ||
                Settings.canDrawOverlays(this)
            if (!startIfPermitted(this, BlinkOverlayService::class.java, permitted)) {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                        .setData(android.net.Uri.parse("package:$packageName"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            }
        }
        syncTile()
    }

    private fun syncTile() {
        val tile = qsTile ?: return
        tile.label = LABEL
        tile.state = if (isServiceRunning(this, BlinkOverlayService::class.java)) {
            Tile.STATE_ACTIVE
        } else {
            Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }

    private companion object {
        const val LABEL = "EyeBlink"
    }
}