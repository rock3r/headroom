package dev.c5inco.charts.core

import java.util.IdentityHashMap
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.max

/**
 * Keyed scene interpolation. The renderer owns the clock and feeds a progress value; this decides
 * what moves where. Nodes pair up by key: matched nodes tween, new nodes grow from the baseline
 * (candles from their open, wicks from their middle, volume bars from the strip's floor, treemap
 * tiles from their centre, heatmap cells from their centre while fading in), removed nodes fade.
 *
 * Interpolation works on data points, not on path commands, so a line with a changed curve or a
 * changed point count still tweens cleanly: lines and areas morph along x when their counts differ
 * (a change of range), and never bridge a gap (see [morph]).
 *
 * Every decision about where a mark grows from is made by the scene's [SceneLayout]:
 *
 * - [SceneLayout.Cartesian]: bars, lines and areas grow from the baseline along the value axis
 *   (an area filled to the plot's bottom rises from there), and entering marks stagger along the
 *   category axis: left to right, or top to bottom for horizontal bars.
 * - [SceneLayout.Polar] (radar, rose) is its own topology: marks grow out of the centre,
 *   staggered clockwise from twelve o'clock.
 * - [SceneLayout.Free] (pie, treemap, heatmap): marks enter in place, slices sweeping from twelve
 *   o'clock and tiles and cells opening out from their own centres.
 *
 * Only an arriving node reads a layout, the one of the scene it arrives in, which compiled it.
 * Matched and leaving nodes never do: they may come from a scene of another layout (or from an
 * interpolated scene still holding nodes leaving an earlier one), so how a matched polygon pairs its
 * points is the node's own ([AreaKind.Polar], [LineNode.polar]), and a leaving node only fades.
 */
public object SceneMotion {
    /** Fraction of the timeline spent staggering entering marks left to right (rows: top to bottom). */
    public const val STAGGER: Float = 0.3f

    // A leaving node below one 8-bit alpha step no longer needs geometry. Interrupted updates
    // reuse their current scene, so retaining these nodes would accumulate every past key.
    private const val MIN_LEAVING_ALPHA = 1f / 255f

    /** The collapsed state a scene grows out of on first appearance. */
    public fun enter(scene: ChartScene): ChartScene = scene.copy(
        guides = scene.guides.map { entering(it, scene) },
        marks = scene.marks.map { entering(it, scene) },
        labels = scene.labels.map { entering(it, scene) },
    )

    /**
     * Where a chart's first appearance starts: [enter]'s collapsed guides and labels, and no marks,
     * so every mark arrives through [lerp] as a new node and staggers left to right (rows: top to
     * bottom) like marks arriving in a later update. `lerp(enter(scene), scene, t)` grows every mark
     * together instead.
     */
    public fun reveal(scene: ChartScene): ChartScene = enter(scene).copy(marks = emptyList())

    /**
     * The scene at [t] (0..1) on the way from [from] to [to]: the target scene, with matched nodes
     * tweened, new ones growing and the ones it no longer has fading where they stand. A polar
     * polygon ([AreaKind.Polar], a [LineNode.polar] edge) pairs its points by index, never by x: its
     * x runs back on itself, so sampling it along x would tear it. A change in the number of axes
     * pads the shorter side with its last point (a closed polygon's last point is its first).
     */
    public fun lerp(from: ChartScene, to: ChartScene, t: Float): ChartScene {
        if (t >= 1f) return to
        return SceneTween(from, to).at(t)
    }

    /**
     * One layer of a tween from [from] to [to] (in [scene], the target), paired once: which nodes
     * leave, which each target node tweens from, and the collapsed state and stagger of each one
     * arriving. Those depend only on the two scenes, so a transition works them out once rather than
     * on every frame; [at] then does only what changes with t.
     */
    internal class LayerTween(from: List<Node>, private val to: List<Node>, private val scene: ChartScene) {
        // Nodes the target no longer has: they fade where they stand, underneath everything arriving.
        private val leaving: List<Node>
        // Per target node: the node it tweens from (matched by key and kind), its collapsed state
        // when it arrives and grows, or null when it arrives some other way (a polar polygon).
        private val starts = arrayOfNulls<Node>(to.size)
        // Per target node: true when matched, so it tweens from its start at t itself.
        private val matched = BooleanArray(to.size)
        // Per arriving node that grows: its stagger fraction, or NaN to grow with t itself.
        private val fractions = FloatArray(to.size)
        // Per target node: for an area's edge whose points tween exactly as the area's top does, the
        // index of that area (earlier in the layer), so the edge takes the area's points rather than
        // interpolating them a second time; else -1.
        private val alongTopOf = IntArray(to.size) { -1 }
        private val hasEdgesAlongTops: Boolean

        init {
            val previous = from.associateBy { it.key }
            val targetKeys = to.mapTo(HashSet()) { it.key }
            leaving = from.filter { it.key !in targetKeys }
            to.forEachIndexed { i, node ->
                val start = previous[node.key]
                if (start != null && start::class == node::class) {
                    starts[i] = start
                    matched[i] = true
                } else if (!arrivesAsPolygon(node, scene)) {
                    starts[i] = entering(node, scene)
                    fractions[i] = staggerFraction(node, scene) ?: Float.NaN
                }
            }
            // An area's edge is compiled along its top: the same list of points.
            var areaByTop: IdentityHashMap<List<XY>, Int>? = null
            to.forEachIndexed { i, node -> if (node is AreaNode) (areaByTop ?: IdentityHashMap<List<XY>, Int>().also { areaByTop = it })[node.top] = i }
            var any = false
            if (areaByTop != null) {
                to.forEachIndexed { i, node ->
                    if (node is LineNode && node.isAreaEdge) {
                        val area = areaByTop!![node.points]
                        if (area != null && area < i && tweensAlongTop(i, area)) {
                            alongTopOf[i] = area
                            any = true
                        }
                    }
                }
            }
            hasEdgesAlongTops = any
        }

        /**
         * Whether edge [edge]'s points come out of every frame exactly as area [area]'s top does: both
         * on the same clock (matched, or arriving without a stagger), from equal points to the same
         * points, paired index for index (equal counts, where a line's and an area's tween both do).
         */
        private fun tweensAlongTop(edge: Int, area: Int): Boolean {
            val e = starts[edge] as? LineNode ?: return false
            val a = starts[area] as? AreaNode ?: return false
            val b = to[area] as AreaNode
            if (matched[edge] != matched[area]) return false
            if (!matched[edge] && !(fractions[edge].isNaN() && fractions[area].isNaN())) return false
            return a.top.size == b.top.size && (e.points === a.top || e.points == a.top)
        }

        fun at(t: Float): List<Node> {
            val out = ArrayList<Node>(leaving.size + to.size)
            for (node in leaving) {
                val alpha = node.alpha * (1f - t)
                if (alpha >= MIN_LEAVING_ALPHA) out += withAlpha(node, alpha)
            }
            val made = if (hasEdgesAlongTops) arrayOfNulls<Node>(to.size) else null
            for (i in to.indices) {
                val node = to[i]
                val start = starts[i]
                val area = alongTopOf[i]
                val next = when {
                    area >= 0 -> edgeAlong(start as LineNode, node as LineNode, (made!![area] as AreaNode).top, t)
                    matched[i] -> lerpNode(start!!, node, t)
                    start != null -> lerpNode(start, node, fractions[i].let { f -> if (f.isNaN()) t else stagger(t, f) })
                    else -> arrivingPolygon(node, scene.layout as SceneLayout.Polar, t)
                }
                if (made != null) made[i] = next
                out += next
            }
            return out
        }
    }

    /** A polygon arriving in a polar scene: it sweeps out vertex by vertex, rather than growing whole. */
    private fun arrivesAsPolygon(node: Node, scene: ChartScene): Boolean =
        scene.layout is SceneLayout.Polar && (node is LineNode || node is AreaNode)

    private fun arrivingPolygon(node: Node, layout: SceneLayout.Polar, t: Float): Node = when (node) {
        is LineNode -> node.copy(points = growPolar(node.points, layout.center, t))
        is AreaNode -> node.copy(top = growPolar(node.top, layout.center, t), bottom = growPolar(node.bottom, layout.center, t))
        else -> error("not a polygon: $node")
    }

    /**
     * Entering marks sweep left to right inside a fixed budget, however many there are; horizontal
     * bars sweep top to bottom. The fraction of the sweep [node] waits for, or null for a node that
     * grows with t itself.
     */
    private fun staggerFraction(node: Node, scene: ChartScene): Float? {
        val plot = scene.plot
        fun across(x: Float) = fraction(x, plot.left, plot.width)
        return when (val layout = scene.layout) {
            // Polar marks sweep clockwise from twelve o'clock: petals by their middle, markers by
            // their vertex (the same fraction as the polygon's vertex under them).
            is SceneLayout.Polar -> when (node) {
                is ArcNode -> turnFraction((node.startAngle + node.endAngle) / 2f)
                is DotNode -> polarFraction(node.center, layout.center)
                is RectNode, is LineNode, is AreaNode, is RuleNode, is TextNode -> null
            }
            is SceneLayout.Cartesian, SceneLayout.Free -> when (node) {
                is RectNode -> when (node.kind) {
                    // A band is an annotation, not data: it fades in all at once.
                    is RectKind.Band -> null
                    // Bars sweep along the category axis: down the rows of horizontal bars.
                    is RectKind.Bar -> if (layout.sideways()) fraction(node.bounds.centerY, plot.top, plot.height) else across(node.bounds.centerX)
                    is RectKind.Candle, is RectKind.Volume, is RectKind.Tile, is RectKind.Cell -> across(node.bounds.centerX)
                }
                is DotNode -> across(node.center.x)
                is RuleNode -> when (node.kind) {
                    is RuleKind.Wick -> across(node.from.x)
                    RuleKind.GridX, RuleKind.GridY, RuleKind.AxisX, RuleKind.AxisY, RuleKind.Baseline, RuleKind.Reference, is RuleKind.XRule -> null
                }
                is LineNode, is AreaNode, is ArcNode, is TextNode -> null
            }
        }
    }

    /** A Cartesian layout whose value axis runs across: horizontal bars. */
    private fun SceneLayout.sideways(): Boolean = when (this) {
        is SceneLayout.Cartesian -> valueAxis == AxisOrientation.Horizontal
        is SceneLayout.Polar, SceneLayout.Free -> false
    }

    private fun fraction(v: Float, start: Float, span: Float): Float =
        if (span > 0f) ((v - start) / span).coerceIn(0f, 1f) else 0f

    private fun stagger(t: Float, f: Float): Float = ((t - f * STAGGER) / (1f - STAGGER)).coerceIn(0f, 1f)

    /**
     * An angle (radians, 0 at twelve o'clock, clockwise) as a fraction of a turn in 0 until 1. A hair
     * before twelve (a rounding error on the first wedge's middle) counts as twelve, never as the
     * end of the sweep.
     */
    internal fun turnFraction(angle: Float): Float {
        val turns = angle / (2 * PI).toFloat()
        val f = turns - floor(turns)
        return if (f > 1f - 1e-4f) 0f else f
    }

    /** Where [p] lies round [origin], as [turnFraction]; the origin itself is at twelve. */
    private fun polarFraction(p: XY, origin: XY): Float {
        val dx = p.x - origin.x
        val dy = p.y - origin.y
        if (dx == 0f && dy == 0f) return 0f
        return turnFraction(atan2(dx, -dy))
    }

    /**
     * A polar line's or area's points arriving: each defined point moves out from [center] on its
     * own clock, staggered by its angle like the markers on it, so vertex and marker stay together.
     * Missing points stay missing, so gaps hold through the reveal.
     */
    private fun growPolar(points: List<XY>, center: XY, t: Float): List<XY> =
        points.map { p -> if (p.isDefined) lerp(center, p, stagger(t, polarFraction(p, center))) else p }

    /** The collapsed state [node] enters from, in [scene]'s layout. */
    private fun entering(node: Node, scene: ChartScene): Node = when (val layout = scene.layout) {
        // Polar marks grow out of the centre: vertices and markers from it, petals from their inner
        // radius. Guides and labels fade in as anywhere else.
        is SceneLayout.Polar -> when (node) {
            is LineNode -> node.copy(points = node.points.map { if (it.isDefined) layout.center else it })
            is AreaNode -> node.copy(
                top = node.top.map { if (it.isDefined) layout.center else it },
                bottom = node.bottom.map { if (it.isDefined) layout.center else it },
            )
            is DotNode -> node.copy(center = layout.center, radius = 0f)
            is ArcNode -> node.copy(outerRadius = node.innerRadius)
            is RectNode, is RuleNode, is TextNode -> enteringInPlace(node, scene)
        }
        is SceneLayout.Cartesian, SceneLayout.Free -> enteringInPlace(node, scene)
    }

    /** The collapsed state of a node that grows where it stands or from the value baseline. */
    private fun enteringInPlace(node: Node, scene: ChartScene): Node = when (node) {
        is RectNode -> when (val kind = node.kind) {
            // A vertical band is an annotation spanning the plot's height: it fades in where it stands.
            is RectKind.Band -> node.copy(alpha = 0f)
            is RectKind.Cell -> node.copy(bounds = node.bounds.centre(), alpha = 0f)
            // A treemap tile has no baseline: it grows out of its own centre.
            is RectKind.Tile -> node.copy(bounds = node.bounds.centre())
            // A candle opens out from its open price; a volume bar rises from the strip's floor.
            // Both collapse onto their base edge, not the price baseline.
            is RectKind.Candle -> node.copy(bounds = node.bounds.onto(kind.base))
            is RectKind.Volume -> node.copy(bounds = node.bounds.onto(Edge.Bottom))
            is RectKind.Bar -> node.copy(bounds = barEntering(node.bounds, kind, scene.layout))
        }
        // An area filled to the plot's bottom (and its edge) rises from there instead.
        is LineNode -> (node.floor ?: riseRow(scene)).let { row -> node.copy(points = node.points.map { it.collapse(row) }) }
        is AreaNode -> (node.floor ?: riseRow(scene)).let { row -> node.copy(top = node.top.map { it.collapse(row) }, bottom = node.bottom.map { it.collapse(row) }) }
        is DotNode -> node.copy(radius = 0f)
        is ArcNode -> node.copy(startAngle = 0f, endAngle = 0f)
        is RuleNode -> when (node.kind) {
            // A wick grows out from its middle as it fades in; other rules just fade in.
            is RuleKind.Wick -> lerp(node.from, node.to, 0.5f).let { mid -> node.copy(from = mid, to = mid, alpha = 0f) }
            RuleKind.GridX, RuleKind.GridY, RuleKind.AxisX, RuleKind.AxisY, RuleKind.Baseline, RuleKind.Reference, is RuleKind.XRule ->
                node.copy(alpha = 0f)
        }
        is TextNode -> node.copy(alpha = 0f)
    }

    /**
     * A bar collapsed onto the value baseline: a y position on an upright value axis, an x position
     * on one that runs across. Outside a Cartesian layout there is no baseline, and a bar collapses
     * onto its own base edge.
     */
    private fun barEntering(bounds: Bounds, bar: RectKind.Bar, layout: SceneLayout): Bounds = when (layout) {
        is SceneLayout.Cartesian -> when (layout.valueAxis) {
            AxisOrientation.Vertical -> bounds.copy(top = layout.baseline, bottom = layout.baseline)
            AxisOrientation.Horizontal -> bounds.copy(left = layout.baseline, right = layout.baseline)
        }
        is SceneLayout.Polar, SceneLayout.Free -> bounds.onto(bar.base)
    }

    /**
     * The row a line or area without a floor rises from: the baseline of an upright value axis. A
     * polar scene grows its lines from the centre instead; any other scene has no such row and uses
     * the plot's bottom.
     */
    private fun riseRow(scene: ChartScene): Float = when (val layout = scene.layout) {
        is SceneLayout.Cartesian -> when (layout.valueAxis) {
            AxisOrientation.Vertical -> layout.baseline
            AxisOrientation.Horizontal -> scene.plot.bottom
        }
        is SceneLayout.Polar -> layout.center.y
        SceneLayout.Free -> scene.plot.bottom
    }

    private fun Bounds.centre() = Bounds(centerX, centerY, centerX, centerY)

    /** These bounds collapsed onto their [edge]. */
    private fun Bounds.onto(edge: Edge): Bounds = when (edge) {
        Edge.Top -> copy(bottom = top)
        Edge.Bottom -> copy(top = bottom)
        Edge.Left -> copy(right = left)
        Edge.Right -> copy(left = right)
    }

    /** Drops a point onto the baseline. Missing points stay missing, so gaps hold through a reveal. */
    private fun XY.collapse(base: Float) = XY(x, if (y.isFinite()) base else y)

    private fun withAlpha(node: Node, alpha: Float): Node = when (node) {
        is RectNode -> node.copy(alpha = alpha)
        is LineNode -> node.copy(alpha = alpha)
        is AreaNode -> node.copy(alpha = alpha)
        is DotNode -> node.copy(alpha = alpha)
        is ArcNode -> node.copy(alpha = alpha)
        is RuleNode -> node.copy(alpha = alpha)
        is TextNode -> node.copy(alpha = alpha)
    }

    /** [lerpNode] of an area edge whose points are already tweened, as its area's [top]. */
    private fun edgeAlong(a: LineNode, b: LineNode, top: List<XY>, t: Float): LineNode = b.copy(
        points = top,
        alpha = lerp(a.alpha, b.alpha, t),
        signSplit = lerpSplit(a.signSplit, b.signSplit, t),
        floor = lerpFloor(a.floor, b.floor, t),
    )

    private fun lerpNode(a: Node, b: Node, t: Float): Node = when (b) {
        is RectNode -> {
            a as RectNode
            b.copy(bounds = lerp(a.bounds, b.bounds, t), kind = lerpKind(a.kind, b.kind, t), alpha = lerp(a.alpha, b.alpha, t))
        }
        is LineNode -> {
            a as LineNode
            b.copy(
                points = if (a.polar || b.polar) byIndex(a.points, b.points, t) else lerpPoints(a.points, b.points, t),
                alpha = lerp(a.alpha, b.alpha, t),
                signSplit = lerpSplit(a.signSplit, b.signSplit, t),
                floor = lerpFloor(a.floor, b.floor, t),
            )
        }
        is AreaNode -> {
            a as AreaNode
            val polar = a.kind.isPolar() || b.kind.isPolar()
            val (top, bottom) = if (polar) listOf(byIndex(a.top, b.top, t), byIndex(a.bottom, b.bottom, t))
            else morph(listOf(a.top, a.bottom), listOf(b.top, b.bottom), t)
            b.copy(
                top = top, bottom = bottom, kind = lerpKind(a.kind, b.kind, t), alpha = lerp(a.alpha, b.alpha, t),
                signSplit = lerpSplit(a.signSplit, b.signSplit, t), floor = lerpFloor(a.floor, b.floor, t),
            )
        }
        is DotNode -> b.copy(center = lerp((a as DotNode).center, b.center, t), radius = lerp(a.radius, b.radius, t), alpha = lerp(a.alpha, b.alpha, t))
        is ArcNode -> {
            a as ArcNode
            b.copy(
                center = lerp(a.center, b.center, t),
                innerRadius = lerp(a.innerRadius, b.innerRadius, t),
                outerRadius = lerp(a.outerRadius, b.outerRadius, t),
                startAngle = lerp(a.startAngle, b.startAngle, t),
                endAngle = lerp(a.endAngle, b.endAngle, t),
                alpha = lerp(a.alpha, b.alpha, t),
            )
        }
        is RuleNode -> b.copy(from = lerp((a as RuleNode).from, b.from, t), to = lerp(a.to, b.to, t), alpha = lerp(a.alpha, b.alpha, t))
        is TextNode -> b.copy(at = lerp((a as TextNode).at, b.at, t), alpha = lerp(a.alpha, b.alpha, t))
    }

    private fun AreaKind.isPolar(): Boolean = when (this) {
        AreaKind.Polar -> true
        is AreaKind.Upright -> false
    }

    /**
     * An upright area's baseline and plot move with its points (a resize), so its gradient stays on
     * them. The edge it fades from moves too: while an area whose values cross over zero turns its
     * gradient over, it lies between the plot's edges, and the renderer crossfades the two fades by
     * how far it has come, rather than turning the gradient over in one frame.
     */
    private fun lerpKind(a: AreaKind, b: AreaKind, t: Float): AreaKind =
        if (a is AreaKind.Upright && b is AreaKind.Upright) {
            AreaKind.Upright(lerp(a.baseline, b.baseline, t), lerp(a.plot, b.plot, t), lerp(a.fadeFrom, b.fadeFrom, t))
        } else {
            b
        }

    /**
     * A bar's track moves with the plot it spans, as the bar does. Only between bars lying the same
     * way: a row's track is a stretch of x and a column's of y, so a bar that turns takes its
     * target's track outright. An unchanged track is shared, not rebuilt each frame.
     */
    private fun lerpKind(a: RectKind, b: RectKind, t: Float): RectKind =
        if (a is RectKind.Bar && b is RectKind.Bar && a.track != b.track && a.base.isHorizontal() == b.base.isHorizontal()) {
            b.copy(track = lerp(a.track, b.track, t))
        } else {
            b
        }

    private fun Edge.isHorizontal(): Boolean = this == Edge.Left || this == Edge.Right

    /** Zero moves with the points that cross it, so colours stay with their side mid-tween. */
    private fun lerpSplit(a: SignSplit?, b: SignSplit?, t: Float): SignSplit? =
        if (a == null || b == null || a.negativeBelow != b.negativeBelow) b else b.copy(zero = lerp(a.zero, b.zero, t))

    /** The floor moves with the bottom points sitting on it (a resize), so the gradient stays on them. */
    private fun lerpFloor(a: Float?, b: Float?, t: Float): Float? = if (a == null || b == null) b else lerp(a, b, t)

    /** Tweens a line's points. See [morph]. */
    internal fun lerpPoints(a: List<XY>, b: List<XY>, t: Float): List<XY> = morph(listOf(a), listOf(b), t)[0]

    /**
     * Tweens parallel edges that share x positions point for point: a line's single edge, or an
     * area's top and bottom, which are morphed together so they keep pairing up.
     *
     * - An empty side has nothing to tween from or to: the target shows at once.
     * - Equal counts pair by index, so a live tick that moves the last point moves only it, and two
     *   ranges sampled to the same count morph point for point.
     * - Unequal counts on a Cartesian line (x finite and never decreasing on both sides) read the
     *   sparser side at the denser side's x positions, clamped to its ends: a change of range
     *   (288 points to 336) morphs along both shapes, and an appended point grows out of the old
     *   end. The tween has the denser count (plus any gap breaks) until it lands.
     * - Gaps hold. A missing (NaN) point on either side stays missing, a sample that falls inside
     *   a gap of the sparser side is missing, and where a gap of the sparser side falls between two
     *   samples a break is inserted, so no segment ever bridges a gap mid-tween. Gaps of both sides
     *   show until the target lands. x stays finite throughout, so a tween retargeted mid-flight
     *   samples cleanly again.
     * - Anything else (x out of order, as in a closed or polar shape, or non-finite x) falls back to
     *   index pairing, padding the shorter side with its last point.
     *
     * Range identity is positional for every other mark: bars, candles and dots pair by key, which
     * is their index, so a range change slides them by index rather than by x.
     */
    private fun morph(a: List<List<XY>>, b: List<List<XY>>, t: Float): List<List<XY>> {
        val na = a[0].size
        val nb = b[0].size
        if (na == 0 || nb == 0) return b
        if (na == nb || a.any { it.size != na } || b.any { it.size != nb } || !ascending(a[0]) || !ascending(b[0])) {
            return List(b.size) { e -> byIndex(a[e], b[e], t) }
        }
        return if (na > nb) resample(dense = a, sparse = b, denseIsSource = true, t) else resample(dense = b, sparse = a, denseIsSource = false, t)
    }

    private fun byIndex(a: List<XY>, b: List<XY>, t: Float): List<XY> {
        if (a.isEmpty() || b.isEmpty()) return b
        if (a.size == b.size) return List(b.size) { i -> lerp(a[i], b[i], t) }
        return List(max(a.size, b.size)) { i -> lerp(a[minOf(i, a.lastIndex)], b[minOf(i, b.lastIndex)], t) }
    }

    /** Finite x that never decreases: a Cartesian polyline that can be read at an x. */
    private fun ascending(points: List<XY>): Boolean {
        var previous = Float.NEGATIVE_INFINITY
        for (p in points) {
            if (!p.x.isFinite() || p.x < previous) return false
            previous = p.x
        }
        return true
    }

    /**
     * Walks [dense]'s x positions once, reading [sparse] at each (both sides' x are ascending, so
     * one forward pass over each: O(dense + sparse) per frame).
     */
    private fun resample(dense: List<List<XY>>, sparse: List<List<XY>>, denseIsSource: Boolean, t: Float): List<List<XY>> {
        val guide = dense[0]
        val track = sparse[0]
        val edges = dense.size
        val out = List(edges) { ArrayList<XY>(guide.size + 2) }
        val last = track.lastIndex
        var j = 0 // the sparse segment [j, j + 1] the walk is in
        for (i in guide.indices) {
            val q = guide[i].x
            // Step over sparse points left of q, noting whether any of them is missing.
            var gap = false
            while (j < last - 1 && track[j + 1].x < q) {
                j++
                for (e in 0 until edges) if (!sparse[e][j].y.isFinite()) gap = true
            }
            val start = out[0].size
            var defined = true
            for (e in 0 until edges) {
                val points = sparse[e]
                val sx = q.coerceIn(points[0].x, points[last].x)
                val sy = sampleY(points, j, q)
                val d = dense[e][i]
                val p = if (denseIsSource) XY(lerp(d.x, sx, t), lerp(d.y, sy, t)) else XY(lerp(sx, d.x, t), lerp(sy, d.y, t))
                if (!p.isDefined) defined = false
                out[e] += p
            }
            // A gap of the sparse side between this sample and the previous one: break the line.
            if (gap && defined && i > 0 && out.all { it[start - 1].isDefined }) {
                for (e in 0 until edges) out[e].add(start, XY(out[e][start - 1].x, Float.NaN))
            }
        }
        return out
    }

    /**
     * The y of [points] at [q], inside segment [j, j + 1] and clamped to the ends (where x clamps
     * too). A point exactly at [q] is read as is; between two points, a missing one makes the
     * sample missing.
     */
    private fun sampleY(points: List<XY>, j: Int, q: Float): Float {
        if (q <= points[0].x) return points[0].y
        if (q >= points[points.lastIndex].x) return points[points.lastIndex].y
        val p = points[j]
        val r = points[j + 1]
        if (q == r.x) return r.y
        if (!p.y.isFinite() || !r.y.isFinite()) return Float.NaN
        val span = r.x - p.x
        val f = if (span > 0f) (q - p.x) / span else 0f
        return p.y + (r.y - p.y) * f
    }
}

/**
 * A transition from [from] to [to], for drawing it frame by frame: [at] is exactly
 * [SceneMotion.lerp]`(from, to, t)`, with the pairing of the two scenes' nodes (and the collapsed
 * state each arriving node grows from) worked out once, here, rather than on every frame. Make one
 * per transition: a transition retargeted mid-flight starts from a new scene, and so a new tween.
 *
 * Immutable, like the scenes it holds: safe to share across threads and to keep while other
 * transitions run.
 */
public class SceneTween(public val from: ChartScene, public val to: ChartScene) {
    private val guides = SceneMotion.LayerTween(from.guides, to.guides, to)
    private val marks = SceneMotion.LayerTween(from.marks, to.marks, to)
    private val labels = SceneMotion.LayerTween(from.labels, to.labels, to)

    /**
     * The scene at [t] (0..1) on the way from [from] to [to]; [to] itself from 1 on. Its marks start
     * with the ones leaving, as many as [ChartScene.leavingMarks] says: every target node is drawn, so
     * the rest are those still fading out.
     */
    public fun at(t: Float): ChartScene {
        if (t >= 1f) return to
        val drawn = marks.at(t)
        return to.copy(guides = guides.at(t), marks = drawn, labels = labels.at(t), leavingMarks = drawn.size - to.marks.size)
    }
}
