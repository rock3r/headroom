package dev.sebastiano.headroom.model.stats

import dev.sebastiano.headroom.model.UsagePoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone

class BurnTest {
    private val start = Instant.parse("2026-09-21T09:00:00Z")

    private fun at(minutes: Long, used: Double) = UsagePoint(start + (minutes * 60).seconds, used)

    @Test
    fun `growth between two points is what was burned`() {
        assertEquals(12.0, burned(at(0, 30.0), at(60, 42.0)))
    }

    @Test
    fun `after a reset all of the new usage counts`() {
        assertEquals(4.0, burned(at(0, 80.0), at(60, 4.0)))
    }

    @Test
    fun `a small drop is noise and burns nothing`() {
        assertEquals(0.0, burned(at(0, 50.0), at(60, 48.0)))
    }

    @Test
    fun `the total adds every step across resets`() {
        val points = listOf(at(0, 10.0), at(60, 30.0), at(120, 29.0), at(180, 90.0), at(240, 5.0))
        // 20, then noise, then 61, then 5 after the reset.
        assertEquals(86.0, totalBurned(points))
    }

    @Test
    fun `no points or a single point burn nothing`() {
        assertEquals(0.0, totalBurned(emptyList()))
        assertEquals(0.0, totalBurned(listOf(at(0, 50.0))))
    }

    @Test
    fun `use between two syncs is spread over the hours between them`() {
        // 09:30 to 11:30 UTC: half an hour at 09, a full hour at 10, half an hour at 11.
        val hours = hourlyBurn(listOf(at(30, 0.0), at(150, 40.0)), TimeZone.UTC)
        assertEquals(
            mapOf(
                LocalDateTime.parse("2026-09-21T09:00") to 10.0,
                LocalDateTime.parse("2026-09-21T10:00") to 20.0,
                LocalDateTime.parse("2026-09-21T11:00") to 10.0,
            ),
            hours,
        )
    }

    @Test
    fun `hours are in the user's zone`() {
        val rome = TimeZone.of("Europe/Rome")
        val hours = hourlyBurn(listOf(at(0, 0.0), at(60, 10.0)), rome)
        assertEquals(mapOf(LocalDateTime.parse("2026-09-21T11:00") to 10.0), hours)
    }

    @Test
    fun `a gap too long to place in time is left out of the hours`() {
        val hours = hourlyBurn(listOf(at(0, 0.0), at(13 * 60, 50.0)), TimeZone.UTC)
        assertEquals(emptyMap<LocalDateTime, Double>(), hours)
    }

    @Test
    fun `two readings at the same moment put the use in that hour`() {
        val hours = hourlyBurn(listOf(at(10, 70.0), at(10, 3.0)), TimeZone.UTC)
        assertEquals(mapOf(LocalDateTime.parse("2026-09-21T09:00") to 3.0), hours)
    }

    @Test
    fun `use in the repeated hour when clocks go back stays in that hour`() {
        // New York's clocks go back from 2:00 to 1:00 at 06:00 UTC: this is the second 1:00 hour.
        val hours =
            hourlyBurn(
                listOf(
                    UsagePoint(Instant.parse("2026-11-01T06:10:00Z"), 0.0),
                    UsagePoint(Instant.parse("2026-11-01T06:50:00Z"), 10.0),
                ),
                TimeZone.of("America/New_York"),
            )

        assertEquals(mapOf(LocalDateTime.parse("2026-11-01T01:00") to 10.0), hours)
    }

    @Test
    fun `use across clocks going back fills both 1 am hours`() {
        val hours =
            hourlyBurn(
                listOf(
                    UsagePoint(Instant.parse("2026-11-01T05:30:00Z"), 0.0),
                    UsagePoint(Instant.parse("2026-11-01T07:30:00Z"), 40.0),
                ),
                TimeZone.of("America/New_York"),
            )

        assertEquals(
            mapOf(
                LocalDateTime.parse("2026-11-01T01:00") to 30.0,
                LocalDateTime.parse("2026-11-01T02:00") to 10.0,
            ),
            hours,
        )
    }
}
