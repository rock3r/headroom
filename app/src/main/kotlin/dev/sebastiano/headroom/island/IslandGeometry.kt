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

    fun offset(dx: Int, dy: Int): PxRect = PxRect(left + dx, top + dy, right + dx, bottom + dy)
}

/**
 * Where the island is, in pixels. [window] is the overlay window on the screen. [collapsed] (the
 * camera hole, or a small dot) and [expanded] (the pill) are inside it, relative to its top left
 * corner.
 */
internal data class IslandGeometry(
    val window: PxRect,
    val collapsed: PxRect,
    val expanded: PxRect,
)

/**
 * Maps the camera [cutout] (or null when the screen has none) and the screen size to the geometry
 * of the island. The pill is about 72% of the screen wide and 40 dp high, centred on the cutout. It
 * grows from the size of the cutout. Without a cutout it grows from a small dot at the top centre,
 * a small margin down. The window is only as large as the pill can get, including the little
 * overshoot of the grow spring.
 */
internal fun islandGeometry(
    cutout: PxRect?,
    screenWidth: Int,
    screenHeight: Int,
    density: Float,
): IslandGeometry {
    val pillWidth = (screenWidth * PILL_WIDTH_SHARE).roundToInt()
    val pillHeight = (PILL_HEIGHT_DP * density).roundToInt()
    val centreX = cutout?.centerX ?: (screenWidth / 2f)
    val left =
        (centreX - pillWidth / 2f)
            .roundToInt()
            .coerceIn(0, (screenWidth - pillWidth).coerceAtLeast(0))

    val expanded: PxRect
    val collapsed: PxRect
    if (cutout != null) {
        // Centred on the hole, but never above the screen, and always covering the whole hole.
        val top = min(cutout.top, (cutout.centerY - pillHeight / 2f).roundToInt().coerceAtLeast(0))
        expanded = PxRect(left, top, left + pillWidth, max(top + pillHeight, cutout.bottom))
        collapsed = cutout
    } else {
        val top = (EDGE_MARGIN_DP * density).roundToInt()
        expanded = PxRect(left, top, left + pillWidth, top + pillHeight)
        val dot = (DOT_DP * density).roundToInt()
        val dotLeft = (expanded.centerX - dot / 2f).roundToInt()
        val dotTop = (expanded.centerY - dot / 2f).roundToInt()
        collapsed = PxRect(dotLeft, dotTop, dotLeft + dot, dotTop + dot)
    }

    val overshoot = (OVERSHOOT_DP * density).roundToInt()
    val reach =
        PxRect(
                expanded.left - overshoot,
                expanded.top - overshoot,
                expanded.right + overshoot,
                expanded.bottom + overshoot,
            )
            .union(collapsed)
    val window =
        PxRect(
            reach.left.coerceAtLeast(0),
            reach.top.coerceAtLeast(0),
            reach.right.coerceAtMost(screenWidth),
            reach.bottom.coerceAtMost(screenHeight),
        )
    return IslandGeometry(
        window = window,
        collapsed = collapsed.offset(-window.left, -window.top),
        expanded = expanded.offset(-window.left, -window.top),
    )
}

/**
 * The geometry of the island in `Overlay` mode. That window cannot draw over the status bar, so the
 * pill hangs just below it, [statusBarHeight] pixels down plus a small margin, centred on the
 * screen. It grows from a small dot at its own centre. The window is exactly the pill and nothing
 * more, because it takes the touches in its own area. The grow spring passes its target by a
 * little, and the window clips that.
 */
internal fun overlayIslandGeometry(
    statusBarHeight: Int,
    screenWidth: Int,
    screenHeight: Int,
    density: Float,
): IslandGeometry {
    val pillWidth = min((screenWidth * PILL_WIDTH_SHARE).roundToInt(), screenWidth)
    val pillHeight = (PILL_HEIGHT_DP * density).roundToInt()
    val left = ((screenWidth - pillWidth) / 2f).roundToInt()
    val top =
        (statusBarHeight + OVERLAY_MARGIN_DP * density)
            .roundToInt()
            .coerceAtMost((screenHeight - pillHeight).coerceAtLeast(0))
    val window = PxRect(left, top, left + pillWidth, top + pillHeight)
    val dot = (DOT_DP * density).roundToInt()
    val dotLeft = ((pillWidth - dot) / 2f).roundToInt()
    val dotTop = ((pillHeight - dot) / 2f).roundToInt()
    return IslandGeometry(
        window = window,
        collapsed = PxRect(dotLeft, dotTop, dotLeft + dot, dotTop + dot),
        expanded = PxRect(0, 0, pillWidth, pillHeight),
    )
}

/** How much of the screen width the pill takes. */
private const val PILL_WIDTH_SHARE = 0.72f

private const val PILL_HEIGHT_DP = 40f

/** The gap above the pill when there is no camera cutout to grow from. */
private const val EDGE_MARGIN_DP = 8f

/** The size of the dot the pill grows from when there is no camera cutout. */
private const val DOT_DP = 16f

/** Room around the pill for the grow spring, which passes its target by a little. */
private const val OVERSHOOT_DP = 8f

/** The gap between the status bar and the pill of the overlay island. */
private const val OVERLAY_MARGIN_DP = 4f
