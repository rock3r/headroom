package dev.sebastiano.headroom.widget.render

import dev.sebastiano.headroom.widget.WidgetSize
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class WidgetMetricsTest {
    @Test
    fun `bars grow to fill a tall widget, up to a limit`() {
        val small = BarsMetrics.of(WidgetSize(300f, 110f), rows = 3)
        val tall = BarsMetrics.of(WidgetSize(360f, 200f), rows = 3)
        val huge = BarsMetrics.of(WidgetSize(360f, 600f), rows = 2)
        assertTrue(tall.rowDp > small.rowDp)
        assertTrue(tall.scale > small.scale)
        assertEquals(BarsMetrics.MAX_ROW_DP, huge.rowDp)
        assertEquals(BarsMetrics.MAX_SCALE, huge.scale)
    }

    @Test
    fun `rows always fit the height they are given`() {
        val size = WidgetSize(360f, 200f)
        val metrics = BarsMetrics.of(size, rows = 3)
        val used = 3 * metrics.rowDp + 2 * BarsMetrics.GAP_DP + 2 * BarsMetrics.PADDING_DP
        assertTrue(used <= size.heightDp + 0.01f, "rows use $used dp of ${size.heightDp}")
    }

    @Test
    fun `tall enough rows show the reset time under the name`() {
        assertTrue(BarsMetrics.of(WidgetSize(360f, 200f), rows = 3).showResetLine)
        assertFalse(BarsMetrics.of(WidgetSize(300f, 110f), rows = 3).showResetLine)
        assertFalse(BarsMetrics.of(WidgetSize(200f, 200f), rows = 3).showResetLine)
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
