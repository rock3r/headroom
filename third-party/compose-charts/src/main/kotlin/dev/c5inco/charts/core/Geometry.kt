package dev.c5inco.charts.core

/** A point in scene space (pixels, y down). */
data class XY(val x: Float, val y: Float)

/** A rectangle in scene space. */
data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun contains(p: XY): Boolean = p.x in left..right && p.y in top..bottom
}

/** A width and height in pixels. */
data class Extent(val width: Float, val height: Float)

internal fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

internal fun lerp(a: XY, b: XY, t: Float): XY = XY(lerp(a.x, b.x, t), lerp(a.y, b.y, t))

internal fun lerp(a: Bounds, b: Bounds, t: Float): Bounds =
    Bounds(lerp(a.left, b.left, t), lerp(a.top, b.top, t), lerp(a.right, b.right, t), lerp(a.bottom, b.bottom, t))
