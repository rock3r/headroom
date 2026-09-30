package dev.sebastiano.headroom.model

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Credits and quotas the app does not recognise are shown, but never drive anything. */
class InformationalWindowsTest {
    private val now = Instant.parse("2026-10-01T12:00:00Z")
    private val account = Account("a1", Provider.Claude, "sam@example.com")

    private val weekly =
        QuotaWindow(
            id = "seven_day",
            label = "Weekly · all models",
            kind = WindowKind.Weekly,
            usedPercent = 40.0,
            resetsAt = now.plus(Duration.ofDays(3)),
            length = Duration.ofDays(7),
        )

    private val credit =
        QuotaWindow(
            id = "iguana_necktie",
            label = "Cloud session credit",
            kind = WindowKind.Credit,
            usedPercent = 95.0,
            resetsAt = null,
            length = null,
            usedAmount = 237.5,
            limitAmount = 250.0,
            amountUnit = "USD",
            expiresAt = now.plus(Duration.ofDays(1)),
        )

    /** An unknown window that looks weekly and resets sooner than the known weekly window. */
    private val unknown =
        QuotaWindow(
            id = "nimbus_quill",
            label = "Nimbus quill",
            kind = WindowKind.Weekly,
            usedPercent = 99.0,
            resetsAt = now.plus(Duration.ofHours(2)),
            length = Duration.ofDays(7),
            isRecognised = false,
        )

    private fun state(vararg windows: QuotaWindow) =
        AccountState(
            account = account,
            snapshot =
                QuotaSnapshot(
                    provider = Provider.Claude,
                    accountId = account.id,
                    planLabel = "Max 20x",
                    windows = windows.toList(),
                    fetchedAt = now,
                ),
        )

    @Test
    fun `credits and unknown windows are informational, other windows are not`() {
        assertTrue(credit.isInformational)
        assertTrue(unknown.isInformational)
        assertFalse(weekly.isInformational)
    }

    @Test
    fun `credits and unknown windows never alert`() {
        assertFalse(ResetPolicy.canAlert(credit))
        assertFalse(ResetPolicy.canAlert(unknown))
        assertFalse(ResetPolicy.alertsByDefault(credit))
        assertFalse(ResetPolicy.alertsByDefault(unknown))
    }

    @Test
    fun `credits and unknown windows are never the next reset`() {
        val next = NextReset.find(listOf(state(unknown, credit, weekly)), now)

        assertEquals("seven_day", next?.window?.id)
        assertNull(NextReset.find(listOf(state(unknown, credit)), now))
    }

    @Test
    fun `credits and unknown windows are never the primary window`() {
        assertEquals("seven_day", state(credit, unknown, weekly).primaryWindow?.id)
        assertNull(state(credit, unknown).primaryWindow)
    }

    @Test
    fun `credits and unknown windows have no pace and never need attention`() {
        assertNull(Pace.expectedPercent(unknown, now))
        assertNull(Pace.delta(unknown, now))
        assertNull(Pace.projectedLimitAt(unknown, now))
        assertEquals(PaceStatus.On, Pace.status(unknown, now))
        assertFalse(Pace.needsAttention(unknown, now))
        assertFalse(Pace.needsAttention(credit, now))
    }

    @Test
    fun `an unknown window is never the session window`() {
        val unknownSession = unknown.copy(kind = WindowKind.Session, length = Duration.ofHours(5))

        assertNull(state(credit, unknownSession).sessionWindow)
    }
}
