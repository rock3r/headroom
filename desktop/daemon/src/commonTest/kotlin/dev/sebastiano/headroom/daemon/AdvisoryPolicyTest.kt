package dev.sebastiano.headroom.daemon

import dev.sebastiano.headroom.model.PaceStatus
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.WindowKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class AdvisoryPolicyTest {
    private val now = Instant.parse("2026-11-10T12:00:00Z")
    private val policy = AdvisoryPolicy()

    @Test
    fun overPaceArmsOnceAndStaysSilentUntilPaceRecovers() {
        val over = weekly(usedPercent = 70.0, resetsIn = 7.days)
        val first = policy.evaluate(account("a"), over, now, AdvisoryMemory())

        assertEquals(AdvisoryKind.OverPace, first.notice?.kind)
        val stillOver = policy.evaluate(account("a"), over, now + 1.hours, first.memory)

        assertNull(stillOver.notice)
        val recovered = weekly(usedPercent = 10.0, resetsIn = 6.days)
        val afterRecovery =
            policy.evaluate(account("a"), recovered, now + 2.hours, stillOver.memory)
        val overAgain = policy.evaluate(account("a"), over, now + 3.hours, afterRecovery.memory)

        assertNull(afterRecovery.notice)
        assertEquals(AdvisoryKind.OverPace, overAgain.notice?.kind)
    }

    @Test
    fun lowRemainingArmsAtTenPercentAndRearmsOnlyAfterHysteresis() {
        val low = weekly(usedPercent = 90.0, resetsIn = 1.days)
        val first = policy.evaluate(account("a"), low, now, AdvisoryMemory())
        val stillLow = policy.evaluate(account("a"), low, now + 1.hours, first.memory)
        val justAbove = weekly(usedPercent = 80.0, resetsIn = 1.days)
        val notYet = policy.evaluate(account("a"), justAbove, now + 2.hours, stillLow.memory)
        val clear = weekly(usedPercent = 70.0, resetsIn = 1.days)
        val rearmed = policy.evaluate(account("a"), clear, now + 3.hours, notYet.memory)
        val lowAgain = policy.evaluate(account("a"), low, now + 4.hours, rearmed.memory)

        assertEquals(AdvisoryKind.LowRemaining, first.notice?.kind)
        assertNull(stillLow.notice)
        assertNull(notYet.notice)
        assertNull(rearmed.notice)
        assertEquals(AdvisoryKind.LowRemaining, lowAgain.notice?.kind)
    }

    @Test
    fun informationalAndSessionWindowsNeverAdvise() {
        val credit = weekly(usedPercent = 99.0, resetsIn = 1.days).copy(kind = WindowKind.Credit)
        val unknown = weekly(usedPercent = 99.0, resetsIn = 1.days).copy(isRecognised = false)
        val session =
            weekly(usedPercent = 99.0, resetsIn = 1.hours)
                .copy(
                    kind = WindowKind.Session,
                    length = 5.hours,
                )

        assertNull(policy.evaluate(account("a"), credit, now, AdvisoryMemory()).notice)
        assertNull(policy.evaluate(account("a"), unknown, now, AdvisoryMemory()).notice)
        assertNull(policy.evaluate(account("a"), session, now, AdvisoryMemory()).notice)
    }

    @Test
    fun cooldownIsPerAccountAndWindowNotGlobal() {
        val left = weekly(id = "seven_day", usedPercent = 95.0, resetsIn = 1.days)
        val right = weekly(id = "seven_day_opus", usedPercent = 95.0, resetsIn = 1.days)
        val first = policy.evaluate(account("a"), left, now, AdvisoryMemory())
        val otherWindow = policy.evaluate(account("a"), right, now, first.memory)
        val otherAccount = policy.evaluate(account("b"), left, now, otherWindow.memory)

        assertEquals("seven_day", first.notice?.windowId)
        assertEquals("seven_day_opus", otherWindow.notice?.windowId)
        assertEquals("claude-b", otherAccount.notice?.accountRef)
    }

    @Test
    fun aMissingResetTimeIsReportedAndNotInvented() {
        val window = weekly(usedPercent = 96.0, resetsIn = 1.days).copy(resetsAt = null)
        val decision = policy.evaluate(account("a"), window, now, AdvisoryMemory())

        assertEquals(PaceStatus.On, decision.pace)
        assertNull(decision.notice?.resetsAt)
        assertEquals(AdvisoryKind.LowRemaining, decision.notice?.kind)
    }

    private fun account(suffix: String) = AccountRef(Provider.Claude, "acct-$suffix")

    private fun weekly(
        id: String = "seven_day",
        usedPercent: Double,
        resetsIn: kotlin.time.Duration,
    ) =
        QuotaWindow(
            id = id,
            label = "Weekly",
            kind = WindowKind.Weekly,
            usedPercent = usedPercent,
            resetsAt = now + resetsIn,
            length = 7.days,
        )
}
