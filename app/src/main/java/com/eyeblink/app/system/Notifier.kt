package com.eyeblink.app.system

import com.eyeblink.app.platform.systemService
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import com.eyeblink.app.R
import com.eyeblink.app.engine.CompatOutcomeSummary
import com.eyeblink.app.ui.i18n.Strings

/** What the ongoing notification has to say about the engine right now. */
data class RunningNote(
    val running: Boolean,
    val suspended: Boolean,
    val snoozed: Boolean,
    val cameraOn: Boolean,
    val nightOnly: Boolean = false,
)

/**
 * Every notification EyeBlink posts, in the language the user chose. The ongoing one is also the
 * visible "EyeBlink is active" marker, says when the camera is on, and carries the three controls
 * that matter: snooze, suspend and stop.
 */
class Notifier(private val app: Application) {

    private val nm = app.systemService<NotificationManager>()

    init {
        // Channel names are in the system language: they are shown by Android's own settings.
        nm.createNotificationChannel(NotificationChannel(CH_RUNNING, app.getString(R.string.channel_running), NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(CH_TEST, app.getString(R.string.channel_test), NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun running(s: Strings, n: RunningNote): Notification {
        val title = "EyeBlink"
        val text = when {
            n.suspended -> s.t("Suspended. Nothing is being drawn and the camera is off.", "Askıda. Hiçbir şey çizilmiyor, kamera kapalı.")
            n.nightOnly -> s.t("Night mode is on.", "Gece modu açık.")
            n.snoozed -> s.t("Snoozed for a while.", "Bir süre ertelendi.")
            n.cameraOn -> s.t("Camera on: counting your blinks. Nothing is saved.", "Kamera açık: göz kırpmalarını sayıyor. Hiçbir şey kaydedilmiyor.")
            else -> s.t("Looking after your eyes.", "Gözlerine göz kulak oluyor.")
        }
        val builder = Notification.Builder(app, CH_RUNNING)
            .setSmallIcon(R.drawable.ic_stat_eye)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        app.packageManager.getLaunchIntentForPackage(app.packageName)?.let {
            builder.setContentIntent(PendingIntent.getActivity(app, 0, it, FLAGS))
        }
        if (!n.nightOnly) {
            builder.addAction(action(s.t("Snooze 15 min", "15 dk ertele"), ACTION_SNOOZE, 1))
            builder.addAction(
                if (n.suspended) action(s.t("Resume", "Sürdür"), ACTION_RESUME, 2)
                else action(s.t("Suspend", "Askıya al"), ACTION_SUSPEND, 2),
            )
            builder.addAction(action(s.t("Stop", "Durdur"), ACTION_STOP, 3))
        }
        return builder.build()
    }

    fun update(s: Strings, n: RunningNote) {
        runCatching { nm.notify(ID_RUNNING, running(s, n)) }
    }

    /** While a compatibility test waits for the user to start a video in the app. */
    fun testGuide(s: Strings, appLabel: String) {
        val n = Notification.Builder(app, CH_TEST)
            .setSmallIcon(R.drawable.ic_stat_eye)
            .setContentTitle(s.t("Testing $appLabel", "$appLabel test ediliyor"))
            .setContentText(s.t("Play a video in $appLabel and leave it playing. EyeBlink will pause and resume it once or twice.", "$appLabel içinde bir video oynat ve çalmasına izin ver. EyeBlink onu bir iki kez duraklatıp sürdürecek."))
            .setOngoing(true)
            .setTimeoutAfter(90_000L)
            .build()
        runCatching { nm.notify(ID_TEST, n) }
    }

    fun testDone(s: Strings, appLabel: String, outcome: CompatOutcomeSummary) {
        val text = when (outcome) {
            CompatOutcomeSummary.VERIFIED -> s.t("EyeBlink can pause and resume $appLabel safely.", "EyeBlink $appLabel uygulamasını güvenle duraklatıp sürdürebiliyor.")
            CompatOutcomeSummary.FAILING -> s.t("Nothing could be confirmed. EyeBlink will blink in $appLabel without touching the video.", "Hiçbir şey doğrulanamadı. EyeBlink $appLabel içinde videoya dokunmadan göz kırpacak.")
            CompatOutcomeSummary.LEFT_PAUSED -> s.t("The video may have stayed paused. Press play. EyeBlink will not touch $appLabel's video.", "Video duraklamış kalmış olabilir. Oynat'a bas. EyeBlink $appLabel videosuna dokunmayacak.")
            CompatOutcomeSummary.NO_PLAYBACK -> s.t("No video was playing in $appLabel, so nothing was tested.", "$appLabel içinde video oynamıyordu, bu yüzden bir şey test edilmedi.")
            CompatOutcomeSummary.NO_STRATEGY -> s.t("No way of controlling playback is available yet. See Power modes and Apps.", "Henüz oynatmayı denetleyecek bir yol yok. Güç modları ve Uygulamalar'a bak.")
        }
        val n = Notification.Builder(app, CH_TEST)
            .setSmallIcon(R.drawable.ic_stat_eye)
            .setContentTitle(s.t("$appLabel test finished", "$appLabel testi bitti"))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(ID_TEST, n) }
    }

    fun clearTest() {
        runCatching { nm.cancel(ID_TEST) }
    }

    private fun action(label: String, action: String, code: Int): Notification.Action {
        val pi = PendingIntent.getBroadcast(app, code, Intent(action).setPackage(app.packageName), FLAGS)
        return Notification.Action.Builder(Icon.createWithResource(app, R.drawable.ic_stat_eye), label, pi).build()
    }

    companion object {
        const val CH_RUNNING = "running"
        const val CH_TEST = "test"
        const val ID_RUNNING = 1
        const val ID_TEST = 2

        const val ACTION_SNOOZE = "com.eyeblink.action.SNOOZE"
        const val ACTION_SUSPEND = "com.eyeblink.action.SUSPEND"
        const val ACTION_RESUME = "com.eyeblink.action.RESUME"
        const val ACTION_STOP = "com.eyeblink.action.STOP"

        private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    }
}
