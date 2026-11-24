package com.example.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Evaluates the exact combined magnitude response (in dB) of the 5-band Parametric EQ
 * (Peak, Low Shelf, High Shelf) and 12/24/48 dB/oct Crossover filters matching C++ DSP math.
 */
object EqResponseCalculator {

    private data class Coeffs(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double)

    private const val INV_SQRT2 = 0.7071067811865475
    private val bw4Q = doubleArrayOf(0.541196100146197, 1.3065629648763766)
    private val bw8Q = doubleArrayOf(0.5097955791041592, 0.6013448869350453, 0.8999762231364156, 2.562915447741506)

    fun evaluateMagnitudeDb(
        freqHz: Double,
        sampleRate: Double,
        params: DspParameters
    ): Float {
        if (params.bypassDsp || sampleRate <= 0.0) return 0f
        var totalMag = 1.0

        if (!params.eqBypass) {
            for (band in params.eqBands) {
                if (band.enabled && abs(band.gainDb) >= 0.01f) {
                    val c = when (band.shape) {
                        EqFilterShape.PEAK -> peakingCoeffs(sampleRate, band.freqHz.toDouble(), band.q.toDouble(), band.gainDb.toDouble())
                        EqFilterShape.LOW_SHELF -> lowShelfCoeffs(sampleRate, band.freqHz.toDouble(), band.q.toDouble(), band.gainDb.toDouble())
                        EqFilterShape.HIGH_SHELF -> highShelfCoeffs(sampleRate, band.freqHz.toDouble(), band.q.toDouble(), band.gainDb.toDouble())
                    }
                    totalMag *= biquadMag(c, freqHz, sampleRate)
                }
            }
        }

        if (!params.xoverBypass) {
            if (params.hpfEnabled && params.hpfSlopeDb != XoverSlopeDb.OFF) {
                totalMag *= evaluateXoverSide(
                    isHpf = true,
                    freqHz = freqHz,
                    sampleRate = sampleRate,
                    cutoffHz = params.hpfFreqHz.toDouble(),
                    filterType = params.hpfFilterType,
                    slopeDb = params.hpfSlopeDb
                )
            }

            if (params.lpfEnabled && params.lpfSlopeDb != XoverSlopeDb.OFF) {
                totalMag *= evaluateXoverSide(
                    isHpf = false,
                    freqHz = freqHz,
                    sampleRate = sampleRate,
                    cutoffHz = params.lpfFreqHz.toDouble(),
                    filterType = params.lpfFilterType,
                    slopeDb = params.lpfSlopeDb
                )
            }
        }

        val db = 20.0 * log10(max(totalMag, 1e-6))
        return db.toFloat().coerceIn(-48f, 20f)
    }

    private fun evaluateXoverSide(
        isHpf: Boolean,
        freqHz: Double,
        sampleRate: Double,
        cutoffHz: Double,
        filterType: XoverFilterType,
        slopeDb: XoverSlopeDb
    ): Double {
        fun stage(q: Double): Double {
            val c = if (isHpf) hpfCoeffs(sampleRate, cutoffHz, q) else lpfCoeffs(sampleRate, cutoffHz, q)
            return biquadMag(c, freqHz, sampleRate)
        }

        return when (slopeDb) {
            XoverSlopeDb.OFF -> 1.0
            XoverSlopeDb.SLOPE_12 -> {
                val q = if (filterType == XoverFilterType.LINKWITZ_RILEY) 0.5 else INV_SQRT2
                stage(q)
            }
            XoverSlopeDb.SLOPE_24 -> {
                if (filterType == XoverFilterType.LINKWITZ_RILEY) {
                    val m = stage(INV_SQRT2)
                    m * m
                } else {
                    stage(bw4Q[0]) * stage(bw4Q[1])
                }
            }
            XoverSlopeDb.SLOPE_48 -> {
                if (filterType == XoverFilterType.LINKWITZ_RILEY) {
                    val m = stage(bw4Q[0]) * stage(bw4Q[1])
                    m * m
                } else {
                    stage(bw8Q[0]) * stage(bw8Q[1]) * stage(bw8Q[2]) * stage(bw8Q[3])
                }
            }
        }
    }

    private fun peakingCoeffs(fs: Double, fc: Double, q: Double, gainDb: Double): Coeffs {
        val clampedF = fc.coerceIn(10.0, fs * 0.45)
        val clampedQ = q.coerceIn(0.1, 20.0)
        val w = 2.0 * PI * clampedF / fs
        val sinW = sin(w)
        val cosW = cos(w)
        val alpha = sinW / (2.0 * clampedQ)
        val a = 10.0.pow(gainDb / 40.0)
        val a0 = 1.0 + alpha / a
        return Coeffs(
            b0 = (1.0 + alpha * a) / a0,
            b1 = (-2.0 * cosW) / a0,
            b2 = (1.0 - alpha * a) / a0,
            a1 = (-2.0 * cosW) / a0,
            a2 = (1.0 - alpha / a) / a0
        )
    }

    private fun lowShelfCoeffs(fs: Double, fc: Double, q: Double, gainDb: Double): Coeffs {
        val clampedF = fc.coerceIn(10.0, fs * 0.45)
        val clampedQ = q.coerceIn(0.25, 2.5)
        val w = 2.0 * PI * clampedF / fs
        val sinW = sin(w)
        val cosW = cos(w)
        val a = 10.0.pow(gainDb / 40.0)
        val shelfAlpha = (sinW / 2.0) * sqrt((a + 1.0 / a) * (1.0 / clampedQ - 1.0) + 2.0)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * shelfAlpha
        val a0 = (a + 1.0) + (a - 1.0) * cosW + twoSqrtAAlpha
        return Coeffs(
            b0 = (a * ((a + 1.0) - (a - 1.0) * cosW + twoSqrtAAlpha)) / a0,
            b1 = (2.0 * a * ((a - 1.0) - (a + 1.0) * cosW)) / a0,
            b2 = (a * ((a + 1.0) - (a - 1.0) * cosW - twoSqrtAAlpha)) / a0,
            a1 = (-2.0 * ((a - 1.0) + (a + 1.0) * cosW)) / a0,
            a2 = ((a + 1.0) + (a - 1.0) * cosW - twoSqrtAAlpha) / a0
        )
    }

    private fun highShelfCoeffs(fs: Double, fc: Double, q: Double, gainDb: Double): Coeffs {
        val clampedF = fc.coerceIn(10.0, fs * 0.45)
        val clampedQ = q.coerceIn(0.25, 2.5)
        val w = 2.0 * PI * clampedF / fs
        val sinW = sin(w)
        val cosW = cos(w)
        val a = 10.0.pow(gainDb / 40.0)
        val shelfAlpha = (sinW / 2.0) * sqrt((a + 1.0 / a) * (1.0 / clampedQ - 1.0) + 2.0)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * shelfAlpha
        val a0 = (a + 1.0) - (a - 1.0) * cosW + twoSqrtAAlpha
        return Coeffs(
            b0 = (a * ((a + 1.0) + (a - 1.0) * cosW + twoSqrtAAlpha)) / a0,
            b1 = (-2.0 * a * ((a - 1.0) + (a + 1.0) * cosW)) / a0,
            b2 = (a * ((a + 1.0) + (a - 1.0) * cosW - twoSqrtAAlpha)) / a0,
            a1 = (2.0 * ((a - 1.0) - (a + 1.0) * cosW)) / a0,
            a2 = ((a + 1.0) - (a - 1.0) * cosW - twoSqrtAAlpha) / a0
        )
    }

    private fun hpfCoeffs(fs: Double, fc: Double, q: Double): Coeffs {
        val clampedF = fc.coerceIn(10.0, fs * 0.45)
        val w = 2.0 * PI * clampedF / fs
        val cosW = cos(w)
        val alpha = sin(w) / (2.0 * q)
        val a0 = 1.0 + alpha
        return Coeffs(
            b0 = ((1.0 + cosW) * 0.5) / a0,
            b1 = (-(1.0 + cosW)) / a0,
            b2 = ((1.0 + cosW) * 0.5) / a0,
            a1 = (-2.0 * cosW) / a0,
            a2 = (1.0 - alpha) / a0
        )
    }

    private fun lpfCoeffs(fs: Double, fc: Double, q: Double): Coeffs {
        val clampedF = fc.coerceIn(10.0, fs * 0.45)
        val w = 2.0 * PI * clampedF / fs
        val cosW = cos(w)
        val alpha = sin(w) / (2.0 * q)
        val a0 = 1.0 + alpha
        return Coeffs(
            b0 = ((1.0 - cosW) * 0.5) / a0,
            b1 = (1.0 - cosW) / a0,
            b2 = ((1.0 - cosW) * 0.5) / a0,
            a1 = (-2.0 * cosW) / a0,
            a2 = (1.0 - alpha) / a0
        )
    }

    private fun biquadMag(c: Coeffs, freqHz: Double, fs: Double): Double {
        val w = 2.0 * PI * freqHz / fs
        val cosW = cos(w)
        val cos2W = cos(2.0 * w)
        val sinW = sin(w)
        val sin2W = sin(2.0 * w)
        val nr = c.b0 + c.b1 * cosW + c.b2 * cos2W
        val ni = -(c.b1 * sinW + c.b2 * sin2W)
        val dr = 1.0 + c.a1 * cosW + c.a2 * cos2W
        val di = -(c.a1 * sinW + c.a2 * sin2W)
        return sqrt((nr * nr + ni * ni) / max(1e-18, dr * dr + di * di))
    }
}
