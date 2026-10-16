package dev.c5inco.charts.core

import kotlin.math.abs
import kotlin.math.min

/**
 * Receives path segments. The Compose renderer writes straight into a `Path`; tests collect
 * [PathCommand]s. Keeping geometry as points until this last step is what lets the motion
 * layer interpolate data points rather than path strings.
 */
interface PathSink {
    fun moveTo(x: Float, y: Float)
    fun lineTo(x: Float, y: Float)
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float)
    fun close()
}

sealed interface PathCommand {
    data class MoveTo(val x: Float, val y: Float) : PathCommand
    data class LineTo(val x: Float, val y: Float) : PathCommand
    data class CubicTo(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val x: Float, val y: Float) : PathCommand
    data object Close : PathCommand
}

class CommandList : PathSink {
    val commands = mutableListOf<PathCommand>()
    override fun moveTo(x: Float, y: Float) { commands += PathCommand.MoveTo(x, y) }
    override fun lineTo(x: Float, y: Float) { commands += PathCommand.LineTo(x, y) }
    override fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) {
        commands += PathCommand.CubicTo(x1, y1, x2, y2, x, y)
    }
    override fun close() { commands += PathCommand.Close }
}

/** How consecutive points are joined. Ports of d3-shape's `curveLinear`, `curveMonotoneX` and `curveStep`. */
enum class Curve {
    Linear,

    /** Smooth, and never overshoots the data: a peak in the curve is a peak in the data. */
    Monotone,
    Step;

    /**
     * Traces [points]. A point with a non-finite coordinate is missing: the line stops before it
     * and starts a new subpath after it. A run of one point traces to itself, which a round-capped
     * stroke draws as a dot.
     */
    fun trace(points: List<XY>, sink: PathSink) {
        runs(points.size, { points[it].isDefined }) { run ->
            val sub = points.subList(run.first, run.last + 1)
            traceRun(sub, sink, connect = false)
            if (sub.size == 1) sink.lineTo(sub[0].x, sub[0].y)
        }
    }

    /**
     * Traces the closed areas between [top] and [bottom], which share x positions. Each run where
     * both edges are defined becomes its own closed shape.
     */
    fun traceArea(top: List<XY>, bottom: List<XY>, sink: PathSink) {
        val n = minOf(top.size, bottom.size)
        runs(n, { top[it].isDefined && bottom[it].isDefined }) { run ->
            if (run.first == run.last) return@runs
            traceRun(top.subList(run.first, run.last + 1), sink, connect = false)
            traceRun(bottom.subList(run.first, run.last + 1).asReversed(), sink, connect = true)
            sink.close()
        }
    }

    /** When [connect] is true the first point is joined with a line rather than starting a subpath. */
    private fun traceRun(points: List<XY>, sink: PathSink, connect: Boolean) {
        val first = points[0]
        if (connect) sink.lineTo(first.x, first.y) else sink.moveTo(first.x, first.y)
        when (this) {
            Linear -> for (i in 1 until points.size) sink.lineTo(points[i].x, points[i].y)
            Step -> for (i in 1 until points.size) {
                val a = points[i - 1]
                val b = points[i]
                val mid = (a.x + b.x) / 2f
                sink.lineTo(mid, a.y)
                sink.lineTo(mid, b.y)
                sink.lineTo(b.x, b.y)
            }
            Monotone -> monotone(points, sink)
        }
    }
}

val XY.isDefined: Boolean get() = x.isFinite() && y.isFinite()

/** Calls [block] with each maximal run of consecutive indices in `0 until n` where [defined] holds. */
internal inline fun runs(n: Int, defined: (Int) -> Boolean, block: (IntRange) -> Unit) {
    var start = -1
    for (i in 0..n) {
        val ok = i < n && defined(i)
        if (ok && start < 0) start = i
        if (!ok && start >= 0) {
            block(start until i)
            start = -1
        }
    }
}

/**
 * Fritsch–Carlson monotone cubic interpolation, following d3-shape's `monotoneX`
 * (Steffen's method for tangents). The first point has already been emitted.
 */
private fun monotone(points: List<XY>, sink: PathSink) {
    val n = points.size
    if (n == 1) return
    if (n == 2) {
        sink.lineTo(points[1].x, points[1].y)
        return
    }
    val tangents = FloatArray(n)
    for (i in 1 until n - 1) tangents[i] = slope3(points[i - 1], points[i], points[i + 1])
    tangents[0] = slope2(points[0], points[1], tangents[1])
    tangents[n - 1] = slope2(points[n - 2], points[n - 1], tangents[n - 2])
    for (i in 0 until n - 1) {
        val a = points[i]
        val b = points[i + 1]
        val dx = (b.x - a.x) / 3f
        sink.cubicTo(a.x + dx, a.y + dx * tangents[i], b.x - dx, b.y - dx * tangents[i + 1], b.x, b.y)
    }
}

private fun sign(x: Float): Float = if (x < 0f) -1f else 1f

private fun slope3(p0: XY, p1: XY, p2: XY): Float {
    val h0 = p1.x - p0.x
    val h1 = p2.x - p1.x
    val s0 = if (h0 != 0f) (p1.y - p0.y) / h0 else 0f
    val s1 = if (h1 != 0f) (p2.y - p1.y) / h1 else 0f
    val p = if (h0 + h1 != 0f) (s0 * h1 + s1 * h0) / (h0 + h1) else 0f
    val result = (sign(s0) + sign(s1)) * min(min(abs(s0), abs(s1)), 0.5f * abs(p))
    return if (result.isNaN()) 0f else result
}

/** One-sided tangent at an end point, from the neighbouring interior tangent [t]. */
private fun slope2(a: XY, b: XY, t: Float): Float {
    val h = b.x - a.x
    return if (h == 0f) t else (3f * (b.y - a.y) / h - t) / 2f
}
