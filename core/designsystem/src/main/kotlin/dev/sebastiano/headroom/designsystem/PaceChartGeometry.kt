package dev.sebastiano.headroom.designsystem

import kotlin.time.Instant

/**
 * Maps a quota window onto a plot area: time from [start] to [end] runs left to right, and usage
 * from 0% to 100% runs bottom to top. Everything is drawn to scale, so the even-pace line is the
 * diagonal and a projection lands where the real limit would be hit.
 */
class PaceChartGeometry(
    private val start: Instant,
    end: Instant,
    private val left: Float,
    private val top: Float,
    private val right: Float,
    private val bottom: Float,
) {
    private val spanMillis = (end - start).inWholeMilliseconds.coerceAtLeast(1L).toFloat()

    fun x(at: Instant): Float {
        val fraction = (at - start).inWholeMilliseconds / spanMillis
        return left + fraction.coerceIn(0f, 1f) * (right - left)
    }

    fun y(percent: Double): Float {
        val fraction = (percent / MAX_PERCENT).toFloat().coerceIn(0f, 1f)
        return bottom - fraction * (bottom - top)
    }

    /** The x positions of [count] + 1 grid lines, from the window start to its end. */
    fun tickXs(count: Int): List<Float> =
        (0..count).map { index -> left + index.toFloat() / count * (right - left) }

    private companion object {
        const val MAX_PERCENT = 100.0
    }
}
