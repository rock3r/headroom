package dev.sebastiano.headroom.designsystem

import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.WindowKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class PaceChipStateTest {
    private val now = Instant.parse("2026-09-27T12:32:00Z")

    private fun weekly(used: Double, elapsed: Duration) =
        QuotaWindow(
            id = "w",
            label = "Weekly",
            kind = WindowKind.Weekly,
            usedPercent = used,
            resetsAt = now.minus(elapsed).plus(7.days),
            length = 7.days,
        )

    @Test
    fun `usage well above even pace is over pace by the rounded number of points`() {
        // Half the week gone, 61% used: 11 points over.
        assertEquals(
            PaceChipState.Over(points = 11),
            PaceChipState.from(weekly(61.0, 84.hours), now),
        )
    }

    @Test
    fun `usage well below even pace is under pace`() {
        assertEquals(
            PaceChipState.Under(points = 20),
            PaceChipState.from(weekly(30.0, 84.hours), now),
        )
    }

    @Test
    fun `usage within the tolerance is on pace`() {
        assertEquals(
            PaceChipState.OnPace,
            PaceChipState.from(weekly(53.0, 84.hours), now),
        )
    }

    @Test
    fun `an unused window that has only just started is just reset`() {
        assertEquals(
            PaceChipState.JustReset,
            PaceChipState.from(weekly(0.0, 30.minutes), now),
        )
    }

    @Test
    fun `a window without a reset time is on pace`() {
        val window = weekly(40.0, 1.hours).copy(resetsAt = null)
        assertEquals(PaceChipState.OnPace, PaceChipState.from(window, now))
    }
}
