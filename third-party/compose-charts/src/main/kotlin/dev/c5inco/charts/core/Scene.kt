package dev.c5inco.charts.core

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * The renderer-neutral output of the compiler: positioned, keyed primitives plus the semantic
 * points interaction needs. A renderer paints it; it never computes layout.
 *
 * Nodes carry semantic references ([SeriesInfo] index, rule and text kinds) rather than colours or
 * stroke widths. Paint is the renderer's job, which is why one scene can be drawn in very
 * different visual styles.
 */
data class ChartScene(
    val size: Extent,
    /** The plot area. Empty-ish for polar charts, where it is the bounding square. */
    val plot: Bounds,
    /**
     * Where zero sits on the value axis, in pixels, clamped into the plot. Marks grow from here on
     * enter. A y position, except in a [HorizontalBarChart] scene ([FocusMode.NearestY]), whose
     * value axis runs across: there it is an x position.
     */
    val baseline: Float,
    val series: List<SeriesInfo>,
    /** Behind the marks: grid lines, axis lines, the zero baseline. */
    val guides: List<Node>,
    val marks: List<Node>,
    /** In front of the marks: tick labels and reference labels. */
    val labels: List<Node>,
    val points: List<FocusPoint>,
    val focusMode: FocusMode,
    val description: String? = null,
    /**
     * The centre of a polar chart ([RadarChart], [RoseChart]); null for every other scene,
     * including pies. It marks the scene's topology, not just a point:
     *
     * - **Motion.** Marks grow out of it rather than up from [baseline]: polygon vertices and
     *   markers move out from it, petals grow from their inner radius, staggered clockwise from
     *   twelve o'clock on a first appearance as on any later arrival. Lines and areas pair their
     *   points by index across updates, never by sampling along x (a polygon's x runs back on
     *   itself), so a change in the number of axes pads rather than resamples.
     * - **Paint.** An area has no baseline to fade toward, so a gradient fill paints it evenly.
     */
    val origin: XY? = null,
    /**
     * How far outside every painted shape a [FocusMode.Containment] pointer still focuses the
     * nearest one, in pixels. A heatmap sets it to half the diagonal of its gap between cells, so a
     * finger crossing a gap, or where two gaps cross, keeps a cell. Zero, the default, is
     * containment only.
     */
    val focusSlop: Float = 0f,
    /**
     * Whether marks (and bar tracks) are clipped to [plot] while drawing: true when compiled for a
     * [ViewportRange], so lines extending to the one point kept past each edge and partially
     * visible bars, candles or dots stop cleanly at the plot border.
     */
    val clipPlot: Boolean = false,
)

/**
 * A legend entry. [color] is a palette slot; [colorBelow] is the slot below zero of a
 * [AreaMark.diverging] area series, and null for every other series. [candle] marks a
 * [CandleMark] series, which is painted by direction (up and down) rather than by its slot.
 */
data class SeriesInfo(val name: String, val color: Int, val colorBelow: Int? = null, val candle: Boolean = false)

/** [XRule] is a vertical reference line on a continuous x axis; [Wick] is a candle's line from its low to its high. */
enum class RuleKind { GridX, GridY, AxisX, AxisY, Baseline, Reference, XRule, Wick }
/** [TileLabel] and [TileDetail] are a treemap tile's two lines; [GroupLabel] its group's header. */
enum class TextKind { TickX, TickY, Reference, TileLabel, TileDetail, GroupLabel }
/**
 * What a [FocusPoint] belongs to, so a tooltip can show what it means. [Arc] is a pie slice (a part
 * of a whole, with a share), [Petal] a [RoseChart] wedge (a value in its own right, no share), and
 * [Cell] a [HeatmapChart] cell. A [RadarChart] vertex is a [Dot] whose label is its axis.
 */
enum class MarkKind { Bar, Line, Area, Dot, Arc, Candle, Tile, Cell, Petal }

/**
 * What a [RectNode] means, so a renderer and a tooltip can tell kinds of rectangle apart without
 * reading meaning into other fields. New kinds arrive with the marks that emit them: [Candle] is
 * a candle's body, from open to close, [Volume] its bar in a [CandleMark]'s volume strip,
 * [Tile] a [TreemapChart] leaf, painted by its value rather than its series, [Cell] a
 * [HeatmapChart] cell, painted by its [RectNode.intensity], and [Band] a shaded vertical span on a
 * continuous x axis ([XBandMark]).
 */
enum class RectKind { Bar, Candle, Volume, Tile, Cell, Band }
/** The side of a bar that sits on the value baseline. Left and Right are horizontal bars. */
enum class Edge { Top, Bottom, Left, Right }
enum class HAlign { Start, Center, End }
enum class VAlign { Top, Center, Bottom }

sealed interface Node {
    /** Stable identity across updates; motion pairs nodes by key. */
    val key: String
    val alpha: Float
}

/**
 * A straight line. A [RuleKind.Wick] carries its candle's [datum] and direction ([down]), so it
 * recedes and colours with its candle; an [RuleKind.XRule] carries its optional palette [color]
 * slot and [pattern] override.
 */
data class RuleNode(
    override val key: String,
    val from: XY,
    val to: XY,
    val kind: RuleKind,
    override val alpha: Float = 1f,
    val datum: Int = -1,
    val down: Boolean = false,
    val color: Int? = null,
    val pattern: LinePattern? = null,
) : Node

/**
 * A bar or bar segment. [base] is the edge that sits on the value baseline: [Edge.Bottom] or
 * [Edge.Top] for upright bars, [Edge.Left] (a positive value, growing right) or [Edge.Right]
 * (negative, growing left) for horizontal ones. [isEnd] marks the outermost segment of a stack
 * (the one a style may round).
 *
 * A [RectKind.Candle] body spans its open and close; its [base] is the edge at the open price
 * ([Edge.Bottom] for an up candle on an upright value axis, [Edge.Top] for a down one), and it
 * opens out from there. A [RectKind.Volume] bar stands on the strip's floor ([Edge.Bottom]).
 * Both set [down] when the period closed below its open, so direction never rests on colour: a
 * renderer reads [down], never a colour or the geometry, to paint up and down differently.
 *
 * A [RectKind.Tile] is a treemap leaf: [series] is its group's index, [datum] the leaf's index in
 * that group's leaves, [value] the leaf's raw signed colour value (NaN for none), and [base] is
 * unused. Its key is `tile:<leaf id>`, so it keeps its identity when data is reordered or resized.
 *
 * A [RectKind.Cell] is a heatmap cell: [series] is its column, [datum] its row, [value] the raw
 * value (NaN for missing data) and [intensity] its position on the sequential colour scale. Its
 * key is `cell:<column>:<row>`, a position: a heatmap's cells have no identity beyond it. [base]
 * is unused.
 */
data class RectNode(
    override val key: String,
    val bounds: Bounds,
    val series: Int,
    val datum: Int,
    val base: Edge,
    val isEnd: Boolean = true,
    override val alpha: Float = 1f,
    val kind: RectKind = RectKind.Bar,
    /**
     * The raw, signed datum value the rectangle encodes; for a stacked segment, the segment's own
     * value rather than its stack bounds. A candle body carries its close and a volume bar its
     * volume, and a treemap tile its leaf's value. Never a normalized colour intensity: tiles
     * need none, since the renderer maps this raw value through the style's diverging scale; a
     * kind that does need one will carry it in a separate field.
     */
    val value: Double = Double.NaN,
    /** The period closed below its open: set on [RectKind.Candle] and [RectKind.Volume] only. */
    val down: Boolean = false,
    /**
     * A [RectKind.Cell]'s position on the sequential colour scale, from 0 (the lowest colour) to 1:
     * its [value] over the chart's maximum, clamped. NaN for a missing cell (drawn in the empty
     * colour) and for every other kind. Separate from [value], which stays the raw datum.
     */
    val intensity: Float = Float.NaN,
    /** Palette slot override for a [RectKind.Band]; null uses the style's band fill colour. */
    val color: Int? = null,
) : Node

data class LineNode(
    override val key: String,
    val points: List<XY>,
    val series: Int,
    /** Null means the renderer's style decides. */
    val curve: Curve? = null,
    /** True for the stroke along the top of an area. */
    val isAreaEdge: Boolean = false,
    override val alpha: Float = 1f,
    /** Set on the edge of a [AreaMark.diverging] area: stroke each side of zero in its own colour. */
    val signSplit: SignSplit? = null,
    /** Set on the edge of an [AreaBase.PlotBottom] area: the row it rises from on entrance. */
    val floor: Float? = null,
    val pattern: LinePattern = LinePattern.Solid,
) : Node

data class AreaNode(
    override val key: String,
    val top: List<XY>,
    val bottom: List<XY>,
    val series: Int,
    val curve: Curve? = null,
    override val alpha: Float = 1f,
    /** Set on a [AreaMark.diverging] area: fill each side of zero in its own colour. */
    val signSplit: SignSplit? = null,
    /**
     * The pixel row an [AreaBase.PlotBottom] area is filled down to (its [bottom] points sit on it):
     * its gradient fades toward it and its entrance rises from it. Null for an area filled to zero
     * or stacked, which use [ChartScene.baseline] for both.
     */
    val floor: Float? = null,
) : Node

/**
 * Where a [AreaMark.diverging] area changes colour: the pixel row of value zero. Pixels on the
 * negative side of it take the series' [SeriesInfo.colorBelow].
 *
 * Splitting the painted shape along this row, rather than splitting the data, is exact for any
 * curve: a nonstacked area's edge is at zero precisely where its drawn curve crosses that row,
 * between samples included. [zero] is not clamped to the plot, unlike [ChartScene.baseline]: with
 * a value axis cropped above zero it lies below the plot, so all the data (positive) takes the
 * series colour. [negativeBelow] is false when the value axis is reversed (min above max), which
 * puts negative values above zero.
 */
data class SignSplit(val zero: Float, val negativeBelow: Boolean = true) {
    /** Whether pixel row [y] shows negative values. Zero itself counts as positive. */
    fun isNegative(y: Float): Boolean = if (negativeBelow) y > zero else y < zero
}

/** A point mark. [isMarker] means it decorates a line rather than being data on its own. */
data class DotNode(
    override val key: String,
    val center: XY,
    val radius: Float,
    val series: Int,
    val datum: Int,
    val isMarker: Boolean = false,
    override val alpha: Float = 1f,
) : Node

/** An annular sector. Angles in radians, 0 at twelve o'clock, increasing clockwise. */
data class ArcNode(
    override val key: String,
    val center: XY,
    val innerRadius: Float,
    val outerRadius: Float,
    val startAngle: Float,
    val endAngle: Float,
    val series: Int,
    val datum: Int,
    override val alpha: Float = 1f,
) : Node

data class TextNode(
    override val key: String,
    val text: String,
    val at: XY,
    val h: HAlign,
    val v: VAlign,
    val kind: TextKind,
    override val alpha: Float = 1f,
) : Node

/**
 * A datum as interaction sees it: where it is drawn, and what it means.
 *
 * Neither [datum] nor [domainX] is a stable identity: x values can repeat and data can be
 * reordered or replaced between updates. Resolve a focus against the spec it was compiled from,
 * or, for marks whose data carries ids, by [id].
 */
data class FocusPoint(
    val series: Int,
    /**
     * Index into the series' values (or a dot series' points, the pie's slices as drawn, a
     * treemap group's leaves, or a heatmap column's rows; a cell's [series] is its column).
     */
    val datum: Int,
    val kind: MarkKind,
    /** Where to anchor a marker or tooltip. */
    val x: Float,
    val y: Float,
    /**
     * Position on the focus axis: x, or y under [FocusMode.NearestY]. Points sharing a slot (one
     * category) focus together.
     */
    val slot: Float,
    val label: String,
    val value: Double,
    val valueText: String,
    /**
     * Fraction of the whole, for part-to-whole marks only: a pie slice's value, or a treemap tile's
     * size over every tile's size. NaN otherwise; never a colour value or a fraction of a maximum.
     */
    val share: Double = Double.NaN,
    val hit: Hit? = null,
    /**
     * The datum's raw x in data units, unlike the pixel [x]: the [NumericAxis] value, epoch
     * milliseconds on a [TimeAxis], or a dot's [Datum.x]. Null where x is not a number (category
     * axes, pies, heatmaps, radar axes and rose wedges); never a synthetic index.
     */
    val domainX: Double? = null,
    /** The candle behind a [MarkKind.Candle] point, for an OHLC readout; null for other marks. */
    val candle: CandleDetail? = null,
    /**
     * The identity the spec gave this datum, stable across updates, reorders and resizes: a
     * treemap leaf's [TreemapLeaf.id]. Set only by marks whose data carries ids; null for the rest,
     * whose datum is a position and has no identity beyond its spec.
     */
    val id: String? = null,
    /** A [MarkKind.Tile] point's size, for the tooltip; null for other marks. */
    val tile: TileDetail? = null,
    /**
     * A [MarkKind.Cell] point's colour position, as its [RectNode.intensity], so a tooltip swatch
     * can take the cell's colour. NaN for other marks. Never a share: a cell's [share] is NaN.
     */
    val intensity: Float = Float.NaN,
)

/**
 * A focused treemap tile's area channel: its leaf's raw [TreemapLeaf.size] and that size printed
 * by the chart's `sizeFormat`. The point's `value` is the colour value and its `share` the size's
 * fraction of the whole.
 */
data class TileDetail(val size: Double, val sizeText: String)

/**
 * A focused candle: the period as drawn ([Ohlc.normalized], so its high and low cover its body) and
 * its values formatted for display, prices by the value axis's `valueFormat` and volume by
 * [Formats.Readable]. The [FocusPoint] carrying it has the close as its value and is anchored at
 * the top of the wick.
 */
data class CandleDetail(
    val ohlc: Ohlc,
    val open: String,
    val high: String,
    val low: String,
    val close: String,
    /** Null when the period has no volume. */
    val volume: String?,
) {
    val down: Boolean get() = ohlc.isDown
}

/**
 * Where datum [datum] of scene series [series] is drawn: the point a focus would anchor to, such
 * as the top of its stack segment. Null when the scene has no such point, including a missing
 * (NaN) value, which has no focus point. For placing overlays.
 */
fun ChartScene.pointAt(series: Int, datum: Int): XY? =
    points.firstOrNull { it.series == series && it.datum == datum }?.let { XY(it.x, it.y) }

/**
 * The last defined datum of [series], by datum index: the live end of a line. Trailing missing
 * values are skipped, so a series that is NaN after 3 p.m. ends at 3 p.m. Null when the series has
 * no defined point.
 */
fun ChartScene.lastPoint(series: Int = 0): XY? =
    points.filter { it.series == series }.maxByOrNull { it.datum }?.let { XY(it.x, it.y) }

/**
 * The palette slot [point] is painted in: the series' [SeriesInfo.colorBelow] when it has one (a
 * diverging area) and the value is negative, else the series colour. Tooltips and focus markers use
 * it, so a loss reads in the loss colour.
 */
fun ChartScene.colorSlot(point: FocusPoint): Int {
    val info = series.getOrNull(point.series)
    val below = info?.colorBelow
    return if (below != null && point.value < 0.0) below else info?.color ?: point.series
}

/** Painted area that counts as "on" a datum, so a finger on a bar focuses that bar. */
sealed interface Hit {
    fun contains(p: XY): Boolean
}

data class RectHit(val bounds: Bounds) : Hit {
    override fun contains(p: XY) = bounds.contains(p)
}

data class ArcHit(
    val center: XY,
    val innerRadius: Float,
    val outerRadius: Float,
    val startAngle: Float,
    val endAngle: Float,
) : Hit {
    /**
     * Angles may run past twelve o'clock either way: a rose's first wedge starts below 0 and ends
     * above it, and a point just left of twelve (an angle near 2π) is inside it.
     */
    override fun contains(p: XY): Boolean {
        val dx = p.x - center.x
        val dy = p.y - center.y
        val r = hypot(dx, dy)
        if (r < innerRadius || r > outerRadius) return false
        val turn = (2 * PI).toFloat()
        var a = atan2(dx, -dy) // 0 at twelve o'clock, clockwise
        if (a < 0) a += turn
        return a in startAngle..endAngle ||
            (startAngle < 0f && a - turn in startAngle..endAngle) ||
            (endAngle > turn && a + turn in startAngle..endAngle)
    }
}
