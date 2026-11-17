package dev.c5inco.charts.core

/*
 * Bar rules shared by upright bars ([compileCartesian]) and horizontal bars ([compileHorizontal]):
 * how a group shares its slot, how a stack segment opens its gap, and which edge sits on the
 * baseline. Each compiler keeps its own loop, since the axes, culling, keys and focus points
 * differ; these are the per-bar rules, so the two orientations cannot drift apart.
 */

/** The value at [i]: NaN when it is missing (not finite) or past the end of the series. */
internal fun Series.at(i: Int): Double {
    if (i >= values.size) return Double.NaN
    val v = values[i]
    return if (v.isFinite()) v else Double.NaN
}

/** [extent] from [start], narrowed to [cap] around its centre when it is wider. */
internal fun capped(start: Float, extent: Float, cap: Float): Pair<Float, Float> =
    if (extent > cap) (start + (extent - cap) / 2f) to cap else start to extent

/** The widest a group of [k] bars may be. Cap the group, not each bar, so capped bars stay together. */
internal fun groupCap(k: Int, m: ChartMetrics): Float {
    val slotCap = if (k > 1) m.maxBarWidth / (1f - m.groupPadding) else m.maxBarWidth
    return slotCap * k
}

/**
 * The slots of a group's [k] bars across `[start, start + extent]`. A lone series fills its slot;
 * padding only separates bars within a group.
 */
internal fun groupSlots(k: Int, start: Float, extent: Float, m: ChartMetrics): BandScale =
    BandScale(k, start, start + extent, if (k > 1) m.groupPadding else 0f, 0f)

/**
 * The edge of a bar of value [v] that sits on the baseline on screen, along value axis [scale]. The
 * bar is drawn from pixel [from] (the baseline, or a stack segment's bound nearer zero) to [to]
 * (its free end), and the edge is the one at [from]: the bottom of a bar drawn upward, the left of
 * one drawn rightward when [horizontal]. Reading the drawn pixels keeps it right on a reversed
 * axis, and for a value on the far side of a baseline clamped to the edge of a cropped domain.
 * A zero-length bar has no direction on screen, so its sign and the axis direction decide: the
 * bottom (left) for zero and up on an axis read low to high, swapped when [reversed][isReversed].
 */
internal fun baseEdge(v: Double, scale: LinearScale, horizontal: Boolean, from: Float, to: Float): Edge {
    // Whether the bar runs the way gains run on a plain axis: up (toward smaller y), or right.
    val forward = when {
        to == from || to.isNaN() || from.isNaN() -> (v >= 0) != scale.isReversed
        horizontal -> to > from
        else -> to < from
    }
    return if (horizontal) {
        if (forward) Edge.Left else Edge.Right
    } else {
        if (forward) Edge.Bottom else Edge.Top
    }
}

/** For each sign, the last series with a defined, nonzero value at [i]: the outermost segments of a stack. */
internal fun stackEnds(series: List<Series>, i: Int): Set<Int> =
    series.indices.filter { s -> series[s].at(i).let { !it.isNaN() && it != 0.0 } }
        .groupBy { s -> series[s].at(i) >= 0 }
        .values.map { it.last() }.toSet()

/**
 * Stack segment [r], between stack bounds [v0] and [v1], with [gap] opened on its [base] side. A
 * segment on the baseline (a zero bound), or no longer along the value axis than the gap, is left
 * whole.
 */
internal fun stackGap(r: Bounds, base: Edge, v0: Double, v1: Double, gap: Float): Bounds {
    val length = if (base == Edge.Left || base == Edge.Right) r.width else r.height
    return if (v0 != 0.0 && v1 != 0.0 && length > gap) {
        when (base) {
            Edge.Bottom -> r.copy(bottom = r.bottom - gap)
            Edge.Top -> r.copy(top = r.top + gap)
            Edge.Left -> r.copy(left = r.left + gap)
            Edge.Right -> r.copy(right = r.right - gap)
        }
    } else {
        r
    }
}
