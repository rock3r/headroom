package dev.c5inco.charts.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Polar charts: radar and rose. Both reuse existing nodes (areas, lines, dots, arcs, rules, text)
 * and set ChartScene.origin, which motion and the renderer read as "this scene is polar".
 */

private const val TURN = (2 * PI).toFloat()

/**
 * The most of the chart's width a polar label beside the circle may take; longer labels are cut with
 * an ellipsis. Labels above and below the circle (centred on their spoke) may take [CENTRED_LABEL_FRACTION].
 */
private const val POLAR_LABEL_FRACTION = 0.25f
private const val CENTRED_LABEL_FRACTION = 0.5f

/** Labels whose spoke is this close to vertical are centred on it, above or below the circle. */
private const val CENTRED_SINE = 0.3f

/** [radius] out from this point at [angle] (radians, 0 at twelve o'clock, clockwise). */
private fun XY.around(radius: Float, angle: Float) = XY(x + sin(angle) * radius, y - cos(angle) * radius)

/** Where a polar chart sits: its centre and radius, and its labels as they fit. */
private class PolarFrame(val center: XY, val radius: Float, val texts: List<String>)

/**
 * Centres the chart and fits its radius inside the room its labels leave. A label beside the circle
 * may take at most [POLAR_LABEL_FRACTION] of the width, one above or below it (centred on a nearly
 * vertical spoke) [CENTRED_LABEL_FRACTION]; longer ones are cut with an ellipsis, or left out when
 * fewer than three characters would survive. The circle gives up the widest side label on each
 * side and the tallest label above and below, plus [inset] (a marker's radius). Label i sits at
 * angle `i·turn/n`. Null when no positive radius is left.
 */
private fun polarFrame(names: List<String>, size: Extent, measure: TextMeasure, m: ChartMetrics, inset: Float): PolarFrame? {
    if (!(size.width > 0f) || !(size.height > 0f)) return null
    val step = TURN / names.size
    val beside = names.indices.map { i -> abs(sin(i * step)) > CENTRED_SINE }
    val texts = names.mapIndexed { i, name ->
        val budget = size.width * (if (beside[i]) POLAR_LABEL_FRACTION else CENTRED_LABEL_FRACTION) - m.labelGap
        if (name.isEmpty() || budget <= 0f) "" else fitLabel(name, budget, TextKind.TickX, measure)
    }
    val extents = texts.map { if (it.isEmpty()) null else measure.measure(it, TextKind.TickX) }
    val padW = extents.filterIndexed { i, _ -> beside[i] }.filterNotNull().maxOfOrNull { it.width }?.plus(m.labelGap) ?: 0f
    val padH = extents.filterNotNull().maxOfOrNull { it.height }?.plus(m.labelGap) ?: 0f
    val radius = min(size.width / 2f - padW, size.height / 2f - padH) - inset
    if (!(radius > 0f)) return null
    return PolarFrame(XY(size.width / 2f, size.height / 2f), radius, texts)
}

/**
 * A label outside the circle at [angle], aligned so it reads away from the centre, then pulled
 * inside the chart if any of it would fall outside.
 */
private fun polarLabel(key: String, text: String, at: XY, angle: Float, size: Extent, measure: TextMeasure): TextNode {
    val s = sin(angle)
    val c = cos(angle)
    val h = when {
        s > CENTRED_SINE -> HAlign.Start
        s < -CENTRED_SINE -> HAlign.End
        else -> HAlign.Center
    }
    val v = when {
        c > 0.3f -> VAlign.Bottom
        c < -0.3f -> VAlign.Top
        else -> VAlign.Center
    }
    val e = measure.measure(text, TextKind.TickX)
    val left = when (h) {
        HAlign.Start -> at.x
        HAlign.Center -> at.x - e.width / 2f
        HAlign.End -> at.x - e.width
    }
    val top = when (v) {
        VAlign.Top -> at.y
        VAlign.Center -> at.y - e.height / 2f
        VAlign.Bottom -> at.y - e.height
    }
    val dx = max(0f, -left) - max(0f, left + e.width - size.width)
    val dy = max(0f, -top) - max(0f, top + e.height - size.height)
    return TextNode(key, text, XY(at.x + dx, at.y + dy), h, v, TextKind.TickX)
}

/** A ring of [segments] rules round [center], so the grid style draws it. */
private fun ring(key: String, center: XY, radius: Float, segments: Int): List<RuleNode> = List(segments) { i ->
    RuleNode("$key:$i", center.around(radius, i * TURN / segments), center.around(radius, (i + 1) * TURN / segments), RuleKind.GridY)
}

/**
 * A radar: spokes ([RuleKind.GridX]) at axis angles, [RadarChart.rings] polygons ([RuleKind.GridY]),
 * axis labels outside the rim, and per series an [AreaNode] (fill), an edge [LineNode] and a marker
 * [DotNode] per defined vertex. Fills come first, then edges, then markers, so no fill covers
 * another series' edge.
 *
 * - **Missing values.** A non-finite value leaves its vertex undefined (NaN), so the closed edge and
 *   the fill break there exactly as a line breaks at a gap: the edge stops at the neighbouring
 *   spokes and the fill, a fan from the centre (each run of defined vertices is closed through the
 *   centre), covers only the wedges between defined neighbours. No marker, no focus point. This is
 *   chosen over a polygon through the defined vertices only, whose chord across the missing spoke
 *   would read as a value there. A real 0 is different: its vertex sits on the centre, the edge
 *   runs into it and out again, and it has a marker and a focus point.
 * - **Range.** A vertex's radius is its value over [RadarChart.max], clamped to 0..1: negatives on
 *   the centre, values above the maximum on the rim. The focus point keeps the raw value.
 * - **Focus.** [FocusMode.NearestXY]: the nearest defined vertex within reach. Its label is the
 *   axis, its value raw, `share` NaN and `domainX` null.
 * - **Empty.** Fewer than three axes, or no room for a positive radius (labels are cut first), give
 *   an empty scene.
 */
internal fun compileRadar(spec: RadarChart, size: Extent, measure: TextMeasure, m: ChartMetrics): ChartScene {
    val n = spec.axes.size
    if (n < 3) return emptyScene(size, spec, FocusMode.NearestXY)
    val frame = polarFrame(spec.axes, size, measure, m, inset = max(0f, m.markerRadius))
        ?: return emptyScene(size, spec, FocusMode.NearestXY)
    val center = frame.center
    val radius = frame.radius
    val step = TURN / n
    val format = spec.valueFormat ?: Formats.Readable

    val guides = mutableListOf<Node>()
    for (k in 1..spec.rings) {
        val r = radius * k / spec.rings
        for (i in 0 until n) guides += RuleNode("ring:$k:$i", center.around(r, i * step), center.around(r, (i + 1) * step), RuleKind.GridY)
    }
    for (i in 0 until n) guides += RuleNode("spoke:$i", center, center.around(radius, i * step), RuleKind.GridX)
    val labels = frame.texts.mapIndexedNotNull { i, text ->
        if (text.isEmpty()) null else polarLabel("axis:$i", text, center.around(radius + m.labelGap, i * step), i * step, size, measure)
    }

    val fills = mutableListOf<Node>()
    val edges = mutableListOf<Node>()
    val markers = mutableListOf<Node>()
    val points = mutableListOf<FocusPoint>()
    spec.series.forEachIndexed { s, series ->
        val vertices = List(n) { i ->
            val v = series.values.getOrNull(i) ?: Double.NaN
            if (!v.isFinite()) XY(center.x, Float.NaN)
            else center.around(radius * (v / spec.max).coerceIn(0.0, 1.0).toFloat(), i * step)
        }
        val closed = vertices + vertices[0]
        fills += AreaNode("radar:$s", closed, List(closed.size) { center }, s, Curve.Linear)
        edges += LineNode("radar-edge:$s", closed, s, Curve.Linear, isAreaEdge = true)
        vertices.forEachIndexed { i, p ->
            if (!p.isDefined) return@forEachIndexed
            val v = series.values[i]
            markers += DotNode("radar-dot:$s:$i", p, m.markerRadius, s, i, isMarker = true)
            points += FocusPoint(s, i, MarkKind.Dot, p.x, p.y, i.toFloat(), spec.axes[i], v, format.format(v))
        }
    }
    val plot = Bounds(center.x - radius, center.y - radius, center.x + radius, center.y + radius)
    return ChartScene(
        size, plot, center.y, spec.legend(), guides, fills + edges + markers, labels, points,
        FocusMode.NearestXY, spec.description, origin = center,
    )
}

/**
 * A rose: [RoseChart.rings] circles ([RuleKind.GridY]) at area-true steps of the value, labels
 * outside the rim at each wedge's middle, and an [ArcNode] petal per defined value.
 *
 * - **Geometry.** Wedge i spans `i·step ± step/2` (wedge 0 centred on twelve o'clock), less half
 *   [ChartMetrics.pieGap] (measured along the middle of the ring, as a pie's) on each side. A
 *   petal runs from the inner radius ([ChartMetrics.roseInnerRatio] of the radius) to the radius
 *   that makes its area out from the inner radius proportional to its value over the maximum,
 *   clamped to 0..1: negatives draw as zero, values above the maximum reach the rim.
 * - **Missing.** A non-finite value has no node and no focus point: the wedge is empty and a finger
 *   over it focuses nothing.
 * - **Zero.** A zero (or negative) petal keeps its node, with no painted area (so motion grows it if
 *   it becomes positive), and its focus point: pressing its wedge shows "0".
 * - **Hit.** An [ArcHit] over the wedge's painted angles, from the inner radius out to the painted
 *   radius or [ChartMetrics.petalMinHit] beyond the inner radius, whichever is further, never past
 *   the rim. The centre hole, the gaps between wedges and the label ring hit nothing.
 * - **Focus.** [FocusMode.Containment]; a [MarkKind.Petal] point anchored mid-way along its hit
 *   region, labelled with the wedge's label, with the raw value, `share` NaN and `domainX` null.
 * - **Empty.** No labels, or no room for a positive radius (labels are cut first), give an empty
 *   scene.
 */
internal fun compileRose(spec: RoseChart, size: Extent, measure: TextMeasure, m: ChartMetrics): ChartScene {
    val n = spec.labels.size
    if (n == 0) return emptyScene(size, spec, FocusMode.Containment)
    val frame = polarFrame(spec.labels, size, measure, m, inset = 0f) ?: return emptyScene(size, spec, FocusMode.Containment)
    val center = frame.center
    val radius = frame.radius
    val inner = radius * m.roseInnerRatio.coerceIn(0f, 1f)
    val top = spec.max ?: spec.values.filter { it.isFinite() }.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
    val step = TURN / n
    val mid = (inner + radius) / 2f
    val pad = if (mid > 0f) min(max(0f, m.pieGap) / mid / 2f, step / 4f) else 0f
    val format = spec.valueFormat ?: Formats.Readable
    // Area-true: the petal's area out from the inner radius, not its length, is proportional.
    fun petal(f: Double): Float = sqrt(inner * inner + (radius * radius - inner * inner) * f.coerceIn(0.0, 1.0).toFloat())

    val guides = mutableListOf<Node>()
    for (k in 1..spec.rings) guides += ring("ring:$k", center, petal(k / spec.rings.toDouble()), 72)
    val labels = frame.texts.mapIndexedNotNull { i, text ->
        if (text.isEmpty()) null else polarLabel("label:$i", text, center.around(radius + m.labelGap, i * step), i * step, size, measure)
    }
    val marks = mutableListOf<Node>()
    val points = mutableListOf<FocusPoint>()
    spec.values.forEachIndexed { i, v ->
        if (!v.isFinite()) return@forEachIndexed
        val a0 = i * step - step / 2f + pad
        val a1 = i * step + step / 2f - pad
        val outer = petal(v / top)
        marks += ArcNode("petal:$i", center, inner, outer, a0, a1, i, i)
        val reach = min(radius, max(outer, inner + m.petalMinHit))
        val at = center.around((inner + reach) / 2f, i * step)
        points += FocusPoint(
            i, i, MarkKind.Petal, at.x, at.y, i * step, spec.labels[i], v, format.format(v),
            hit = ArcHit(center, inner, reach, a0, a1),
        )
    }
    val plot = Bounds(center.x - radius, center.y - radius, center.x + radius, center.y + radius)
    return ChartScene(size, plot, center.y, spec.legend(), guides, marks, labels, points, FocusMode.Containment, spec.description, origin = center)
}
