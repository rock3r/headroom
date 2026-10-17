package dev.c5inco.charts.core

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Maps a continuous numeric domain onto a pixel range. Equivalent to d3-scale's `scaleLinear`.
 */
class LinearScale(
    val domainStart: Double,
    val domainEnd: Double,
    val rangeStart: Float = 0f,
    val rangeEnd: Float = 1f,
) {
    operator fun invoke(value: Double): Float {
        val span = domainEnd - domainStart
        val t = if (span == 0.0) 0.5 else (value - domainStart) / span
        return (rangeStart + (rangeEnd - rangeStart) * t).toFloat()
    }

    fun invert(px: Float): Double {
        val span = rangeEnd - rangeStart
        val t = if (span == 0f) 0.5 else ((px - rangeStart) / span).toDouble()
        return domainStart + (domainEnd - domainStart) * t
    }

    fun withRange(start: Float, end: Float) = LinearScale(domainStart, domainEnd, start, end)

    /** Human-friendly tick values: multiples of 1, 2 or 5 × 10ⁿ. */
    fun ticks(count: Int = 5): List<Double> = Ticks.ticks(domainStart, domainEnd, count)

    /**
     * Extends the domain outward so both ends land on round tick values, preserving the range.
     * A nonpositive count, equal endpoints, or an unrepresentable tick step leaves the domain
     * unchanged. Non-finite endpoints are also preserved rather than introducing new NaNs.
     */
    fun nice(count: Int = 5): LinearScale {
        if (count <= 0 || domainStart == domainEnd || !domainStart.isFinite() || !domainEnd.isFinite()) return this
        var start = domainStart
        var stop = domainEnd
        val reversed = stop < start
        if (reversed) start = stop.also { stop = start }
        var previous = Double.NaN
        for (iteration in 0 until 10) {
            val step = Ticks.tickIncrement(start, stop, count)
            if (!step.isFinite() || step == 0.0 || step == previous) break
            val nextStart = if (step > 0) floor(start / step) * step else ceil(start * step) / step
            val nextStop = if (step > 0) ceil(stop / step) * step else floor(stop * step) / step
            // Rounding an endpoint near Double.MAX_VALUE can overflow even with a finite step.
            if (!nextStart.isFinite() || !nextStop.isFinite()) break
            start = nextStart
            stop = nextStop
            previous = step
        }
        return if (reversed) LinearScale(stop, start, rangeStart, rangeEnd)
        else LinearScale(start, stop, rangeStart, rangeEnd)
    }
}

/**
 * Divides a pixel range into equal bands, one per category. Equivalent to d3-scale's
 * `scaleBand`; [point] gives `scalePoint` (zero-width bands).
 */
class BandScale(
    val count: Int,
    val rangeStart: Float,
    val rangeEnd: Float,
    val paddingInner: Float = 0.2f,
    val paddingOuter: Float = 0.1f,
    val align: Float = 0.5f,
) {
    val step: Float
    val bandwidth: Float
    private val origin: Float

    init {
        val span = rangeEnd - rangeStart
        step = span / max(1f, count - paddingInner + paddingOuter * 2)
        origin = rangeStart + (span - step * (count - paddingInner)) * align
        bandwidth = step * (1f - paddingInner)
    }

    fun start(index: Int): Float = origin + step * index
    fun center(index: Int): Float = start(index) + bandwidth / 2f

    /** The band whose center is closest to [px]. */
    fun nearestIndex(px: Float): Int {
        if (count == 0) return -1
        val raw = (px - origin - bandwidth / 2f) / step
        return Math.round(raw).coerceIn(0, count - 1)
    }

    companion object {
        fun point(count: Int, rangeStart: Float, rangeEnd: Float, padding: Float = 0f) =
            BandScale(count, rangeStart, rangeEnd, paddingInner = 1f, paddingOuter = padding)
    }
}

/** A port of d3-array's tick generation (`ticks`, `tickIncrement`, `tickStep`). */
object Ticks {
    private val e10 = sqrt(50.0)
    private val e5 = sqrt(10.0)
    private val e2 = sqrt(2.0)

    private data class Spec(val i1: Double, val i2: Double, val inc: Double)

    private fun jsRound(x: Double): Double = floor(x + 0.5)

    private fun tickSpec(start: Double, stop: Double, count: Double): Spec {
        val step = (stop - start) / max(0.0, count)
        val power = floor(log10(step))
        val error = step / 10.0.pow(power)
        val factor = when {
            error >= e10 -> 10.0
            error >= e5 -> 5.0
            error >= e2 -> 2.0
            else -> 1.0
        }
        var i1: Double
        var i2: Double
        var inc: Double
        if (power < 0) {
            inc = 10.0.pow(-power) / factor
            i1 = jsRound(start * inc)
            i2 = jsRound(stop * inc)
            if (i1 / inc < start) ++i1
            if (i2 / inc > stop) --i2
            inc = -inc
        } else {
            inc = 10.0.pow(power) * factor
            i1 = jsRound(start / inc)
            i2 = jsRound(stop / inc)
            if (i1 * inc < start) ++i1
            if (i2 * inc > stop) --i2
        }
        if (i2 < i1 && 0.5 <= count && count < 2) return tickSpec(start, stop, count * 2)
        return Spec(i1, i2, inc)
    }

    fun ticks(start: Double, stop: Double, count: Int): List<Double> {
        if (count <= 0) return emptyList()
        if (start == stop) return listOf(start)
        val reverse = stop < start
        val (i1, i2, inc) = if (reverse) tickSpec(stop, start, count.toDouble()) else tickSpec(start, stop, count.toDouble())
        if (!(i2 >= i1)) return emptyList()
        val n = (i2 - i1 + 1).toInt()
        return List(n) { i ->
            val k = if (reverse) i2 - i else i1 + i
            if (inc < 0) k / -inc else k * inc
        }
    }

    /** The signed tick increment: positive is a step, negative is a reciprocal step. */
    fun tickIncrement(start: Double, stop: Double, count: Int): Double =
        tickSpec(start, stop, count.toDouble()).inc

    fun tickStep(start: Double, stop: Double, count: Int): Double {
        val reverse = stop < start
        val inc = if (reverse) tickIncrement(stop, start, count) else tickIncrement(start, stop, count)
        return (if (reverse) -1 else 1) * (if (inc < 0) 1 / -inc else inc)
    }

    /** The spacing between consecutive ticks in [ticks], or 1 when there are fewer than two. */
    internal fun spacing(ticks: List<Double>): Double =
        if (ticks.size < 2) 1.0 else abs(ticks[1] - ticks[0])
}
