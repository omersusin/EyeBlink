package com.eyeblink.app.engine

import com.eyeblink.app.platform.systemService
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * A second opinion about which app is in front, from Usage Access, for when the accessibility
 * service is not connected. It reads only the package of the most recent "app came to the
 * foreground" event; usage history is not kept.
 */
class UsageForeground(ctx: Context) {

    private val usage = ctx.systemService<UsageStatsManager>()

    fun poll(): String? {
        val end = System.currentTimeMillis()
        val events = try { usage.queryEvents(end - WINDOW_MS, end) } catch (e: Exception) { return null } ?: return null
        val e = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            @Suppress("DEPRECATION")
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) last = e.packageName
        }
        return last
    }

    private companion object {
        const val WINDOW_MS = 30_000L
    }
}
