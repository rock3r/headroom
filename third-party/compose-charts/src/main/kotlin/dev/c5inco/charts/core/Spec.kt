package dev.c5inco.charts.core

import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** What to draw. A chart is a declaration; [compile] turns it into a [ChartScene]. */
sealed interface ChartSpec {
    val description: String?
}

/**
 * What a screen reader says for the chart: [ChartSpec.description], or the series names. A
 * [HeatmapChart] has no series and says "Heatmap"; give it a description.
 */
fun ChartSpec.contentDescription(): String {
    description?.let { return it }
    if (this is HeatmapChart) return "Heatmap"
    val names = legend().map { it.name }
    val annotations = if (this is CartesianChart) {
        marks.mapNotNull {
            when (it) {
                is XRuleMark -> it.label?.takeIf(String::isNotBlank)
                is XBandMark -> it.label?.takeIf(String::isNotBlank)
                else -> null
            }
        }
    } else emptyList()
    return (names + annotations).joinToString(prefix = "Chart: ")
}

/**
 * Values aligned to the x positions of the chart's axis: its categories, or the values or times of
 * a continuous axis. A non-finite value (`Double.NaN`) is missing: lines and areas break around it
 * and bars skip it. [color] overrides the palette slot.
 *
 * [colorBelow] is the palette slot for the part of a [AreaMark.diverging] area below zero; null
 * takes the slot after the series' own. Other marks ignore it.
 */
data class Series(val name: String, val values: List<Double>, val color: Int? = null, val colorBelow: Int? = null)

data class Datum(val x: Double, val y: Double)

/** Free x/y points, for dots on a [NumericAxis] or [TimeAxis] (x in epoch milliseconds). */
data class XYSeries(val name: String, val points: List<Datum>, val color: Int? = null)

sealed interface XAxis {
    /**
     * False hides tick labels, vertical grid lines and the axis line, skips measuring labels, and
     * gives their space to the plot. With both axes hidden the plot reaches the chart's edge, inset
     * only by [ChartMetrics.strokeInset] (or a marker's or dot's radius): a sparkline.
     */
    val visible: Boolean
}

/** Discrete, evenly spaced positions. Bars get bands; lines and areas run edge to edge. */
data class CategoryAxis(val labels: List<String>, override val visible: Boolean = true) : XAxis

/**
 * A continuous numeric x axis. [values] are the x positions series values align to, in ascending
 * order; dots carry their own x and may leave it empty. Bars take their width from the smallest
 * gap between values. Positions and explicit [min]/[max] bounds must be finite; invalid values
 * throw [IllegalArgumentException] at construction. Represent a missing aligned sample with a
 * non-finite series value, while keeping its x position finite.
 */
data class NumericAxis(
    val values: List<Double> = emptyList(),
    val min: Double? = null,
    val max: Double? = null,
    val tickCount: Int = 5,
    val format: ValueFormat? = null,
    /** How tooltips print an x value. Defaults to [Formats.Readable]. */
    val pointFormat: ValueFormat? = null,
    override val visible: Boolean = true,
) : XAxis {
    init {
        values.forEachIndexed { index, value ->
            require(value.isFinite()) { "NumericAxis.values[$index] must be finite, was $value" }
        }
        require(min == null || min.isFinite()) { "NumericAxis.min must be finite when specified, was $min" }
        require(max == null || max.isFinite()) { "NumericAxis.max must be finite when specified, was $max" }
    }

    internal val minStep: Double by lazy(LazyThreadSafetyMode.PUBLICATION) { computeMinStep(values) }
}

/**
 * A continuous time axis. [times] are the instants series values align to, in ascending order.
 * Ticks land on calendar boundaries (hours, days, months, years) in [zone], and labels show only
 * what changed: `Oct 5, 03:00, 06:00` or `Jul, Aug, Sep`. Formats receive epoch milliseconds.
 */
data class TimeAxis(
    val times: List<Instant>,
    val min: Instant? = null,
    val max: Instant? = null,
    val tickCount: Int = 5,
    val format: ValueFormat? = null,
    /** How tooltips print a time. Defaults to a pattern matched to the data's spacing. */
    val pointFormat: ValueFormat? = null,
    val zone: ZoneId = ZoneId.systemDefault(),
    val locale: Locale = Locale.getDefault(),
    override val visible: Boolean = true,
) : XAxis {
    internal val epochMillisArray: DoubleArray by lazy(LazyThreadSafetyMode.PUBLICATION) {
        DoubleArray(times.size) { times[it].toEpochMilli().toDouble() }
    }

    internal val minStep: Double by lazy(LazyThreadSafetyMode.PUBLICATION) { computeMinStep(epochMillisArray) }

    internal val defaultTickFormat: ValueFormat by lazy(LazyThreadSafetyMode.PUBLICATION) {
        TimeTicks.tickFormat(zone, locale)
    }

    internal val defaultPointFormat: ValueFormat by lazy(LazyThreadSafetyMode.PUBLICATION) {
        TimeTicks.pointFormat(if (minStep > 0.0) minStep else 86_400_000.0, zone, locale)
    }
}

data class ValueAxis(
    val min: Double? = null,
    val max: Double? = null,
    /** Null: include zero when a mark has a length (bars, areas). */
    val includeZero: Boolean? = null,
    val tickCount: Int = 5,
    val format: ValueFormat? = null,
    /** How tooltips print values. Defaults to [Formats.Readable]. */
    val valueFormat: ValueFormat? = null,
    /**
     * False hides tick labels and horizontal grid lines, and fits the domain tightly to the data
     * rather than rounding it out to nice ticks; flat data sits in the middle of the plot. With a
     * hidden x axis too, this is a sparkline.
     */
    val visible: Boolean = true,
    /**
     * Fraction of the data range added above and below, so a line never touches the plot's edges.
     * Applied to the data extent first, then [includeZero], then [min]/[max], then nice rounding,
     * so explicit limits always win. When zero is included ([includeZero] true, or bars and areas
     * by default), padding never carries the domain across zero on a side the data does not
     * cross: all-positive data keeps its floor at 0 and all-negative data its ceiling at 0, so
     * bars and areas sit on the plot edge; only the far side is padded. Without zero, both sides
     * are padded, so a line starting at 0 dips below it. Flat data has no range to pad. Zero (the
     * default) changes nothing.
     */
    val padding: Double = 0.0,
)

enum class BarLayout { Grouped, Stacked }

enum class Markers { None, Last, All }

/**
 * How a [LineMark] or non-stacked [AreaMark] handles non-finite (`Double.NaN`) values.
 *
 * Missing values never get a [FocusPoint] or a marker dot, whatever the gap policy: only the drawn
 * line or area changes.
 */
sealed interface Gaps {
    /** A non-finite value breaks the line or area into separate runs. */
    data object Break : Gaps

    /**
     * Non-finite values are skipped and their finite neighbours are joined directly, for overlaid
     * series sampled at different times and aligned to one merged x axis with `NaN` holes.
     */
    data object Connect : Gaps

    /**
     * Connects across a run of non-finite values when the x distance between the finite points on
     * either side is at most [maxGap] (in x-axis units: numeric span, epoch milliseconds on a
     * [TimeAxis], or category steps on a [CategoryAxis]), and breaks across longer outages.
     */
    data class ConnectWithin(val maxGap: Double) : Gaps {
        constructor(maxGap: java.time.Duration) : this(maxGap.toMillis().toDouble())

        init {
            require(maxGap.isFinite() && maxGap >= 0.0) {
                "Gaps.ConnectWithin.maxGap must be finite and non-negative, was $maxGap"
            }
        }
    }
}

/** How a [LineMark]'s stroke is patterned. Dash and dot lengths scale with the line's stroke width. */
enum class LinePattern { Solid, Dashed, Dotted }

/**
 * A mark is one visual encoding of data. Marks compose: a chart may hold bars, a line and a
 * reference rule at once, all sharing scales.
 */
sealed interface Mark

data class BarMark(val series: List<Series>, val layout: BarLayout = BarLayout.Grouped) : Mark

data class LineMark(
    val series: List<Series>,
    val curve: Curve? = null,
    val markers: Markers = Markers.None,
    val gaps: Gaps = Gaps.Break,
    val pattern: LinePattern = LinePattern.Solid,
) : Mark

/**
 * Filled areas from the zero baseline, stacked by default.
 *
 * [diverging] colours each series by the sign of its data, for profit and loss: the fill and the
 * edge stroke above zero take the series colour and those below zero take [Series.colorBelow],
 * split exactly where the drawn curve crosses zero, between samples too. It needs `stacked = false`:
 * in a stack, a segment's position says nothing about the sign of its own value.
 *
 * [base] is what the fill closes on: zero (the default), or the bottom of the plot
 * ([AreaBase.PlotBottom]) for a silhouette such as terrain, which should read as solid ground
 * below the line whatever its sign. It needs `stacked = false` and `diverging = false`.
 *
 * [gaps] controls whether non-finite values break the area ([Gaps.Break], the default) or connect
 * neighbouring finite points ([Gaps.Connect], [Gaps.ConnectWithin]). Connecting gaps needs
 * `stacked = false`, since stacked layers must share x positions with the series below.
 */
data class AreaMark(
    val series: List<Series>,
    val stacked: Boolean = true,
    val curve: Curve? = null,
    val diverging: Boolean = false,
    val base: AreaBase = AreaBase.Zero,
    val gaps: Gaps = Gaps.Break,
) : Mark {
    init {
        require(!(diverging && stacked)) {
            "AreaMark(diverging = true) colours each series by its own sign, which a stack hides; pass stacked = false"
        }
        require(base == AreaBase.Zero || !stacked) {
            "AreaMark(base = PlotBottom) fills each series down to the plot, which a stack's layers cannot share; pass stacked = false"
        }
        require(base == AreaBase.Zero || !diverging) {
            "AreaMark(base = PlotBottom) has no zero to split its fill at; diverging areas fill to zero"
        }
        require(gaps == Gaps.Break || !stacked) {
            "AreaMark(gaps = $gaps) connects across missing x positions, which a stack's layers cannot share; pass stacked = false"
        }
    }
}

/**
 * What a nonstacked [AreaMark]'s fill closes on. Only the closure changes: the line, focus points,
 * raw values, value domain and zero baseline guide are the same either way.
 *
 * - [Zero]: the fill runs from the line to value zero (clamped into the plot), so it reads as a
 *   quantity: above zero it hangs down to zero, below zero it rises up to it. Its gradient fades
 *   toward zero and its entrance grows out of zero.
 * - [PlotBottom]: the fill runs from the line down to the bottom edge of the plot (the price area's
 *   bottom, above a candle volume strip), on screen, whatever the sign of the data and the
 *   direction of the value axis: a silhouette, like terrain below a ride that dips under sea level.
 *   Its gradient fades toward the plot's bottom and its entrance rises from it.
 */
enum class AreaBase { Zero, PlotBottom }

data class DotMark(val series: List<XYSeries>) : Mark

/**
 * A reference line at value [y], such as a target or an average: horizontal in a [CartesianChart],
 * vertical in a [HorizontalBarChart], where the value axis runs across.
 */
data class RuleMark(val y: Double, val label: String? = null) : Mark

/**
 * A vertical reference line at [x] on a continuous x axis: a value on a [NumericAxis], or epoch
 * milliseconds on a [TimeAxis]. A [CategoryAxis] has no numeric x and is rejected by
 * [CartesianChart] with `require`.
 *
 * [color] is an optional palette slot (like [Series.color]), so resets for overlaid series can
 * match their series colour; null uses the style's x-rule colour. [pattern] overrides the style's
 * x-rule dash pattern for this mark.
 *
 * Drawn across the full height of the plot, with [label] (if any) at the top of the plot, shifted
 * or omitted when it would collide with another reference label. A rule outside the visible x
 * domain is culled. An `XRuleMark` is an annotation: it is not focusable by scrubbing and does not
 * appear in [legend], and is included in the default [contentDescription] only when [label] is set.
 */
data class XRuleMark(
    val x: Double,
    val label: String? = null,
    val color: Int? = null,
    val pattern: LinePattern? = null,
) : Mark {
    constructor(
        x: Instant,
        label: String? = null,
        color: Int? = null,
        pattern: LinePattern? = null,
    ) : this(x.toEpochMilli().toDouble(), label, color, pattern)

    init {
        require(x.isFinite()) { "XRuleMark.x must be finite, was $x" }
    }
}

/**
 * A shaded vertical band from [from] to [to] on a continuous x axis: values on a [NumericAxis], or
 * epoch milliseconds on a [TimeAxis]. A [CategoryAxis] has no numeric x and is rejected by
 * [CartesianChart] with `require`.
 *
 * [color] is an optional palette slot (like [Series.color]), painted at the style's band alpha;
 * null uses the style's band fill colour.
 *
 * Drawn behind data marks across the full height of the plot, with [label] (if any) centred at the
 * top of the visible band when it fits without colliding. Bands outside the visible x domain are
 * culled, and partially visible bands are clipped to the plot's x range. Like [XRuleMark], a band
 * is an annotation: it is not focusable by scrubbing, does not appear in [legend], and is included
 * in the default [contentDescription] only when [label] is set.
 */
data class XBandMark(
    val from: Double,
    val to: Double,
    val label: String? = null,
    val color: Int? = null,
) : Mark {
    constructor(
        from: Instant,
        to: Instant,
        label: String? = null,
        color: Int? = null,
    ) : this(from.toEpochMilli().toDouble(), to.toEpochMilli().toDouble(), label, color)

    init {
        require(from.isFinite() && to.isFinite()) { "XBandMark bounds must be finite, was $from..$to" }
        require(from <= to) { "XBandMark.from ($from) must not exceed to ($to)" }
    }
}

/**
 * One trading period: its open, high, low and close prices, and its traded [volume] if known.
 *
 * Feeds are messy, so the compiler draws every candle through [normalized] rather than rejecting
 * data: a period with any price that is not finite is missing, and inconsistent extremes are
 * widened to cover the body. Prices need not be positive.
 */
data class Ohlc(
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double? = null,
) {
    /** Closed below its open. A flat period (a doji) counts as up. */
    val isDown: Boolean get() = close < open

    /**
     * The period as a chart draws and reports it, or null when it is missing.
     *
     * - Any of open, high, low or close not finite: missing (null). It gets no body, wick, volume
     *   bar or focus point, and its x position stays empty.
     * - [high] below [low], or [open] or [close] outside them: normalized, not rejected. The high
     *   becomes the largest of the four prices and the low the smallest, so a swapped high and low
     *   are swapped back and a close past the high (a live period whose high lags its last trade)
     *   extends the wick. Open and close are kept, so direction never changes.
     * - [volume] not finite or negative: no volume (null); the prices still draw.
     */
    fun normalized(): Ohlc? {
        if (!open.isFinite() || !high.isFinite() || !low.isFinite() || !close.isFinite()) return null
        val top = maxOf(open, high, low, close)
        val bottom = minOf(open, high, low, close)
        val v = volume?.takeIf { it.isFinite() && it >= 0.0 }
        return if (top == high && bottom == low && v == volume) this else Ohlc(open, top, bottom, close, v)
    }
}

/**
 * Candlesticks: one [Ohlc] per x position of the axis, aligned as [Series] values are, on a
 * category, numeric or time axis. Each takes a slot like a bar (on a continuous axis, sized by the
 * smallest gap between x values, so weekends and other gaps stay empty). Candles are prices, not
 * lengths: the value axis covers their lows and highs and never reaches for zero on their behalf.
 * They share the value scale with any lines, areas, bars and rules in the chart, so a moving
 * average or a previous close lines up with them.
 *
 * [volumeFraction] above zero opts in to a volume strip: the bottom [volumeFraction] of the plot,
 * below a gap ([ChartMetrics.volumeGap]), with bars on their own linear scale from zero to the
 * largest volume. Volume never affects the price axis, which keeps the rest of the plot. The strip
 * is reserved whenever it is asked for, even when no candle has a volume, so a chart keeps its
 * layout across data. At most one candle mark in a chart may have a strip.
 *
 * Several candle marks may share a chart (each is its own series, keyed by [name], which must be
 * unique among them). [color] is the mark's legend slot; candles themselves are painted by
 * direction, not by palette.
 */
data class CandleMark(
    val candles: List<Ohlc>,
    val name: String = "Price",
    val volumeFraction: Float = 0f,
    val color: Int? = null,
) : Mark {
    init {
        require(volumeFraction >= 0f && volumeFraction < 1f) { "CandleMark.volumeFraction must be in [0, 1), was $volumeFraction" }
    }
}

data class CartesianChart(
    val x: XAxis,
    val marks: List<Mark>,
    val y: ValueAxis = ValueAxis(),
    override val description: String? = null,
) : ChartSpec {
    init {
        val candles = marks.filterIsInstance<CandleMark>()
        require(candles.map { it.name }.toSet().size == candles.size) {
            "Candle marks in one chart need distinct names; got ${candles.map { it.name }}"
        }
        require(candles.count { it.volumeFraction > 0f } <= 1) {
            "Only one candle mark per chart can have a volume strip"
        }
        require(x !is CategoryAxis || marks.none { it is XRuleMark || it is XBandMark }) {
            "XRuleMark and XBandMark need a NumericAxis or TimeAxis; categories have no numeric x"
        }
    }

    internal val prep: CartesianPrep by lazy(LazyThreadSafetyMode.PUBLICATION) { CartesianPrep(this) }
}

/**
 * Bars that grow sideways: one row per category, down the left, and the value axis along the
 * bottom. Rows read well when labels are long or there are many categories.
 *
 * A restricted chart rather than an orientation switch: it holds one [bars] mark (grouped, or
 * stacked with negative values stacking left of zero) and reference [rules], and nothing else.
 * Lines, areas and dots have no meaning across rows, so there is nowhere to put them and nothing
 * is ever silently dropped.
 *
 * [categories] hides its labels and axis line with `visible = false`. [value] works as on a
 * [CartesianChart], turned sideways: ticks along the bottom, vertical grid lines (drawn when the
 * style shows vertical grid lines), and the zero baseline as a vertical line. Category labels
 * longer than 40% of the width are cut short with an ellipsis; the tooltip keeps the full label.
 */
data class HorizontalBarChart(
    val categories: CategoryAxis,
    val bars: BarMark,
    val value: ValueAxis = ValueAxis(),
    val rules: List<RuleMark> = emptyList(),
    override val description: String? = null,
) : ChartSpec

/**
 * One tile of a [TreemapChart]. Two channels, never mixed: [size] sets its area (a part of the
 * whole) and [value] its colour on the style's diverging scale (a market heatmap: market cap and
 * today's change).
 *
 * [id] is the leaf's identity, unique among every leaf of the chart: motion pairs tiles across
 * updates by it, and a focus reports it as [FocusPoint.id], so a tap resolves without reading the
 * [label]. Labels are display text and may repeat (two groups may each have an "Other").
 *
 * A [size] that is zero, negative or not finite takes no area: the leaf is left out of the scene
 * (no tile, no label, no focus point) and out of the whole that shares are taken of. A NaN [value]
 * is "no value": the tile takes the scale's neutral colour and the tooltip shows no value.
 */
data class TreemapLeaf(
    val id: String,
    val size: Double,
    val value: Double = Double.NaN,
    val label: String = id,
    /** A second line under the label. Null prints [value] with the chart's `valueFormat`. */
    val sublabel: String? = null,
)

/**
 * A group of [leaves], tiled together under an optional header with its [label]. [id] is unique
 * among the chart's groups (a group and a leaf may share an id). [color] is the group's palette
 * slot as `legend()` reports it, like a series' slot; tiles themselves are coloured by value.
 */
data class TreemapGroup(
    val id: String,
    val leaves: List<TreemapLeaf>,
    val label: String = id,
    val color: Int? = null,
)

/**
 * A two-level squarified treemap: groups are tiled first, then each group's leaves inside it, both
 * largest first (ties broken by id, so layout never depends on input order). A market heatmap is
 * sectors of companies, sized by market cap and coloured by today's change.
 *
 * Groups with no leaf that takes area are left out, and a chart with none gives an empty scene.
 * Every group id and every leaf id must be unique.
 */
data class TreemapChart(
    val groups: List<TreemapGroup>,
    /** A header strip with each group's label, where it fits. */
    val groupLabels: Boolean = true,
    /** Prints [TreemapLeaf.value] in tiles and tooltips. Defaults to [Formats.Readable]. */
    val valueFormat: ValueFormat? = null,
    /** Prints [TreemapLeaf.size] in tooltips. Defaults to [Formats.Readable]. */
    val sizeFormat: ValueFormat? = null,
    override val description: String? = null,
) : ChartSpec {
    init {
        val groupIds = groups.map { it.id }
        require(groupIds.toSet().size == groupIds.size) {
            "TreemapChart group ids must be unique; repeated: ${groupIds.repeated()}"
        }
        val leafIds = groups.flatMap { g -> g.leaves.map { it.id } }
        require(leafIds.toSet().size == leafIds.size) {
            "TreemapChart leaf ids must be unique across the chart; repeated: ${leafIds.repeated()}"
        }
    }
}

private fun List<String>.repeated(): List<String> = groupingBy { it }.eachCount().filterValues { it > 1 }.keys.toList()

/**
 * A grid of cells coloured by value on a sequential scale, such as a practice calendar: one column
 * per week and one row per weekday. [values] is column-major, `values[column][row]`.
 *
 * - **Shape.** [values] has one list per entry of [columns]. A column may be shorter than [rows]
 *   (a week still in progress): its missing tail is missing data. A column longer than [rows], or
 *   a column count that differs from [columns], fails a `require`: nothing is silently dropped.
 *   [cellLabels], when given, follows the same rules; a cell without a label is described as
 *   "column row" (blank parts left out).
 * - **Missing.** A value that is not finite (NaN, the infinities) is no data, such as a day still
 *   to come. Its cell is drawn in the style's empty colour, distinct from zero, and has no focus
 *   point: a finger over it focuses nothing and ticks nothing.
 * - **Zero and negatives.** Zero is a real value: the lowest colour on the scale, focusable, and
 *   printed as itself ("0 XP"). A negative value is also real and printed as itself, but a
 *   sequential scale has no colour below its lowest, so it takes intensity 0, the colour of zero.
 *   Use a diverging scale ([TreemapChart]) for signed data.
 * - **Intensity.** A cell's colour position is its value over [max], clamped to 0..1. [max] null
 *   takes the largest finite value. When that maximum is zero or negative, or [max] is not a
 *   positive finite number, every value cell has intensity 0 (the lowest colour), never NaN.
 *
 * Column labels are drawn above the grid at their column's left edge; blank labels are skipped, so
 * a calendar can name only the first week of each month, and a label that would run into the one
 * before it is left out. Row labels sit on the left; blank ones are skipped too.
 */
data class HeatmapChart(
    val columns: List<String>,
    val rows: List<String>,
    val values: List<List<Double>>,
    val max: Double? = null,
    val cellLabels: List<List<String>>? = null,
    /** Prints values in tooltips. Defaults to [Formats.Readable]. */
    val valueFormat: ValueFormat? = null,
    override val description: String? = null,
) : ChartSpec {
    init {
        require(values.size == columns.size) {
            "HeatmapChart needs one values column per column label: ${values.size} columns of values for ${columns.size} labels"
        }
        values.forEachIndexed { c, column ->
            require(column.size <= rows.size) { "HeatmapChart column $c has ${column.size} values for ${rows.size} rows" }
        }
        if (cellLabels != null) {
            require(cellLabels.size == columns.size) {
                "HeatmapChart.cellLabels needs one list per column: ${cellLabels.size} for ${columns.size} columns"
            }
            cellLabels.forEachIndexed { c, column ->
                require(column.size <= rows.size) { "HeatmapChart.cellLabels column $c has ${column.size} labels for ${rows.size} rows" }
            }
        }
    }
}

/**
 * A radar (spider) chart: one spoke per axis, clockwise from twelve o'clock, and each series a
 * closed polygon through its values. Values run from 0 at the centre to [max] at the rim; [rings]
 * concentric polygons mark the steps between.
 *
 * - **Shape.** A series has at most one value per axis; a shorter series is missing its tail.
 *   Longer, a [max] that is not a positive finite number, or negative [rings], fails a `require`.
 * - **Missing.** A value that is not finite (NaN, the infinities) is no data, never zero: its
 *   vertex is left out, so the polygon's edge and fill break there (the library's gap semantics),
 *   and it has no marker and no focus point. A real 0 sits on the centre: the edge runs into it and
 *   back out, with a marker there, and it is focusable. See `compileRadar`.
 * - **Range.** Negative values are drawn at the centre and values above [max] at the rim; the
 *   tooltip prints the raw value.
 * - Fewer than three axes, or no room for a radius, give an empty scene.
 */
data class RadarChart(
    val axes: List<String>,
    val series: List<Series>,
    val max: Double = 1.0,
    val rings: Int = 4,
    /** Prints values in tooltips. Defaults to [Formats.Readable]. */
    val valueFormat: ValueFormat? = null,
    override val description: String? = null,
) : ChartSpec {
    init {
        require(max.isFinite() && max > 0.0) { "RadarChart.max must be a positive finite number, was $max" }
        require(rings >= 0) { "RadarChart.rings must not be negative, was $rings" }
        series.forEach { s ->
            require(s.values.size <= axes.size) { "RadarChart series ${s.name} has ${s.values.size} values for ${axes.size} axes" }
        }
    }
}

/**
 * A rose (Nightingale) chart: equal wedges round a circle, wedge 0 centred on twelve o'clock and the
 * rest clockwise, one per label. Each petal is area-true: its area out from the empty centre, not
 * its length, is proportional to its value. Wedge i takes palette slot i unless [colors] says
 * otherwise; [rings] guides mark steps of the value.
 *
 * - **Shape.** One value per label, and one colour per label when [colors] is given; anything
 *   else fails a `require`.
 * - **Maximum.** The value that fills a wedge to the rim. Null takes the largest finite value, or
 *   1.0 when there is no positive one. An explicit [max] must be a positive finite number.
 * - **Missing.** A value that is not finite is no data: the wedge is empty and has no focus point.
 * - **Zero.** A real value: it has no painted area but stays focusable through a minimum hit ring
 *   ([ChartMetrics.petalMinHit]), so "0%" can be pressed. Negatives are drawn as zero and values
 *   above [max] reach the rim; the tooltip prints the raw value. A petal is not a part of a whole,
 *   so its focus has no share.
 */
data class RoseChart(
    val labels: List<String>,
    val values: List<Double>,
    val max: Double? = null,
    val colors: List<Int>? = null,
    val rings: Int = 4,
    /** Prints values in tooltips. Defaults to [Formats.Readable]. */
    val valueFormat: ValueFormat? = null,
    override val description: String? = null,
) : ChartSpec {
    init {
        require(values.size == labels.size) { "RoseChart needs one value per label: ${values.size} values for ${labels.size} labels" }
        require(colors == null || colors.size == labels.size) { "RoseChart.colors needs one slot per label: ${colors?.size} for ${labels.size} labels" }
        require(max == null || (max.isFinite() && max > 0.0)) { "RoseChart.max must be null or a positive finite number, was $max" }
        require(rings >= 0) { "RoseChart.rings must not be negative, was $rings" }
    }
}

/**
 * A pie slice. Only positive, finite [value]s take area or have a focus point; zero, negative and
 * missing values stay in the legend but do not contribute to the total.
 */
data class Slice(val label: String, val value: Double, val color: Int? = null)

data class PieChart(
    val slices: List<Slice>,
    /** Largest first, as d3's pie does by default. */
    val sort: Boolean = false,
    val valueFormat: ValueFormat? = null,
    override val description: String? = null,
) : ChartSpec

/** Legend entries in palette order, for building a legend without compiling. */
fun ChartSpec.legend(): List<SeriesInfo> = when (this) {
    is PieChart -> (if (sort) slices.sortedByDescending { it.value } else slices)
        .mapIndexed { i, s -> SeriesInfo(s.label, s.color ?: i) }
    is HorizontalBarChart -> bars.series.mapIndexed { i, s -> SeriesInfo(s.name, s.color ?: i) }
    // One entry per group, in spec order, empty groups included: a tile's series is its group.
    is TreemapChart -> groups.mapIndexed { i, g -> SeriesInfo(g.label, g.color ?: i) }
    // Cells are coloured by value, not by series: the legend is the style's colour scale.
    is HeatmapChart -> emptyList()
    is RadarChart -> series.mapIndexed { i, s -> SeriesInfo(s.name, s.color ?: i) }
    // One entry per wedge: a petal's series is its wedge.
    is RoseChart -> labels.mapIndexed { i, l -> SeriesInfo(l, colors?.get(i) ?: i) }
    is CartesianChart -> {
        val infos = mutableListOf<SeriesInfo>()
        fun add(name: String, color: Int?) { infos += SeriesInfo(name, color ?: infos.size) }
        marks.forEach { mark ->
            when (mark) {
                is BarMark -> mark.series.forEach { add(it.name, it.color) }
                is LineMark -> mark.series.forEach { add(it.name, it.color) }
                is AreaMark -> mark.series.forEach { s ->
                    val slot = s.color ?: infos.size
                    infos += SeriesInfo(s.name, slot, if (mark.diverging) s.colorBelow ?: (slot + 1) else null)
                }
                is DotMark -> mark.series.forEach { add(it.name, it.color) }
                is CandleMark -> infos += SeriesInfo(mark.name, mark.color ?: infos.size, candle = true)
                is RuleMark, is XRuleMark, is XBandMark -> Unit
            }
        }
        infos
    }
}
