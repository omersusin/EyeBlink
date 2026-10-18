package com.eyeblink.app.ui.i18n

/**
 * The reasons the power layer gives for not working, and the name of the overlay in use, are plain English made far from the
 * screen (in :core and in the controllers), and they end up inside sentences of the screen's own language. On a Turkish screen the
 * known ones are put into Turkish here; one that is not known stays as it was, which is better than a wrong guess.
 */
fun Strings.reason(text: String?): String? {
    if (text == null) return null
    return when {
        text == "this build has no Shizuku support" -> t(text, "bu sürümde Shizuku desteği yok")
        text == "Shizuku is not running" -> t(text, "Shizuku çalışmıyor")
        text == "permission not granted" -> t(text, "izin verilmedi")
        text == "not checked yet" -> t(text, "henüz denetlenmedi")
        text == "timed out waiting for a grant" -> t(text, "izin beklenirken zaman aşımına uğradı")
        text == "no shell" -> t(text, "kabuk yok")
        text == "root" -> t(text, "root")
        text == "shell (adb-level)" -> t(text, "kabuk (adb düzeyi)")
        text == "Accessibility overlay" -> t(text, "Erişilebilirlik bindirmesi")
        text == "Application overlay" -> t(text, "Uygulama bindirmesi")
        text.startsWith("unrecognised identity: ") -> t(text, "tanınmayan kimlik: " + text.removePrefix("unrecognised identity: "))
        text.startsWith("running as uid ") ->
            t(text, text.replace("running as uid", "şu uid ile çalışıyor:").replace(", expected root (0) or shell (2000)", ", beklenen: root (0) ya da kabuk (2000)"))
        else -> text
    }
}
