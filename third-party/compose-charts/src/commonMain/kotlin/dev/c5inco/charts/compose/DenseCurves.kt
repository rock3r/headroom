package dev.c5inco.charts.compose

import dev.c5inco.charts.core.PathSink
import kotlin.math.abs

/**
 * Below this x-distance in pixels, a curved segment of a solid line or an area is drawn straight
 * ([StraightensDense]).
 *
 * A monotone cubic segment (`Curve.Monotone`, the only curve that traces cubics) is x-monotone and
 * never overshoots its endpoints, so it stays inside the bounding box of its two points, and its
 * distance from the chord is at most the box's smaller side (the corner's distance from the
 * diagonal, `w·h / √(w² + h²)`), so at most its x-span. Below a pixel the straight segment is
 * therefore within a sub-pixel of the curve it replaces, and a pinch that moves a segment across the
 * threshold cannot make it visibly pop. Dense series (a viewport window of thousands of points, or M4
 * decimation's four points per pixel column) are where this applies, and stroking cubics there is the
 * renderer's largest cost: Skia strokes and rasterises every frame's path on the CPU, and stroking a
 * dense line of cubics took about three times as long as the same line straight on a Pixel 4 XL
 * (docs/benchmark-baseline.md).
 *
 * Half a pixel was measured too: it left a 2,000-point window on a 1,440 px phone (points about
 * 0.6 px apart) entirely curved, so pans gained nothing; at 1 px its screenshots match `Linear`'s.
 */
internal const val STRAIGHT_BELOW_PX: Float = 1f

/**
 * Passes a trace to [target], drawing each cubic whose ends are less than [STRAIGHT_BELOW_PX] apart
 * along x as a straight line to its end point. Each segment decides alone, so a series that is dense
 * in places and sparse in others keeps its curves where they show, and the curved segments are the
 * curve's own, tangents and all: a curved segment meets a straight neighbour at the same point, and
 * any change of direction there is confined to the sub-pixel straight segment, which the round join
 * covers.
 *
 * Only for solid strokes and fills. A dash pattern is laid along the path's length, and a chord is
 * shorter than its curve: on a noisy dense line the dashes past a straightened stretch would move by
 * up to a quarter of a dash period, and jump when a pinch crosses the threshold. Patterned lines are
 * traced into [target] directly.
 *
 * One instance per [TextCache], reused every frame: it holds only the current point.
 */
internal class StraightensDense(private val target: PathSink) : PathSink {
    private var x = 0f
    private var startX = 0f

    override fun moveTo(x: Float, y: Float) {
        this.x = x
        startX = x
        target.moveTo(x, y)
    }

    override fun lineTo(x: Float, y: Float) {
        this.x = x
        target.lineTo(x, y)
    }

    override fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) {
        if (abs(x - this.x) < STRAIGHT_BELOW_PX) target.lineTo(x, y) else target.cubicTo(x1, y1, x2, y2, x, y)
        this.x = x
    }

    override fun close() {
        x = startX
        target.close()
    }
}
