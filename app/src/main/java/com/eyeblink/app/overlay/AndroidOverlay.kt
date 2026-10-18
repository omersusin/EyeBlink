package com.eyeblink.app.overlay

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import com.eyeblink.app.platform.Signals
import com.eyeblink.app.platform.nowMs
import com.eyeblink.core.anim.BlinkSpec
import com.eyeblink.core.anim.BlinkTimeline
import com.eyeblink.core.engine.OverlayPort
import com.eyeblink.core.rest.RestSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** Draws the long rest. Implemented where Compose is available; see `ComposeRestRenderer`. */
interface RestRenderer {
    /** Shows the rest scene for [session] and returns once it is over and gone from the screen. */
    suspend fun run(session: RestSession, spec: BlinkSpec)

    /** Takes the scene off the screen at once. Main thread. */
    fun clear()
}

/**
 * The [OverlayPort] of the real device: blinks are drawn by [BlinkView] on a frame-clock loop, rests
 * by a [RestRenderer]. Everything touching a window happens on the main thread, and [clear] can be
 * called from anywhere and returns only once the windows are really gone.
 */
class AndroidOverlay(
    private val app: Application,
    private val windows: OverlayWindows,
    private val rest: RestRenderer,
    private val reducedMotion: () -> Boolean,
    private val onBlinkStart: () -> Unit,
) : OverlayPort {

    private val main = Handler(Looper.getMainLooper())
    private var current: BlinkView? = null

    override suspend fun playBlink(spec: BlinkSpec) {
        withContext(Dispatchers.Main.immediate) {
            val effective = spec.validated().let { it.copy(reducedMotion = it.reducedMotion || reducedMotion()) }
            val timeline = BlinkTimeline(effective)
            val view = BlinkView(app).also { it.spec = effective }

            Signals.ownWindowsUntilMs = nowMs() + timeline.totalMs + OWN_WINDOW_GRACE_MS
            if (!windows.attach(view, touchable = false)) error("No window can be shown: grant Accessibility or Display over other apps")
            current = view
            onBlinkStart()
            try {
                suspendCancellableCoroutine<Unit> { cont ->
                    val callback = object : Choreographer.FrameCallback {
                        var t0 = -1L
                        override fun doFrame(frameTimeNanos: Long) {
                            if (!cont.isActive) return
                            if (t0 < 0) t0 = frameTimeNanos
                            val elapsed = (frameTimeNanos - t0) / 1_000_000f
                            val f = timeline.frameAt(elapsed)
                            if (f.isIdle && elapsed > 0f) {
                                cont.resume(Unit)
                                return
                            }
                            view.frame = f
                            view.invalidate()
                            Choreographer.getInstance().postFrameCallback(this)
                        }
                    }
                    Choreographer.getInstance().postFrameCallback(callback)
                }
            } finally {
                windows.detach(view)
                if (current === view) current = null
                Signals.ownWindowsUntilMs = nowMs() + OWN_WINDOW_GRACE_MS
            }
        }
    }

    override suspend fun runRest(session: RestSession, spec: BlinkSpec) {
        Signals.ownWindowsUntilMs = Long.MAX_VALUE
        try {
            rest.run(session, spec)
        } finally {
            Signals.ownWindowsUntilMs = nowMs() + OWN_WINDOW_GRACE_MS
        }
    }

    override fun clear() {
        val work = Runnable {
            current?.let { windows.detach(it) }
            current = null
            rest.clear()
        }
        if (Looper.myLooper() === Looper.getMainLooper()) {
            work.run()
        } else {
            // The caller is about to hand a video back; the overlay has to really be gone first.
            val done = CountDownLatch(1)
            main.post { try { work.run() } finally { done.countDown() } }
            done.await(CLEAR_WAIT_MS, TimeUnit.MILLISECONDS)
        }
    }

    private companion object {
        /** Window-state events from our own package keep being ignored for a moment after a window goes. */
        const val OWN_WINDOW_GRACE_MS = 900L
        const val CLEAR_WAIT_MS = 600L
    }
}
