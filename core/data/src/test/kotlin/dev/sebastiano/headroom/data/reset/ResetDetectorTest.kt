package dev.sebastiano.headroom.data.reset

import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.WindowKind
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class ResetDetectorTest {
    private val resetsAt = Instant.parse("2026-09-28T06:00:00Z")

    private fun weekly(used: Double, resetsAt: Instant = this.resetsAt) =
        QuotaWindow("weekly", "Weekly", WindowKind.Weekly, used, resetsAt, 7.days)

    @Test
    fun `usage dropping means the window reset`() {
        assertTrue(
            ResetDetector.hasReset(
                before = weekly(88.0),
                after = weekly(0.0, resetsAt.plus(7.days)),
            )
        )
    }

    @Test
    fun `reset time moving forward by about a week means the window reset even with some usage`() {
        assertTrue(
            ResetDetector.hasReset(
                before = weekly(88.0),
                after = weekly(91.0, resetsAt.plus(7.days)),
            )
        )
    }

    @Test
    fun `same reset time and same or higher usage is not a reset`() {
        assertFalse(ResetDetector.hasReset(before = weekly(88.0), after = weekly(88.0)))
        assertFalse(ResetDetector.hasReset(before = weekly(88.0), after = weekly(90.0)))
    }

    @Test
    fun `a small drop from rounding is not a reset`() {
        assertFalse(ResetDetector.hasReset(before = weekly(88.0), after = weekly(87.5)))
    }
}
