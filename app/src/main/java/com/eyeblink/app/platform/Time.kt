package com.eyeblink.app.platform

import android.os.SystemClock

/**
 * The one clock the app uses. Monotonic, so a wall-clock change or a time-zone hop can never make
 * a blink last for hours or a rest finish before it started.
 */
fun nowMs(): Long = SystemClock.elapsedRealtime()
