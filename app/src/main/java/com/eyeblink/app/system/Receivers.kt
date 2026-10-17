package com.eyeblink.app.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.eyeblink.app.engine.engine

/** Brings EyeBlink back after a reboot if - and only if - the user asked it to start with the phone. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) context.engine.resumeIfNeeded()
    }
}

/** The buttons on the ongoing notification. Not exported: only EyeBlink's own pending intents reach it. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val e = context.engine
        when (intent.action) {
            Notifier.ACTION_SNOOZE -> e.snooze(15)
            Notifier.ACTION_SUSPEND -> e.setSuspended(true)
            Notifier.ACTION_RESUME -> e.setSuspended(false)
            Notifier.ACTION_STOP -> e.setRunning(false)
        }
    }
}

/** Where the optional LSPosed module's answers arrive, through a pending intent only EyeBlink created. */
class LsposedReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.engine.lsposed.onReply(intent.extras)
    }
}
