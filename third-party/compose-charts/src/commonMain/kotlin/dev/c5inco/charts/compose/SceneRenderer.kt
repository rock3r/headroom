package dev.c5inco.charts.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.c5inco.charts.core.ArcKind
import dev.c5inco.charts.core.ArcNode
import dev.c5inco.charts.core.AreaKind
import dev.c5inco.charts.core.AreaNode
import dev.c5inco.charts.core.ChartScene
import dev.c5inco.charts.core.DotNode
import dev.c5inco.charts.core.Edge
import dev.c5inco.charts.core.Extent
import dev.c5inco.charts.core.Focus
import dev.c5inco.charts.core.FocusDetail
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
import dev.c5inco.charts.core.Span
import dev.c5inco.charts.core.SignSplit
import dev.c5inco.charts.core.colorSlot
import dev.c5inco.charts.core.TextKind
import dev.c5inco.charts.core.TextMeasure
import dev.c5inco.charts.core.TextNode
import dev.c5inco.charts.core.VAlign
import dev.c5inco.charts.core.isDefined
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
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
 *
 * Text laid out while its font was still loading is laid out again once the font arrives: see
 * [fontsRevision], which the scene's inputs are keyed on.
 */
internal class TextCache(
    private val measurer: TextMeasurer,
    private val styles: ChartTextStyles,
    private val capacity: Int = 512,
) : TextMeasure {
    constructor(measurer: TextMeasurer, style: ChartStyle) : this(measurer, style.textStyles())

    internal class Entry(val extent: Extent, val inkHeight: Float)
    private class CachedLayout(val layout: TextLayoutResult, val entry: Entry)

    private val ink = InkMeter()

    init { require(capacity > 0) }

    // Reusable path and sink for drawScene, avoiding per-frame allocations on the draw path.
    internal val scratchPath = Path()
    internal val scratchSink: PathSink = ComposePathSink(scratchPath)

    // The same sink, drawing dense curved segments straight: for solid lines and areas (see
    // [StraightensDense]). [straightenDense] false traces every curve as it is, the reference the
    // straightened drawing is tested against.
    internal val straightSink: PathSink = StraightensDense(scratchSink)
    internal var straightenDense = true

    // The stroked outline of a dense line (see [OUTLINE_MIN_POINTS]); [outlineStrokes] false draws
    // every line as a stroke instead, the reference the outline is tested against.
    internal val scratchOutline = Path()
    internal var outlineStrokes = true

    // A dense solid line made of straight segments is outlined as the region between its stroke's
    // lowest and highest point at each x ([RoundStrokeOutline]) rather than by the stroker:
    // [envelopeOutlines] false uses the stroker's outline for every line, the reference it is
    // tested against.
    internal val roundOutline = RoundStrokeOutline()
    internal val roundOutlineStraight: PathSink = StraightensDense(roundOutline)
    internal val outlineSink: PathSink = ComposePathSink(scratchOutline)
    internal var envelopeOutlines = true

    /** How many lines were outlined through [roundOutline]: for tests. */
    internal var envelopedLines = 0

    /** How many lines were drawn as an outline: for tests. */
    internal var outlinedLines = 0

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

    // Fonts still loading. A font family can load asynchronously: until it arrives, text is laid
    // out in a fallback typeface, and Compose tells only by snapshot state that the layout went
    // stale (`ParagraphIntrinsics.hasStaleResolvedFonts`). Every label of a kind has the same style,
    // so the same fonts: one layout per kind laid out while one of them was loading stands for the
    // rest, and [fontsRevision] checks those few, never a hit. Not tied to cache residency: a scene
    // can hold sizes whose layouts were evicted (a background compile's [known] copy).
    private val waiting = arrayOfNulls<TextLayoutResult>(TextKind.entries.size)
    private var waitingKinds = 0
    private var fontRevision = 0
    private var measureReadState = false
    private val noteRead: (Any) -> Unit = { measureReadState = true }

    /**
     * How many times a font text was laid out in while it loaded has since arrived. Each arrival
     * drops every layout of the kinds in that font, so they are laid out again in it; a scene
     * compiled before then has the fallback's sizes, so compile scenes keyed on this.
     *
     * It reads the loading fonts' state, so their arrival invalidates the reader: [Chart] reads it
     * in composition, before compiling, so the scene compiles again, and [ScenePainter.paint] once
     * per recording, so the marks are drawn again even when that scene is unchanged and for text
     * first laid out while drawing. It changes only plain fields, never snapshot state. Constant
     * time until some text is laid out while its font loads; then a check of at most one layout
     * per [TextKind], until the font arrives.
     */
    fun fontsRevision(): Int {
        if (waitingKinds == 0) return fontRevision
        for (i in waiting.indices) {
            // Every waiting layout is read, so the reader is subscribed to all of their fonts.
            val layout = waiting[i] ?: continue
            if (!layout.multiParagraph.intrinsics.hasStaleResolvedFonts) continue
            waiting[i] = null
            waitingKinds--
            fontRevision++
            val kind = TextKind.entries[i]
            val each = layouts.entries.iterator()
            while (each.hasNext()) if (each.next().key.second == kind) each.remove()
        }
        return fontRevision
    }

    private fun cached(text: String, kind: TextKind): CachedLayout = layouts.getOrPut(text to kind) {
        val style = styles.of(kind)
        // A miss only: a layout that read snapshot state (a font still loading) may go stale.
        measureReadState = false
        val cached = Snapshot.observe(readObserver = noteRead) {
            val layout = measurer.measure(text, style, maxLines = 1, softWrap = false)
            val extent = Extent(layout.size.width.toFloat(), layout.size.height.toFloat())
            CachedLayout(layout, Entry(extent, ink.height(text, style, layout)))
        }
        if (measureReadState && waiting[kind.ordinal] == null) {
            waiting[kind.ordinal] = cached.layout
            waitingKinds++
        }
        cached
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

/**
 * Palette slot for a scene series index: for focus points and tooltips, which belong to the scene.
 * Marks never use it: each node carries its own slot, so it paints the same in any scene it is drawn in.
 */
internal fun ChartScene.slot(series: Int): Int = this.series.getOrNull(series)?.color ?: series

/**
 * The axis position a scrubbing focus ([FocusMode.NearestX], [FocusMode.NearestY]) is on: the datum
 * of its points aligned to the category or x axis ([FocusDetail.Aligned]), which every series drawn
 * there shares. Never a dot's datum, which indexes the dot series' own points: a dot can be the
 * primary point above a bar without being at that bar's index. Null when only dots are focused.
 */
internal fun Focus.position(): Int? =
    if (primary.detail is FocusDetail.Aligned) primary.datum else group.firstOrNull { it.detail is FocusDetail.Aligned }?.datum

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

/**
 * The gradient of an area whose [AreaKind.Upright.fadeFrom] is between [plot]'s edges: a tween
 * turning it over, from fading off one edge to fading off the other. It crossfades the two,
 * weighted by how far [from] has come across the plot: one gradient over the whole plot, faint at
 * [end], whose two ends trade [AreaFill.Gradient.topAlpha] for [AreaFill.Gradient.bottomAlpha].
 * Settled, at either edge, it is the plain gradient from that edge to [end]; so nothing jumps where
 * [from] passes [end], as a gradient from [from] to [end] would (its two sides trade alphas there).
 */
private fun turningGradient(color: Color, fill: AreaFill.Gradient, plot: Span, from: Float, end: Float): Brush {
    val w = ((from - plot.start) / plot.length).coerceIn(0f, 1f)
    val f = ((end - plot.start) / plot.length).coerceIn(0f, 1f)
    val top = fill.topAlpha + (fill.bottomAlpha - fill.topAlpha) * w
    val bottom = fill.bottomAlpha + (fill.topAlpha - fill.bottomAlpha) * w
    return Brush.verticalGradient(
        0f to color.copy(alpha = top), f to color.copy(alpha = fill.bottomAlpha), 1f to color.copy(alpha = bottom),
        startY = plot.start, endY = plot.end,
    )
}

/** The row to split a diverging node's paint at, or NaN; kept inside the clip's reach. */
private fun SignSplit?.row(): Float = this?.zero?.coerceIn(-FAR, FAR) ?: Float.NaN

/** Where the scrub trail starts receding: the needle, or NaN when nothing recedes. */
private fun trailX(style: ChartStyle, focus: Focus?): Float =
    if (focus != null && focus.mode == FocusMode.NearestX && style.focus.dimTrail && style.focus.dimAlpha < 1f) focus.anchor.x
    else Float.NaN

/** Paints a scene: [drawMarks], then [drawFocus]. Pure function of its inputs. */
internal fun DrawScope.drawScene(scene: ChartScene, style: ChartStyle, focus: Focus?, texts: TextCache) {
    drawMarks(scene, style, { focus }, texts)
    if (focus != null) drawFocus(scene, style, focus)
}

/**
 * Whether any mark's paint depends on the focus under [style]: the scrub trail, when it recedes
 * ([trailX]); or a mark [drawMark] dims, pops out or recedes by focus (bars, candles and their wicks
 * and volume, tiles, cells, slices and petals, scatter dots). Lines, areas, their markers, bands,
 * guides, tracks and labels are painted alike whatever the focus.
 */
internal fun ChartScene.marksFollowFocus(style: ChartStyle): Boolean =
    (style.focus.dimTrail && style.focus.dimAlpha < 1f) || marks.any { node ->
        when (node) {
            is RectNode -> node.kind !is RectKind.Band
            is DotNode -> !node.isMarker
            is ArcNode -> true
            is RuleNode -> node.kind is RuleKind.Wick
            is AreaNode, is LineNode, is TextNode -> false
        }
    }

/**
 * Paints a scene's guides, marks and labels: everything but the focus overlay ([drawFocus]). Reads
 * [focus] only when the marks' paint depends on it ([marksFollowFocus]), so drawing scrubbed lines
 * does not observe the focus and a new focus does not record them again. Called every animation
 * frame.
 */
internal fun DrawScope.drawMarks(scene: ChartScene, style: ChartStyle, focus: () -> Focus?, texts: TextCache) =
    paintMarks(scene, style, if (scene.marksFollowFocus(style)) focus() else null, texts)

/** Paints a scene's guides, marks and labels with [focus]: dimming, the trail, a popped-out slice. */
internal fun DrawScope.paintMarks(scene: ChartScene, style: ChartStyle, focus: Focus?, texts: TextCache) {
    texts.prepareForScene(scene)
    val path = texts.scratchPath
    val sink = texts.scratchSink
    // Only a viewport's scenes: see [OUTLINE_MIN_POINTS].
    val outline = if (outlinesDenseLines && texts.outlineStrokes && scene.clipPlot) texts.scratchOutline else null
    val trail = trailX(style, focus)
    scene.guides.forEach { if (it is RuleNode) drawRule(it, style) }
    // The marks leaving a tween lead the list ([ChartScene.leavingMarks]): never the focused ones.
    val marks = scene.marks
    val leaving = scene.leavingMarks
    if (scene.clipPlot) {
        val p = scene.plot
        clipRect(p.left, p.top, p.right, p.bottom) {
            style.bar.track?.let { drawTracks(scene, style, it) }
            for (i in marks.indices) drawMark(marks[i], i < leaving, style, focus, trail, path, sink, outline, texts)
        }
    } else {
        style.bar.track?.let { drawTracks(scene, style, it) }
        for (i in marks.indices) drawMark(marks[i], i < leaving, style, focus, trail, path, sink, outline, texts)
    }
    scene.labels.forEach { if (it is TextNode) drawLabel(it, texts, style) }
}

/**
 * Whether every mark in this scene is painted by the library's own code, which blends source-over
 * only: true unless a dot or marker is drawn with a [Glyph] other than the library's
 * ([LibraryGlyph]), which may blend against what lies under it (clear, punch out). Only such marks
 * may be isolated in a layer and kept as pixels ([ScenePainter]); state they read needs no rule,
 * since recording is driven by the framework's observation of it.
 */
internal fun ChartScene.paintedByLibrary(style: ChartStyle): Boolean =
    style.dot.glyphs.all { it is LibraryGlyph } || marks.none { it is DotNode }

private const val SQRT2 = 1.4142135f

/** The longest side of a layer kept as pixels: past it, a GPU may not allocate the layer. */
private const val MAX_LAYER_SIDE = 4096

/**
 * Paints one chart's marks ([drawMarks]: guides, tracks, marks and labels; the focus overlay is
 * drawn in a layer of its own) through [layer].
 *
 * [paint] records the marks every time it is called, and it is called from the chart's marks draw
 * node, which sits in a graphics layer of its own (its recording boundary: something else on screen
 * drawing, a sibling, an ancestor, the tooltip, does not draw it again). The framework draws it
 * again exactly when something the last drawing read has changed: the scene or its tween, the
 * style, the size, the focus when the marks follow it, and any snapshot state read by user code
 * inside (a glyph, a text brush). So nothing here decides whether the marks are up to date; when
 * nothing changed, the draw node is not run and the layer is not recorded.
 *
 * Not recording the marks again is not enough for a scrub: the renderer still replays their display
 * list on every frame the needle moves, and on Android that means stroking and rasterising every
 * dense line again on the RenderThread. So when a focus is drawn over the marks ([focusShownOver])
 * and they were not recorded since the previous frame a focus was drawn, the marks hold still while
 * it moves: the painter asks for one more recording, with the marks' ink bounds, and keeps that
 * recording's pixels as an offscreen layer (where [cachePixels]: Android's renderer keeps a
 * compositing layer's pixels until its content changes). Any new recording (a tween, a
 * paint-only animation, new data) makes it a plain display list again, as does the focus clearing
 * ([focusCleared]), which lets the pixels go. Ink bounds trace every line, so they are worked out
 * only for the recording whose pixels are kept, never on an ordinary frame (an animation, a pan).
 *
 * To be kept as pixels the marks must be painted by the library alone ([paintedByLibrary]), and the
 * layer is recorded over every pixel they can touch ([inkBounds]), not just the chart's bounds, so
 * isolating it never clips a stroke, label shadow or glyph that overhangs the chart. It is
 * composited source-over at full alpha, which paints what drawing the marks directly paints. A layer
 * that would be far larger than the chart, longer than [MAX_LAYER_SIDE] on a side, or empty is
 * never kept.
 *
 * Main thread only, like [TextCache].
 */
internal class ScenePainter(private val layer: GraphicsLayer, private val cachePixels: Boolean = layerCachesPixels) {
    // Requests to keep the pixels (from a still scrub), read while recording: a request records the
    // marks once more, with their ink bounds, and keeps that recording's pixels. Ink bounds trace
    // every line, so they are worked out only then, never on an ordinary frame.
    private val keepRequests = mutableIntStateOf(0)
    private var keepServed = 0

    // What was recorded last: its scene, and whether its marks may be kept (they do not follow the focus).
    private var scene: ChartScene? = null
    private var mayKeep = false
    private var refused = false

    // [records] as of the last frame a focus was drawn, or -1: no recording in between means the marks held still.
    private var recordsAtFocus = -1

    /** Whether the layer currently keeps its pixels, and whether it was released: for tests. */
    internal val cached: Boolean get() = !layer.isReleased && layer.compositingStrategy == CompositingStrategy.Offscreen
    internal val released: Boolean get() = layer.isReleased

    /** How often the marks were recorded, and how often they started being kept as pixels: for tests. */
    internal var records = 0
        private set
    internal var offscreenSettles = 0
        private set

    /** The scene last recorded: for tests. */
    internal val recordedScene: ChartScene? get() = scene

    /** Whether a request to keep the pixels awaits the next recording (the framework draws again for it): for tests. */
    internal val wantsRecording: Boolean get() = keepRequests.intValue != keepServed

    /** Where the layer sits and how far it reaches, in the chart's coordinates: for tests. */
    internal val layerBounds: IntRect
        get() = IntRect(layer.topLeft.x, layer.topLeft.y, layer.topLeft.x + layer.size.width, layer.topLeft.y + layer.size.height)

    /** Records and draws [scene]'s marks, reading [focus] only when their paint depends on it, as [drawMarks]. */
    fun DrawScope.paint(scene: ChartScene, style: ChartStyle, focus: () -> Focus?, texts: TextCache) {
        // Once per recording, never per label: a font arriving draws the marks again, with their
        // text laid out in it, even when the scene compiled after it is the same (the label's size
        // moved nothing) or its text was first laid out here, while drawing.
        texts.fontsRevision()
        val follows = scene.marksFollowFocus(style)
        val marksFocus = if (follows) focus() else null
        val chart = IntRect(0, 0, ceil(size.width).toInt(), ceil(size.height).toInt())
        // A scrub keeps only marks that do not follow the focus, drawn by the library alone.
        val mayKeep = cachePixels && !follows && scene.paintedByLibrary(style)
        val requested = keepRequests.intValue
        val keepNow = mayKeep && requested != keepServed
        keepServed = requested
        // Bounded here, inside the observed draw, so keeping the pixels never records outside it.
        val area = if (keepNow) keepableArea(chart, scene, style, texts) else null
        // A recording too large to keep stays refused until the marks are recorded again for a
        // change of their own, so a still focus does not keep asking for one that cannot be served.
        refused = keepNow && area == null
        this@ScenePainter.scene = scene
        this@ScenePainter.mayKeep = mayKeep
        records++
        val recorded = area ?: chart
        layer.topLeft = recorded.topLeft
        layer.record(this, layoutDirection, recorded.size) {
            translate(-recorded.left.toFloat(), -recorded.top.toFloat()) { paintMarks(scene, style, marksFocus, texts) }
        }
        // A new recording is a plain display list until it is seen to hold still.
        if (area == null) {
            layer.compositingStrategy = CompositingStrategy.Auto
        } else {
            layer.compositingStrategy = CompositingStrategy.Offscreen
            offscreenSettles++
        }
        drawLayer(layer)
    }

    /**
     * Draws the marks as last recorded, without recording: what the chart's display list does on a
     * frame its draw node does not run. For tests.
     */
    internal fun DrawScope.replay() = drawLayer(layer)

    /**
     * Tells the painter a focus is being drawn over [scene], from the focus layer's draw. When the
     * marks were not recorded since the previous frame a focus was drawn, they hold still while it
     * moves: ask to keep their pixels.
     */
    fun focusShownOver(scene: ChartScene) {
        val still = records == recordsAtFocus
        recordsAtFocus = records
        if (still && scene === this.scene) requestKeep()
    }

    /** Tells the painter no focus is shown: let the kept pixels go. */
    fun focusCleared() {
        recordsAtFocus = -1
        layer.compositingStrategy = CompositingStrategy.Auto
    }

    /** Asks the framework to record the marks once more and keep that recording's pixels. */
    private fun requestKeep() {
        if (!mayKeep || refused || cached || wantsRecording || layer.isReleased) return
        keepRequests.intValue++
    }

    /** The area to record marks that may be kept as pixels, or null when they may not. */
    private fun DrawScope.keepableArea(chart: IntRect, scene: ChartScene, style: ChartStyle, texts: TextCache): IntRect? {
        if (chart.width <= 0 || chart.height <= 0) return null
        val ink = inkBounds(scene, style, null, texts) ?: return null
        val covered = chart.union(ink)
        // A layer far larger than the chart (a mark drawn far outside it) is not worth its memory.
        if (covered.area() > 4L * chart.area() || covered.width > MAX_LAYER_SIDE || covered.height > MAX_LAYER_SIDE) return null
        return covered
    }
}

/** Receives each [ScenePainter] a [Chart] creates, where a test provides it; null everywhere else. */
internal val LocalScenePainterProbe = staticCompositionLocalOf<((ScenePainter) -> Unit)?> { null }

@Composable
internal fun rememberScenePainter(): ScenePainter {
    val layer = rememberGraphicsLayer()
    val probe = LocalScenePainterProbe.current
    return remember(layer) { ScenePainter(layer).also { probe?.invoke(it) } }
}

private fun IntRect.area(): Long = width.toLong() * height.toLong()

private fun IntRect.union(other: IntRect) =
    IntRect(min(left, other.left), min(top, other.top), max(right, other.right), max(bottom, other.bottom))

/** Collects the extent of what it is given, each point grown by [pad]: a traced path's control points bound its curve. */
private class InkExtent : PathSink {
    var left = Float.POSITIVE_INFINITY
    var top = Float.POSITIVE_INFINITY
    var right = Float.NEGATIVE_INFINITY
    var bottom = Float.NEGATIVE_INFINITY
    var pad = 0f

    fun add(x: Float, y: Float, r: Float = pad) {
        if (!x.isFinite() || !y.isFinite()) return
        left = min(left, x - r)
        top = min(top, y - r)
        right = max(right, x + r)
        bottom = max(bottom, y + r)
    }

    fun add(left: Float, top: Float, right: Float, bottom: Float, r: Float) {
        add(left, top, r)
        add(right, bottom, r)
    }

    override fun moveTo(x: Float, y: Float) = add(x, y)
    override fun lineTo(x: Float, y: Float) = add(x, y)
    override fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) {
        add(x1, y1)
        add(x2, y2)
        add(x, y)
    }
    override fun close() = Unit
}

/**
 * A box around every pixel [drawMarks] can touch for these inputs, rounded out and grown for
 * anti-aliasing; null when there is nothing to bound. Generous rather than tight: a Bézier curve
 * lies inside its control points, a stroke reaches half its width past its path, a glyph is held to
 * twice its radius and a label to its line box.
 */
private fun DrawScope.inkBounds(scene: ChartScene, style: ChartStyle, focus: Focus?, texts: TextCache): IntRect? {
    val ink = InkExtent()
    val grid = style.grid
    val halfStroke = max(
        max(style.line.width.toPx(), style.area.edgeWidth.toPx()),
        max(max(grid.width.toPx(), grid.referenceWidth.toPx()), max(grid.xRuleWidth.toPx(), style.candle.wickWidth.toPx())),
    ) / 2f
    // A square or round cap reaches half the width past a rule's end along it and across it.
    val ruleReach = halfStroke * SQRT2
    scene.guides.forEach { if (it is RuleNode) ink.add(it.from.x, it.from.y, it.to.x, it.to.y, ruleReach) }
    val expand = if (focus != null) style.focus.arcExpand.toPx() else 0f
    val dotReach = 2f * style.dot.outlineWidth.toPx()
    scene.marks.forEach { node ->
        when (node) {
            is RectNode -> {
                val b = node.bounds
                ink.add(b.left, b.top, b.right, b.bottom, halfStroke)
                val bar = node.kind as? RectKind.Bar
                if (bar != null && style.bar.track != null) {
                    val span = bar.track
                    if (bar.base.horizontal) ink.add(span.start, b.top, span.start + span.length, b.bottom, 0f)
                    else ink.add(b.left, span.start, b.right, span.start + span.length, 0f)
                }
            }
            is AreaNode -> {
                ink.pad = 0f
                (node.curve ?: style.curve).traceArea(node.top, node.bottom, ink)
            }
            is LineNode -> {
                ink.pad = halfStroke
                (node.curve ?: style.curve).trace(node.points, ink)
            }
            is DotNode -> {
                // Only the library's glyphs reach here ([paintedByLibrary]); the outline is the
                // same glyph, grown by the outline's width.
                val glyph = style.glyph(node.color) as? LibraryGlyph
                val center = Offset(node.center.x, node.center.y)
                if (glyph == null) {
                    ink.add(node.center.x, node.center.y, 2f * node.radius + dotReach)
                } else {
                    val box = glyph.inkBox(center, node.radius + dotReach / 2f)
                    ink.add(box.left, box.top, box.right, box.bottom, 0f)
                }
            }
            is ArcNode -> ink.add(node.center.x, node.center.y, node.outerRadius + expand + halfStroke)
            is RuleNode -> ink.add(node.from.x, node.from.y, node.to.x, node.to.y, ruleReach)
            is TextNode -> Unit
        }
    }
    val chipX = 6.dp.toPx()
    val textStyles = style.textStyles()
    scene.labels.forEach { node ->
        if (node !is TextNode || node.alpha <= 0.01f) return@forEach
        val textStyle = textStyles.of(node.kind)
        val layout = texts.layout(node.text, node.kind)
        val w = layout.size.width.toFloat()
        val h = layout.size.height.toFloat()
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
        // Glyph ink can overhang its line box a little (italics, wide glyphs): allow a line's
        // height, and the reference chip's padding. A shadow is offset and blurred past that, and
        // an outlined text style strokes half its width past its glyphs.
        val shadow = textStyle.shadow
        val shadowReach = if (shadow == null) 0f else max(abs(shadow.offset.x), abs(shadow.offset.y)) + 2f * shadow.blurRadius + 1f
        val strokeReach = (textStyle.drawStyle as? Stroke)?.width?.div(2f) ?: 0f
        ink.add(x, y, x + w, y + h, chipX + h + shadowReach + strokeReach)
    }
    if (ink.left > ink.right || ink.top > ink.bottom) return null
    // Anti-aliasing reaches a pixel past the geometry; round out to whole pixels.
    return IntRect(floor(ink.left).toInt() - 2, floor(ink.top).toInt() - 2, ceil(ink.right).toInt() + 2, ceil(ink.bottom).toInt() + 2)
}

private fun DrawScope.drawRule(node: RuleNode, style: ChartStyle, dim: Boolean = false) {
    val grid = style.grid
    val kind = node.kind
    val color = when (kind) {
        RuleKind.GridY -> grid.color.takeIf { grid.horizontal }
        RuleKind.GridX -> grid.color.takeIf { grid.vertical }
        RuleKind.AxisX, RuleKind.AxisY -> grid.axisLines
        RuleKind.Baseline -> grid.baseline
        RuleKind.Reference -> grid.reference
        is RuleKind.XRule -> kind.color?.let { style.color(it).copy(alpha = grid.xRule.alpha.coerceAtLeast(0.6f)) } ?: grid.xRule
        is RuleKind.Wick -> style.dimmed(style.candle.color(kind.down), dim)
    } ?: return
    val isGrid = kind == RuleKind.GridX || kind == RuleKind.GridY
    var cap = if (isGrid && grid.roundCaps) StrokeCap.Round else StrokeCap.Butt
    val width = when (kind) {
        RuleKind.Reference -> grid.referenceWidth.toPx()
        is RuleKind.XRule -> grid.xRuleWidth.toPx()
        is RuleKind.Wick -> style.candle.wickWidth.toPx()
        RuleKind.GridX, RuleKind.GridY, RuleKind.AxisX, RuleKind.AxisY, RuleKind.Baseline -> grid.width.toPx()
    }
    val pattern = when (kind) {
        is RuleKind.XRule -> when (kind.pattern) {
            LinePattern.Solid -> StrokePattern.Solid
            LinePattern.Dashed -> StrokePattern.Dashed(6.dp, 4.dp)
            LinePattern.Dotted -> StrokePattern.Dotted(5.dp)
            null -> grid.xRulePattern
        }
        RuleKind.Reference -> grid.referencePattern
        RuleKind.GridX, RuleKind.GridY, RuleKind.AxisX, RuleKind.AxisY, RuleKind.Baseline, is RuleKind.Wick -> null
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

/** Whether a bar resting on [this] edge is horizontal: it grows from the left or the right. */
private val Edge.horizontal: Boolean get() = this == Edge.Left || this == Edge.Right

/** What makes two bars' tracks the same track. */
private data class TrackSlot(val horizontal: Boolean, val from: Float, val to: Float, val span: Span)

private fun DrawScope.drawTracks(scene: ChartScene, style: ChartStyle, track: Color) {
    // Each bar's own base edge says which way its track runs, and its own track span how far, not
    // the scene's layout or plot: while one layout tweens to another, the outgoing bars are drawn in
    // the incoming scene and keep theirs.
    val bars = scene.marks.filterIsInstance<RectNode>().mapNotNull { r -> (r.kind as? RectKind.Bar)?.let { r to it } }
    // One track per slot and span: a stack's segments share theirs, while a bar leaving and one
    // arriving in the same slot each keep their own.
    bars.distinctBy { (r, bar) ->
        if (bar.base.horizontal) TrackSlot(true, r.bounds.top, r.bounds.bottom, bar.track) else TrackSlot(false, r.bounds.left, r.bounds.right, bar.track)
    }.forEach { (r, bar) ->
        // Across its plot: full height under an upright bar, full width behind a horizontal one.
        val horizontal = bar.base.horizontal
        val span = bar.track
        val thickness = if (horizontal) r.bounds.height else r.bounds.width
        val radius = when (style.bar.corners) {
            BarCorners.Pill -> thickness / 2f
            is BarCorners.End -> min(style.bar.corners.radius.toPx(), thickness / 2f)
            BarCorners.Square -> 0f
        }
        val (topLeft, size) = if (horizontal) {
            Offset(span.start, r.bounds.top) to Size(span.length, thickness)
        } else {
            Offset(r.bounds.left, span.start) to Size(thickness, span.length)
        }
        drawRoundRect(track, topLeft, size, CornerRadius(radius), alpha = r.alpha)
    }
}

/**
 * In a viewport's scenes (the only ones that clip to the plot), lines with at least this many points
 * are stroked into an outline while recording ([strokeOutline]) and drawn as that fill. Panning,
 * flinging or pinching a dense window redraws it on every frame, and stroking it is then most of
 * the RenderThread's work, which on a slower phone is longer than a frame while the main thread
 * waits for it: stroking on the main thread instead shortens the frame. Elsewhere a scene changes
 * only during a short tween, while the main thread is the busier of the two, and moving the
 * stroking there makes the frame longer, not shorter (measured: Pixel 4 XL entrance, Pixel 7
 * tween). Fewer points can be a single segment, which the renderer may draw as a line or rounded
 * rectangle on the GPU rather than stroking it. Solid lines of straight segments are outlined by
 * [RoundStrokeOutline], the rest by the stroker ([strokeOutline]).
 */
private const val OUTLINE_MIN_POINTS = 32

/**
 * Paints [node]. A [leaving] node (fading out of a tween, [ChartScene.leavingMarks]) is never the
 * focused one: its series and datum index the scene it comes from, and [focus] was resolved on the
 * scene it is leaving for, so it recedes wherever a focus picks out some marks and leaves the rest
 * to recede. Dimming by the trail goes by position on screen, and applies to it as to any node.
 */
private fun DrawScope.drawMark(
    node: Node, leaving: Boolean, style: ChartStyle, focus: Focus?, trail: Float, path: Path, sink: PathSink, outline: Path?, texts: TextCache,
) {
    when (node) {
        is RectNode -> when (val kind = node.kind) {
            is RectKind.Bar -> {
                // Scrubbing columns or rows: bars outside the focused category recede.
                val dim = focus != null && (focus.mode == FocusMode.NearestX || focus.mode == FocusMode.NearestY) &&
                    (leaving || kind.datum != focus.position())
                drawBar(node, kind, style.dimmed(style.color(kind.color), dim), node.alpha, style.bar.corners, path)
            }
            // Candles and volume are painted by direction, never by palette.
            is RectKind.Candle -> drawCandle(node, kind, style.dimmed(style.candle.color(kind.down), periodDimmed(kind.datum, leaving, node.bounds.centerX, focus, trail)), style)
            is RectKind.Volume -> {
                val b = node.bounds
                if (b.width <= 0f || b.height <= 0f) return
                val color = style.dimmed(style.candle.color(kind.down), periodDimmed(kind.datum, leaving, node.bounds.centerX, focus, trail))
                drawRect(color, Offset(b.left, b.top), Size(b.width, b.height), node.alpha * style.candle.volumeAlpha)
            }
            // Painted by its raw value on the diverging scale, never by palette; the other tiles
            // recede while one is focused.
            is RectKind.Tile -> {
                val b = node.bounds
                if (b.width <= 0f || b.height <= 0f) return
                val dim = focus != null && focus.mode == FocusMode.Containment &&
                    (leaving || !(focus.primary.series == kind.group && focus.primary.datum == kind.leaf))
                drawRoundRect(
                    style.dimmed(style.diverging.color(kind.value), dim), Offset(b.left, b.top), Size(b.width, b.height),
                    CornerRadius(min(style.treemap.corner.toPx(), min(b.width, b.height) / 2f)), alpha = node.alpha,
                )
            }
            // Painted by intensity on the sequential scale; missing data in the empty colour, never
            // the colour of zero. The other cells with data recede while one is focused; empty
            // cells already have, and mixing a translucent empty colour would lighten or darken it.
            is RectKind.Cell -> {
                val b = node.bounds
                if (b.width <= 0f || b.height <= 0f || node.alpha <= 0f) return
                val missing = kind.intensity.isNaN()
                val dim = !missing && focus != null && focus.mode == FocusMode.Containment &&
                    (leaving || !(focus.primary.series == kind.column && focus.primary.datum == kind.row))
                val color = if (missing) style.heatmap.empty else style.sequential.color(kind.intensity)
                drawRoundRect(
                    style.dimmed(color, dim), Offset(b.left, b.top), Size(b.width, b.height),
                    CornerRadius(min(style.heatmap.corner.toPx(), min(b.width, b.height) / 2f)), alpha = node.alpha,
                )
            }
            is RectKind.Band -> {
                val b = node.bounds
                if (b.width <= 0f || b.height <= 0f || node.alpha <= 0f) return
                val color = kind.color?.let { style.color(it).copy(alpha = style.grid.band.alpha) } ?: style.grid.band
                drawRect(color, Offset(b.left, b.top), Size(b.width, b.height), alpha = node.alpha)
            }
        }
        is AreaNode -> {
            path.reset()
            (node.curve ?: style.curve).traceArea(node.top, node.bottom, if (texts.straightenDense) texts.straightSink else sink)
            val split = node.signSplit
            // The node's own slots, never the scene's series: a node fading out of a tween to
            // another scene is drawn in that scene.
            val above = style.color(node.color)
            val below = if (split != null) style.color(split.colorBelow) else above
            val fill = style.area.fill
            // The area says how its gradient runs, never the scene, so an area fading out of a
            // scene tweening to another layout keeps its paint. An upright area fades toward its
            // floor, else its baseline, across its own plot; a polar one (a radar polygon) has no
            // baseline and fills evenly.
            val upright = when (val kind = node.kind) {
                is AreaKind.Upright -> kind
                AreaKind.Polar -> null
            }
            regions(trail, split.row()) { right, low ->
                // Low is the region past zero's row: losses, unless the value axis is reversed.
                val negative = split != null && low == split.negativeBelow
                val color = style.dimmed(if (negative) below else above, right)
                when (fill) {
                    is AreaFill.Solid -> drawPath(path, color, alpha = fill.alpha * node.alpha)
                    is AreaFill.Gradient -> if (upright == null) {
                        drawPath(path, color, alpha = fill.topAlpha * node.alpha)
                    } else {
                        // A gradient fades toward the baseline (an area filled to the plot's bottom:
                        // toward that) from the edge the area's values point to, in value terms, as
                        // the area says. A sign-split area fades each side from its own edge: the
                        // bottom below zero's row, the top above it.
                        val plot = upright.plot
                        val end = node.floor ?: upright.baseline
                        val from = when {
                            split == null -> upright.fadeFrom
                            low -> plot.end
                            else -> plot.start
                        }
                        drawPath(
                            path,
                            if (from == plot.start || from == plot.end || plot.length <= 0f) {
                                Brush.verticalGradient(
                                    listOf(color.copy(alpha = fill.topAlpha), color.copy(alpha = fill.bottomAlpha)),
                                    startY = from, endY = end,
                                )
                            } else {
                                turningGradient(color, fill, plot, from, end)
                            },
                            alpha = node.alpha,
                        )
                    }
                }
            }
        }
        is LineNode -> {
            val width = if (node.isAreaEdge) style.area.edgeWidth else style.line.width
            if (width.value <= 0f || node.points.none { it.isDefined }) return
            // A dash pattern runs along the path's length, which straightening would shorten: only
            // solid lines are straightened where dense.
            val straight = texts.straightenDense && node.pattern == LinePattern.Solid
            val curve = node.curve ?: style.curve
            val strokePx = width.toPx()
            val dense = outline != null && node.points.size >= OUTLINE_MIN_POINTS
            // A dense solid line (round caps and joins, below) made of straight segments is outlined
            // as the region its stroke covers ([RoundStrokeOutline]): a few edges where the
            // stroker's outline has several a point, which the renderer fills many times faster.
            // One with a curved segment left, or x running both ways, takes the stroker below.
            var enveloped = false
            if (dense && texts.envelopeOutlines && node.pattern == LinePattern.Solid) {
                val round = texts.roundOutline
                round.reset()
                curve.trace(node.points, if (straight) texts.roundOutlineStraight else round)
                outline!!.rewind()
                enveloped = round.outline(strokePx / 2f, texts.outlineSink)
                if (enveloped) texts.envelopedLines++
            }
            path.reset()
            if (!enveloped) curve.trace(node.points, if (straight) texts.straightSink else sink)
            val split = node.signSplit
            // The node's own slots, never the scene's series: a node fading out of a tween to
            // another scene is drawn in that scene.
            val above = style.color(node.color)
            val below = if (split != null) style.color(split.colorBelow) else above
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
            // A dense line in a viewport is stroked here, once, rather than by the renderer for each
            // region it is painted in: the same outline, filled (see [OUTLINE_MIN_POINTS]).
            val outlined = enveloped || (dense && strokeOutline(path, stroke, outline!!))
            if (outlined) texts.outlinedLines++
            regions(trail, split.row()) { right, low ->
                val negative = split != null && low == split.negativeBelow
                val color = style.dimmed(if (negative) below else above, right)
                if (outlined) drawPath(outline!!, color, alpha = node.alpha) else drawPath(path, color, alpha = node.alpha, style = stroke)
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
            val slot = node.color
            val focused = focus?.primary?.let { !leaving && it.series == node.series && it.datum == node.datum } ?: true
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
            val focused = !leaving && focus?.primary?.datum == node.datum && focus.mode == FocusMode.Containment
            val dim = focus != null && focus.mode == FocusMode.Containment && !focused
            val expand = if (focused) style.focus.arcExpand.toPx() else 0f
            // Round caps are for slices; a rose petal keeps square ends, so its area stays true.
            val round = style.arc.cap == ArcCap.Round && when (node.kind) {
                ArcKind.Slice -> true
                ArcKind.Petal -> false
            }
            drawArcNode(node, style.dimmed(style.color(node.color), dim), node.alpha, round, expand, path)
        }
        is RuleNode -> drawRule(node, style, dim = (node.kind as? RuleKind.Wick)?.let { periodDimmed(it.datum, leaving, node.from.x, focus, trail) } ?: false)
        is TextNode -> Unit
    }
}

/**
 * Whether a candle, its wick or its volume bar recedes while scrubbing: right of the needle when
 * the trail dims, otherwise every period but the focused one, as bars do (one [leaving] a tween is
 * never the focused one).
 */
private fun periodDimmed(datum: Int, leaving: Boolean, x: Float, focus: Focus?, trail: Float): Boolean {
    if (focus == null || focus.mode != FocusMode.NearestX) return false
    return if (!trail.isNaN()) x > trail + 0.5f else leaving || datum != focus.position()
}

/**
 * A candle body. One thinner than its wick (a doji, or a body still opening out) is a bar as thick
 * as the wick across it, so a flat period still shows where it opened and closed.
 */
private fun DrawScope.drawCandle(node: RectNode, candleKind: RectKind.Candle, color: Color, style: ChartStyle) {
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
    if (candle.hollowUp && !candleKind.down && w > stroke * 2f && h > stroke * 2f) {
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

private fun DrawScope.drawBar(node: RectNode, bar: RectKind.Bar, color: Color, alpha: Float, corners: BarCorners, path: Path) {
    val b = node.bounds
    val w = b.width
    val h = b.height
    if (w <= 0f || h <= 0.01f) return
    when (corners) {
        BarCorners.Square -> drawRect(color, Offset(b.left, b.top), Size(w, h), alpha)
        BarCorners.Pill -> drawRoundRect(color, Offset(b.left, b.top), Size(w, h), CornerRadius(min(w, h) / 2f), alpha = alpha)
        is BarCorners.End -> {
            if (!bar.isEnd) {
                drawRect(color, Offset(b.left, b.top), Size(w, h), alpha)
                return
            }
            // The end away from the base rounds, up to half the bar's thickness and its full length.
            val r = CornerRadius(if (bar.base.horizontal) min(corners.radius.toPx(), min(h / 2f, w)) else min(corners.radius.toPx(), min(w / 2f, h)))
            val rect = Rect(b.left, b.top, b.right, b.bottom)
            val rounded = when (bar.base) {
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

/** The focus overlay: the needle and focus markers while scrubbing, a scatter dot's marker. */
internal fun DrawScope.drawFocus(scene: ChartScene, style: ChartStyle, focus: Focus) {
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
