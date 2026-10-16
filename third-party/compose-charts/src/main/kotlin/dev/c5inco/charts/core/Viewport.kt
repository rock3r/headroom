package dev.c5inco.charts.core

import kotlin.math.max
import kotlin.math.min

/**
 * A visible x window `[start, end]` on a continuous axis ([NumericAxis], or epoch milliseconds on a
 * [TimeAxis]).
 *
 * Passed to [compile], it sets the x scale's domain to `[start, end]`, culls aligned series data
 * outside the window (keeping one point beyond each edge so lines and areas run to the plot border),
 * recomputes ticks for the window, and marks the scene to clip marks to the plot rectangle.
 */
data class ViewportRange(val start: Double, val end: Double) {
    init {
        require(start.isFinite() && end.isFinite()) { "ViewportRange bounds must be finite, got [$start, $end]" }
        require(end > start) { "ViewportRange.end must be greater than start, got [$start, $end]" }
    }

    val span: Double get() = end - start

    operator fun contains(value: Double): Boolean = value in start..end
}

/**
 * Pure domain bounds and span constraints for a continuous x viewport, with clamping, panning and
 * anchor-preserving zoom arithmetic.
 *
 * - [min] and [max] bound the scrollable domain (`min < max`).
 * - [minSpan] and [maxSpan] bound the visible window's width `end - start`. A zero [minSpan]
 *   defaults to a one-ppm floor of the full domain so zooming in can never collapse the window to
 *   zero width; [maxSpan] is capped at `max - min`.
 */
data class ViewportBounds(
    val min: Double,
    val max: Double,
    val minSpan: Double = 0.0,
    val maxSpan: Double = max - min,
) {
    init {
        require(min.isFinite() && max.isFinite() && max > min) {
            "ViewportBounds requires finite min < max, got [$min, $max]"
        }
        require(minSpan.isFinite() && minSpan >= 0.0) {
            "ViewportBounds.minSpan must be finite and non-negative, was $minSpan"
        }
        require(maxSpan.isFinite() && maxSpan > 0.0) {
            "ViewportBounds.maxSpan must be finite and positive, was $maxSpan"
        }
        require(minSpan <= maxSpan) {
            "ViewportBounds.minSpan ($minSpan) must not exceed maxSpan ($maxSpan)"
        }
    }

    val totalSpan: Double get() = max - min

    /** Largest permitted window span, never exceeding [totalSpan]. */
    val effectiveMaxSpan: Double = min(maxSpan, totalSpan).coerceAtLeast(Double.MIN_VALUE)

    /** Smallest permitted window span, positive and never exceeding [effectiveMaxSpan]. */
    val effectiveMinSpan: Double = max(minSpan, max(totalSpan * 1e-6, Double.MIN_VALUE)).coerceAtMost(effectiveMaxSpan)

    /**
     * Clamps `[start, end]` into `[min, max]` with its span in `[effectiveMinSpan, effectiveMaxSpan]`.
     * When the requested span is out of range it is resized around the window's midpoint before
     * clamping to the domain ends.
     */
    fun clamp(start: Double, end: Double): ViewportRange {
        require(start.isFinite() && end.isFinite()) { "Viewport window must be finite, got [$start, $end]" }
        val lo = min(start, end)
        val hi = max(start, end)
        val rawSpan = hi - lo
        val targetSpan = rawSpan.coerceIn(effectiveMinSpan, effectiveMaxSpan)
        val unshiftedStart = if (rawSpan == targetSpan) lo else (lo + hi) * 0.5 - targetSpan * 0.5
        return placeSpan(unshiftedStart, targetSpan)
    }

    /**
     * Shifts [range] by [delta] domain units while keeping its span constant, clamped to `[min, max]`.
     */
    fun pan(range: ViewportRange, delta: Double): ViewportRange {
        if (!delta.isFinite() || delta == 0.0) return clamp(range.start, range.end)
        val targetSpan = range.span.coerceIn(effectiveMinSpan, effectiveMaxSpan)
        return placeSpan(range.start + delta, targetSpan)
    }

    /**
     * Scales [range] by magnification [factor] (`> 1` zooms in, `< 1` zooms out) around [anchor]
     * (in domain units; defaults to the window's centre).
     *
     * While neither data bound is hit, [anchor] stays at the same fraction `(anchor - start) / span`
     * of the window before and after the zoom, so the datum under a pinch centroid stays under the
     * fingers.
     */
    fun zoom(range: ViewportRange, factor: Double, anchor: Double = (range.start + range.end) * 0.5): ViewportRange {
        require(factor.isFinite() && factor > 0.0) { "Zoom factor must be finite and positive, was $factor" }
        require(anchor.isFinite()) { "Zoom anchor must be finite, was $anchor" }
        val currentSpan = range.span
        val fraction = if (currentSpan > 0.0) ((anchor - range.start) / currentSpan).coerceIn(0.0, 1.0) else 0.5
        val targetSpan = (currentSpan / factor).coerceIn(effectiveMinSpan, effectiveMaxSpan)
        val desiredStart = anchor - fraction * targetSpan
        return placeSpan(desiredStart, targetSpan)
    }

    private fun placeSpan(desiredStart: Double, span: Double): ViewportRange {
        val latestStart = (max - span).coerceAtLeast(min)
        val clampedStart = desiredStart.coerceIn(min, latestStart)
        val clampedEnd = (clampedStart + span).coerceAtMost(max)
        val finalStart = if (clampedEnd == max) latestStart else clampedStart
        return ViewportRange(finalStart, clampedEnd)
    }
}
