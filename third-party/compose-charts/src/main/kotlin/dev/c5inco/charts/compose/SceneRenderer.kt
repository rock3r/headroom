package dev.c5inco.charts.compose

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.resolveDefaults
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.c5inco.charts.core.ArcNode
import dev.c5inco.charts.core.AreaNode
import dev.c5inco.charts.core.ChartScene
import dev.c5inco.charts.core.DotNode
import dev.c5inco.charts.core.Edge
import dev.c5inco.charts.core.Extent
import dev.c5inco.charts.core.Focus
import dev.c5inco.charts.core.FocusMode
import dev.c5inco.charts.core.FocusPoint
import dev.c5inco.charts.core.HAlign
import dev.c5inco.charts.core.LineNode
import dev.c5inco.charts.core.LinePattern
import dev.c5inco.charts.core.MarkKind
import dev.c5inco.charts.core.Node
import dev.c5inco.charts.core.PathSink
import dev.c5inco.charts.core.RectKind
import dev.c5inco.charts.core.RectNode
import dev.c5inco.charts.core.RuleKind
import dev.c5inco.charts.core.RuleNode
import dev.c5inco.charts.core.SignSplit
import dev.c5inco.charts.core.colorSlot
import dev.c5inco.charts.core.TextKind
import dev.c5inco.charts.core.TextMeasure
import dev.c5inco.charts.core.TextNode
import dev.c5inco.charts.core.VAlign
import dev.c5inco.charts.core.isDefined
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The text styles a scene's layout depends on: label sizes move margins, so a change here is a new
 * scene, while every other style field is paint.
 */
internal data class ChartTextStyles(
    val ticks: TextStyle,
    val reference: TextStyle,
    val tileLabel: TextStyle,
    val tileDetail: TextStyle,
    val groupLabel: TextStyle,
) {
    fun of(kind: TextKind): TextStyle = when (kind) {
        TextKind.TickX, TextKind.TickY -> ticks
        TextKind.Reference -> reference
        TextKind.TileLabel -> tileLabel
        TextKind.TileDetail -> tileDetail
        TextKind.GroupLabel -> groupLabel
    }
}

internal fun ChartStyle.textStyles() =
    ChartTextStyles(axis.labels, axis.referenceLabel, treemap.label, treemap.detail, treemap.groupLabel)

/**
 * Caches recent chart text by string and kind, for both layout measurement and drawing.
 *
 * Main thread only: Compose's [TextMeasurer] (font resolution, its layout cache, the platform text
 * layout) is never called from another thread. A scene compiled off the main thread measures
 * through [known] instead, which reads the sizes laid out here so far and records what it lacks.
 */
internal class TextCache(
    private val measurer: TextMeasurer,
    private val styles: ChartTextStyles,
    private val capacity: Int = 512,
) : TextMeasure {
    constructor(measurer: TextMeasurer, style: ChartStyle) : this(measurer, style.textStyles())

    internal class Entry(val extent: Extent, val inkHeight: Float)
    private class CachedLayout(val layout: TextLayoutResult, val entry: Entry)

    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val bounds = android.graphics.Rect()

    private fun measureInkHeight(text: String, style: TextStyle, layout: TextLayoutResult): Float {
        val box = layout.size.height.toFloat()
        if (text.isEmpty() || box <= 0f) return box
        val input = layout.layoutInput
        val resolved = resolveDefaults(style, input.layoutDirection)
        paint.typeface = input.fontFamilyResolver.resolve(
            resolved.fontFamily,
            resolved.fontWeight ?: FontWeight.Normal,
            resolved.fontStyle ?: FontStyle.Normal,
            resolved.fontSynthesis ?: FontSynthesis.All,
        ).value as? android.graphics.Typeface
        paint.textSize = with(input.density) { if (resolved.fontSize.isSp) resolved.fontSize.toPx() else 14.sp.toPx() }
        paint.getTextBounds(text, 0, text.length, bounds)
        val ink = bounds.height().toFloat()
        return if (ink > 0f) min(ink, box) else box
    }

    init { require(capacity > 0) }

    // Reusable path and sink for drawScene, avoiding per-frame allocations on the draw path.
    internal val scratchPath = Path()
    internal val scratchSink: PathSink = ComposePathSink(scratchPath)

    // Live charts see new prices and timestamps indefinitely. Retain recent layouts, not the
    // entire history of the feed; evicted text is measured again if it returns to the screen.
    private var retainedLimit = capacity
    private val layouts = object : LinkedHashMap<Pair<String, TextKind>, CachedLayout>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, TextKind>, CachedLayout>): Boolean =
            size > retainedLimit
    }

    /**
     * Keep the current frame's working set even when it exceeds the history budget. Otherwise a
     * large treemap would evict and remeasure every label on every animation frame. Recompute the
     * limit as scenes change and trim immediately, so a formerly large chart is not a high-water
     * mark for retained history. Invisible exiting text does not contribute to the working set.
     */
    fun prepareForScene(scene: ChartScene) {
        retainedLimit = if (scene.labels.size <= capacity) {
            capacity
        } else {
            val visible = scene.labels.asSequence().filterIsInstance<TextNode>().filter { it.alpha > 0.01f }
                .map { it.text to it.kind }.toSet()
            maxOf(capacity, visible.size)
        }
        val oldest = layouts.entries.iterator()
        while (layouts.size > retainedLimit && oldest.hasNext()) {
            oldest.next()
            oldest.remove()
        }
    }

    private fun cached(text: String, kind: TextKind): CachedLayout = layouts.getOrPut(text to kind) {
        val style = styles.of(kind)
        val layout = measurer.measure(text, style, maxLines = 1, softWrap = false)
        val extent = Extent(layout.size.width.toFloat(), layout.size.height.toFloat())
        CachedLayout(layout, Entry(extent, measureInkHeight(text, style, layout)))
    }

    fun layout(text: String, kind: TextKind): TextLayoutResult = cached(text, kind).layout

    override fun measure(text: String, kind: TextKind): Extent = cached(text, kind).entry.extent

    override fun inkHeight(text: String, kind: TextKind): Float = cached(text, kind).entry.inkHeight

    /** A private size snapshot for one background compile, independent of cache eviction. */
    fun known(): KnownSizes = KnownSizes(layouts.mapValues { (_, cached) -> cached.entry }.toMutableMap())
}

/**
 * Text sizes owned by one background compile. Missing strings measure as zero and are recorded;
 * the host measures them on the main thread, then retries against this same snapshot. The
 * snapshot can fit a scene larger than the layout cache without losing earlier measurements,
 * and is released when this compile completes or is cancelled. No mutable map is shared with
 * the renderer or another compile.
 */
internal class KnownSizes(private val sizes: MutableMap<Pair<String, TextKind>, TextCache.Entry>) : TextMeasure {
    val missing = LinkedHashSet<Pair<String, TextKind>>()

    override fun measure(text: String, kind: TextKind): Extent {
        val key = text to kind
        return sizes[key]?.extent ?: run {
            missing += key
            Extent(0f, 0f)
        }
    }

    override fun inkHeight(text: String, kind: TextKind): Float {
        val key = text to kind
        return sizes[key]?.inkHeight ?: run {
            missing += key
            0f
        }
    }

    /** Called on the main thread only, after the background compile has returned. */
    fun measureMissing(texts: TextMeasure) {
        missing.forEach { (text, kind) ->
            sizes[text to kind] = TextCache.Entry(texts.measure(text, kind), texts.inkHeight(text, kind))
        }
        missing.clear()
    }
}

private class ComposePathSink(val path: Path) : PathSink {
    override fun moveTo(x: Float, y: Float) = path.moveTo(x, y)
    override fun lineTo(x: Float, y: Float) = path.lineTo(x, y)
    override fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) = path.cubicTo(x1, y1, x2, y2, x, y)
    override fun close() = path.close()
}

/** Palette slot for a scene series index. */
internal fun ChartScene.slot(series: Int): Int = this.series.getOrNull(series)?.color ?: series

/** Palette slot below zero for a diverging area: the series' own choice, else the next slot. */
private fun ChartScene.slotBelow(series: Int): Int = this.series.getOrNull(series)?.colorBelow ?: (slot(series) + 1)

/**
 * Recedes a colour toward the surface. Mixing, rather than lowering alpha, keeps marks opaque, so
 * overlapping strokes and caps never show seams.
 */
private fun ChartStyle.dimmed(color: Color, dim: Boolean): Color =
    if (dim) lerp(focus.halo, color, focus.dimAlpha) else color

/** Far enough outside the canvas that a clip never cuts a stroke that overhangs it. */
private const val FAR = 1e5f

/**
 * Runs [block] once per region of the canvas split at [splitX] (left, then right with
 * `right = true`) and at [splitY] (above, then below with `below = true`), clipped to that region.
 * NaN means no split, and no clip, on that axis. One traced path painted in parts keeps its joins,
 * caps and gaps exactly as a single draw would: the scrub trail, and the two sides of zero.
 */
private inline fun DrawScope.regions(splitX: Float, splitY: Float, block: DrawScope.(right: Boolean, below: Boolean) -> Unit) {
    if (splitX.isNaN() && splitY.isNaN()) {
        block(false, false)
        return
    }
    val columns = if (splitX.isNaN()) 1 else 2
    val rows = if (splitY.isNaN()) 1 else 2
    for (i in 0 until columns) for (j in 0 until rows) {
        val l = if (i == 1) splitX else -FAR
        val r = if (i == 0 && columns == 2) splitX else FAR
        val t = if (j == 1) splitY else -FAR
        val b = if (j == 0 && rows == 2) splitY else FAR
        clipRect(l, t, r, b) { block(i == 1, j == 1) }
    }
}

/** The row to split a diverging node's paint at, or NaN; kept inside the clip's reach. */
private fun SignSplit?.row(): Float = this?.zero?.coerceIn(-FAR, FAR) ?: Float.NaN

/** Where the scrub trail starts receding: the needle, or NaN when nothing recedes. */
private fun trailX(style: ChartStyle, focus: Focus?): Float =
    if (focus != null && focus.mode == FocusMode.NearestX && style.focus.dimTrail && style.focus.dimAlpha < 1f) focus.anchor.x
    else Float.NaN

/** Paints a scene. Pure function of its inputs; called every animation frame. */
internal fun DrawScope.drawScene(scene: ChartScene, style: ChartStyle, focus: Focus?, texts: TextCache) {
    texts.prepareForScene(scene)
    val path = texts.scratchPath
    val sink = texts.scratchSink
    val trail = trailX(style, focus)
    scene.guides.forEach { if (it is RuleNode) drawRule(it, style) }
    if (scene.clipPlot) {
        val p = scene.plot
        clipRect(p.left, p.top, p.right, p.bottom) {
            style.bar.track?.let { drawTracks(scene, style, it) }
            scene.marks.forEach { node -> drawMark(node, scene, style, focus, trail, path, sink) }
        }
    } else {
        style.bar.track?.let { drawTracks(scene, style, it) }
        scene.marks.forEach { node -> drawMark(node, scene, style, focus, trail, path, sink) }
    }
    scene.labels.forEach { if (it is TextNode) drawLabel(it, texts, style) }
    if (focus != null) drawFocus(scene, style, focus)
}

private fun DrawScope.drawRule(node: RuleNode, style: ChartStyle, dim: Boolean = false) {
    val grid = style.grid
    val color = when (node.kind) {
        RuleKind.GridY -> grid.color.takeIf { grid.horizontal }
        RuleKind.GridX -> grid.color.takeIf { grid.vertical }
        RuleKind.AxisX, RuleKind.AxisY -> grid.axisLines
        RuleKind.Baseline -> grid.baseline
        RuleKind.Reference -> grid.reference
        RuleKind.XRule -> node.color?.let { style.color(it).copy(alpha = grid.xRule.alpha.coerceAtLeast(0.6f)) } ?: grid.xRule
        RuleKind.Wick -> style.dimmed(style.candle.color(node.down), dim)
    } ?: return
    val isGrid = node.kind == RuleKind.GridX || node.kind == RuleKind.GridY
    var cap = if (isGrid && grid.roundCaps) StrokeCap.Round else StrokeCap.Butt
    val width = when (node.kind) {
        RuleKind.Reference -> grid.referenceWidth.toPx()
        RuleKind.XRule -> grid.xRuleWidth.toPx()
        RuleKind.Wick -> style.candle.wickWidth.toPx()
        else -> grid.width.toPx()
    }
    val pattern = when {
        node.kind == RuleKind.XRule && node.pattern != null -> when (node.pattern!!) {
            LinePattern.Solid -> StrokePattern.Solid
            LinePattern.Dashed -> StrokePattern.Dashed(6.dp, 4.dp)
            LinePattern.Dotted -> StrokePattern.Dotted(5.dp)
        }
        node.kind == RuleKind.Reference -> grid.referencePattern
        node.kind == RuleKind.XRule -> grid.xRulePattern
        else -> null
    }
    val effect = when {
        pattern != null -> when (pattern) {
            StrokePattern.Solid -> null
            is StrokePattern.Dashed -> PathEffect.dashPathEffect(floatArrayOf(pattern.on.toPx(), pattern.off.toPx()))
            is StrokePattern.Dotted -> {
                // A near-zero dash with a round cap is a dot as wide as the stroke.
                cap = StrokeCap.Round
                PathEffect.dashPathEffect(floatArrayOf(0.01f, max(0.01f, pattern.spacing.toPx() - 0.01f)))
            }
        }
        isGrid -> grid.dash?.let { (on, off) -> PathEffect.dashPathEffect(floatArrayOf(on.toPx(), off.toPx())) }
        else -> null
    }
    drawLine(
        color, Offset(node.from.x, node.from.y), Offset(node.to.x, node.to.y), width,
        cap = cap, pathEffect = effect, alpha = node.alpha,
    )
}

private val RectNode.horizontal: Boolean get() = base == Edge.Left || base == Edge.Right

private fun DrawScope.drawTracks(scene: ChartScene, style: ChartStyle, track: Color) {
    val rects = scene.marks.filterIsInstance<RectNode>().filter { it.kind == RectKind.Bar }
    rects.distinctBy { if (it.horizontal) Triple(true, it.bounds.top, it.bounds.bottom) else Triple(false, it.bounds.left, it.bounds.right) }.forEach { r ->
        // Across the plot: full height under an upright bar, full width behind a horizontal one.
        val thickness = if (r.horizontal) r.bounds.height else r.bounds.width
        val radius = when (style.bar.corners) {
            BarCorners.Pill -> thickness / 2f
            is BarCorners.End -> min(style.bar.corners.radius.toPx(), thickness / 2f)
            BarCorners.Square -> 0f
        }
        val (topLeft, size) = if (r.horizontal) {
            Offset(scene.plot.left, r.bounds.top) to Size(scene.plot.width, thickness)
        } else {
            Offset(r.bounds.left, scene.plot.top) to Size(thickness, scene.plot.height)
        }
        drawRoundRect(track, topLeft, size, CornerRadius(radius), alpha = r.alpha)
    }
}

private fun DrawScope.drawMark(node: Node, scene: ChartScene, style: ChartStyle, focus: Focus?, trail: Float, path: Path, sink: PathSink) {
    when (node) {
        is RectNode -> when (node.kind) {
            RectKind.Bar -> {
                // Scrubbing columns or rows: bars outside the focused category recede.
                val dim = focus != null && (focus.mode == FocusMode.NearestX || focus.mode == FocusMode.NearestY) &&
                    node.datum != focus.primary.datum
                drawBar(node, style.dimmed(style.color(scene.slot(node.series)), dim), node.alpha, style.bar.corners, path)
            }
            // Candles and volume are painted by direction, never by palette.
            RectKind.Candle -> drawCandle(node, style.dimmed(style.candle.color(node.down), periodDimmed(node.datum, node.bounds.centerX, focus, trail)), style)
            RectKind.Volume -> {
                val b = node.bounds
                if (b.width <= 0f || b.height <= 0f) return
                val color = style.dimmed(style.candle.color(node.down), periodDimmed(node.datum, node.bounds.centerX, focus, trail))
                drawRect(color, Offset(b.left, b.top), Size(b.width, b.height), node.alpha * style.candle.volumeAlpha)
            }
            // Painted by its raw value on the diverging scale, never by palette; the other tiles
            // recede while one is focused.
            RectKind.Tile -> {
                val b = node.bounds
                if (b.width <= 0f || b.height <= 0f) return
                val dim = focus != null && focus.mode == FocusMode.Containment &&
                    !(focus.primary.series == node.series && focus.primary.datum == node.datum)
                drawRoundRect(
                    style.dimmed(style.diverging.color(node.value), dim), Offset(b.left, b.top), Size(b.width, b.height),
                    CornerRadius(min(style.treemap.corner.toPx(), min(b.width, b.height) / 2f)), alpha = node.alpha,
                )
            }
            // Painted by intensity on the sequential scale; missing data in the empty colour, never
            // the colour of zero. The other cells with data recede while one is focused; empty
            // cells already have, and mixing a translucent empty colour would lighten or darken it.
            RectKind.Cell -> {
                val b = node.bounds
                if (b.width <= 0f || b.height <= 0f || node.alpha <= 0f) return
                val missing = node.intensity.isNaN()
                val dim = !missing && focus != null && focus.mode == FocusMode.Containment &&
                    !(focus.primary.series == node.series && focus.primary.datum == node.datum)
                val color = if (missing) style.heatmap.empty else style.sequential.color(node.intensity)
                drawRoundRect(
                    style.dimmed(color, dim), Offset(b.left, b.top), Size(b.width, b.height),
                    CornerRadius(min(style.heatmap.corner.toPx(), min(b.width, b.height) / 2f)), alpha = node.alpha,
                )
            }
            RectKind.Band -> {
                val b = node.bounds
                if (b.width <= 0f || b.height <= 0f || node.alpha <= 0f) return
                val color = node.color?.let { style.color(it).copy(alpha = style.grid.band.alpha) } ?: style.grid.band
                drawRect(color, Offset(b.left, b.top), Size(b.width, b.height), alpha = node.alpha)
            }
        }
        is AreaNode -> {
            path.reset()
            (node.curve ?: style.curve).traceArea(node.top, node.bottom, sink)
            val split = node.signSplit
            val above = style.color(scene.slot(node.series))
            val below = if (split != null) style.color(scene.slotBelow(node.series)) else above
            val fill = style.area.fill
            regions(trail, split.row()) { right, low ->
                // Low is the region past zero's row: losses, unless the value axis is reversed.
                val negative = split != null && low == split.negativeBelow
                val color = style.dimmed(if (negative) below else above, right)
                when (fill) {
                    is AreaFill.Solid -> drawPath(path, color, alpha = fill.alpha * node.alpha)
                    // A polar area (a radar polygon) has no baseline to fade toward: fill it evenly.
                    is AreaFill.Gradient if scene.origin != null -> drawPath(path, color, alpha = fill.topAlpha * node.alpha)
                    // Each side fades from the plot edge farthest from zero to the baseline; an area
                    // filled to the plot's bottom fades toward that instead.
                    is AreaFill.Gradient -> drawPath(
                        path,
                        Brush.verticalGradient(
                            listOf(color.copy(alpha = fill.topAlpha), color.copy(alpha = fill.bottomAlpha)),
                            startY = if (low) scene.plot.bottom else scene.plot.top, endY = node.floor ?: scene.baseline,
                        ),
                        alpha = node.alpha,
                    )
                }
            }
        }
        is LineNode -> {
            val width = if (node.isAreaEdge) style.area.edgeWidth else style.line.width
            if (width.value <= 0f || node.points.none { it.isDefined }) return
            path.reset()
            (node.curve ?: style.curve).trace(node.points, sink)
            val split = node.signSplit
            val above = style.color(scene.slot(node.series))
            val below = if (split != null) style.color(scene.slotBelow(node.series)) else above
            val strokePx = width.toPx()
            val metrics = style.metrics(this)
            val (cap, effect) = when (node.pattern) {
                LinePattern.Solid -> StrokeCap.Round to null
                LinePattern.Dashed -> StrokeCap.Butt to PathEffect.dashPathEffect(
                    floatArrayOf(metrics.lineDashOn, max(0.01f, metrics.lineDashOff)),
                )
                LinePattern.Dotted -> StrokeCap.Round to PathEffect.dashPathEffect(
                    floatArrayOf(0.01f, max(0.01f, metrics.lineDotSpacing - 0.01f)),
                )
            }
            val stroke = Stroke(strokePx, cap = cap, join = StrokeJoin.Round, pathEffect = effect)
            regions(trail, split.row()) { right, low ->
                val negative = split != null && low == split.negativeBelow
                val color = style.dimmed(if (negative) below else above, right)
                drawPath(path, color, alpha = node.alpha, style = stroke)
                // A singleton run is a zero-length segment, which Butt-capped dashes would drop.
                if (node.pattern != LinePattern.Solid) {
                    val pts = node.points
                    for (i in pts.indices) {
                        val p = pts[i]
                        if (p.isDefined && (i == 0 || !pts[i - 1].isDefined) && (i == pts.lastIndex || !pts[i + 1].isDefined)) {
                            drawCircle(color, strokePx / 2f, Offset(p.x, p.y), alpha = node.alpha)
                        }
                    }
                }
            }
        }
        is DotNode -> {
            if (node.isMarker && !style.line.showMarkers) return
            if (node.radius <= 0f) return
            val slot = scene.slot(node.series)
            val focused = focus?.primary?.let { it.series == node.series && it.datum == node.datum } ?: true
            val dim = if (node.isMarker) node.center.x > trail + 0.5f // false while trail is NaN
            else focus != null && focus.mode == FocusMode.NearestXY && !focused
            val alpha = node.alpha * (if (node.isMarker) 1f else style.dot.alpha)
            val center = Offset(node.center.x, node.center.y)
            val glyph = style.glyph(slot)
            style.dot.outline?.let { outline ->
                with(glyph) { draw(center, node.radius + style.dot.outlineWidth.toPx(), outline, node.alpha) }
            }
            with(glyph) { draw(center, node.radius, style.dimmed(style.color(slot), dim), alpha) }
        }
        is ArcNode -> {
            val focused = focus?.primary?.datum == node.datum && focus.mode == FocusMode.Containment
            val dim = focus != null && focus.mode == FocusMode.Containment && !focused
            val expand = if (focused) style.focus.arcExpand.toPx() else 0f
            // Round caps are for rings; a rose petal keeps square ends, so its area stays true.
            val round = style.arc.cap == ArcCap.Round && scene.origin == null
            drawArcNode(node, style.dimmed(style.color(scene.slot(node.series)), dim), node.alpha, round, expand, path)
        }
        is RuleNode -> drawRule(node, style, dim = node.kind == RuleKind.Wick && periodDimmed(node.datum, node.from.x, focus, trail))
        is TextNode -> Unit
    }
}

/**
 * Whether a candle, its wick or its volume bar recedes while scrubbing: right of the needle when
 * the trail dims, otherwise every period but the focused one, as bars do.
 */
private fun periodDimmed(datum: Int, x: Float, focus: Focus?, trail: Float): Boolean {
    if (focus == null || focus.mode != FocusMode.NearestX) return false
    return if (!trail.isNaN()) x > trail + 0.5f else datum != focus.primary.datum
}

/**
 * A candle body. One thinner than its wick (a doji, or a body still opening out) is a bar as thick
 * as the wick across it, so a flat period still shows where it opened and closed.
 */
private fun DrawScope.drawCandle(node: RectNode, color: Color, style: ChartStyle) {
    val b = node.bounds
    val w = b.width
    val h = b.height
    if (w <= 0f || node.alpha <= 0f) return
    val candle = style.candle
    val stroke = candle.wickWidth.toPx()
    if (h < stroke) {
        drawRect(color, Offset(b.left, b.centerY - stroke / 2f), Size(w, stroke), node.alpha)
        return
    }
    val r = CornerRadius(min(candle.bodyCorner.toPx(), min(w, h) / 2f))
    if (candle.hollowUp && !node.down && w > stroke * 2f && h > stroke * 2f) {
        // Filled with the surface first, so the wick does not show through the outline.
        drawRoundRect(style.focus.halo, Offset(b.left, b.top), Size(w, h), r, alpha = node.alpha)
        drawRoundRect(
            color, Offset(b.left + stroke / 2f, b.top + stroke / 2f), Size(w - stroke, h - stroke), r,
            alpha = node.alpha, style = Stroke(stroke),
        )
    } else {
        drawRoundRect(color, Offset(b.left, b.top), Size(w, h), r, alpha = node.alpha)
    }
}

private fun DrawScope.drawBar(node: RectNode, color: Color, alpha: Float, corners: BarCorners, path: Path) {
    val b = node.bounds
    val w = b.width
    val h = b.height
    if (w <= 0f || h <= 0.01f) return
    when (corners) {
        BarCorners.Square -> drawRect(color, Offset(b.left, b.top), Size(w, h), alpha)
        BarCorners.Pill -> drawRoundRect(color, Offset(b.left, b.top), Size(w, h), CornerRadius(min(w, h) / 2f), alpha = alpha)
        is BarCorners.End -> {
            if (!node.isEnd) {
                drawRect(color, Offset(b.left, b.top), Size(w, h), alpha)
                return
            }
            // The end away from the base rounds, up to half the bar's thickness and its full length.
            val r = CornerRadius(if (node.horizontal) min(corners.radius.toPx(), min(h / 2f, w)) else min(corners.radius.toPx(), min(w / 2f, h)))
            val rect = Rect(b.left, b.top, b.right, b.bottom)
            val rounded = when (node.base) {
                Edge.Bottom -> RoundRect(rect, topLeft = r, topRight = r)
                Edge.Top -> RoundRect(rect, bottomLeft = r, bottomRight = r)
                Edge.Left -> RoundRect(rect, topRight = r, bottomRight = r)
                Edge.Right -> RoundRect(rect, topLeft = r, bottomLeft = r)
            }
            path.reset()
            path.addRoundRect(rounded)
            drawPath(path, color, alpha)
        }
    }
}

private fun degrees(radians: Float): Float = (radians * 180.0 / PI).toFloat()

private fun DrawScope.drawArcNode(node: ArcNode, color: Color, alpha: Float, round: Boolean, expand: Float, path: Path) {
    val sweep = node.endAngle - node.startAngle
    // No ring to paint: a zero petal, or one still growing out of its inner radius.
    if (sweep <= 0f || node.outerRadius <= 0f || node.outerRadius <= node.innerRadius) return
    // A focused slice slides outward along its bisector.
    val mid = (node.startAngle + node.endAngle) / 2f
    val cx = node.center.x + sin(mid) * expand
    val cy = node.center.y - cos(mid) * expand
    val inner = node.innerRadius
    val outer = node.outerRadius
    if (round && inner > 0f) {
        // Round caps: stroke along the middle of the ring, pulling each end in by the cap's radius.
        val thickness = outer - inner
        val radius = (outer + inner) / 2f
        val capAngle = (thickness / 2f) / radius
        var a0 = node.startAngle + capAngle
        var a1 = node.endAngle - capAngle
        if (a1 < a0) { a0 = mid; a1 = mid }
        drawArc(
            color, degrees(a0) - 90f, degrees(a1 - a0).coerceAtLeast(0.01f), useCenter = false,
            topLeft = Offset(cx - radius, cy - radius), size = Size(radius * 2f, radius * 2f),
            alpha = alpha, style = Stroke(thickness, cap = StrokeCap.Round),
        )
        return
    }
    val sweepDegrees = degrees(sweep).coerceAtMost(359.99f)
    path.reset()
    path.arcTo(Rect(Offset(cx, cy), outer), degrees(node.startAngle) - 90f, sweepDegrees, forceMoveTo = true)
    if (inner > 0f) {
        path.arcTo(Rect(Offset(cx, cy), inner), degrees(node.startAngle) - 90f + sweepDegrees, -sweepDegrees, forceMoveTo = false)
    } else {
        path.lineTo(cx, cy)
    }
    path.close()
    drawPath(path, color, alpha)
}

private fun DrawScope.drawLabel(node: TextNode, texts: TextCache, style: ChartStyle) {
    if (node.alpha <= 0.01f) return
    val layout = texts.layout(node.text, node.kind)
    val w = layout.size.width
    val h = layout.size.height
    val x = when (node.h) {
        HAlign.Start -> node.at.x
        HAlign.Center -> node.at.x - w / 2f
        HAlign.End -> node.at.x - w
    }
    val y = when (node.v) {
        VAlign.Top -> node.at.y
        VAlign.Center -> node.at.y - h / 2f
        VAlign.Bottom -> node.at.y - h
    }
    if (node.kind == TextKind.Reference) {
        style.grid.referenceChip?.let { chip ->
            val padX = 6.dp.toPx()
            val padY = 2.dp.toPx()
            drawRoundRect(
                chip, Offset(x - padX, y - padY), Size(w + padX * 2, h + padY * 2),
                CornerRadius((h + padY * 2) / 2f), alpha = node.alpha,
            )
        }
    }
    drawText(layout, topLeft = Offset(x, y), alpha = node.alpha)
}

private fun DrawScope.drawFocus(scene: ChartScene, style: ChartStyle, focus: Focus) {
    val markerRadius = style.focus.markerRadius.toPx()
    val halo = 2.5.dp.toPx()
    // The glyph follows the series; the colour follows the value's sign on a diverging area.
    fun marker(point: FocusPoint, radius: Float) {
        val glyph = style.glyph(scene.slot(point.series))
        val c = Offset(point.x, point.y)
        with(glyph) {
            draw(c, radius + halo, style.focus.halo, 1f)
            draw(c, radius, style.color(scene.colorSlot(point)), 1f)
        }
    }
    when (focus.mode) {
        FocusMode.NearestX -> {
            val traced = focus.group.filter { it.kind == MarkKind.Line || it.kind == MarkKind.Area }
            // Candles get the needle too; bars alone do not.
            if (traced.isEmpty() && focus.group.none { it.kind == MarkKind.Candle }) return
            if (style.focus.showRule) {
                drawLine(
                    style.focus.rule, Offset(focus.anchor.x, scene.plot.top), Offset(focus.anchor.x, scene.plot.bottom),
                    style.focus.ruleWidth.toPx(), cap = StrokeCap.Round,
                )
            }
            if (style.focus.showMarkers) traced.forEach { marker(it, markerRadius) }
        }
        FocusMode.NearestY -> Unit // the other rows recede in drawMark
        FocusMode.NearestXY -> marker(focus.primary, style.dot.radius.toPx() * 1.35f)
        FocusMode.Containment -> Unit // the slice pops out, or the other tiles recede, in drawMark
    }
}
