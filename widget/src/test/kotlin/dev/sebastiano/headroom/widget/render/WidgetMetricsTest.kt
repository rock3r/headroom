package dev.sebastiano.headroom.widget.render

import dev.sebastiano.headroom.widget.WidgetSize
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class WidgetMetricsTest {
    /** Metrics for a player that scrolls, like the Android 17 one. */
    private fun scrolling(size: WidgetSize, rows: Int) =
        BarsMetrics.of(size, rows, canScroll = true)

    /** Metrics for a player that must not scroll, like the Android 16 one. */
    private fun fixed(size: WidgetSize, rows: Int) = BarsMetrics.of(size, rows, canScroll = false)

    private fun BarsMetrics.usedDp(): Float {
        val slots = shownRows + if (hiddenRows > 0) 1 else 0
        return slots * rowDp + (slots - 1) * BarsMetrics.GAP_DP + 2 * BarsMetrics.PADDING_DP
    }

    @Test
    fun `bars grow to fill a tall widget, up to a limit`() {
        val small = scrolling(WidgetSize(300f, 110f), rows = 3)
        val tall = scrolling(WidgetSize(360f, 200f), rows = 3)
        val huge = scrolling(WidgetSize(360f, 600f), rows = 2)
        assertTrue(tall.rowDp > small.rowDp)
        assertTrue(tall.scale > small.scale)
        assertEquals(BarsMetrics.MAX_ROW_DP, huge.rowDp)
        assertEquals(BarsMetrics.MAX_SCALE, huge.scale)
    }

    @Test
    fun `rows always fit the height they are given`() {
        val size = WidgetSize(360f, 200f)
        listOf(scrolling(size, rows = 3), fixed(size, rows = 3)).forEach { metrics ->
            assertTrue(metrics.usedDp() <= size.heightDp + 0.01f, "$metrics in $size")
        }
    }

    @Test
    fun `rows that fit the widget do not scroll and show every account`() {
        listOf(WidgetSize(300f, 110f) to 3, WidgetSize(300f, 400f) to 8).forEach { (size, rows) ->
            listOf(scrolling(size, rows), fixed(size, rows)).forEach { metrics ->
                assertFalse(metrics.scrolls)
                assertEquals(rows, metrics.shownRows)
                assertEquals(0, metrics.hiddenRows)
            }
        }
    }

    @Test
    fun `many rows in a short widget keep their minimum height and scroll`() {
        val metrics = scrolling(WidgetSize(300f, 110f), rows = 8)

        assertTrue(metrics.scrolls)
        assertEquals(BarsMetrics.MIN_ROW_DP, metrics.rowDp)
        assertEquals(8, metrics.shownRows)
        assertEquals(0, metrics.hiddenRows)
        assertFalse(metrics.showResetLine)
    }

    @Test
    fun `without scrolling, rows shrink to a compact height before accounts are hidden`() {
        val size = WidgetSize(300f, 240f)
        assertTrue(scrolling(size, rows = 8).scrolls)

        val metrics = fixed(size, rows = 8)

        assertFalse(metrics.scrolls)
        assertEquals(8, metrics.shownRows)
        assertTrue(metrics.rowDp >= BarsMetrics.COMPACT_ROW_DP)
        assertTrue(metrics.rowDp < BarsMetrics.MIN_ROW_DP)
        assertTrue(metrics.usedDp() <= size.heightDp + 0.01f, "$metrics in $size")
    }

    @Test
    fun `without scrolling, the rows that do not fit become one more row`() {
        val size = WidgetSize(300f, 110f)
        val metrics = fixed(size, rows = 8)

        assertFalse(metrics.scrolls)
        // 86 dp inside the padding hold three compact rows: two accounts and "+6 more".
        assertEquals(2, metrics.shownRows)
        assertEquals(6, metrics.hiddenRows)
        assertTrue(metrics.rowDp >= BarsMetrics.COMPACT_ROW_DP)
        assertTrue(metrics.usedDp() <= size.heightDp + 0.01f, "$metrics in $size")
    }

    @Test
    fun `a one row widget without scrolling shows the more row alone`() {
        val metrics = fixed(WidgetSize(300f, 40f), rows = 3)

        assertEquals(0, metrics.shownRows)
        assertEquals(3, metrics.hiddenRows)
    }

    @Test
    fun `many rows in a tall widget share its height, with the reset line when there is room`() {
        val tall = scrolling(WidgetSize(360f, 440f), rows = 8)

        assertFalse(tall.scrolls)
        assertTrue(tall.rowDp > BarsMetrics.MIN_ROW_DP)
        assertTrue(tall.usedDp() <= 440f + 0.01f, "$tall in 440 dp")
        assertTrue(tall.showResetLine)
    }

    @Test
    fun `tall enough rows show the reset time under the name`() {
        assertTrue(scrolling(WidgetSize(360f, 200f), rows = 3).showResetLine)
        assertFalse(scrolling(WidgetSize(300f, 110f), rows = 3).showResetLine)
        assertFalse(scrolling(WidgetSize(200f, 200f), rows = 3).showResetLine)
    }

    @Test
    fun `a wide widget lays its accounts out in one row`() {
        assertEquals(4, GridLayout.columns(WidgetSize(360f, 160f), count = 4))
        assertEquals(3, GridLayout.columns(WidgetSize(360f, 160f), count = 3))
    }

    @Test
    fun `a square widget uses a two-by-two grid`() {
        assertEquals(2, GridLayout.columns(WidgetSize(160f, 160f), count = 4))
        assertEquals(2, GridLayout.columns(WidgetSize(160f, 160f), count = 3))
    }

    @Test
    fun `a tall widget stacks its accounts`() {
        assertEquals(1, GridLayout.columns(WidgetSize(110f, 300f), count = 3))
    }
}
