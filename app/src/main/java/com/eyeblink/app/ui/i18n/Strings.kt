package com.eyeblink.app.ui.i18n

import com.eyeblink.core.model.Language

/**
 * Every user-visible string, in English and Turkish, side by side.
 *
 * Strings are Kotlin rather than Android XML so the exact same text reaches the settings UI,
 * the overlay scenes, the notification and the desktop design lab. Each entry is a single
 * `t("English", "Türkçe")` call, so a string cannot exist in one language and be forgotten in
 * the other - the compiler will not let a call omit either.
 *
 * Screens add their own strings as extension properties next to the code that uses them.
 */
class Strings private constructor(val lang: Lang) {

    enum class Lang { EN, TR }

    fun t(en: String, tr: String): String = if (lang == Lang.TR) tr else en

    companion object {
        val EN = Strings(Lang.EN)
        val TR = Strings(Lang.TR)

        fun of(lang: Lang): Strings = if (lang == Lang.TR) TR else EN

        /** [systemTag] is the device locale as a BCP-47 tag such as `tr-TR`. */
        fun resolve(setting: Language, systemTag: String?): Strings = when (setting) {
            Language.EN -> EN
            Language.TR -> TR
            Language.SYSTEM -> if (systemTag?.lowercase()?.startsWith("tr") == true) TR else EN
        }
    }
}
