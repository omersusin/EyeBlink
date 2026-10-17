package com.eyeblink.core.rest

import com.eyeblink.core.model.ConfirmMode
import com.eyeblink.core.model.RestStyle

enum class RestPhase { SETTLING, RESTING, AWAITING_CONFIRM, DONE }

enum class RestOutcome { COMPLETED, SKIPPED, CANCELLED }

enum class ConfirmedBy { AUTO, TAP, BLINK, TIMEOUT }

data class RestRequest(
    val style: RestStyle,
    val durationSec: Int,
    val confirm: ConfirmMode,
    /** The camera is tracking reliably right now, so a real blink can confirm. */
    val blinkConfirmAvailable: Boolean,
    val reducedMotion: Boolean = false,
) {
    /** A blink confirmation without a working camera quietly becomes a tap. */
    val effectiveConfirm: ConfirmMode
        get() = if (confirm == ConfirmMode.BLINK && !blinkConfirmAvailable) ConfirmMode.TAP else confirm
}

/** What the rest screen should show at one instant. */
data class RestView(
    val phase: RestPhase,
    /** 0..1 across the resting period. */
    val progress: Float,
    val remainingMs: Long,
    val awaiting: ConfirmMode?,
    val outcome: RestOutcome?,
    val confirmedBy: ConfirmedBy?,
)

/**
 * A long break (Blackout or Distance Focus) as a pure state machine over time.
 *
 * Phases: a short SETTLING fade-in, the RESTING period itself, then - only if the user
 * asked for confirmation - AWAITING_CONFIRM. A single tap or a single real blink confirms.
 * Nothing ever asks the user to keep their eyes open or to blink repeatedly: confirmation
 * has a timeout, a tap is always accepted as an alternative to a blink, and skipping is
 * always possible.
 */
class RestSession(val request: RestRequest, private val startedMs: Long) {

    private var endedAtMs: Long? = null
    private var outcome: RestOutcome? = null
    private var confirmedBy: ConfirmedBy? = null

    private val restStart = startedMs + SETTLE_MS
    private val restEnd = restStart + request.durationSec.coerceAtLeast(1) * 1_000L

    val isFinished: Boolean get() = outcome != null

    fun view(nowMs: Long): RestView {
        resolve(nowMs)
        val done = outcome
        if (done != null) {
            return RestView(RestPhase.DONE, if (nowMs >= restEnd) 1f else progress(nowMs), 0L, null, done, confirmedBy)
        }
        return when {
            nowMs < restStart -> RestView(RestPhase.SETTLING, 0f, restEnd - restStart, null, null, null)
            nowMs < restEnd -> RestView(RestPhase.RESTING, progress(nowMs), restEnd - nowMs, null, null, null)
            else -> RestView(RestPhase.AWAITING_CONFIRM, 1f, 0L, request.effectiveConfirm, null, null)
        }
    }

    /** A tap on the confirm affordance. Returns true if it ended the rest. */
    fun onTap(nowMs: Long): Boolean = confirm(nowMs, ConfirmedBy.TAP, accepts = { it != ConfirmMode.AUTO })

    /** A real blink seen by the camera. Counts only when blink confirmation was asked for. */
    fun onBlink(nowMs: Long): Boolean = confirm(nowMs, ConfirmedBy.BLINK, accepts = { it == ConfirmMode.BLINK })

    fun skip(nowMs: Long) {
        resolve(nowMs)
        finish(nowMs, RestOutcome.SKIPPED, null)
    }

    fun cancel(nowMs: Long) {
        resolve(nowMs)
        finish(nowMs, RestOutcome.CANCELLED, null)
    }

    private fun confirm(nowMs: Long, by: ConfirmedBy, accepts: (ConfirmMode) -> Boolean): Boolean {
        resolve(nowMs)
        if (outcome != null || nowMs < restEnd) return false
        if (!accepts(request.effectiveConfirm)) {
            // A tap is the universal alternative to a blink; a blink never confirms a tap-only rest.
            if (!(by == ConfirmedBy.TAP && request.confirm == ConfirmMode.BLINK)) return false
        }
        finish(nowMs, RestOutcome.COMPLETED, by)
        return true
    }

    /** Lazily applies the time-driven transitions so [view] can be called at any rate. */
    private fun resolve(nowMs: Long) {
        if (outcome != null || nowMs < restEnd) return
        when (request.effectiveConfirm) {
            ConfirmMode.AUTO -> finish(restEnd, RestOutcome.COMPLETED, ConfirmedBy.AUTO)
            ConfirmMode.TAP -> if (nowMs - restEnd >= TAP_TIMEOUT_MS) finish(restEnd + TAP_TIMEOUT_MS, RestOutcome.COMPLETED, ConfirmedBy.TIMEOUT)
            ConfirmMode.BLINK -> if (nowMs - restEnd >= BLINK_TIMEOUT_MS) finish(restEnd + BLINK_TIMEOUT_MS, RestOutcome.COMPLETED, ConfirmedBy.TIMEOUT)
        }
    }

    private fun finish(atMs: Long, result: RestOutcome, by: ConfirmedBy?) {
        if (outcome != null) return
        outcome = result
        confirmedBy = by
        endedAtMs = atMs
    }

    private fun progress(nowMs: Long): Float =
        ((nowMs - restStart).toFloat() / (restEnd - restStart)).coerceIn(0f, 1f)

    companion object {
        const val SETTLE_MS = 800L
        const val TAP_TIMEOUT_MS = 60_000L
        const val BLINK_TIMEOUT_MS = 12_000L
    }
}
