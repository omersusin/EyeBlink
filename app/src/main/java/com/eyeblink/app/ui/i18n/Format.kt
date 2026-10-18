package com.eyeblink.app.ui.i18n

import com.eyeblink.core.engine.Reason

/** `m:ss` for countdowns. */
fun fmtClock(ms: Long): String {
    val total = (ms.coerceAtLeast(0) + 500) / 1000
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

/** "45 s", "2 min", "2 min 30 s", "1 h 20 min". */
fun fmtSeconds(sec: Int, s: Strings): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val r = sec % 60
    val hh = s.t("h", "sa")
    val mm = s.t("min", "dk")
    val ss = s.t("s", "sn")
    return when {
        h > 0 -> if (m > 0) "$h $hh $m $mm" else "$h $hh"
        m > 0 -> if (r > 0) "$m $mm $r $ss" else "$m $mm"
        else -> "$r $ss"
    }
}

fun fmtMinutes(min: Int, s: Strings): String = fmtSeconds(min * 60, s)

/** Why EyeBlink is waiting, in a few honest words. */
fun Reason.label(s: Strings): String = when (this) {
    Reason.DISABLED -> s.t("Switched off", "Kapalı")
    Reason.SUSPENDED -> s.t("Suspended", "Askıda")
    Reason.SNOOZED -> s.t("Snoozed", "Ertelendi")
    Reason.SCREEN_OFF -> s.t("Screen is off", "Ekran kapalı")
    Reason.DEVICE_LOCKED -> s.t("Device is locked", "Cihaz kilitli")
    Reason.UNKNOWN_APP -> s.t("Waiting to see which app is open", "Hangi uygulamanın açık olduğu bekleniyor")
    Reason.NOT_SELECTED_APP -> s.t("Not one of your selected apps", "Seçtiğin uygulamalardan biri değil")
    Reason.NEVER_LIST -> s.t("A screen EyeBlink never covers", "EyeBlink'in asla örtmediği bir ekran")
    Reason.QUIET_HOURS -> s.t("Quiet hours", "Sessiz saatler")
    Reason.BATTERY_SAVER -> s.t("Battery saver", "Pil tasarrufu")
    Reason.THERMAL -> s.t("Phone is hot", "Telefon ısındı")
    Reason.IN_CALL -> s.t("On a call", "Aramada")
    Reason.CAMERA_IN_USE -> s.t("Camera in use elsewhere", "Kamera başka yerde kullanılıyor")
    Reason.NAVIGATION -> s.t("Navigation is active", "Navigasyon açık")
    Reason.SENSITIVE_SCREEN -> s.t("Sensitive screen", "Hassas ekran")
    Reason.TEXT_INPUT -> s.t("You are typing", "Yazıyorsun")
    Reason.COOLDOWN -> s.t("Just had a break", "Az önce mola verildi")
    Reason.NO_PAUSE_SUPPORT -> s.t("Can't pause this app", "Bu uygulama duraklatılamıyor")
    Reason.TOUCHING -> s.t("Finger on screen", "Parmak ekranda")
    Reason.RECENT_TOUCH -> s.t("Waiting for a still moment", "Sakin bir an bekleniyor")
    Reason.VIDEO_TRANSITION -> s.t("Video is changing", "Video değişiyor")
    Reason.PREFER_TRANSITION -> s.t("Waiting for the next swipe", "Sonraki kaydırma bekleniyor")
}

/** "just now", "5 min ago", "2 h ago", "3 d ago". */
fun fmtAgo(ms: Long, s: Strings): String {
    val m = ms / 60_000
    return when {
        ms < 60_000 -> s.t("just now", "az önce")
        m < 60 -> s.t("$m min ago", "$m dk önce")
        m < 60 * 24 -> s.t("${m / 60} h ago", "${m / 60} sa önce")
        else -> s.t("${m / (60 * 24)} d ago", "${m / (60 * 24)} gün önce")
    }
}

/** A decimal with the language's own separator: "13.4" in English, "13,4" in Turkish. */
fun fmtDecimal(value: Float, s: Strings, digits: Int = 1): String {
    var scale = 1
    repeat(digits) { scale *= 10 }
    val scaled = kotlin.math.round(kotlin.math.abs(value) * scale).toInt()
    val sign = if (value < 0f && scaled != 0) "-" else ""
    if (digits == 0) return "$sign$scaled"
    val frac = (scaled % scale).toString().padStart(digits, '0')
    return "$sign${scaled / scale}${s.t(".", ",")}$frac"
}

/** A span of active time as the single most telling number: minutes up to ~2 h, then hours. */
fun fmtActive(ms: Long, s: Strings): Pair<String, String> {
    val min = (ms / 60_000L).toInt()
    return if (min < 120) min.toString() to s.t("min", "dk") else fmtDecimal(min / 60f, s) to s.t("hours", "saat")
}
