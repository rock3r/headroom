package dev.sebastiano.headroom.ui.detail

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StackedBarLayoutTest {
    /** Where the last visible segment ends, with the gaps between the visible ones. */
    private fun end(widths: List<Float>): Float {
        val visible = widths.filter { it > 0f }
        return visible.sum() + GAP * (visible.size - 1)
    }

    @Test
    fun `a nearly full pool keeps every product inside the bar`() {
        // Shares of the whole bar: 97% and three 1% products, 100% used in all.
        val widths = stackedBarWidths(listOf(0.97, 0.01, 0.01, 0.01), WIDTH, GAP, MIN)

        assertTrue(widths.all { it >= MIN }, "$widths")
        assertEquals(WIDTH, end(widths), absoluteTolerance = 0.01f)
    }

    @Test
    fun `the segments end where the share used ends`() {
        // Chris's pool: 84% used, of which Cursor & API 57%, Grok Bot 22%, Imagine 4%, Chat 1%.
        val widths = stackedBarWidths(listOf(0.57, 0.22, 0.04, 0.01), WIDTH, GAP, MIN)

        assertEquals(0.84f * WIDTH, end(widths), absoluteTolerance = 0.01f)
        assertTrue(widths.all { it >= MIN }, "$widths")
        // The big ones keep their order of size; only the gaps came out of them.
        assertTrue(widths[0] > widths[1] && widths[1] > widths[2], "$widths")
    }

    @Test
    fun `a product that used nothing has no segment`() {
        val widths = stackedBarWidths(listOf(0.3, 0.0, 0.2), WIDTH, GAP, MIN)

        assertEquals(0f, widths[1])
        assertEquals(0.5f * WIDTH, end(widths), absoluteTolerance = 0.01f)
    }

    private companion object {
        const val WIDTH = 300f
        const val GAP = 2f
        const val MIN = 3f
    }
}
