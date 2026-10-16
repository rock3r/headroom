package dev.c5inco.charts.core

import kotlin.math.abs
import kotlin.math.hypot

/** How a pointer position resolves to data. */
enum class FocusMode {
    /** The category or x position closest to the pointer, with every series at that position. */
    NearestX,

    /**
     * [NearestX] turned sideways, for [HorizontalBarChart]: the row closest to the pointer's y,
     * with every series in that row.
     */
    NearestY,

    /** The single closest point, within a distance. Scatter plots. */
    NearestXY,

    /**
     * Whatever painted shape is under the pointer, or nothing: pies, donuts, treemap tiles and
     * heatmap cells. Gaps, treemap group headers and missing heatmap cells belong to no datum.
     */
    Containment,
}

/**
 * The result of resolving a pointer. [group] holds every point that should be described
 * together (all series in a category); [primary] is the one under the finger.
 */
data class Focus(
    val primary: FocusPoint,
    val group: List<FocusPoint>,
    /**
     * Where a tooltip should point: the top of the group, or, under [FocusMode.NearestY], the
     * right-hand end of the row at its centre.
     */
    val anchor: XY,
    val mode: FocusMode,
)

fun ChartScene.focusAt(pointer: XY, maxDistance: Float = Float.POSITIVE_INFINITY): Focus? {
    if (points.isEmpty()) return null
    return when (focusMode) {
        FocusMode.NearestX -> {
            val nearest = points.minWith(
                compareBy<FocusPoint> { abs(it.slot - pointer.x) }.thenBy { abs(it.y - pointer.y) },
            )
            // At most one datum per series: when a dense range packs several x positions within
            // half a pixel of the needle, pick each series' point nearest the needle's slot.
            val group = points.filter { abs(it.slot - nearest.slot) < 0.5f }
                .groupBy { it.series }
                .values
                .map { candidates ->
                    candidates.minWith(
                        compareBy<FocusPoint> { abs(it.slot - nearest.slot) }
                            .thenBy { it.datum != nearest.datum }
                            .thenBy { abs(it.slot - pointer.x) }
                            .thenBy { abs(it.y - pointer.y) },
                    )
                }
                .sortedBy { it.series }
            // A finger on a painted segment (a stacked bar) beats the nearest value.
            val primary = group.lastOrNull { it.hit?.contains(pointer) == true }
                ?: group.minBy { abs(it.y - pointer.y) }
            Focus(primary, group, XY(nearest.slot, group.minOf { it.y }), focusMode)
        }
        FocusMode.NearestY -> {
            val nearest = points.minWith(
                compareBy<FocusPoint> { abs(it.slot - pointer.y) }.thenBy { abs(it.x - pointer.x) },
            )
            val group = points.filter { abs(it.slot - nearest.slot) < 0.5f }
                .groupBy { it.series }
                .values
                .map { candidates ->
                    candidates.minWith(
                        compareBy<FocusPoint> { abs(it.slot - nearest.slot) }
                            .thenBy { it.datum != nearest.datum }
                            .thenBy { abs(it.slot - pointer.y) }
                            .thenBy { abs(it.x - pointer.x) },
                    )
                }
                .sortedBy { it.series }
            val primary = group.lastOrNull { it.hit?.contains(pointer) == true }
                ?: group.minBy { abs(it.x - pointer.x) }
            Focus(primary, group, XY(group.maxOf { it.x }, nearest.slot), focusMode)
        }
        FocusMode.NearestXY -> {
            val nearest = points.minBy { dist2(it, pointer) }
            if (dist2(nearest, pointer) > maxDistance * maxDistance) null
            else Focus(nearest, listOf(nearest), XY(nearest.x, nearest.y), focusMode)
        }
        FocusMode.Containment -> {
            val hit = points.lastOrNull { it.hit?.contains(pointer) == true }
                ?: nearestWithinSlop(pointer)
                ?: return null
            Focus(hit, listOf(hit), XY(hit.x, hit.y), focusMode)
        }
    }
}

/**
 * The point whose painted rectangle is nearest [pointer], when it is within
 * [ChartScene.focusSlop].
 */
private fun ChartScene.nearestWithinSlop(pointer: XY): FocusPoint? {
    if (focusSlop <= 0f) return null
    var best: FocusPoint? = null
    var bestDistance = focusSlop
    for (point in points) {
        val bounds = (point.hit as? RectHit)?.bounds ?: continue
        val dx = maxOf(bounds.left - pointer.x, 0f, pointer.x - bounds.right)
        val dy = maxOf(bounds.top - pointer.y, 0f, pointer.y - bounds.bottom)
        val distance = hypot(dx, dy)
        if (distance <= bestDistance) {
            best = point
            bestDistance = distance
        }
    }
    return best
}

private fun dist2(p: FocusPoint, q: XY): Float {
    val dx = p.x - q.x
    val dy = p.y - q.y
    return dx * dx + dy * dy
}
