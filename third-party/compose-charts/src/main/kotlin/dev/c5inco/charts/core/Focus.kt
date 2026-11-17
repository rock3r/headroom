package dev.c5inco.charts.core

import kotlin.math.abs
import kotlin.math.hypot

/** How a pointer position resolves to data. */
public enum class FocusMode {
    /**
     * The category or x position closest to the pointer, with one point per series. Aligned marks
     * share the selected axis datum index; dots with raw x join only at the same numeric x.
     * If both points lack raw x, exact slots are the fallback. Duplicate aligned x values select
     * one shared index, rather than mixing records across series.
     */
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
public data class Focus(
    val primary: FocusPoint,
    val group: List<FocusPoint>,
    /**
     * Where a tooltip should point: the top of the group, or, under [FocusMode.NearestY], the
     * right-hand end of the row at its centre.
     */
    val anchor: XY,
    val mode: FocusMode,
)

public fun ChartScene.focusAt(pointer: XY, maxDistance: Float = Float.POSITIVE_INFINITY): Focus? {
    if (points.isEmpty()) return null
    // Called on every pointer move while scrubbing, over every focus point of a dense scene: plain
    // loops over the scene's [FocusTable] (primitive arrays, read in order) rather than comparator
    // chains (which box every key), collection pipelines and a walk over every point object. Ties
    // go to the earliest point in [points], as `minWith`/`minBy` did, and keys compare as
    // `Float.compareTo` does (NaN above everything), so results are the same instances.
    val table = focusTable
    return when (focusMode) {
        FocusMode.NearestX -> {
            val nearest = points[table.nearestOnAxis(pointer.x, pointer.y, alongY = false)]
            // At most one datum per series: for aligned Cartesian series, resolve every series at
            // the selected datum index from the raw values (skipping NaNs) so all rows in the
            // tooltip report the exact same x position even when M4 decimation retained different
            // extrema across series.
            val aligned = alignedFocus
            val datum = if (nearest.detail is FocusDetail.Aligned) nearest.datum.takeIf { it >= 0 }
                else matchingAlignedDatum(table, nearest, pointer) ?: nearest.domainX()?.let { aligned?.datumAt(it) }
            // Custom scenes may have incomplete series metadata; keep a non-empty group.
            val group = if (datum != null) {
                alignedGroup(table, aligned, datum, nearest, pointer)
            } else {
                slotGroup(table, nearest, pointer.x, pointer.y, alongY = false)
            }.ifEmpty { listOf(nearest) }
            // A finger on a painted segment (a stacked bar) beats the nearest value.
            val primary = group.lastOrNull { it.hit?.contains(pointer) == true }
                ?: group.minBy { abs(it.y - pointer.y) }
            Focus(primary, group, XY(nearest.slot, group.minOf { it.y }), focusMode)
        }
        FocusMode.NearestY -> {
            val nearest = points[table.nearestOnAxis(pointer.y, pointer.x, alongY = true)]
            val datum = if (nearest.detail is FocusDetail.Aligned) nearest.datum.takeIf { it >= 0 }
                else matchingAlignedDatum(table, nearest, pointer, alongY = true)
            val group = slotGroup(table, nearest, pointer.y, pointer.x, alongY = true, alignedDatum = datum)
                .ifEmpty { listOf(nearest) }
            val primary = group.lastOrNull { it.hit?.contains(pointer) == true }
                ?: group.minBy { abs(it.x - pointer.x) }
            Focus(primary, group, XY(group.maxOf { it.x }, nearest.slot), focusMode)
        }
        FocusMode.NearestXY -> {
            val xs = table.x
            val ys = table.y
            var nearest = 0
            var best = dist2(xs[0], ys[0], pointer)
            for (i in 1 until xs.size) {
                val d = dist2(xs[i], ys[i], pointer)
                if (d.compareTo(best) < 0) {
                    nearest = i
                    best = d
                }
            }
            val point = points[nearest]
            if (best > maxDistance * maxDistance) null
            else Focus(point, listOf(point), XY(point.x, point.y), focusMode)
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
 * A scene's focus points as primitive arrays, index for index: what [focusAt] reads of every point
 * on every pointer move, kept together so a scan touches a few arrays rather than thousands of
 * objects. Built once per scene instance, on its first focus ([ChartScene.focusTable]); immutable.
 */
internal class FocusTable(points: List<FocusPoint>) {
    val x = FloatArray(points.size)
    val y = FloatArray(points.size)
    val slot = FloatArray(points.size)
    val datum = IntArray(points.size)
    /** Raw continuous x, or NaN when this point has no domain x. Never a rounded pixel. */
    val domainX = DoubleArray(points.size)
    /** Whether the point is [FocusDetail.Aligned] to the category or x axis. */
    val aligned = BooleanArray(points.size)

    init {
        points.forEachIndexed { i, p ->
            x[i] = p.x
            y[i] = p.y
            slot[i] = p.slot
            datum[i] = p.datum
            domainX[i] = p.domainX() ?: Double.NaN
            aligned[i] = p.detail is FocusDetail.Aligned
        }
    }

    /**
     * The index of the first point nearest [along] on the focus axis (its slot), ties broken by the
     * nearest [across] the other way: y when scrubbing columns, x when scrubbing rows ([alongY]).
     */
    fun nearestOnAxis(along: Float, across: Float, alongY: Boolean): Int {
        val slot = slot
        val cross = if (alongY) x else y
        var nearest = 0
        var bestSlot = abs(slot[0] - along)
        var bestCross = abs(cross[0] - across)
        for (i in 1 until slot.size) {
            val ds = abs(slot[i] - along)
            val c = ds.compareTo(bestSlot)
            if (c > 0) continue
            val dc = abs(cross[i] - across)
            if (c < 0 || dc.compareTo(bestCross) < 0) {
                nearest = i
                bestSlot = ds
                bestCross = dc
            }
        }
        return nearest
    }
}

/** A point's position across the focus axis: y when scrubbing columns, x when scrubbing rows. */
private fun FocusPoint.across(alongY: Boolean): Float = if (alongY) x else y

/** The continuous domain coordinate; absent/non-finite metadata falls back to a pixel slot. */
private fun FocusPoint.domainX(): Double? = when (val d = detail) {
    is FocusDetail.Aligned -> d.domainX
    is FocusDetail.Dot -> d.domainX
    else -> null
}?.takeIf { it.isFinite() }

/**
 * A dot chooses one shared aligned datum, also when multiple rows round onto one slot under
 * NearestY. Row matching ignores raw x; column matching uses raw x. If both lack raw x,
 * an exact slot match supplies that datum. For duplicate positions, prefer the retained point
 * nearest the pointer across the focus axis, with scene order breaking ties.
 * If all matching samples were decimated away, AlignedFocus supplies the first matching index.
 */
private fun matchingAlignedDatum(table: FocusTable, nearest: FocusPoint, pointer: XY, alongY: Boolean = false): Int? {
    val x = nearest.domainX()
    var best = -1
    var distance = Float.POSITIVE_INFINITY
    for (i in table.datum.indices) {
        if (!table.aligned[i] || table.datum[i] < 0) continue
        val matches = when {
            alongY -> table.slot[i] == nearest.slot
            x != null -> table.domainX[i] == x
            else -> table.domainX[i].isNaN() && table.slot[i] == nearest.slot
        }
        if (!matches) continue
        val d = if (alongY) abs(table.x[i] - pointer.x) else abs(table.y[i] - pointer.y)
        if (best < 0 || d.compareTo(distance) < 0) {
            best = i
            distance = d
        }
    }
    return if (best < 0) null else table.datum[best]
}

/**
 * One point per series: along rows, use the selected aligned index or an exact row slot; along
 * columns, dots share raw x, falling back to exact slots when both raw x values are absent.
 * Dot list indices never rank another series; keep scene order on a full tie.
 */
private fun ChartScene.slotGroup(table: FocusTable, nearest: FocusPoint, along: Float, across: Float, alongY: Boolean, alignedDatum: Int? = null): List<FocusPoint> {
    val best = HashMap<Int, FocusPoint>()
    val slot = table.slot
    val nearestSlot = nearest.slot
    val domainX = nearest.domainX()
    val isAligned = nearest.detail is FocusDetail.Aligned
    for (i in slot.indices) {
        val same = when {
            alongY -> if (alignedDatum != null && table.aligned[i]) table.datum[i] == alignedDatum
                else slot[i] == nearestSlot
            isAligned && nearest.datum < 0 -> slot[i] == nearestSlot
            domainX != null -> table.domainX[i] == domainX && !table.aligned[i]
            else -> slot[i] == nearestSlot && table.domainX[i].isNaN()
        }
        if (!same) continue
        val p = points[i]
        val current = best[p.series]
        if (current == null || slotRank(p, current, nearest, along, across, alongY) < 0) best[p.series] = p
    }
    return best.values.sortedBy { it.series }
}

private fun slotRank(a: FocusPoint, b: FocusPoint, nearest: FocusPoint, along: Float, across: Float, alongY: Boolean): Int {
    var c = abs(a.slot - nearest.slot).compareTo(abs(b.slot - nearest.slot))
    if (c != 0) return c
    // Aligned indices were already matched; a dot's list index must never rank another series.
    c = abs(a.slot - along).compareTo(abs(b.slot - along))
    if (c != 0) return c
    return abs(a.across(alongY) - across).compareTo(abs(b.across(alongY) - across))
}

/**
 * Every aligned series at [datum]: the scene's own point where it has one (the last, for a series
 * listed twice), else what [aligned] resolves. Dots join only at the same raw x, one per series;
 * absent raw x on both points falls back to an exact slot match. All rows remain in series order.
 */
private fun ChartScene.alignedGroup(table: FocusTable, aligned: AlignedFocus?, datum: Int, nearest: FocusPoint, pointer: XY): List<FocusPoint> {
    val domainX = nearest.domainX()
    val seriesCount = series.size
    val own = arrayOfNulls<FocusPoint>(seriesCount)
    var dots: HashMap<Int, FocusPoint>? = null
    val data = table.datum
    val isAligned = table.aligned
    for (i in data.indices) {
        if (isAligned[i]) {
            if (data[i] == datum) {
                val p = points[i]
                if (p.series in 0 until seriesCount) own[p.series] = p
            }
        } else if (if (domainX != null) table.domainX[i] == domainX
            else table.domainX[i].isNaN() && table.slot[i] == nearest.slot) {
            val p = points[i]
            val found = dots ?: HashMap<Int, FocusPoint>().also { dots = it }
            val current = found[p.series]
            if (current == null || dotRank(p, current, nearest, pointer) < 0) found[p.series] = p
        }
    }
    val resolved = ArrayList<FocusPoint>(seriesCount)
    for (s in 0 until seriesCount) {
        val pt = own[s] ?: aligned?.pointAt(s, datum)
        if (pt != null) resolved += pt
    }
    val dotSeries = dots ?: return resolved
    // A dot's datum indexes its own points; its raw x is the shared identity.
    for ((s, dot) in dotSeries) {
        if (resolved.none { it.series == s }) resolved += dot
    }
    return resolved.sortedBy { it.series }
}

private fun dotRank(a: FocusPoint, b: FocusPoint, nearest: FocusPoint, pointer: XY): Int {
    var c = abs(a.slot - nearest.slot).compareTo(abs(b.slot - nearest.slot))
    if (c != 0) return c
    c = abs(a.slot - pointer.x).compareTo(abs(b.slot - pointer.x))
    if (c != 0) return c
    return abs(a.y - pointer.y).compareTo(abs(b.y - pointer.y))
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

private fun dist2(x: Float, y: Float, q: XY): Float {
    val dx = x - q.x
    val dy = y - q.y
    return dx * dx + dy * dy
}
