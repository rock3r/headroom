package dev.c5inco.charts.core

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
 * Polar scenes ([ChartScene.origin] set: radar, rose) are their own topology: marks grow out of
 * the origin, staggered clockwise from twelve o'clock, and lines and areas always pair points by
 * index (see [lerp]).
 */
object SceneMotion {
    /** Fraction of the timeline spent staggering entering marks left to right (rows: top to bottom). */
    const val STAGGER = 0.3f

    // A leaving node below one 8-bit alpha step no longer needs geometry. Interrupted updates
    // reuse their current scene, so retaining these nodes would accumulate every past key.
    private const val MIN_LEAVING_ALPHA = 1f / 255f

    /** The collapsed state a scene grows out of on first appearance. */
    fun enter(scene: ChartScene): ChartScene = scene.copy(
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
    fun reveal(scene: ChartScene): ChartScene = enter(scene).copy(marks = emptyList())

    /**
     * The scene at [t] (0..1) on the way from [from] to [to]. When either scene is polar, lines and
     * areas pair their points by index, never by x: a radar polygon's x runs back on itself, so
     * sampling it along x would tear it. A change in the number of axes pads the shorter side
     * with its last point (a closed polygon's last point is its first).
     */
    fun lerp(from: ChartScene, to: ChartScene, t: Float): ChartScene {
        if (t >= 1f) return to
        val polar = from.origin != null || to.origin != null
        return to.copy(
            guides = layer(from.guides, to.guides, to, t, polar),
            marks = layer(from.marks, to.marks, to, t, polar),
            labels = layer(from.labels, to.labels, to, t, polar),
        )
    }

    private fun layer(from: List<Node>, to: List<Node>, scene: ChartScene, t: Float, polar: Boolean): List<Node> {
        val previous = from.associateBy { it.key }
        val targetKeys = to.mapTo(HashSet()) { it.key }
        val out = ArrayList<Node>(from.size + to.size)
        // Leaving nodes fade where they stand, underneath everything arriving.
        from.forEach {
            if (it.key !in targetKeys) {
                val alpha = it.alpha * (1f - t)
                if (alpha >= MIN_LEAVING_ALPHA) out += withAlpha(it, alpha)
            }
        }
        to.forEach { node ->
            val start = previous[node.key]
            val origin = scene.origin
            out += when {
                start != null && start::class == node::class -> lerpNode(start, node, t, polar)
                // A polygon arriving in a polar scene sweeps out vertex by vertex.
                origin != null && (node is LineNode || node is AreaNode) -> growPolar(node, origin, t)
                else -> lerpNode(entering(node, scene), node, staggered(node, scene, t), polar)
            }
        }
        return out
    }

    /**
     * Entering marks sweep left to right inside a fixed budget, however many there are; horizontal
     * bars sweep top to bottom.
     */
    private fun staggered(node: Node, scene: ChartScene, t: Float): Float {
        val plot = scene.plot
        val origin = scene.origin
        val f = when {
            // Polar marks sweep clockwise from twelve o'clock: petals by their middle, markers by
            // their vertex (the same fraction as the polygon's vertex under them).
            origin != null && node is ArcNode -> turnFraction((node.startAngle + node.endAngle) / 2f)
            origin != null && node is DotNode -> polarFraction(node.center, origin)
            origin != null -> return t
            node is RectNode && node.kind == RectKind.Band -> return t
            node is RectNode && node.horizontal -> fraction(node.bounds.centerY, plot.top, plot.height)
            node is RectNode -> fraction(node.bounds.centerX, plot.left, plot.width)
            node is DotNode -> fraction(node.center.x, plot.left, plot.width)
            node is RuleNode && node.kind == RuleKind.Wick -> fraction(node.from.x, plot.left, plot.width)
            else -> return t
        }
        return stagger(t, f)
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
     * A polar line or area arriving: each defined point moves out from [origin] on its own clock,
     * staggered by its angle like the markers on it, so vertex and marker stay together. Missing
     * points stay missing, so gaps hold through the reveal.
     */
    private fun growPolar(node: Node, origin: XY, t: Float): Node {
        fun grow(points: List<XY>) = points.map { p -> if (p.isDefined) lerp(origin, p, stagger(t, polarFraction(p, origin))) else p }
        return when (node) {
            is LineNode -> node.copy(points = grow(node.points))
            is AreaNode -> node.copy(top = grow(node.top), bottom = grow(node.bottom))
            else -> node
        }
    }

    private val RectNode.horizontal: Boolean get() = base == Edge.Left || base == Edge.Right

    private fun entering(node: Node, scene: ChartScene): Node {
        val base = scene.baseline
        val origin = scene.origin
        if (origin != null) {
            // Polar marks grow out of the centre: vertices and markers from it, petals from their
            // inner radius. Guides and labels fade in below.
            when (node) {
                is LineNode -> return node.copy(points = node.points.map { if (it.isDefined) origin else it })
                is AreaNode -> return node.copy(
                    top = node.top.map { if (it.isDefined) origin else it },
                    bottom = node.bottom.map { if (it.isDefined) origin else it },
                )
                is DotNode -> return node.copy(center = origin, radius = 0f)
                is ArcNode -> return node.copy(outerRadius = node.innerRadius)
                else -> Unit
            }
        }
        return when (node) {
            // A vertical band is an annotation spanning the plot's height: it fades in where it stands.
            is RectNode -> if (node.kind == RectKind.Band) {
                node.copy(alpha = 0f)
            } else if (node.kind == RectKind.Cell) {
                node.copy(bounds = node.bounds.let { Bounds(it.centerX, it.centerY, it.centerX, it.centerY) }, alpha = 0f)
            } else node.copy(
                bounds = when {
                    // A treemap tile has no baseline: it grows out of its own centre.
                    node.kind == RectKind.Tile -> node.bounds.let { Bounds(it.centerX, it.centerY, it.centerX, it.centerY) }
                    // A candle opens out from its open price; a volume bar rises from the strip's
                    // floor. Both collapse onto their base edge, not the price baseline.
                    node.kind == RectKind.Candle || node.kind == RectKind.Volume ->
                        if (node.base == Edge.Top) node.bounds.copy(bottom = node.bounds.top) else node.bounds.copy(top = node.bounds.bottom)
                    // A horizontal scene's baseline is an x position.
                    node.horizontal -> node.bounds.copy(left = base, right = base)
                    else -> node.bounds.copy(top = base, bottom = base)
                },
            )
            // An area filled to the plot's bottom (and its edge) rises from there instead.
            is LineNode -> (node.floor ?: base).let { row -> node.copy(points = node.points.map { it.collapse(row) }) }
            is AreaNode -> (node.floor ?: base).let { row -> node.copy(top = node.top.map { it.collapse(row) }, bottom = node.bottom.map { it.collapse(row) }) }
            is DotNode -> node.copy(radius = 0f)
            is ArcNode -> node.copy(startAngle = 0f, endAngle = 0f)
            // A wick grows out from its middle as it fades in; other rules just fade in.
            is RuleNode -> if (node.kind == RuleKind.Wick) {
                val mid = lerp(node.from, node.to, 0.5f)
                node.copy(from = mid, to = mid, alpha = 0f)
            } else {
                node.copy(alpha = 0f)
            }
            is TextNode -> node.copy(alpha = 0f)
        }
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

    private fun lerpNode(a: Node, b: Node, t: Float, polar: Boolean): Node = when (b) {
        is RectNode -> b.copy(bounds = lerp((a as RectNode).bounds, b.bounds, t), alpha = lerp(a.alpha, b.alpha, t))
        is LineNode -> b.copy(
            points = if (polar) byIndex((a as LineNode).points, b.points, t) else lerpPoints((a as LineNode).points, b.points, t),
            alpha = lerp(a.alpha, b.alpha, t),
            signSplit = lerpSplit(a.signSplit, b.signSplit, t),
            floor = lerpFloor(a.floor, b.floor, t),
        )
        is AreaNode -> {
            a as AreaNode
            val (top, bottom) = if (polar) listOf(byIndex(a.top, b.top, t), byIndex(a.bottom, b.bottom, t))
            else morph(listOf(a.top, a.bottom), listOf(b.top, b.bottom), t)
            b.copy(
                top = top, bottom = bottom, alpha = lerp(a.alpha, b.alpha, t),
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
