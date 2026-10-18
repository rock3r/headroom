package dev.sebastiano.headroom.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Windows a second sign-in added sit beside the account's own, and never lead it: signing in to
 * something else must not change what the account's headline, pace and sorting describe.
 */
class SecondaryWindowsTest {
    private val now = Instant.parse("2026-10-18T12:00:00Z")
    private val account = Account("g1", Provider.Grok, "sam@example.com")

    /** A Grok account on unified billing: no weekly period, so its own window is monthly. */
    private val monthly =
        QuotaWindow(
            id = "monthly",
            label = "Monthly",
            kind = WindowKind.Monthly,
            usedPercent = 10.0,
            resetsAt = now.plus(30.days),
            length = 30.days,
        )

    /** Cursor's pools, which a Grok account's Cursor sign-in adds. */
    private val bot =
        QuotaWindow(
            id = "grok-bot",
            label = "Grok Bot",
            kind = WindowKind.Weekly,
            usedPercent = 96.0,
            resetsAt = now.plus(1.days),
            length = 7.days,
            isSecondary = true,
        )

    private fun state(vararg windows: QuotaWindow) =
        AccountState(
            account = account,
            snapshot =
                QuotaSnapshot(
                    provider = Provider.Grok,
                    accountId = account.id,
                    planLabel = "SuperGrok Heavy",
                    windows = windows.toList(),
                    fetchedAt = now,
                ),
        )

    @Test
    fun `an added weekly window does not take the headline from the account's monthly one`() {
        assertEquals("monthly", state(monthly, bot).primaryWindow?.id)
        assertFalse(monthly.isSecondary)
        assertTrue(bot.isSecondary)
    }

    @Test
    fun `an account whose windows are all added still leads with one`() {
        assertEquals("grok-bot", state(bot).primaryWindow?.id)
    }
}
