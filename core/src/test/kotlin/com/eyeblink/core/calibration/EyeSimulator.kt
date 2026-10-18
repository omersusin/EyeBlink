package com.eyeblink.core.calibration

import java.util.Random
import kotlin.math.max

/**
 * A kind of eye, as a face tracker would measure it: the eye aspect ratio it reads when open and when shut.
 * "Narrow" here means exactly what a person with such eyes experiences: the open reading is low and the
 * range between open and shut is small, so a full blink moves the number by far less than on a wide eye.
 */
data class EyeProfile(
    val name: String,
    val openEar: Float,
    val closedEar: Float,
    /** The tracker's noise on one reading, as a share of the open reading. */
    val noise: Float = 0.035f,
    /** The right eye's open reading as a share of the left's (1 = symmetrical). */
    val rightShare: Float = 1f,
) {
    /** How far a full blink moves the reading, as a share of the open one. */
    val fullClosure: Float get() = 1f - closedEar / openEar

    companion object {
        val WIDE = EyeProfile("wide", openEar = 0.36f, closedEar = 0.06f)
        val TYPICAL = EyeProfile("typical", openEar = 0.30f, closedEar = 0.07f)
        val NARROW = EyeProfile("narrow", openEar = 0.20f, closedEar = 0.07f, noise = 0.05f)
        val MONOLID = EyeProfile("monolid", openEar = 0.17f, closedEar = 0.08f, noise = 0.06f)
        val HEAVY_LIDS = EyeProfile("heavy lids", openEar = 0.14f, closedEar = 0.075f, noise = 0.07f)
        val ASYMMETRIC = EyeProfile("asymmetric", openEar = 0.28f, closedEar = 0.07f, rightShare = 0.72f)

        val ALL = listOf(WIDE, TYPICAL, NARROW, MONOLID, HEAVY_LIDS, ASYMMETRIC)
    }
}

/**
 * Builds the stream of samples a camera would deliver for one person: blinks of a chosen depth and
 * speed, a squint, a slow change of how far the lids are open (looking down at a phone), the tracker's
 * noise, an occasional glitch frame, and frames that arrive a little early, late or not at all.
 *
 * Deterministic for a given seed, so a failing case can be replayed.
 */
class EyeSimulator(
    private val eye: EyeProfile,
    private val fps: Int = 30,
    seed: Long = 7,
    /** Chance per frame of a single wild reading (a landmark that jumped). */
    private val glitchRate: Float = 0.004f,
    private val dropRate: Float = 0.04f,
    private val withBlinkScore: Boolean = false,
) {
    private val random = Random(seed)

    private class Blink(val startMs: Long, val depth: Float, val closeMs: Long, val holdMs: Long, val openMs: Long) {
        val endMs: Long get() = startMs + closeMs + holdMs + openMs

        /** 0 = open, [depth] = as shut as it gets. */
        fun at(t: Long): Float {
            val x = t - startMs
            return when {
                x <= 0 -> 0f
                x < closeMs -> depth * smooth(x.toFloat() / closeMs)
                x < closeMs + holdMs -> depth
                x < closeMs + holdMs + openMs -> depth * (1f - smooth((x - closeMs - holdMs).toFloat() / openMs))
                else -> 0f
            }
        }

        private fun smooth(u: Float): Float = u * u * (3f - 2f * u)
    }

    private class Dip(val startMs: Long, val durationMs: Long, val share: Float, val rampMs: Long) {
        /** The share of the open reading the lids have given up at [t]. */
        fun at(t: Long): Float {
            val x = t - startMs
            return when {
                x <= 0 || x >= durationMs + 2 * rampMs -> 0f
                x < rampMs -> share * x / rampMs
                x < rampMs + durationMs -> share
                else -> share * (1f - (x - rampMs - durationMs).toFloat() / rampMs)
            }
        }
    }

    private val blinks = mutableListOf<Blink>()
    private val dips = mutableListOf<Dip>()

    /** A natural blink: the lids close in about 80 ms and open again in about 180. */
    fun blink(atMs: Long, depth: Float = 1f, closeMs: Long = 80, holdMs: Long = 30, openMs: Long = 180): EyeSimulator {
        blinks += Blink(atMs, depth, closeMs, holdMs, openMs)
        return this
    }

    /** A blink on purpose: slower to close, held, slower to open. */
    fun deliberate(atMs: Long, depth: Float = 1f, holdMs: Long = 350): EyeSimulator =
        blink(atMs, depth, closeMs = 140, holdMs = holdMs, openMs = 220)

    /** The lids give up [share] of the open reading for [durationMs]: looking down at a phone, squinting at glare. */
    fun lower(atMs: Long, durationMs: Long, share: Float, rampMs: Long = 600): EyeSimulator {
        dips += Dip(atMs, durationMs, share, rampMs)
        return this
    }

    /** The samples of [fromMs, toMs). */
    fun samples(fromMs: Long, toMs: Long): List<EyeOpennessSample> {
        val out = ArrayList<EyeOpennessSample>()
        val step = 1000.0 / fps
        var n = 0
        while (true) {
            val nominal = fromMs + (n * step).toLong()
            n++
            if (nominal >= toMs) break
            if (random.nextFloat() < dropRate) continue
            val t = nominal + random.nextInt(9) - 4
            out += sampleAt(t)
        }
        return out
    }

    private fun sampleAt(t: Long): EyeOpennessSample {
        val dip = dips.sumOf { it.at(t).toDouble() }.toFloat().coerceIn(0f, 0.9f)
        val blink = blinks.maxOfOrNull { it.at(t) } ?: 0f
        fun reading(open: Float): Float {
            val openNow = open * (1f - dip)
            val closedNow = eye.closedEar * (1f - dip)
            var ear = openNow - blink * (openNow - closedNow)
            ear += (random.nextGaussian() * eye.noise * open).toFloat()
            if (random.nextFloat() < glitchRate) ear *= 0.4f + 0.3f * random.nextFloat()
            return max(ear, 0f)
        }
        val left = EyeGeometry.openness(reading(eye.openEar))
        val right = EyeGeometry.openness(reading(eye.openEar * eye.rightShare))
        val score = if (withBlinkScore) {
            (0.05f + 0.9f * blink + (random.nextGaussian() * 0.03).toFloat()).coerceIn(0f, 1f)
        } else {
            null
        }
        return EyeOpennessSample(t, left, right, confidence = 0.9f, illuminance = 120f, blinkScore = score)
    }
}
