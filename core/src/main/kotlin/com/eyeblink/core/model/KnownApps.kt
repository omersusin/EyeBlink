package com.eyeblink.core.model

/**
 * Apps EyeBlink knows by package name, used to pre-fill the app picker and to pick a
 * sensible category. Being listed here is NOT a claim that playback control works for
 * the app - that is decided only by an on-device compatibility test.
 */
object KnownApps {

    data class Entry(val packageName: String, val label: String, val category: AppCategory)

    val catalog: List<Entry> = listOf(
        Entry("com.instagram.android", "Instagram", AppCategory.SOCIAL),
        Entry("com.zhiliaoapp.musically", "TikTok", AppCategory.VIDEO),
        Entry("com.ss.android.ugc.trill", "TikTok", AppCategory.VIDEO),
        Entry("com.google.android.youtube", "YouTube", AppCategory.VIDEO),
        Entry("app.revanced.android.youtube", "YouTube ReVanced", AppCategory.VIDEO),
        Entry("com.facebook.katana", "Facebook", AppCategory.SOCIAL),
        Entry("com.twitter.android", "X", AppCategory.SOCIAL),
        Entry("com.snapchat.android", "Snapchat", AppCategory.SOCIAL),
        Entry("com.reddit.frontpage", "Reddit", AppCategory.SOCIAL),
        Entry("com.pinterest", "Pinterest", AppCategory.SOCIAL),
        Entry("com.instagram.barcelona", "Threads", AppCategory.SOCIAL),
        Entry("com.netflix.mediaclient", "Netflix", AppCategory.VIDEO),
        Entry("tv.twitch.android.app", "Twitch", AppCategory.VIDEO),
        Entry("com.amazon.avod.thirdpartyclient", "Prime Video", AppCategory.VIDEO),
        Entry("com.amazon.kindle", "Kindle", AppCategory.READING),
        Entry("com.google.android.apps.books", "Play Books", AppCategory.READING),
        Entry("com.medium.reader", "Medium", AppCategory.READING),
    )

    fun find(packageName: String): Entry? = catalog.firstOrNull { it.packageName == packageName }

    fun categoryOf(packageName: String): AppCategory = find(packageName)?.category ?: AppCategory.OTHER
}

/**
 * Packages EyeBlink must never draw over, even when the user selected them. Phone calls,
 * emergency, system UI (lock screen, biometric prompts), settings, camera capture,
 * wallet and credential/payment surfaces.
 *
 * Matching is by exact package or by a marker inside the package name so that OEM variants
 * (`com.sec.android.app.camera`, `com.samsung.android.incallui`) are covered too.
 */
object NeverInterrupt {

    private val exact = setOf(
        "com.android.systemui",
        "com.android.settings",
        "com.android.server.telecom",
        "com.android.phone",
        "com.google.android.gms",
        "com.google.android.apps.walletnfcrel",
        "com.google.android.apps.nbu.paisa.user",
        "com.android.emergency",
        "com.google.android.apps.safetyhub",
    )

    private val markers = listOf(
        ".dialer", ".incallui", ".telecom", ".emergency", ".camera", "authenticat", "biometric",
        "keyguard", ".wallet", "packageinstaller", "permissioncontroller",
    )

    fun matches(packageName: String?): Boolean {
        if (packageName == null) return false
        if (packageName in exact) return true
        val lower = packageName.lowercase()
        return markers.any { lower.contains(it) }
    }
}
