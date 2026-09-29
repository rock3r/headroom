package dev.sebastiano.headroom.island

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A rectangle in whole pixels. It is the pure twin of `android.graphics.Rect`. */
internal data class PxRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int
        get() = right - left

    val height: Int
        get() = bottom - top

    val centerX: Float
        get() = (left + right) / 2f

    val centerY: Float
        get() = (top + bottom) / 2f

    fun contains(other: PxRect): Boolean =
        other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom

    fun union(other: PxRect): PxRect =
        PxRect(
            min(left, other.left),
            min(top, other.top),
            max(right, other.right),
            max(bottom, other.bottom),
        )

    val isEmpty: Boolean
        get() = width <= 0 || height <= 0

    fun offset(dx: Int, dy: Int): PxRect = PxRect(left + dx, top + dy, right + dx, bottom + dy)
}

/**
 * The camera hole itself. Android reports a cutout as a bounding [rect], which on a Pixel runs from
 * the top edge down past the hole, so the [outline] of the cutout is used when there is one.
 * Without it, a rect taller than wide is taken as a round hole at its bottom.
 */
internal fun cameraHole(outline: PxRect?, rect: PxRect?): PxRect? =
    outline
        ?: rect?.let {
            if (it.height > it.width) PxRect(it.left, it.bottom - it.width, it.right, it.bottom)
            else it
        }

/**
 * Where the island is, in pixels. [window] is the overlay window on the screen. The other rects are
 * inside it, relative to its top left corner: [collapsed] is the camera hole (or a small dot),
 * [capsule] a pill as tall as the island around the hole, and [expanded] the whole island. The
 * island grows from [collapsed] to [capsule], then sideways to [expanded].
 */
internal data class IslandGeometry(
    val window: PxRect,
    val collapsed: PxRect,
    val capsule: PxRect,
    val expanded: PxRect,
)

/**
 * Maps the camera [cutout] (or null when the screen has none) and the screen size to the geometry
 * of the island. The island is a compact pill around the hole: a little larger than it all round,
 * and [SIDE_DP] wider on each side, so the hole sits exactly in the middle. Without a cutout it is
 * the same pill around a small dot at the top centre, a small margin down.
 *
 * A [tight] window is exactly the pill, for a window that takes the touches in its own area. A
 * loose one has room for the grow spring, which passes its target by a little.
 */
internal fun islandGeometry(
    cutout: PxRect?,
    screenWidth: Int,
    screenHeight: Int,
    density: Float,
    tight: Boolean = false,
): IslandGeometry {
    fun px(dp: Float) = (dp * density).roundToInt()
    val hole =
        cutout
            ?: run {
                val dot = px(DOT_DP)
                val left = ((screenWidth - dot) / 2f).roundToInt()
                val top = px(EDGE_MARGIN_DP) + (px(MIN_HEIGHT_DP) - dot) / 2
                PxRect(left, top, left + dot, top + dot)
            }
    val pad = px(PAD_DP)
    val pillHeight = max(hole.height + 2 * pad, px(MIN_HEIGHT_DP))
    val top = (hole.centerY - pillHeight / 2f).roundToInt().coerceAtLeast(0)
    val bottom = max(top + pillHeight, hole.bottom + pad)
    // Measured from the hole's own edges, so both sides are the same to the pixel.
    val side = px(SIDE_DP)
    val expanded = PxRect(hole.left - side, top, hole.right + side, bottom)
    val capsuleSide = max(pad, ((bottom - top) - hole.width) / 2)
    val capsule = PxRect(hole.left - capsuleSide, top, hole.right + capsuleSide, bottom)

    val room = if (tight) 0 else px(OVERSHOOT_DP)
    val reach =
        PxRect(
            expanded.left - room,
            expanded.top - room,
            expanded.right + room,
            expanded.bottom + room,
        )
    val window =
        PxRect(
            reach.left.coerceAtLeast(0),
            reach.top.coerceAtLeast(0),
            reach.right.coerceAtMost(screenWidth),
            reach.bottom.coerceAtMost(screenHeight),
        )
    // Near an edge the window is cut; the pill moves in with it, so it stays whole.
    val shift =
        (window.left - expanded.left).coerceAtLeast(0) -
            (expanded.right - window.right).coerceAtLeast(0)
    return IslandGeometry(
        window = window,
        collapsed = hole.offset(-window.left, -window.top),
        capsule = capsule.offset(shift - window.left, -window.top),
        expanded = expanded.offset(shift - window.left, -window.top),
    )
}

/** How far the pill reaches past the hole on each side, for the logo and for the ring. */
private const val SIDE_DP = 70f

/** How much larger than the hole the pill is, above, below and around it. */
private const val PAD_DP = 6f

/** The pill is never lower than this, however small the hole. */
private const val MIN_HEIGHT_DP = 32f

/** The gap above the pill when there is no camera cutout to grow from. */
private const val EDGE_MARGIN_DP = 8f

/** The size of the dot the pill grows from when there is no camera cutout. */
private const val DOT_DP = 16f

/** Room around the pill for the grow spring, which passes its target by a little. */
private const val OVERSHOOT_DP = 8f
