package com.eyeblink.app.platform

import android.content.Context

/**
 * `getSystemService(Class)` is annotated nullable in some SDKs and not in others, which makes the
 * same call an error with one compile SDK and fine with another. Every system service EyeBlink
 * uses exists on every supported phone, so asking for one that is missing is a bug worth a clear
 * message rather than a null.
 */
inline fun <reified T : Any> Context.systemService(): T =
    checkNotNull(getSystemService(T::class.java)) { "${T::class.java.simpleName} is not available" }
