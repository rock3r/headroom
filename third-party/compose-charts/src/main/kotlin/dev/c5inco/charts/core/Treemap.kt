package dev.c5inco.charts.core

import kotlin.math.max
import kotlin.math.sqrt

/** Treemap tiling: d3-hierarchy's `treemapSquarify`, as a pure function from sizes to rectangles. */
object Treemap {
    /** The golden ratio, d3's default target aspect ratio for squarified rows. */
    val PHI: Double = (1 + sqrt(5.0)) / 2

    /**
     * Tiles [bounds] with one rectangle per value, in input order, each with an area proportional
     * to its value (Bruls, Huizing and van Wijk). Values are packed into rows along the shorter
     * side while the row's worst aspect ratio keeps improving, so tiles stay close to [ratio].
     * Sorting values largest first gives the squarest result.
     *
     * Only positive, finite values take area. Anything else (zero, negative, NaN, infinite) gets an
     * empty rectangle at the top-left of [bounds], as does every value when [bounds] is empty.
     */
    fun squarify(values: List<Double>, bounds: Bounds, ratio: Double = PHI): List<Bounds> {
        require(ratio >= 1.0) { "Treemap.squarify ratio must be at least 1, was $ratio" }
        val n = values.size
        val v = DoubleArray(n) { values[it].takeIf { x -> x.isFinite() && x > 0.0 } ?: 0.0 }
        val empty = Bounds(bounds.left, bounds.top, bounds.left, bounds.top)
        val out = MutableList(n) { empty }
        var remaining = v.sum()
        if (bounds.width <= 0f || bounds.height <= 0f || !(remaining > 0.0) || !remaining.isFinite()) return out

        var x0 = bounds.left.toDouble()
        var y0 = bounds.top.toDouble()
        val x1 = bounds.right.toDouble()
        val y1 = bounds.bottom.toDouble()
        var start = 0
        while (start < n) {
            // Skip empty values; they keep their empty rectangle.
            if (v[start] == 0.0) { start++; continue }
            val dx = x1 - x0
            val dy = y1 - y0
            // Worst aspect ratio of a row holding [sum] whose extremes are [lo] and [hi], scaled so
            // that the row spans the shorter side of the remaining space.
            val alpha = max(dy / dx, dx / dy) / (remaining * ratio)
            fun worst(sum: Double, lo: Double, hi: Double): Double {
                val beta = sum * sum * alpha
                return max(hi / beta, beta / lo)
            }
            var end = start + 1
            var sum = v[start]
            var lo = sum
            var hi = sum
            var best = worst(sum, lo, hi)
            while (end < n) {
                val next = v[end]
                if (next == 0.0) { end++; continue } // empty: rides along without area
                val candidate = worst(sum + next, minOf(lo, next), maxOf(hi, next))
                if (candidate > best) break
                sum += next
                lo = minOf(lo, next)
                hi = maxOf(hi, next)
                best = candidate
                end++
            }
            // The last row takes whatever space is left and each row's last tile reaches its far
            // edge, so rounding never leaves a sliver.
            val last = (end until n).all { v[it] == 0.0 }
            val edge = lastPositive(v, start, end)
            if (dx < dy) {
                // A row across the top of a tall space.
                val bottom = if (last) y1 else y0 + dy * sum / remaining
                var x = x0
                for (i in start until end) {
                    if (v[i] == 0.0) continue
                    val next = if (i == edge) x1 else x + dx * v[i] / sum
                    out[i] = Bounds(x.toFloat(), y0.toFloat(), next.toFloat(), bottom.toFloat())
                    x = next
                }
                y0 = bottom
            } else {
                // A column down the left of a wide space.
                val right = if (last) x1 else x0 + dx * sum / remaining
                var y = y0
                for (i in start until end) {
                    if (v[i] == 0.0) continue
                    val next = if (i == edge) y1 else y + dy * v[i] / sum
                    out[i] = Bounds(x0.toFloat(), y.toFloat(), right.toFloat(), next.toFloat())
                    y = next
                }
                x0 = right
            }
            remaining -= sum
            start = end
        }
        return out
    }

    private fun lastPositive(v: DoubleArray, start: Int, end: Int): Int = (end - 1 downTo start).first { v[it] > 0.0 }
}
