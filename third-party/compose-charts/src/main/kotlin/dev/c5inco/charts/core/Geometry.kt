package dev.c5inco.charts.core

/** A point in scene space (pixels, y down). */
public data class XY(val x: Float, val y: Float)

/** A rectangle in scene space. */
public data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    public fun contains(p: XY): Boolean = p.x in left..right && p.y in top..bottom
}

/** A width and height in pixels. */
public data class Extent(val width: Float, val height: Float)

/** A stretch of one axis in scene space, from [start] to [end] (pixels: top to bottom, or left to right). */
public data class Span(val start: Float, val end: Float) {
    val length: Float get() = end - start
}

internal fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

internal fun lerp(a: XY, b: XY, t: Float): XY = XY(lerp(a.x, b.x, t), lerp(a.y, b.y, t))

internal fun lerp(a: Bounds, b: Bounds, t: Float): Bounds =
    Bounds(lerp(a.left, b.left, t), lerp(a.top, b.top, t), lerp(a.right, b.right, t), lerp(a.bottom, b.bottom, t))

internal fun lerp(a: Span, b: Span, t: Float): Span = Span(lerp(a.start, b.start, t), lerp(a.end, b.end, t))
