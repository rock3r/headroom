package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.UsagePoint
import dev.sebastiano.headroom.model.WindowKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class HistoryMappingTest {
    private val now = Instant.parse("2026-09-27T12:00:00Z")
    private val weeklyReset = now + 3.days
    private val weeklyStart = weeklyReset - 7.days
    private val monthlyReset = now + 10.days
    private val session =
        QuotaWindow("five_hour", "5h", WindowKind.Session, 10.0, now + 2.hours, 5.hours)
    private val weekly =
        QuotaWindow("seven_day", "Weekly", WindowKind.Weekly, 40.0, weeklyReset, 7.days)
    private val monthly =
        QuotaWindow("monthly", "Monthly", WindowKind.Monthly, 20.0, monthlyReset, 30.days)

    private fun state(
        windows: List<QuotaWindow>,
        fetchedAt: Instant = now,
        error: QuotaErrorKind? = null,
    ) =
        AccountState(
            account = Account("a", Provider.Claude, "me"),
            snapshot = QuotaSnapshot(Provider.Claude, "a", null, windows, fetchedAt),
            lastError = error,
        )

    @Test
    fun `only weekly and monthly windows are charted with the primary one first`() {
        val chart =
            HistoryMapping.history(state(listOf(session, monthly, weekly)), emptyMap(), now, true)!!

        assertEquals(listOf("seven_day", "monthly"), chart.windows.map { it.id })
        assertTrue(chart.windows.first().isPrimary)
    }

    @Test
    fun `an account without long windows has no history chart`() {
        assertNull(HistoryMapping.history(state(listOf(session)), emptyMap(), now, true))
    }

    @Test
    fun `past periods stay in the history and their resets are found`() {
        val lastWeek = weeklyStart - 7.days
        val points =
            listOf(
                UsagePoint(lastWeek + 1.days, 30.0, weeklyStart),
                UsagePoint(weeklyStart - 1.hours, 90.0, weeklyStart),
                UsagePoint(weeklyStart + 1.hours, 2.0, weeklyReset),
                UsagePoint(now - 1.hours, 40.0, weeklyReset),
            )

        val chart =
            HistoryMapping.history(
                state(listOf(weekly), fetchedAt = now - 1.hours),
                mapOf("seven_day" to points),
                now,
                true,
            )!!
        val window = chart.windows.single()

        assertEquals(4, window.points.size)
        assertEquals(listOf((weeklyStart + 1.hours).epochSeconds), window.resetsEpochSeconds)
        assertEquals((lastWeek + 1.days).epochSeconds, chart.minEpochSeconds)
        assertEquals(weeklyReset.epochSeconds, chart.maxEpochSeconds)
        // The current period's summary only keeps this week's points.
        assertEquals(2, window.summary.points.size)
    }

    @Test
    fun `the period range follows the shortest shown window`() {
        val chart = HistoryMapping.history(state(listOf(weekly, monthly)), emptyMap(), now, true)!!

        assertEquals(
            weeklyStart.epochSeconds,
            chart.rangeStartEpochSeconds("period", listOf("seven_day", "monthly")),
        )
        assertEquals(
            weeklyReset.epochSeconds,
            chart.rangeEndEpochSeconds("period", listOf("seven_day", "monthly")),
        )
        assertEquals(
            (monthlyReset - 30.days).epochSeconds,
            chart.rangeStartEpochSeconds("period", listOf("monthly")),
        )
        assertEquals(
            monthlyReset.epochSeconds,
            chart.rangeEndEpochSeconds("period", listOf("monthly")),
        )
    }

    @Test
    fun `longer ranges need history older than the current period`() {
        val fresh = HistoryMapping.history(state(listOf(weekly)), emptyMap(), now, true)!!
        assertEquals(listOf("period"), fresh.ranges)

        val old = listOf(UsagePoint(now - 120.days, 10.0), UsagePoint(now - 1.hours, 40.0))
        val long =
            HistoryMapping.history(state(listOf(weekly)), mapOf("seven_day" to old), now, true)!!
        assertEquals(listOf("period", "1m", "3m", "1y"), long.ranges)

        val short =
            HistoryMapping.history(state(listOf(weekly)), mapOf("seven_day" to old), now, false)!!
        assertEquals(listOf("period", "1m", "3m"), short.ranges)
        assertEquals(
            weeklyReset.epochSeconds - 30.days.inWholeSeconds,
            long.rangeStartEpochSeconds("1m", listOf("seven_day")),
        )
    }

    @Test
    fun `a stale account is charted as of its last sync`() {
        val fetchedAt = now - 2.days
        val chart =
            HistoryMapping.history(
                state(listOf(weekly), fetchedAt = fetchedAt, error = QuotaErrorKind.Auth),
                emptyMap(),
                now,
                true,
            )!!

        assertEquals(fetchedAt.epochSeconds, chart.windows.single().sampleAtEpochSeconds)
        assertEquals(40.0, chart.windows.single().points.single().usedPercent)
    }
}
