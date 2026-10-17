package com.eyeblink.core.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** The one [Json] configuration used for everything EyeBlink stores locally. */
object CoreJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true      // a newer build's file must still load in an older one
        coerceInputValues = true      // unknown enum value or null for a defaulted field -> default
        encodeDefaults = true
        explicitNulls = false
    }
}

/**
 * Settings <-> JSON text. Decoding never throws: a missing, empty or corrupt blob yields
 * the defaults, and every successfully decoded value is passed through `validated()` so a
 * hand-edited file cannot push the animation or scheduler out of range.
 */
object SettingsCodec {

    fun encode(settings: EyeBlinkSettings): String =
        CoreJson.instance.encodeToString(settings.validated())

    fun decode(text: String?): EyeBlinkSettings = decodeOrNull(text) ?: EyeBlinkSettings()

    fun decodeOrNull(text: String?): EyeBlinkSettings? {
        if (text.isNullOrBlank()) return null
        return runCatching { CoreJson.instance.decodeFromString<EyeBlinkSettings>(text).validated() }.getOrNull()
    }
}
