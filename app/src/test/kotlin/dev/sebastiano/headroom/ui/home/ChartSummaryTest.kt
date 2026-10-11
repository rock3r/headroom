package dev.sebastiano.headroom.ui.home

import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.UsagePoint
import dev.sebastiano.headroom.model.WindowKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class ChartSummaryTest {
    private val now = Instant.parse("2026-09-30T19:34:00Z")
    private val start = Instant.parse("2026-09-29T19:44:00Z")
    private val week = 7.days
    private val window =
        QuotaWindow(
            id = "weekly",
            label = "Weekly",
            kind = WindowKind.Weekly,
            usedPercent = 11.0,
            resetsAt = start.plus(week),
            length = week,
        )

    @Test
    fun `the chart only draws the points of the current window`() {
        val lastWeek = UsagePoint(start.minus(1.hours), 97.0)
        val thisWeek =
            listOf(
                UsagePoint(start.plus(2.hours), 0.0),
                UsagePoint(now.minus(1.hours), 9.0),
            )

        val chart = assertNotNull(chartSummary(window, listOf(lastWeek) + thisWeek, now))

        // A sample from before the reset would sit on the left edge and draw a line straight up.
        assertEquals(thisWeek, chart.points)
    }
}
