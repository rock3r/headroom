package dev.sebastiano.headroom.model

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaceTest {
    private val resetsAt = Instant.parse("2026-09-30T09:00:00Z")

    private fun weekly(used: Double) =
        QuotaWindow(
            id = "seven_day",
            label = "Weekly",
            kind = WindowKind.Weekly,
            usedPercent = used,
            resetsAt = resetsAt,
            length = Duration.ofDays(7),
        )

    @Test
    fun `expected percent is the share of the window that has elapsed`() {
        val now = resetsAt.minus(Duration.ofHours(84)) // half of 168h
        assertEquals(50.0, Pace.expectedPercent(weekly(10.0), now)!!, 0.001)
    }

    @Test
    fun `expected percent is null without a reset time or a length`() {
        val window = weekly(10.0).copy(resetsAt = null)
        assertNull(Pace.expectedPercent(window, Instant.EPOCH))
    }

    @Test
    fun `expected percent is clamped to the window`() {
        assertEquals(100.0, Pace.expectedPercent(weekly(10.0), resetsAt.plusSeconds(60))!!, 0.001)
        assertEquals(
            0.0,
            Pace.expectedPercent(weekly(10.0), resetsAt.minus(Duration.ofDays(9)))!!,
            0.001,
        )
    }

    @Test
    fun `status is over pace beyond the tolerance`() {
        val now = resetsAt.minus(Duration.ofHours(84))
        assertEquals(PaceStatus.Over, Pace.status(weekly(60.0), now))
        assertEquals(PaceStatus.On, Pace.status(weekly(54.0), now))
        assertEquals(PaceStatus.Under, Pace.status(weekly(40.0), now))
    }

    @Test
    fun `a window needs attention when over pace or nearly used up`() {
        val now = resetsAt.minus(Duration.ofHours(12)) // expected ~92.9%
        assertTrue(Pace.needsAttention(weekly(88.0), now))
        assertEquals(false, Pace.needsAttention(weekly(30.0), resetsAt.minus(Duration.ofHours(84))))
    }

    @Test
    fun `projection finds when the limit is reached at the current rate`() {
        val now = resetsAt.minus(Duration.ofHours(84))
        val hit = Pace.projectedLimitAt(weekly(75.0), now)
        // 75% in 84h -> 100% after 112h -> 28h from now
        assertEquals(now.plus(Duration.ofHours(28)), hit)
        assertNull(Pace.projectedLimitAt(weekly(20.0), now))
    }
}
