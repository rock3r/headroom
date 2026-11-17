package dev.c5inco.charts.core

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * The renderer-neutral output of the compiler: positioned, keyed primitives plus the semantic
 * points interaction needs. A renderer paints it; it never computes layout.
 *
 * Nodes carry semantic references ([SeriesInfo] index, palette slot, rule and text kinds) rather
 * than colours or stroke widths. Paint is the renderer's job, which is why one scene can be drawn in
 * very different visual styles.
 */
public data class ChartScene(
    val size: Extent,
    /** The plot area. For a polar chart or a pie, the bounding square of its circle. */
    val plot: Bounds,
    /**
     * The scene's coordinate system: what its marks grow from as they arrive. Paint and the tween of
     * a kept or leaving node never read it: a node leaving for another layout is drawn in the
     * incoming scene, so what decides its paint is on the node ([ArcKind], [AreaKind], and its
     * palette slot).
     */
    val layout: SceneLayout,
    /**
     * The legend: one entry per series, in the order nodes' series indices refer to. The renderer
     * never reads a node's colour here: a node fading out of a tween to another scene is drawn in
     * that scene, whose series are not its own, so each node carries its own palette slot.
     */
    val series: List<SeriesInfo>,
    /** Behind the marks: grid lines, axis lines, the zero baseline. */
    val guides: List<Node>,
    val marks: List<Node>,
    /** In front of the marks: tick labels and reference labels. */
    val labels: List<Node>,
    val points: List<FocusPoint>,
    /** How a pointer resolves to [points]. Says nothing about orientation: that is [layout]'s. */
    val focusMode: FocusMode,
    val description: String? = null,
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
    /**
     * Resolves aligned line and area points that M4 decimation left out of [points], so focus can
     * report every series at one datum. Set by a [CartesianChart] with positions; null otherwise.
     * A constructor property, so `copy()` (and so motion) keeps it, and part of equality: scenes
     * that would resolve a datum differently are not equal (see [AlignedFocus]).
     */
    val alignedFocus: AlignedFocus? = null,
    /**
     * How many of [marks], at their start, are leaving: in a scene part way through a tween
     * ([SceneTween.at], [SceneMotion.lerp]), the nodes its target no longer has, fading out
     * underneath the rest. Zero in a compiled scene. Their series and datum index the scene they come
     * from, while a focus is resolved on the target scene, so a renderer never counts one as focused,
     * however its indices compare with the focused point's.
     */
    val leavingMarks: Int = 0,
) {
    // Not a constructor property: outside equality, hashing and `copy()`, so a copy (each frame of
    // motion) carries none and builds its own only if focus is resolved on it.
    @Volatile private var focusTableCache: FocusTable? = null

    /**
     * [points] as [focusAt] scans them, built on the first focus of this instance. A race between
     * threads builds it twice at worst: it is immutable and depends only on [points].
     */
    internal val focusTable: FocusTable
        get() = focusTableCache ?: FocusTable(points).also { focusTableCache = it }
}

/**
 * A scene's coordinate system. Each case carries only what that system needs, and an entrance
 * decides by it rather than by reading meaning into other fields. Paint does not: what a node's
 * paint depends on is on the node, which may be fading out of a scene of another layout.
 */
public sealed interface SceneLayout {
    /**
     * Marks stand on a value axis ([CartesianChart], [HorizontalBarChart]): bars, lines and areas
     * grow from [baseline], where zero sits on that axis in pixels, clamped into the plot. Along
     * [valueAxis]: a y position when the value axis is [AxisOrientation.Vertical], an x position
     * when it runs across ([AxisOrientation.Horizontal], horizontal bars).
     */
    public data class Cartesian(val valueAxis: AxisOrientation, val baseline: Float) : SceneLayout

    /**
     * A polar chart ([RadarChart], [RoseChart]) around [center]. Marks grow out of the centre
     * rather than up from a baseline: polygon vertices and markers move out from it, petals grow
     * from their inner radius, staggered clockwise from twelve o'clock on a first appearance as on
     * any later arrival.
     *
     * What a polar mark is travels on the mark itself, so it holds while the mark fades out of a
     * scene tweening to another layout: a radar polygon is an [AreaKind.Polar] area with a
     * [LineNode.polar] edge (paired by index, filled evenly) and a petal is an [ArcKind.Petal].
     */
    public data class Polar(val center: XY) : SceneLayout

    /**
     * Neither a value axis nor a centre to grow from: pies, treemaps and heatmaps, whose marks
     * enter in place (slices sweep from twelve o'clock, tiles and cells open out from their own
     * centres), and the empty scene of a polar chart with nothing to draw.
     */
    public data object Free : SceneLayout
}

/** Which way a [SceneLayout.Cartesian] value axis runs. */
public enum class AxisOrientation { Vertical, Horizontal }

/**
 * A legend entry. [color] is a palette slot; [colorBelow] is the slot below zero of a
 * [AreaMark.diverging] area series, and null for every other series. [candle] marks a
 * [CandleMark] series, which is painted by direction (up and down) rather than by its slot.
 */
public data class SeriesInfo(val name: String, val color: Int, val colorBelow: Int? = null, val candle: Boolean = false)

/**
 * Resolves the [FocusPoint] of an aligned line or area series at a datum from the series' raw
 * values, for focus on a decimated scene: M4 keeps each pixel column's extremes, which differ
 * between series, so a datum one series kept may be missing from another's points.
 *
 * Immutable, and holds only what resolution needs: the spec's prepared tables (shared by every
 * compile of the spec; no marks, no text measurements) and the few values this layout chose (the
 * scales, the visible window, the value format). A retained scene therefore pins nothing a later
 * compile of the same spec, at another size or viewport, changes or needs.
 *
 * Opaque, but part of scene equality: two resolvers are equal when they would resolve every point
 * alike, whatever spec instance they came from. Two scenes whose nodes and focus points match can
 * still resolve a datum differently (a data update that changes only samples decimation left out),
 * and the host keys focus on the scene, so equal scenes must resolve equally. Equality compares, in
 * constant time:
 *
 * - this layout's values: the scales, the zero row, the visible window and whether texts are cached;
 * - the value format, with its own `equals`: identity for a lambda, so two format instances are
 *   never taken to print alike (scenes compiled with separate but identical lambdas are unequal and
 *   cost the host at most a focus resolved again; a format that is an `object` or a data class
 *   compares as such);
 * - the prepared tables' [ResolutionKey]: their identity, or else a 128-bit fingerprint of the drawn
 *   head of every line and area series, the x positions or categories, how marks lay out series,
 *   and the x label format, built once per spec with the tables. Equal content (an over-long series
 *   and its truncation too) gives equal keys; different content gives different ones but for a hash
 *   collision (about 2⁻¹²⁸ for unrelated data), which would only let a held focus show the earlier
 *   data's value.
 */
public class AlignedFocus internal constructor(
    private val prep: CartesianPrep,
    private val y: LinearScale,
    /** Where a stack bound of zero sits: the baseline, kept in the plot. */
    private val zeroY: Float,
    /** The continuous x scale; null on a category axis. */
    private val x: LinearScale?,
    /** The category scale; null on a continuous axis. */
    private val band: BandScale?,
    private val valueFormat: ValueFormat,
    private val cacheValueTexts: Boolean,
    /** Whether a viewport limits focus to [inStart]..[inEnd] and x in [viewLo]..[viewHi]. */
    private val windowed: Boolean,
    private val inStart: Int,
    private val inEnd: Int,
    private val viewLo: Double,
    private val viewHi: Double,
) {
    private fun inWindow(i: Int): Boolean {
        val xValues = prep.xValues
        return !windowed || (i in inStart..inEnd && (xValues == null || xValues[i] in viewLo..viewHi))
    }

    private fun yStack(v: Double): Float = if (v == 0.0) zeroY else y(v)

    /** First axis index at a dot's raw x, including samples absent from every decimated series. */
    internal fun datumAt(domainX: Double): Int? {
        val xs = prep.xValues ?: return null
        if (!domainX.isFinite()) return null
        if (prep.xAscending) {
            val i = lowerBound(xs, domainX)
            return i.takeIf { it < xs.size && xs[it] == domainX && inWindow(it) }
        }
        return xs.indices.firstOrNull { xs[it] == domainX && inWindow(it) }
    }

    /** The focus point of line or area series [series] at [datum], or null when it has none. */
    internal fun pointAt(series: Int, datum: Int): FocusPoint? {
        if (series !in 0 until prep.totalSeries || datum !in 0 until prep.positions || !inWindow(datum)) return null
        val kind = prep.seriesAlignedKind[series] ?: return null
        val markIndex = prep.seriesMarkIndex[series]
        val s = prep.seriesLocalIndex[series]
        val sValues = prep.seriesArrays[markIndex]?.get(s) ?: return null
        if (datum >= sValues.size) return null
        val raw = sValues[datum]
        if (!raw.isFinite()) return null
        val px = band?.center(datum) ?: x!!(prep.xValues!![datum])
        val domainX = prep.boxedDomainX?.get(datum)
        val detail = when (kind) {
            AlignedKind.Line -> FocusDetail.Line(domainX)
            AlignedKind.Area -> FocusDetail.Area(domainX)
        }
        val py = when (kind) {
            // On a stacked layer, the segment's outer edge, as the area's top: the upper bound of a
            // gain, the lower bound of a loss.
            AlignedKind.Area -> prep.stacks[markIndex]?.get(s)?.get(datum)?.let { yStack(if (raw < 0.0) it.first else it.second) } ?: y(raw)
            AlignedKind.Line -> y(raw)
        }
        return FocusPoint(
            series = series, datum = datum, x = px, y = py, slot = px,
            label = prep.xLabel(datum), value = raw,
            valueText = prep.valueText(markIndex, s, datum, raw, valueFormat, cacheValueTexts),
            detail = detail,
        )
    }

    override fun equals(other: Any?): Boolean = this === other || other is AlignedFocus &&
        inStart == other.inStart && inEnd == other.inEnd && windowed == other.windowed &&
        cacheValueTexts == other.cacheValueTexts && zeroY.toBits() == other.zeroY.toBits() &&
        viewLo.toBits() == other.viewLo.toBits() && viewHi.toBits() == other.viewHi.toBits() &&
        y.sameAs(other.y) && x.sameAs(other.x) && band.sameAs(other.band) &&
        valueFormat == other.valueFormat &&
        (prep === other.prep || prep.resolution == other.prep.resolution)

    override fun hashCode(): Int {
        var h = prep.resolution.hashCode()
        h = 31 * h + valueFormat.hashCode()
        h = 31 * h + inStart
        h = 31 * h + inEnd
        h = 31 * h + windowed.hashCode()
        h = 31 * h + cacheValueTexts.hashCode()
        h = 31 * h + zeroY.toBits()
        h = 31 * h + viewLo.toBits().hashCode()
        h = 31 * h + viewHi.toBits().hashCode()
        h = 31 * h + y.contentHash()
        h = 31 * h + (x?.contentHash() ?: 0)
        h = 31 * h + (band?.contentHash() ?: 0)
        return h
    }

    override fun toString(): String = "AlignedFocus"
}

private fun LinearScale?.sameAs(other: LinearScale?): Boolean =
    if (this == null || other == null) this === other
    else domainStart.toBits() == other.domainStart.toBits() && domainEnd.toBits() == other.domainEnd.toBits() &&
        rangeStart.toBits() == other.rangeStart.toBits() && rangeEnd.toBits() == other.rangeEnd.toBits()

private fun LinearScale.contentHash(): Int =
    ((domainStart.toBits().hashCode() * 31 + domainEnd.toBits().hashCode()) * 31 + rangeStart.toBits()) * 31 + rangeEnd.toBits()

private fun BandScale?.sameAs(other: BandScale?): Boolean =
    if (this == null || other == null) this === other
    else count == other.count && rangeStart.toBits() == other.rangeStart.toBits() && rangeEnd.toBits() == other.rangeEnd.toBits() &&
        paddingInner.toBits() == other.paddingInner.toBits() && paddingOuter.toBits() == other.paddingOuter.toBits() &&
        align.toBits() == other.align.toBits()

private fun BandScale.contentHash(): Int =
    ((((count * 31 + rangeStart.toBits()) * 31 + rangeEnd.toBits()) * 31 + paddingInner.toBits()) * 31 + paddingOuter.toBits()) * 31 + align.toBits()

/** The kinds of series [AlignedFocus] resolves. */
internal enum class AlignedKind { Line, Area }

/**
 * What a [RuleNode] is. Most kinds are plain lines; [XRule] and [Wick] carry what only they need,
 * so no rule can hold another kind's data.
 */
public sealed interface RuleKind {
    public data object GridX : RuleKind
    public data object GridY : RuleKind
    public data object AxisX : RuleKind
    public data object AxisY : RuleKind
    public data object Baseline : RuleKind
    /** A horizontal reference line across the plot ([RuleMark]), vertical on horizontal bars. */
    public data object Reference : RuleKind

    /**
     * A vertical reference line on a continuous x axis ([XRuleMark]): its optional palette [color]
     * slot (null: the style's x-rule colour) and [pattern] override (null: the style's).
     */
    public data class XRule(val color: Int? = null, val pattern: LinePattern? = null) : RuleKind

    /**
     * A candle's line from its low to its high, carrying its candle's [datum] and direction
     * ([down]), so it recedes and colours with its candle.
     */
    public data class Wick(val datum: Int, val down: Boolean) : RuleKind
}

/** [TileLabel] and [TileDetail] are a treemap tile's two lines; [GroupLabel] its group's header. */
public enum class TextKind { TickX, TickY, Reference, TileLabel, TileDetail, GroupLabel }
/**
 * What a [FocusPoint] belongs to, as a plain tag: its [FocusPoint.detail]'s kind, one per case of
 * [FocusDetail]. [Arc] is a pie slice (a part of a whole, with a share), [Petal] a [RoseChart]
 * wedge (a value in its own right, no share), and [Cell] a [HeatmapChart] cell. A [RadarChart]
 * vertex is a [Dot] whose label is its axis.
 */
public enum class MarkKind { Bar, Line, Area, Dot, Arc, Candle, Tile, Cell, Petal }

/** The side of a bar that sits on the value baseline. Left and Right are horizontal bars. */
public enum class Edge { Top, Bottom, Left, Right }
public enum class HAlign { Start, Center, End }
public enum class VAlign { Top, Center, Bottom }

public sealed interface Node {
    /** Stable identity across updates; motion pairs nodes by key. */
    public val key: String
    public val alpha: Float
}

/** A straight line. Its [kind] says what it is, and carries what only that kind needs. */
public data class RuleNode(
    override val key: String,
    val from: XY,
    val to: XY,
    val kind: RuleKind,
    override val alpha: Float = 1f,
) : Node

/**
 * A rectangle. Its geometry ([bounds]) and [alpha] are common, so motion tweens any rectangle the
 * same way; what it means, and the data only that meaning has, is its [kind].
 */
public data class RectNode(
    override val key: String,
    val bounds: Bounds,
    val kind: RectKind,
    override val alpha: Float = 1f,
) : Node

/**
 * What a [RectNode] means, with the data only that kind has, so a renderer and a tooltip tell
 * rectangles apart without reading meaning into other fields, and no rectangle can carry another
 * kind's data.
 */
public sealed interface RectKind {
    /**
     * A bar or bar segment of series [series] at axis position [datum]. [base] is the edge that
     * sits on the value baseline on screen: [Edge.Bottom] (a positive value, growing up) or
     * [Edge.Top] (negative, growing down) for upright bars, [Edge.Left] (positive, growing right) or
     * [Edge.Right] (negative, growing left) for horizontal ones. A reversed value axis (min above
     * max) turns bars round, so there the edges swap: a positive upright bar hangs from [Edge.Top].
     * [value] is the raw, signed datum; for a stacked segment, the segment's own value rather than
     * its stack bounds. [isEnd] marks the outermost segment of a stack (the one a style may round);
     * a bar that is not stacked is its own end. [track] is its plot's extent along the value axis
     * (top to bottom for an upright bar, left to right for a horizontal one), which a style's bar
     * track fills behind it. [color] is its series' palette slot ([SeriesInfo.color]).
     */
    public data class Bar(
        val series: Int,
        val color: Int,
        val datum: Int,
        val base: Edge,
        val value: Double,
        val track: Span,
        val isEnd: Boolean = true,
    ) : RectKind

    /**
     * A candle's body, spanning its open and close, for the candle of series [series] at axis
     * position [datum]. [base] is the edge at the open price ([Edge.Bottom] for an up candle on an
     * upright value axis, [Edge.Top] for a down one), and it opens out from there. [close] is the
     * period's close. [down] is set when the period closed below its open, so direction never rests
     * on colour: a renderer reads it, never a colour or the geometry, to paint up and down
     * differently.
     */
    public data class Candle(val series: Int, val datum: Int, val base: Edge, val close: Double, val down: Boolean) : RectKind

    /**
     * A candle's bar in its [CandleMark]'s volume strip, standing on the strip's floor: series
     * [series], axis position [datum], the period's [volume], and its direction ([down]) as on its
     * candle.
     */
    public data class Volume(val series: Int, val datum: Int, val volume: Double, val down: Boolean) : RectKind

    /**
     * A [TreemapChart] leaf: [leaf] is its index in group [group]'s leaves and [value] its raw,
     * signed colour value (NaN for none), which the renderer maps through the style's diverging
     * scale. Its key is `tile:<leaf id>`, so it keeps its identity when data is reordered or resized.
     */
    public data class Tile(val group: Int, val leaf: Int, val value: Double) : RectKind

    /**
     * A [HeatmapChart] cell at [column] and [row]: [value] is the raw value (NaN for missing data)
     * and [intensity] its position on the sequential colour scale, from 0 (the lowest colour) to 1:
     * [value] over the chart's maximum, clamped, and NaN for a missing cell (drawn in the empty
     * colour). Its key is `cell:<column>:<row>`, a position: a heatmap's cells have no identity
     * beyond it.
     */
    public data class Cell(val column: Int, val row: Int, val value: Double, val intensity: Float) : RectKind

    /**
     * A shaded vertical span on a continuous x axis ([XBandMark]), with its palette [color] slot
     * override; null uses the style's band fill colour.
     */
    public data class Band(val color: Int? = null) : RectKind
}

public data class LineNode(
    override val key: String,
    val points: List<XY>,
    val series: Int,
    /** Its series' palette slot ([SeriesInfo.color]), carried so it paints the same in any scene. */
    val color: Int,
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
    /**
     * A closed polygon round a centre (a [RadarChart] series' edge), whose x runs back on itself:
     * motion pairs its points by index, never by sampling along x.
     */
    val polar: Boolean = false,
) : Node

/**
 * A filled region between [top] and [bottom]. Its [kind] says how it is filled and paired in motion.
 * On an upright value axis [top] is the edge on the area's values, which its edge line strokes, and
 * [bottom] the edge it closes on: zero, its [floor], or the layer beneath in a stack. A stacked
 * layer's values lie on the side of zero they are on, so a layer of losses has its [top] below its
 * [bottom] on a normal axis.
 */
public data class AreaNode(
    override val key: String,
    val top: List<XY>,
    val bottom: List<XY>,
    val series: Int,
    /** Its series' palette slot ([SeriesInfo.color]), carried so it paints the same in any scene. */
    val color: Int,
    val kind: AreaKind,
    val curve: Curve? = null,
    override val alpha: Float = 1f,
    /** Set on a [AreaMark.diverging] area: fill each side of zero in its own colour. */
    val signSplit: SignSplit? = null,
    /**
     * The pixel row an [AreaBase.PlotBottom] area is filled down to (its [bottom] points sit on it):
     * its gradient fades toward it and its entrance rises from it. Null for an area filled to zero
     * or stacked, whose gradient fades toward [AreaKind.Upright.baseline] and whose entrance rises
     * from the [SceneLayout.Cartesian.baseline].
     */
    val floor: Float? = null,
) : Node

/**
 * What an [AreaNode] is, carried on the area so it paints and tweens the same whichever scene it is
 * drawn in, as when it fades out of a scene tweening to another layout.
 */
public sealed interface AreaKind {
    /**
     * An area standing on an upright value axis ([AreaMark]). Its gradient fades from [fadeFrom], an
     * edge of [plot] (its plot's top to bottom), toward [baseline] (where zero sits, clamped into the
     * plot, as the scene's [SceneLayout.Cartesian.baseline]), or toward the area's [AreaNode.floor]
     * when it has one. Its points are paired along x in motion.
     *
     * [fadeFrom] is the end of the value axis the area's values point to, farthest from zero: the
     * positive end (the plot's top, or its bottom on a reversed axis), or the negative end for a
     * series with values below zero and none above. An area filled to its floor fades from the top.
     * A sign-split ([AreaNode.signSplit]) area fades each side from its own edge instead. A compiled
     * scene always puts it on one of [plot]'s edges; a tween turning the gradient over passes it
     * between them, which a renderer reads as a crossfade of the fades from the two edges.
     */
    public data class Upright(val baseline: Float, val plot: Span, val fadeFrom: Float) : AreaKind

    /**
     * A closed polygon round a centre ([RadarChart]): it has no baseline to fade toward, so a
     * gradient fill paints it evenly, and motion pairs its points by index, never along x.
     */
    public data object Polar : AreaKind
}

/**
 * Where a [AreaMark.diverging] area changes colour: the pixel row of value zero. Pixels on the
 * negative side of it take [colorBelow], the series' [SeriesInfo.colorBelow].
 *
 * Splitting the painted shape along this row, rather than splitting the data, is exact for any
 * curve: a nonstacked area's edge is at zero precisely where its drawn curve crosses that row,
 * between samples included. [zero] is not clamped to the plot, unlike [SceneLayout.Cartesian.baseline]: with
 * a value axis cropped above zero it lies below the plot, so all the data (positive) takes the
 * series colour. [negativeBelow] is false when the value axis is reversed (min above max), which
 * puts negative values above zero.
 */
public data class SignSplit(val zero: Float, val colorBelow: Int, val negativeBelow: Boolean = true) {
    /** Whether pixel row [y] shows negative values. Zero itself counts as positive. */
    public fun isNegative(y: Float): Boolean = if (negativeBelow) y > zero else y < zero
}

/** A point mark. [isMarker] means it decorates a line rather than being data on its own. */
public data class DotNode(
    override val key: String,
    val center: XY,
    val radius: Float,
    val series: Int,
    /** Its series' palette slot ([SeriesInfo.color]): its colour and glyph. */
    val color: Int,
    val datum: Int,
    val isMarker: Boolean = false,
    override val alpha: Float = 1f,
) : Node

/**
 * An annular sector. Angles in radians, 0 at twelve o'clock, increasing clockwise. Its [kind] says
 * whether it is a pie slice or a rose petal.
 */
public data class ArcNode(
    override val key: String,
    val center: XY,
    val innerRadius: Float,
    val outerRadius: Float,
    val startAngle: Float,
    val endAngle: Float,
    val series: Int,
    /** Its slice's or wedge's palette slot ([SeriesInfo.color]). */
    val color: Int,
    val datum: Int,
    val kind: ArcKind,
    override val alpha: Float = 1f,
) : Node

/** What an [ArcNode] is, carried on the arc so it paints the same whichever scene it is drawn in. */
public sealed interface ArcKind {
    /** A [PieChart] slice, a part of a whole: a style may round its ends. */
    public data object Slice : ArcKind

    /**
     * A [RoseChart] wedge, a value in its own right: its ends stay square, so its area stays true to
     * its value.
     */
    public data object Petal : ArcKind
}

public data class TextNode(
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
 * Every point has the fields here; what only some kinds of mark have (a hit region, an x in data
 * units, a share, a candle's prices, a tile's id) is in its [detail], one case per [MarkKind].
 *
 * [datum] is not a stable identity: x values can repeat and data can be reordered or replaced
 * between updates. Resolve a focus against the spec it was compiled from, or, for a treemap tile,
 * by its [FocusDetail.Tile.id].
 */
public data class FocusPoint(
    /** The scene series: an index into [ChartScene.series] (a heatmap cell's column). */
    val series: Int,
    /**
     * Where the datum is in its data, per [detail]: an axis position for marks
     * [FocusDetail.Aligned] to the category or x axis (shared by every series drawn there), an index
     * into a dot series' own points, the pie's slices as drawn, a treemap group's leaves, a heatmap
     * column's rows, a radar's axes or a rose's wedges.
     */
    val datum: Int,
    /** Where to anchor a marker or tooltip. */
    val x: Float,
    val y: Float,
    /**
     * Position on the focus axis: x, or y under [FocusMode.NearestY], used to find the nearest
     * position. Aligned marks group by datum index; dots join by raw domain x under NearestX,
     * even when slots round together. Non-aligned points under NearestY use exact row slots.
     * Comparing raw x falls back to exact slots only when both points lack it.
     */
    val slot: Float,
    val label: String,
    val value: Double,
    val valueText: String,
    /** What the point belongs to, with the data only that kind of mark has. */
    val detail: FocusDetail,
) {
    /** The kind of mark, as a plain tag: [detail]'s. */
    val kind: MarkKind get() = detail.kind

    /** The painted area that counts as "on" this datum, or null for marks without one (lines, areas, dots). */
    val hit: Hit? get() = detail.hit
}

/**
 * The part of a [FocusPoint] that depends on its kind of mark: one case per [MarkKind], each with
 * only the data that kind has, so a tooltip reads it with an exhaustive `when`.
 */
public sealed interface FocusDetail {
    public val kind: MarkKind

    /** The painted area that counts as "on" the datum: a bar, candle, slice, tile, cell or petal. */
    public val hit: Hit?

    /**
     * A mark drawn at a position on the category or x axis, so its point's [FocusPoint.datum] is
     * that position: bars, lines, areas and candles. Not a dot, whose datum indexes its own points.
     */
    public sealed interface Aligned : FocusDetail {
        /**
         * The datum's raw x in data units, unlike the pixel [FocusPoint.x]: the [NumericAxis] value
         * or epoch milliseconds on a [TimeAxis]. Null on a category axis; never a synthetic index.
         */
        public val domainX: Double?
    }

    /** A bar or stack segment; its [hit] is the bar. [domainX] is null on horizontal bars. */
    public data class Bar(override val hit: RectHit, override val domainX: Double? = null) : Aligned {
        override val kind: MarkKind get() = MarkKind.Bar
    }

    public data class Line(override val domainX: Double? = null) : Aligned {
        override val kind: MarkKind get() = MarkKind.Line
        override val hit: Hit? get() = null
    }

    public data class Area(override val domainX: Double? = null) : Aligned {
        override val kind: MarkKind get() = MarkKind.Area
        override val hit: Hit? get() = null
    }

    /**
     * A candle, for an OHLC readout: the period as drawn ([Ohlc.normalized], so its high and low
     * cover its body) and its values formatted for display, prices by the value axis's
     * `valueFormat` and volume by [Formats.Readable] ([volume] is null when the period has none).
     * The point has the close as its value, is anchored at the top of the wick, and its [hit] spans
     * the wick from high to low across the body's width.
     */
    public data class Candle(
        override val hit: RectHit,
        override val domainX: Double?,
        val ohlc: Ohlc,
        val open: String,
        val high: String,
        val low: String,
        val close: String,
        val volume: String?,
    ) : Aligned {
        override val kind: MarkKind get() = MarkKind.Candle
        val down: Boolean get() = ohlc.isDown
    }

    /**
     * A free point: a scatter dot, whose [FocusPoint.datum] indexes its series' points and whose
     * [domainX] is its [Datum.x], or a [RadarChart] vertex, whose label is its axis and whose
     * [domainX] is null.
     *
     * Under NearestX, [domainX] must numerically equal an aligned axis x to join its tooltip.
     * Reuse the axis value: independently computed doubles (for example, 0.1 * 3 and 0.3) may
     * differ. Signed zeros compare equal. Null uses exact-slot matching with other points that
     * also lack raw x; NearestY ignores raw x and matches row positions.
     */
    public data class Dot(val domainX: Double? = null) : FocusDetail {
        override val kind: MarkKind get() = MarkKind.Dot
        override val hit: Hit? get() = null
    }

    /**
     * A pie slice, a part of a whole: [share] is its value's fraction of the whole (never a colour
     * value or a fraction of a maximum).
     */
    public data class Arc(override val hit: ArcHit, val share: Double) : FocusDetail {
        override val kind: MarkKind get() = MarkKind.Arc
    }

    /** A [RoseChart] wedge: a value in its own right, with no share. */
    public data class Petal(override val hit: ArcHit) : FocusDetail {
        override val kind: MarkKind get() = MarkKind.Petal
    }

    /**
     * A treemap tile. [id] is its leaf's [TreemapLeaf.id], stable across updates, reorders and
     * resizes. Its area channel is [size], the leaf's raw [TreemapLeaf.size], printed by the chart's
     * `sizeFormat` as [sizeText], and [share] is that size over every tile's size. The point's
     * value is the colour value.
     */
    public data class Tile(
        override val hit: RectHit,
        val id: String,
        val size: Double,
        val sizeText: String,
        val share: Double,
    ) : FocusDetail {
        override val kind: MarkKind get() = MarkKind.Tile
    }

    /**
     * A [HeatmapChart] cell: [intensity] is its colour position, as its [RectKind.Cell.intensity],
     * so a tooltip swatch can take the cell's colour. Never a share: a cell is not a part of a whole.
     */
    public data class Cell(override val hit: RectHit, val intensity: Float) : FocusDetail {
        override val kind: MarkKind get() = MarkKind.Cell
    }
}

/**
 * Where datum [datum] of scene series [series] is drawn: the point a focus would anchor to, such
 * as the top of its stack segment. Null when the scene has no such point, including a missing
 * (NaN) value, which has no focus point. For placing overlays.
 */
public fun ChartScene.pointAt(series: Int, datum: Int): XY? =
    points.firstOrNull { it.series == series && it.datum == datum }?.let { XY(it.x, it.y) }
        ?: alignedFocus?.pointAt(series, datum)?.let { XY(it.x, it.y) }

/**
 * The last defined datum of [series], by datum index: the live end of a line. Trailing missing
 * values are skipped, so a series that is NaN after 3 p.m. ends at 3 p.m. Null when the series has
 * no defined point.
 */
public fun ChartScene.lastPoint(series: Int = 0): XY? =
    points.filter { it.series == series }.maxByOrNull { it.datum }?.let { XY(it.x, it.y) }

/**
 * The palette slot [point] is painted in: the series' [SeriesInfo.colorBelow] when it has one (a
 * diverging area) and the value is negative, else the series colour. Tooltips and focus markers use
 * it, so a loss reads in the loss colour.
 */
public fun ChartScene.colorSlot(point: FocusPoint): Int {
    val info = series.getOrNull(point.series)
    val below = info?.colorBelow
    return if (below != null && point.value < 0.0) below else info?.color ?: point.series
}

/** Painted area that counts as "on" a datum, so a finger on a bar focuses that bar. */
public sealed interface Hit {
    public fun contains(p: XY): Boolean
}

public data class RectHit(val bounds: Bounds) : Hit {
    override fun contains(p: XY): Boolean = bounds.contains(p)
}

public data class ArcHit(
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
