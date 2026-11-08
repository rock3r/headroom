package dev.sebastiano.headroom.model

import kotlin.test.Test
import kotlin.test.assertEquals

class WindowPartsTest {
    private fun window(usedPercent: Double, vararg parts: Double) =
        QuotaWindow(
            id = "weekly",
            label = "Weekly",
            kind = WindowKind.Weekly,
            usedPercent = usedPercent,
            resetsAt = null,
            length = null,
            parts = parts.mapIndexed { i, percent -> WindowPart("p$i", "P$i", percent, i) },
        )

    @Test
    fun `the parts of a bar fill exactly the share the window used`() {
        // xAI rounds each product, so they can add up to a little more or less than the total.
        listOf(window(61.0, 37.0, 20.0, 3.0, 1.0), window(61.0, 38.0, 20.0, 3.0, 1.0)).forEach {
            assertEquals(0.61, it.partFractions.sum(), absoluteTolerance = 1e-9)
        }
        val fractions = window(50.0, 30.0, 10.0).partFractions
        assertEquals(0.375, fractions[0], absoluteTolerance = 1e-9)
        assertEquals(0.125, fractions[1], absoluteTolerance = 1e-9)
    }

    @Test
    fun `parts that used nothing cover nothing`() {
        assertEquals(listOf(0.0, 0.0), window(10.0, 0.0, 0.0).partFractions)
    }
}
