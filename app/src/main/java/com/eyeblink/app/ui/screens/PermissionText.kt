package com.eyeblink.app.ui.screens

import com.eyeblink.app.ui.i18n.Strings
import com.eyeblink.app.ui.kit.Icon
import com.eyeblink.app.ui.state.PermissionKind

/** Plain-language names and reasons for every permission, shared by Home, Onboarding and Diagnostics. */
internal fun permissionTitle(k: PermissionKind, s: Strings): String = when (k) {
    PermissionKind.OVERLAY -> s.t("Display over other apps", "Diğer uygulamaların üzerinde göster")
    PermissionKind.ACCESSIBILITY -> s.t("Accessibility service", "Erişilebilirlik hizmeti")
    PermissionKind.NOTIFICATION_LISTENER -> s.t("Media session access", "Medya oturumu erişimi")
    PermissionKind.USAGE_ACCESS -> s.t("Usage access", "Kullanım erişimi")
    PermissionKind.CAMERA -> s.t("Camera", "Kamera")
    PermissionKind.NOTIFICATIONS -> s.t("Notifications", "Bildirimler")
    PermissionKind.WRITE_SETTINGS -> s.t("Change system settings", "Sistem ayarlarını değiştir")
    PermissionKind.BATTERY_OPTIMIZATION -> s.t("Unrestricted battery", "Kısıtsız pil")
}

internal fun permissionWhy(k: PermissionKind, s: Strings): String = when (k) {
    PermissionKind.OVERLAY -> s.t("Lets EyeBlink draw the blink over your app.", "EyeBlink'in göz kırpmayı uygulamanın üstüne çizmesini sağlar.")
    PermissionKind.ACCESSIBILITY -> s.t("Draws over everything, status bar included, and sees which app is in front. It never reads what is on screen.", "Durum çubuğu dahil her şeyin üstüne çizer ve hangi uygulamanın önde olduğunu görür. Ekrandakini asla okumaz.")
    PermissionKind.NOTIFICATION_LISTENER -> s.t("Lets EyeBlink see media sessions so it can pause a video and give it back. It does not read your notifications.", "Bir videoyu duraklatıp geri verebilmesi için medya oturumlarını görmesini sağlar. Bildirimlerini okumaz.")
    PermissionKind.USAGE_ACCESS -> s.t("A fallback way to know which app is open.", "Hangi uygulamanın açık olduğunu bilmenin yedek yolu.")
    PermissionKind.CAMERA -> s.t("Only for camera-assisted mode: it counts your real blinks. Frames are never saved or sent anywhere.", "Yalnızca kamera destekli mod için: gerçek göz kırpmalarını sayar. Kareler asla kaydedilmez veya bir yere gönderilmez.")
    PermissionKind.NOTIFICATIONS -> s.t("A quiet notification with a stop button while EyeBlink runs.", "EyeBlink çalışırken durdurma düğmeli sessiz bir bildirim.")
    PermissionKind.WRITE_SETTINGS -> s.t("Only needed if you let EyeBlink dim the screen at night.", "Yalnızca geceleri ekranı kısmasına izin verirsen gerekir.")
    PermissionKind.BATTERY_OPTIMIZATION -> s.t("Keeps Android from stopping EyeBlink in the background.", "Android'in EyeBlink'i arka planda durdurmasını engeller.")
}

internal fun permissionIcon(k: PermissionKind): Icon = when (k) {
    PermissionKind.OVERLAY -> Icon.Apps
    PermissionKind.ACCESSIBILITY -> Icon.Eye
    PermissionKind.NOTIFICATION_LISTENER -> Icon.Play
    PermissionKind.USAGE_ACCESS -> Icon.Chart
    PermissionKind.CAMERA -> Icon.Camera
    PermissionKind.NOTIFICATIONS -> Icon.Info
    PermissionKind.WRITE_SETTINGS -> Icon.Sun
    PermissionKind.BATTERY_OPTIMIZATION -> Icon.Battery
}
