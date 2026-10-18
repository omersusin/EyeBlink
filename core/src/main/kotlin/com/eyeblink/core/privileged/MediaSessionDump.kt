package com.eyeblink.core.privileged

import com.eyeblink.core.playback.PlayState

/** One media session as listed by `dumpsys media_session`. */
data class DumpedSession(
    val packageName: String,
    val tag: String?,
    val active: Boolean,
    val stateCode: Int?,
    val positionMs: Long?,
    /** The description line of the metadata (title, artist...), used as a content fingerprint. */
    val description: String?,
    val durationMs: Long?,
) {
    val state: PlayState get() = PlaybackStateCodes.toPlayState(stateCode)

    /** Stable text identifying what is playing, or null when the dump did not say. */
    val contentKey: String? get() = listOfNotNull(description, durationMs?.toString()).takeIf { it.isNotEmpty() }?.joinToString("|")
}

data class MediaDump(
    /** Package of the session that currently receives media keys, if the dump names one. */
    val mediaButtonPackage: String?,
    /** Priority order, highest first. */
    val sessions: List<DumpedSession>,
) {
    fun forPackage(pkg: String): DumpedSession? {
        val candidates = sessions.filter { it.packageName == pkg }
        // Prefer one that is actually playing, then the highest priority one.
        return candidates.firstOrNull { it.state == PlayState.PLAYING } ?: candidates.firstOrNull()
    }
}

object PlaybackStateCodes {
    // android.media.session.PlaybackState constants.
    const val NONE = 0
    const val STOPPED = 1
    const val PAUSED = 2
    const val PLAYING = 3
    const val FAST_FORWARDING = 4
    const val REWINDING = 5
    const val BUFFERING = 6
    const val ERROR = 7
    const val CONNECTING = 8
    const val SKIPPING_TO_PREVIOUS = 9
    const val SKIPPING_TO_NEXT = 10
    const val SKIPPING_TO_QUEUE_ITEM = 11

    fun toPlayState(code: Int?): PlayState = when (code) {
        null -> PlayState.UNKNOWN
        NONE -> PlayState.NONE
        STOPPED, ERROR -> PlayState.STOPPED
        PAUSED -> PlayState.PAUSED
        PLAYING, FAST_FORWARDING, REWINDING -> PlayState.PLAYING
        BUFFERING, CONNECTING, SKIPPING_TO_PREVIOUS, SKIPPING_TO_NEXT, SKIPPING_TO_QUEUE_ITEM -> PlayState.BUFFERING
        else -> PlayState.UNKNOWN
    }
}

/**
 * Parses `dumpsys media_session`.
 *
 * The format is not a public API and drifts between Android versions, so the parser is
 * tolerant by design: it anchors on the `package=` line of each record, accepts the playback
 * state as `state=3` or `state=PLAYING(3)`, and returns whatever it could read instead of
 * failing. Missing fields stay null and the layers above treat null as "unknown" - which they
 * never act on.
 */
object MediaSessionDump {

    private val PACKAGE_LINE = Regex("""^\s*package=([A-Za-z0-9_.]+)\s*$""")
    private val ACTIVE_LINE = Regex("""^\s*active=(true|false)\s*$""")

    // "MediaSessionRecord 1a2b3c com.pkg/tag (userId=0)": every record is introduced by a
    // line that ends in "(userId=N)".
    private val HEADER_LINE = Regex("""^\s*(\S.*?)\s+\(userId=\d+\)\s*$""")
    private val STATE_LINE = Regex("""PlaybackState\s*\{[^}]*?\bstate=(?:[A-Z_]+\()?(\d+)\)?""")
    private val POSITION = Regex("""\bposition=(-?\d+)""")
    private val DESCRIPTION = Regex("""\bdescription=(.*)$""")
    private val DURATION = Regex("""\bandroid\.media\.metadata\.DURATION[=:]\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val BUTTON_LINE = Regex("""(?i)media\s*button\s*session""")
    private val PACKAGE_SLASH = Regex("""([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+)/""")

    private class Builder(var tag: String? = null) {
        var pkg: String? = null
        var active = false
        var state: Int? = null
        var position: Long? = null
        var description: String? = null
        var duration: Long? = null

        fun build(): DumpedSession? = pkg?.let { DumpedSession(it, tag, active, state, position, description, duration) }
    }

    fun parse(text: String): MediaDump {
        var buttonPackage: String? = null
        val sessions = mutableListOf<DumpedSession>()
        var b: Builder? = null

        fun flush() {
            b?.build()?.let(sessions::add)
            b = null
        }

        for (line in text.lineSequence()) {
            if (BUTTON_LINE.containsMatchIn(line)) {
                // The session that receives media keys. Named as "<pkg>/<tag>" after a record hash.
                PACKAGE_SLASH.find(line)?.let { buttonPackage = it.groupValues[1] }
                continue
            }

            val header = HEADER_LINE.find(line)
            if (header != null) {
                flush()
                b = Builder(tag = header.groupValues[1])
                continue
            }

            val pm = PACKAGE_LINE.find(line)
            if (pm != null) {
                if (b == null || b?.pkg != null) { flush(); b = Builder() }
                b?.pkg = pm.groupValues[1]
                continue
            }

            val cur = b ?: continue
            ACTIVE_LINE.find(line)?.let { cur.active = it.groupValues[1] == "true" }
            STATE_LINE.find(line)?.let { m ->
                cur.state = m.groupValues[1].toIntOrNull()
                POSITION.find(line)?.let { cur.position = it.groupValues[1].toLongOrNull() }
            }
            if (line.trimStart().startsWith("metadata:")) {
                DESCRIPTION.find(line)?.let { m ->
                    cur.description = m.groupValues[1].trim().takeIf { d -> d.isNotEmpty() && d != "null" }
                }
            }
            DURATION.find(line)?.let { cur.duration = it.groupValues[1].toLongOrNull() }
        }
        flush()

        return MediaDump(buttonPackage, sessions)
    }

    private val OUTLINE_PREFIXES = listOf(
        "Record for full_user=", "Sessions Stack", "ownerPid=", "package=", "active=", "flags=", "controllers:", "state=",
        "audioAttrs=", "volumeType=", "metadata:", "queueTitle=",
    )
    private val SESSION_ALIAS = Regex("""(app\d+\.pkg)/(\S+)""")

    /**
     * A copy of a dump that can go into a bug report: its shape, the playback states and the numbers stay; package
     * names and session tags become placeholders, and titles, artists and intents - everything that says what is
     * being watched or listened to - are dropped. It exists so that a dump format this parser does not understand
     * can be fixed without anybody sending their media sessions around.
     */
    fun outline(text: String, maxLines: Int = 60): String {
        val dump = parse(text)
        val aliases = LinkedHashMap<String, String>()
        val packages = (dump.sessions.map { it.packageName } + listOfNotNull(dump.mediaButtonPackage))
            .distinct().sortedByDescending { it.length }
        // Placeholders that look like package names, so that the outline reads back with the same parser.
        packages.forEach { aliases.getOrPut(it) { "app${aliases.size + 1}.pkg" } }

        val out = ArrayList<String>()
        for (raw in text.lineSequence()) {
            val line = raw.trimEnd()
            val t = line.trimStart()
            val keep = BUTTON_LINE.containsMatchIn(line) || HEADER_LINE.containsMatchIn(line) || OUTLINE_PREFIXES.any { t.startsWith(it) }
            if (!keep) continue

            var s = line
            if (t.startsWith("metadata:")) s = s.substringBefore("description=") + "description=…"
            if (t.startsWith("queueTitle=")) s = "${s.substring(0, s.indexOf("queueTitle="))}queueTitle=…, size=${s.substringAfterLast("size=", "?")}"
            for (p in packages) s = s.replace(p, aliases.getValue(p))
            // "app1/SomeTag" (Android 15: "app1/SomeTag/uniqueId"): the tag may name the app or the content, and is
            // printed again in front of the line, so every part of it goes wherever it appears.
            SESSION_ALIAS.find(s)?.let { m ->
                for (part in m.groupValues[2].split('/').filter { it.isNotEmpty() }) {
                    s = s.replace(Regex("(?<![A-Za-z0-9_])" + Regex.escape(part) + "(?![A-Za-z0-9_])"), "tag")
                }
            }
            if (t.startsWith("audioAttrs=")) s = s.replace(Regex("""tags=\S*"""), "tags=…")
            out += s
            if (out.size >= maxLines) break
        }
        return out.joinToString("\n")
    }
}

/** Reads the foreground package out of `dumpsys window` / `dumpsys activity` text. */
object FocusDump {

    private val WINDOW = Regex("""mCurrentFocus=Window\{[0-9a-f]+ u\d+ ([A-Za-z0-9_.]+)/""")
    private val ACTIVITY = Regex("""(?:topResumedActivity|mResumedActivity|mFocusedApp|ResumedActivity)[^{]*\{[0-9a-f]+ u\d+ ([A-Za-z0-9_.]+)/""")

    fun foregroundPackage(text: String): String? {
        WINDOW.find(text)?.let { return it.groupValues[1] }
        ACTIVITY.find(text)?.let { return it.groupValues[1] }
        return null
    }
}
