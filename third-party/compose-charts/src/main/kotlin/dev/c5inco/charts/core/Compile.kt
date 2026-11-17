package dev.c5inco.charts.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.nextDown
import kotlin.math.nextUp

/** Measures text the way the renderer will draw it. Layout needs label sizes to size margins. */
public fun interface TextMeasure {
    public fun measure(text: String, kind: TextKind): Extent

    /**
     * Vertical ink extent of [text], without the line box's ascent and descent padding, for testing
     * whether stacked single-line labels collide. Defaults to the full line height.
     */
    public fun inkHeight(text: String, kind: TextKind): Float = measure(text, kind).height
}

/**
 * Geometry knobs, in pixels. These change where things are, so they live in layout; colours and
 * stroke widths change only how things look, and stay in the renderer's style.
 */
public data class ChartMetrics(
    val labelGap: Float = 8f,
    /** Fraction of each category band left empty between bars. */
    val bandPadding: Float = 0.3f,
    val bandOuterPadding: Float = 0.15f,
    /** Fraction of each bar slot left empty between bars of one group. */
    val groupPadding: Float = 0.12f,
    val maxBarWidth: Float = Float.POSITIVE_INFINITY,
    /** Gap between stacked segments. */
    val stackGap: Float = 0f,
    val dotRadius: Float = 4f,
    val markerRadius: Float = 3.5f,
    val minLabelSpacing: Float = 12f,
    val pieInnerRatio: Float = 0.6f,
    /** Gap between slices, measured along the middle of the ring. */
    val pieGap: Float = 0f,
    val pieInset: Float = 0f,
    /**
     * The plot's inset on a side whose axis is hidden: half the widest stroke (lines, area edges,
     * wicks, x rules), so a line or rule at the edge of a sparkline is not clipped. Line markers
     * widen it to [markerRadius] and dots to [dotRadius], when a chart has them.
     */
    val strokeInset: Float = 1.5f,
    /**
     * Fraction of each slot left empty between candles, used in place of [bandPadding] when a
     * chart has candles and no bars.
     */
    val candlePadding: Float = 0.3f,
    /** Space between the price area and a [CandleMark]'s volume strip under it. */
    val volumeGap: Float = 4f,
    /** Gap between neighbouring treemap tiles. Tiles on a group's outer edge keep that edge. */
    val tileGap: Float = 1f,
    /** Gap between treemap groups. Groups on the chart's outer edge keep that edge. */
    val groupGap: Float = 4f,
    /**
     * Space between a treemap tile's edge and its text; a group header strip is its label's
     * height plus this much.
     */
    val tilePadding: Float = 4f,
    /** Gap between neighbouring heatmap cells; shrinks to a quarter of a cell's step when cells are tiny. */
    val cellGap: Float = 3f,
    /** The empty centre of a [RoseChart], as a fraction of its radius. */
    val roseInnerRatio: Float = 0.16f,
    /**
     * How far a [RoseChart] petal's hit region reaches out from the inner radius at least, so a zero
     * or tiny petal can still be pressed. Never past the rim.
     */
    val petalMinHit: Float = 24f,
    /** Dash and gap lengths in pixels for a [LinePattern.Dashed] line, scaled from its stroke width. */
    val lineDashOn: Float = 7.5f,
    val lineDashOff: Float = 5f,
    /** Centre-to-centre dot spacing in pixels for a [LinePattern.Dotted] line, scaled from its stroke width. */
    val lineDotSpacing: Float = 6.25f,
)

/**
 * Compiles [spec] into a renderer-neutral [ChartScene] of size [size].
 *
 * When [viewport] is given, [spec] must be a [CartesianChart] with a [NumericAxis] or [TimeAxis]
 * (any other chart or a [CategoryAxis] throws [IllegalArgumentException]): the x scale's domain
 * becomes `[viewport.start, viewport.end]`, data outside the window is culled by binary search on
 * the ascending x positions (keeping one point beyond each edge so lines and areas run to the plot
 * border), dense windows (`visiblePositions > ceil(plot.width)`) are M4-decimated per pixel column
 * (`Markers.All` then places markers on the decimated defined samples), ticks are recomputed for
 * the window, and [ChartScene.clipPlot] is set so marks are clipped to the plot rectangle. When
 * [viewport] is null, no culling or decimation is applied.
 */
public fun compile(
    spec: ChartSpec,
    size: Extent,
    measure: TextMeasure,
    metrics: ChartMetrics = ChartMetrics(),
    viewport: ViewportRange? = null,
): ChartScene {
    if (viewport != null) {
        require(spec is CartesianChart && (spec.x is NumericAxis || spec.x is TimeAxis)) {
            val axisOrKind = if (spec is CartesianChart) spec.x::class.simpleName else spec::class.simpleName
            "A viewport requires a CartesianChart with a NumericAxis or TimeAxis, got $axisOrKind"
        }
    }
    return when (spec) {
        is CartesianChart -> compileCartesian(spec, size, measure, metrics, viewport)
        is HorizontalBarChart -> compileHorizontal(spec, size, measure, metrics)
        is PieChart -> compilePie(spec, size, metrics)
        is TreemapChart -> compileTreemap(spec, size, measure, metrics)
        is HeatmapChart -> compileHeatmap(spec, size, measure, metrics)
        is RadarChart -> compileRadar(spec, size, measure, metrics)
        is RoseChart -> compileRose(spec, size, measure, metrics)
    }
}

/** A scene with nothing to draw in [layout], its plot the whole canvas. */
internal fun emptyScene(size: Extent, spec: ChartSpec, layout: SceneLayout, focusMode: FocusMode, clipPlot: Boolean = false) = ChartScene(
    size = size,
    plot = Bounds(0f, 0f, size.width, size.height),
    layout = layout,
    series = spec.legend(),
    guides = emptyList(),
    marks = emptyList(),
    labels = emptyList(),
    points = emptyList(),
    focusMode = focusMode,
    description = spec.description,
    clipPlot = clipPlot,
)

/** Category labels never take more than this fraction of a horizontal bar chart's width. */
internal const val MAX_LABEL_FRACTION = 0.4f

/** [text], cut short with an ellipsis until it measures at most [maxWidth]; empty if nothing fits. */
internal fun fit(text: String, maxWidth: Float, kind: TextKind, measure: TextMeasure): String {
    if (measure.measure(text, kind).width <= maxWidth) return text
    fun cut(n: Int) = text.take(n).trimEnd() + "\u2026"
    // The longest prefix that fits with its ellipsis.
    var lo = 0
    var hi = text.length - 1
    while (lo < hi) {
        val mid = (lo + hi + 1) / 2
        if (measure.measure(cut(mid), kind).width <= maxWidth) lo = mid else hi = mid - 1
    }
    return if (lo > 0 && measure.measure(cut(lo), kind).width <= maxWidth) cut(lo) else ""
}

/** A cut label must keep at least this many characters before its ellipsis, or it is left out. */
private const val MIN_CUT_CHARS = 3

/** [text] as [fit] cuts it, or empty when the cut would keep fewer than [MIN_CUT_CHARS] characters. */
internal fun fitLabel(text: String, maxWidth: Float, kind: TextKind, measure: TextMeasure): String {
    val fitted = fit(text, maxWidth, kind, measure)
    return if (fitted == text || fitted.length - 1 >= MIN_CUT_CHARS) fitted else ""
}

/**
 * The value scale over a data extent: [ValueAxis.padding] (never across zero on a side the data
 * does not cross, when zero is included), then zero for marks with a length, then explicit limits, then nice ends when the axis is shown. A hidden axis has no ticks to round
 * to, so the data fills the height. Flat data (or equal limits) has no range: on a shown axis it
 * sits at the bottom of a one-unit range, counting up from the value; on a hidden axis (a
 * sparkline) it sits in the middle of the plot.
 *
 * Only both limits, `min` above `max`, reverse the axis. A lone limit is always an end of the
 * domain; one at or past the far end of the domain built so far (a `min` at or above it, a `max`
 * at or below it) puts the free end one unit beyond the limit, so the axis keeps its natural
 * direction (issue #30). Past 2^53, where one unit is lost to rounding, every widening here takes
 * the next representable value instead, so the domain has a span unless it would have to reach
 * past the largest finite double.
 */
internal fun valueScale(axis: ValueAxis, dataLo: Double, dataHi: Double, hasLength: Boolean): LinearScale {
    var lo = dataLo
    var hi = dataHi
    val zero = axis.includeZero ?: hasLength
    if (!lo.isFinite()) { lo = 0.0; hi = 1.0 } else if (axis.padding > 0.0) {
        // With zero in the domain, padding never pushes it across zero on a side the data does
        // not cross, so bars and areas keep their baseline on the plot edge.
        val pad = (hi - lo) * axis.padding
        if (!zero || lo < 0.0) lo -= pad
        if (!zero || hi > 0.0) hi += pad
    }
    if (zero) { lo = min(lo, 0.0); hi = max(hi, 0.0) }
    val minLimit = axis.min
    val maxLimit = axis.max
    when {
        // Both limits are taken as given: the only way to reverse the axis.
        minLimit != null && maxLimit != null -> { lo = minLimit; hi = maxLimit }
        // A lone limit is always an end. At or past the data's far end it would leave an empty or
        // reversed domain, so the free end goes one unit beyond it and the axis keeps its direction.
        minLimit != null -> { lo = minLimit; if (hi <= lo) hi = unitAbove(lo) }
        maxLimit != null -> { hi = maxLimit; if (lo >= hi) lo = unitBelow(hi) }
    }
    // Flat data, or equal limits, has no range: one unit up on a shown axis, one each way on a
    // hidden one. A lone limit is left alone, even at the largest double with no room beyond it.
    if (lo == hi && (minLimit == null) == (maxLimit == null)) {
        if (axis.visible) hi = unitAbove(lo) else { lo = unitBelow(lo); hi = unitAbove(hi) }
    }
    val scale = LinearScale(lo, hi)
    return if (axis.visible && axis.min == null && axis.max == null) scale.nice(axis.tickCount) else scale
}

/**
 * [v] plus one, or the next double up where one unit is too small to change [v] (from about 2^53),
 * so a widened domain has a span. At [Double.MAX_VALUE] there is no finite double above, so [v]
 * stays: an empty domain draws everything mid-plot, where an infinite end would draw nothing.
 */
private fun unitAbove(v: Double): Double = (v + 1.0).let { if (it > v) it else v.nextUp() }.let { if (it.isFinite()) it else v }

/** [v] minus one, or the next double down where one unit is too small to change [v]; never past `-Double.MAX_VALUE`. */
private fun unitBelow(v: Double): Double = (v - 1.0).let { if (it < v) it else v.nextDown() }.let { if (it.isFinite()) it else v }

/** The smallest positive gap between consecutive distinct values, or 0 with fewer than two. */
internal fun computeMinStep(values: List<Double>?): Double {
    if (values == null || values.size < 2) return 0.0
    return computeMinStep(values.toDoubleArray())
}

internal fun computeMinStep(values: DoubleArray): Double {
    if (values.size < 2) return 0.0
    var minGap = Double.POSITIVE_INFINITY
    var prev = Double.NaN
    var ascending = true
    for (i in values.indices) {
        val v = values[i]
        if (!v.isFinite()) continue
        if (!prev.isNaN()) {
            if (v < prev) {
                ascending = false
                break
            }
            val gap = v - prev
            if (gap > 0.0 && gap < minGap) minGap = gap
        }
        prev = v
    }
    if (ascending) return if (minGap.isFinite()) minGap else 0.0
    val sorted = values.filter { it.isFinite() }.distinct().sorted()
    return sorted.zipWithNext { a, b -> b - a }.minOrNull() ?: 0.0
}
