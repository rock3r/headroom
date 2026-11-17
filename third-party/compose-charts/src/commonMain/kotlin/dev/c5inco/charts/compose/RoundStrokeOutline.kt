package dev.c5inco.charts.compose

import dev.c5inco.charts.core.PathSink
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Outlines the round-capped, round-joined stroke of a traced line made of straight segments, as one
 * simple shape per subpath: the region between the lowest and highest point the stroke covers at
 * each x. Filling it paints the stroke's pixels, but it has a few edges where the stroker's outline
 * (`strokeOutline`, Skia's stroker) has several per point, most of them inside the stroke, so the
 * renderer rasterises it many times faster.
 *
 * **Why the region between two curves is the stroke.** A round-capped, round-joined stroke of
 * half-width `r` is every point within `r` of the line. When x never decreases along a subpath (a
 * series traced over an ascending axis; or never increases, over a reversed one), the part of the
 * line within `r` of a vertical line `X = x` is one connected piece, and each of its points covers
 * a vertical run of that line around its own height: so the stroke meets `X = x` in one interval,
 * from `low(x)` to `high(x)`, and the stroke is the region between them.
 *
 * **The two curves.** `high(x)` is the upper envelope of the segments' capsule tops: each capsule's
 * top is an arc of the disk at its first point, the segment's offset line, then an arc of the disk at
 * its last point. Every capsule is the union of disks of radius `r` centred along its segment, and
 * the disks are translates of one another; for two of them the right one's top minus the left one's
 * never decreases with x (the arc is concave), so as x grows the envelope passes from capsule to
 * capsule in the order of the line. That makes it a monotone stack (as for a lower envelope of
 * parabolas): each capsule is pushed once and popped at most once, at the x where it starts to win.
 * `low(x)` is the same with y negated.
 *
 * The arcs are written as cubics (at most a quarter turn each, within 0.03 % of the radius), or as
 * their chord where they bulge less than [FLAT_SAGITTA] from it, so the shape is the exact stroke to
 * within 0.03 % of its half-width (0.003 px for a 5 dp line on a Pixel 4 XL), and 0.004 px. The stroker's outline of a dense zigzag is not quite: it overlaps itself thousands of
 * times and leaves rounding slivers, so the two differ along the stroke's anti-aliased edge, where
 * this one is the closer to the exact coverage (RoundOutlineRenderTest).
 *
 * Only straight segments: a cubic or a closed subpath makes [outline] decline, as does a subpath
 * whose x both rises and falls, and the caller strokes the line as before. One instance per
 * [TextCache], reused every frame; main thread only.
 */
internal class RoundStrokeOutline : PathSink {
    // The traced line: vertices, and where each subpath starts.
    private var xs = FloatArray(256)
    private var ys = FloatArray(256)
    private var count = 0
    private var starts = IntArray(8)
    private var directions = IntArray(8)
    private var contours = 0
    private var straight = true

    // One subpath at a time, in ascending x, and in the current envelope's orientation (y or -y).
    private var vx = DoubleArray(256)
    private var vy = DoubleArray(256)

    // Per segment (a capsule): where it starts and ends, where its first arc gives way to its offset
    // line ([ta]) and the line to its last arc ([tb]), the line's height at [ta] and its slope.
    private var segStart = DoubleArray(256)
    private var segEnd = DoubleArray(256)
    private var ta = DoubleArray(256)
    private var tb = DoubleArray(256)
    private var lineY = DoubleArray(256)
    private var slope = DoubleArray(256)

    // 1 / length of each segment, shared by both orientations (its length does not change sign).
    private var invLen = DoubleArray(256)

    // The envelope: capsule indices, each winning from its x in [stackZ] to the next one's.
    private var stackSeg = IntArray(256)
    private var stackZ = DoubleArray(256)
    private var top = -1

    private var r = 0.0
    private var sign = 1.0

    // The output's current point, so a jump in the envelope becomes a vertical edge.
    private lateinit var out: PathSink
    private var curX = 0.0
    private var curY = 0.0

    /** How many envelope pieces (capsule tops) the last [outline] wrote, both edges of every subpath: for tests. */
    internal var pieces = 0
        private set

    /** Starts a new line. */
    fun reset() {
        count = 0
        contours = 0
        straight = true
    }

    override fun moveTo(x: Float, y: Float) {
        if (contours == starts.size) starts = starts.copyOf(contours * 2)
        starts[contours++] = count
        add(x, y)
    }

    override fun lineTo(x: Float, y: Float) {
        // A line with no subpath started is not something the tracer writes.
        if (contours == 0) straight = false else add(x, y)
    }

    override fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) {
        straight = false
    }

    override fun close() {
        straight = false
    }

    private fun add(x: Float, y: Float) {
        if (count == xs.size) {
            xs = xs.copyOf(count * 2)
            ys = ys.copyOf(count * 2)
        }
        xs[count] = x
        ys[count] = y
        count++
    }

    /**
     * Writes the outline of the traced line stroked [halfWidth] either side, with round caps and
     * joins, into [out] and returns true; or writes nothing and returns false when the line is not
     * one this can outline (see the class).
     */
    fun outline(halfWidth: Float, out: PathSink): Boolean {
        if (!straight || !(halfWidth > 0f) || !halfWidth.isFinite()) return false
        if (directions.size < contours) directions = IntArray(starts.size)
        for (c in 0 until contours) {
            directions[c] = direction(c)
            if (directions[c] == 0) return false
        }
        this.out = out
        r = halfWidth.toDouble()
        pieces = 0
        for (c in 0 until contours) writeContour(c)
        return true
    }

    private fun contourEnd(c: Int) = if (c + 1 < contours) starts[c + 1] else count

    /** 1 when x never decreases along subpath [c], -1 when it never increases, 0 when it does both or a point is not finite. */
    private fun direction(c: Int): Int {
        var up = false
        var down = false
        val end = contourEnd(c)
        for (i in starts[c] until end) {
            if (!xs[i].isFinite() || !ys[i].isFinite()) return 0
            if (i > starts[c]) {
                val dx = xs[i] - xs[i - 1]
                if (dx > 0f) up = true else if (dx < 0f) down = true
            }
        }
        return if (up && down) 0 else if (down) -1 else 1
    }

    private fun writeContour(c: Int) {
        val start = starts[c]
        val m = contourEnd(c) - start
        // A subpath that never leaves its first point strokes nothing (a run of one is traced to itself).
        if (m < 2) return
        ensure(m)
        val reversed = directions[c] < 0
        for (k in 0 until m) {
            val i = if (reversed) start + m - 1 - k else start + k
            vx[k] = xs[i].toDouble()
            vy[k] = ys[i].toDouble()
        }
        // The edge at the larger y (the stroke's bottom on screen), left to right; then the edge at the
        // smaller y, right to left. Each envelope is worked out in its own orientation: y, then -y.
        build(m, 1.0)
        val z0 = stackZ[0]
        moveOut(z0, sign * value(stackSeg[0], z0))
        emitEnvelope(forward = true)
        flipY(m)
        build(m, -1.0)
        curY = -curY
        val zEnd = segEnd[stackSeg[top]]
        joinTo(zEnd, value(stackSeg[top], zEnd))
        emitEnvelope(forward = false)
        out.close()
    }

    private fun flipY(m: Int) {
        for (k in 0 until m) vy[k] = -vy[k]
    }

    private fun ensure(m: Int) {
        if (vx.size >= m) return
        val n = max(m, vx.size * 2)
        vx = DoubleArray(n)
        vy = DoubleArray(n)
        segStart = DoubleArray(n)
        segEnd = DoubleArray(n)
        ta = DoubleArray(n)
        tb = DoubleArray(n)
        lineY = DoubleArray(n)
        slope = DoubleArray(n)
        invLen = DoubleArray(n)
        stackSeg = IntArray(n)
        stackZ = DoubleArray(n)
    }

    /** Works out the upper envelope of the capsules of the [m] - 1 segments in [vx], [vy] (already in orientation [orientation]). */
    private fun build(m: Int, orientation: Double) {
        sign = orientation
        for (i in 0 until m - 1) {
            val ax = vx[i]
            val ay = vy[i]
            val bx = vx[i + 1]
            val by = vy[i + 1]
            val dx = bx - ax
            val dy = by - ay
            segStart[i] = ax - r
            segEnd[i] = bx + r
            if (dx <= VERTICAL_DX) {
                // Vertical or a single point: the top is the higher end's arc throughout, its own
                // ends included (no part switch inside the capsule).
                val t = if (by >= ay) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
                ta[i] = t
                tb[i] = t
                lineY[i] = 0.0
                slope[i] = 0.0
            } else {
                if (orientation > 0.0) invLen[i] = 1.0 / sqrt(dx * dx + dy * dy)
                val k = r * invLen[i]
                // The offset line touches the first disk at the upward normal (-dy, dx) / length.
                ta[i] = ax - k * dy
                tb[i] = bx - k * dy
                lineY[i] = ay + k * dx
                slope[i] = dy / dx
            }
        }
        top = -1
        for (q in 0 until m - 1) {
            var z: Double
            while (true) {
                if (top < 0) {
                    z = segStart[q]
                    break
                }
                val p = stackSeg[top]
                val zp = stackZ[top]
                if (q == p + 1) {
                    val s = nextOvertakes(p)
                    if (!s.isNaN()) {
                        if (s <= zp) {
                            top--
                            continue
                        }
                        z = s
                        break
                    }
                }
                val dq = value(q, zp)
                val dp = value(p, zp)
                if (dq >= dp) {
                    // q is at least as high from where p starts winning: p never shows.
                    top--
                    continue
                }
                z = overtake(p, q, zp)
                break
            }
            if (z.isFinite()) {
                top++
                stackSeg[top] = q
                stackZ[top] = z
            }
        }
    }

    /**
     * Where capsule p + 1 starts to be higher than capsule [p], when both have an offset line and
     * the answer follows from their shared vertex alone; NaN otherwise (work it out in full).
     *
     * Both capsules hold the disk at the shared vertex b, and each one's offset line is tangent to
     * that disk's arc: p's at `tb[p]`, past which p's top is the arc; q's at `ta[q]`, before which
     * q's top is the arc. Left of `tb[p]`, p is above the arc and so above q while q is the arc. So q
     * takes over at `tb[p]` when its own line starts no earlier; otherwise where the two tangent
     * lines cross, between the tangent points (when both are on their lines there).
     */
    private fun nextOvertakes(p: Int): Double {
        val q = p + 1
        if (!(tb[p] > ta[p]) || !(tb[q] > ta[q])) return Double.NaN
        if (ta[q] >= tb[p]) return tb[p]
        if (ta[q] < ta[p]) return Double.NaN
        val dm = slope[q] - slope[p]
        if (dm == 0.0) return Double.NaN
        val x = ((lineY[p] - slope[p] * ta[p]) - (lineY[q] - slope[q] * ta[q])) / dm
        // Only where both are on their lines: past tb[q], q is on its far arc.
        return if (x >= ta[q] && x <= tb[p] && x <= tb[q]) x else Double.NaN
    }

    /** Capsule [i]'s top at [x], or negative infinity outside it. */
    private fun value(i: Int, x: Double): Double {
        if (x < segStart[i] || x > segEnd[i]) return Double.NEGATIVE_INFINITY
        return when {
            x <= ta[i] -> arc(vx[i], vy[i], x)
            x < tb[i] -> lineY[i] + slope[i] * (x - ta[i])
            else -> arc(vx[i + 1], vy[i + 1], x)
        }
    }

    private fun arc(cx: Double, cy: Double, x: Double): Double {
        val d = x - cx
        return cy + sqrt(max(0.0, r * r - d * d))
    }

    /** Which part of capsule [i] holds at [x] (inside it): 0 its first arc, 1 its line, 2 its last arc. */
    private fun part(i: Int, x: Double): Int = when {
        x <= ta[i] -> 0
        x < tb[i] -> 1
        else -> 2
    }

    private val breaks = DoubleArray(6)

    /**
     * Where capsule [q] (later in the line) starts to be higher than capsule [p], which is higher at
     * [zp]; positive infinity when it never is. Past that point it stays higher (see the class).
     */
    private fun overtake(p: Int, q: Int, zp: Double): Double {
        val endP = segEnd[p]
        var n = 0
        n = brk(n, segStart[q], zp, endP)
        n = brk(n, ta[q], zp, endP)
        n = brk(n, tb[q], zp, endP)
        n = brk(n, ta[p], zp, endP)
        n = brk(n, tb[p], zp, endP)
        n = brk(n, endP, zp, endP)
        // Insertion sort: six values at most.
        for (i in 1 until n) {
            val v = breaks[i]
            var j = i - 1
            while (j >= 0 && breaks[j] > v) {
                breaks[j + 1] = breaks[j]
                j--
            }
            breaks[j + 1] = v
        }
        var lo = zp
        for (k in 0 until n) {
            val x = breaks[k]
            if (x <= lo) continue
            if (value(q, x) - value(p, x) >= 0.0) return crossing(p, q, lo, x)
            lo = x
        }
        // Lower than p to p's end: q takes over past it, if it reaches further.
        return if (segEnd[q] > endP) endP else Double.POSITIVE_INFINITY
    }

    private fun brk(n: Int, x: Double, after: Double, upTo: Double): Int {
        if (x > after && x <= upTo) {
            breaks[n] = x
            return n + 1
        }
        return n
    }

    /** The x in [lo, hi] where [q] rises to [p], q being lower at [lo] and not lower at [hi], each one part of its capsule between. */
    private fun crossing(p: Int, q: Int, lo: Double, hi: Double): Double {
        // q only starts at hi.
        if (value(q, lo) == Double.NEGATIVE_INFINITY) return hi
        val mid = 0.5 * (lo + hi)
        val pp = part(p, mid)
        val pq = part(q, mid)
        var root = Double.NaN
        when {
            pp != 1 && pq != 1 -> {
                val pv = if (pp == 0) p else p + 1
                val qv = if (pq == 0) q else q + 1
                root = arcArc(vx[pv], vy[pv], vx[qv], vy[qv], lo, hi, p, q)
            }
            pp == 1 && pq == 1 -> {
                val dm = slope[q] - slope[p]
                if (dm != 0.0) {
                    // lineY + slope * (x - ta) for each.
                    val ip = lineY[p] - slope[p] * ta[p]
                    val iq = lineY[q] - slope[q] * ta[q]
                    root = (ip - iq) / dm
                }
            }
            else -> {
                val line = if (pp == 1) p else q
                val arcSeg = if (pp == 1) q else p
                val v = if ((if (pp == 1) pq else pp) == 0) arcSeg else arcSeg + 1
                root = arcLine(vx[v], vy[v], line, lo, hi, p, q)
            }
        }
        if (!(root >= lo && root <= hi)) root = bisect(p, q, lo, hi)
        return root
    }

    /** Where two upper arcs of radius r cross within [lo, hi] (allowing for rounding), or NaN. */
    private fun arcArc(ax: Double, ay: Double, bx: Double, by: Double, lo: Double, hi: Double, p: Int, q: Int): Double {
        val dx = bx - ax
        val dy = by - ay
        val d2 = dx * dx + dy * dy
        val h2 = r * r - d2 / 4.0
        if (d2 == 0.0 || h2 < 0.0) return Double.NaN
        val k = sqrt(h2 / d2)
        val mx = (ax + bx) / 2.0
        return pick(mx - k * dy, mx + k * dy, lo, hi, p, q)
    }

    /** Where an upper arc centred at ([cx], [cy]) crosses the offset line of capsule [line] within [lo, hi], or NaN. */
    private fun arcLine(cx: Double, cy: Double, line: Int, lo: Double, hi: Double, p: Int, q: Int): Double {
        val m = slope[line]
        // y - cy = m * u + k with u = x - cx.
        val k = lineY[line] + m * (cx - ta[line]) - cy
        val a = 1.0 + m * m
        val disc = r * r * a - k * k
        if (disc < 0.0) return Double.NaN
        val s = sqrt(disc)
        return pick(cx + (-m * k - s) / a, cx + (-m * k + s) / a, lo, hi, p, q)
    }

    /**
     * Of two candidate crossings (where the full circles, or circle and line, meet), the first in
     * [lo, hi] where [p] and [q] are level: the other may be on a lower half, where they are not.
     */
    private fun pick(c1: Double, c2: Double, lo: Double, hi: Double, p: Int, q: Int): Double {
        val a = min(c1, c2)
        val b = max(c1, c2)
        return when {
            level(a, lo, hi, p, q) -> a.coerceIn(lo, hi)
            level(b, lo, hi, p, q) -> b.coerceIn(lo, hi)
            else -> Double.NaN
        }
    }

    private fun level(c: Double, lo: Double, hi: Double, p: Int, q: Int): Boolean {
        val slack = 1e-7 * (1.0 + abs(lo) + abs(hi))
        if (!(c >= lo - slack && c <= hi + slack)) return false
        val x = c.coerceIn(lo, hi)
        return abs(value(q, x) - value(p, x)) <= 1e-6 * (1.0 + r)
    }

    /** The crossing found by halving [lo, hi]: rounding left no analytic root inside. */
    private fun bisect(p: Int, q: Int, lo0: Double, hi0: Double): Double {
        var lo = lo0
        var hi = hi0
        repeat(48) {
            val mid = 0.5 * (lo + hi)
            if (value(q, mid) - value(p, mid) >= 0.0) hi = mid else lo = mid
        }
        return hi
    }

    /** Writes the envelope in [stackSeg]: left to right when [forward], else right to left. */
    private fun emitEnvelope(forward: Boolean) {
        pieces += top + 1
        if (forward) {
            for (k in 0..top) {
                val i = stackSeg[k]
                val x2 = if (k < top) stackZ[k + 1] else segEnd[i]
                emitCapsule(i, stackZ[k], x2, true)
            }
        } else {
            for (k in top downTo 0) {
                val i = stackSeg[k]
                val x2 = if (k < top) stackZ[k + 1] else segEnd[i]
                emitCapsule(i, stackZ[k], x2, false)
            }
        }
    }

    /** Writes capsule [i]'s top between [x1] and [x2], from [x1] when [forward], else from [x2]. */
    private fun emitCapsule(i: Int, x1: Double, x2: Double, forward: Boolean) {
        if (!(x2 > x1)) return
        val a0 = max(x1, segStart[i])
        val a1 = min(x2, ta[i])
        val l0 = max(x1, ta[i])
        val l1 = min(x2, tb[i])
        val b0 = max(x1, tb[i])
        val b1 = min(x2, segEnd[i])
        if (forward) {
            if (a1 > a0) emitArc(vx[i], vy[i], a0, a1)
            if (l1 > l0) emitLine(i, l0, l1)
            if (b1 > b0) emitArc(vx[i + 1], vy[i + 1], b0, b1)
        } else {
            if (b1 > b0) emitArc(vx[i + 1], vy[i + 1], b1, b0)
            if (l1 > l0) emitLine(i, l1, l0)
            if (a1 > a0) emitArc(vx[i], vy[i], a1, a0)
        }
    }

    private fun emitLine(i: Int, from: Double, to: Double) {
        joinTo(from, lineY[i] + slope[i] * (from - ta[i]))
        lineOut(to, lineY[i] + slope[i] * (to - ta[i]))
    }

    /** Writes the upper arc of the circle at ([cx], [cy]) from x = [from] to x = [to]. */
    private fun emitArc(cx: Double, cy: Double, from: Double, to: Double) {
        val y0 = arc(cx, cy, from)
        val y1 = arc(cx, cy, to)
        joinTo(from, y0)
        val chordX = to - from
        val chordY = y1 - y0
        // Its chord when the arc bulges less than FLAT_SAGITTA from it (an upper arc is at most half
        // the circle, so the sagitta follows from the chord and the radius).
        val halfChord2 = (chordX * chordX + chordY * chordY) / 4.0
        if (halfChord2 / (r + sqrt(max(0.0, r * r - halfChord2))) < FLAT_SAGITTA) {
            lineOut(to, y1)
            return
        }
        val u0x = (from - cx) / r
        val u0y = (y0 - cy) / r
        val u1x = (to - cx) / r
        val u1y = (y1 - cy) / r
        val dot = u0x * u1x + u0y * u1y
        if (dot < COS_QUARTER) {
            // More than a quarter turn: two halves, split where the bisector meets the arc.
            var mx = u0x + u1x
            var my = u0y + u1y
            val len = sqrt(mx * mx + my * my)
            if (len < 1e-9) {
                mx = 0.0
                my = 1.0
            } else {
                mx /= len
                my /= len
            }
            arcCubic(cx, cy, u0x, u0y, mx, my)
            arcCubic(cx, cy, mx, my, u1x, u1y)
        } else {
            arcCubic(cx, cy, u0x, u0y, u1x, u1y)
        }
    }

    /** One cubic for the arc of the circle at ([cx], [cy]) from unit direction u0 to u1 (at most a quarter turn). */
    private fun arcCubic(cx: Double, cy: Double, u0x: Double, u0y: Double, u1x: Double, u1y: Double) {
        val cross = u0x * u1y - u0y * u1x
        val dot = u0x * u1x + u0y * u1y
        // tan(φ/4) from tan(φ/2) = sin φ / (1 + cos φ).
        val t2 = abs(cross) / (1.0 + dot)
        val k = 4.0 / 3.0 * (t2 / (1.0 + sqrt(1.0 + t2 * t2)))
        // Tangents in the direction of travel.
        val s = if (cross >= 0.0) 1.0 else -1.0
        val t0x = -u0y * s
        val t0y = u0x * s
        val t1x = -u1y * s
        val t1y = u1x * s
        val x3 = cx + r * u1x
        val y3 = cy + r * u1y
        out.cubicTo(
            (cx + r * (u0x + k * t0x)).toFloat(), (sign * (cy + r * (u0y + k * t0y))).toFloat(),
            (cx + r * (u1x - k * t1x)).toFloat(), (sign * (cy + r * (u1y - k * t1y))).toFloat(),
            x3.toFloat(), (sign * y3).toFloat(),
        )
        curX = x3
        curY = y3
    }

    /** Draws an edge to ([x], [y]) (in the current orientation) when the current point is elsewhere. */
    private fun joinTo(x: Double, y: Double) {
        if (abs(x - curX) > JOIN_EPS || abs(y - curY) > JOIN_EPS) lineOut(x, y)
    }

    private fun lineOut(x: Double, y: Double) {
        out.lineTo(x.toFloat(), (sign * y).toFloat())
        curX = x
        curY = y
    }

    private fun moveOut(x: Double, yOut: Double) {
        out.moveTo(x.toFloat(), yOut.toFloat())
        curX = x
        curY = sign * yOut
    }

    private companion object {
        /** Below this x-extent (px) a segment is vertical: its offset line has no width. */
        const val VERTICAL_DX = 1e-6

        /** An arc bulging less than this (px) from its chord is written as the chord. */
        const val FLAT_SAGITTA = 0.004

        /** Points closer than this (px) need no edge between them. */
        const val JOIN_EPS = 1e-4

        /** cos 90°, with a little room: arcs up to a quarter turn take one cubic. */
        const val COS_QUARTER = 1e-9
    }
}
