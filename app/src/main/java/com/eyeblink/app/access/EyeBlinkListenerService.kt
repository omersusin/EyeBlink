package com.eyeblink.app.access

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.eyeblink.app.engine.engine
import com.eyeblink.app.platform.Signals

/**
 * Notification access is how a normal app is allowed to see other apps' media sessions, which is
 * what makes pausing and resuming a video possible without root. EyeBlink uses that and one
 * other fact: whether turn-by-turn navigation is running, so it never covers a map.
 *
 * It reads each notification's category and "ongoing" flag. It never reads titles, text or any
 * other extra.
 */
class EyeBlinkListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        Signals.listenerConnected = true
        refreshNavigation()
        engine.onListenerChanged(true)
    }

    override fun onListenerDisconnected() {
        Signals.listenerConnected = false
        Signals.navigationActive = false
        engine.onListenerChanged(false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refreshNavigation()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refreshNavigation()

    private fun refreshNavigation() {
        Signals.navigationActive = try {
            activeNotifications.orEmpty().any { it.isOngoing && it.notification.category == Notification.CATEGORY_NAVIGATION }
        } catch (e: Exception) {
            false
        }
    }
}
