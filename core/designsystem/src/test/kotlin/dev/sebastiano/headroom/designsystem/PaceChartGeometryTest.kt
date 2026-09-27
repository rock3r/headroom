package dev.sebastiano.headroom.designsystem

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class PaceChartGeometryTest {
    private val start = Instant.parse("2026-09-23T07:00:00Z")
    private val end = start.plus(Duration.ofDays(7))
    private val geometry =
        PaceChartGeometry(
            start = start,
            end = end,
            left = 10f,
            top = 20f,
            right = 290f,
            bottom = 120f,
        )

    @Test
    fun `the window start and end map to the plot edges`() {
        assertEquals(10f, geometry.x(start), 0.01f)
        assertEquals(290f, geometry.x(end), 0.01f)
    }

    @Test
    fun `time maps linearly to x`() {
        assertEquals(150f, geometry.x(start.plus(Duration.ofHours(84))), 0.01f)
    }

    @Test
    fun `percent maps to y with the limit at the top`() {
        assertEquals(120f, geometry.y(0.0), 0.01f)
        assertEquals(20f, geometry.y(100.0), 0.01f)
        assertEquals(70f, geometry.y(50.0), 0.01f)
    }

    @Test
    fun `values outside the window are clamped to the plot`() {
        assertEquals(290f, geometry.x(end.plus(Duration.ofDays(1))), 0.01f)
        assertEquals(20f, geometry.y(130.0), 0.01f)
    }

    @Test
    fun `tick positions split the window into equal parts`() {
        assertEquals(listOf(10f, 150f, 290f), geometry.tickXs(count = 2))
    }
}
