package dev.sebastiano.headroom.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Accounts with both a weekly and a monthly limit surface the monthly window beside the weekly
 * primary, so the overview can show both across every provider.
 */
class MonthlyWindowTest {
    private val now = Instant.parse("2026-10-18T12:00:00Z")

    private val session =
        QuotaWindow(
            id = "rolling",
            label = "5 hour",
            kind = WindowKind.Session,
            usedPercent = 25.0,
            resetsAt = now.plus(3.hours),
            length = 5.hours,
        )

    private val weekly =
        QuotaWindow(
            id = "weekly",
            label = "Weekly",
            kind = WindowKind.Weekly,
            usedPercent = 60.0,
            resetsAt = now.plus(3.days),
            length = 7.days,
        )

    private val monthly =
        QuotaWindow(
            id = "monthly",
            label = "Monthly",
            kind = WindowKind.Monthly,
            usedPercent = 42.0,
            resetsAt = now.plus(14.days),
            length = 30.days,
        )

    private fun state(provider: Provider, vararg windows: QuotaWindow) =
        AccountState(
            account = Account("a1", provider, "sam@example.com"),
            snapshot =
                QuotaSnapshot(
                    provider = provider,
                    accountId = "a1",
                    planLabel = null,
                    windows = windows.toList(),
                    fetchedAt = now,
                ),
        )

    @Test
    fun `returns the monthly window when primary is weekly`() {
        val opencode = state(Provider.OpenCodeGo, session, weekly, monthly)
        val kimi = state(Provider.Kimi, weekly, monthly)

        assertEquals("weekly", opencode.primaryWindow?.id)
        assertEquals("monthly", opencode.monthlyWindow?.id)
        assertEquals("monthly", kimi.monthlyWindow?.id)
    }

    @Test
    fun `returns null when primary is already monthly`() {
        val anotherMonthly = monthly.copy(id = "chat", label = "Chat")
        val copilot = state(Provider.Copilot, monthly, anotherMonthly)

        assertEquals("monthly", copilot.primaryWindow?.id)
        assertNull(copilot.monthlyWindow)
    }

    @Test
    fun `ignores secondary monthly windows from a second sign-in`() {
        val cursorMonthly =
            monthly.copy(id = "cursor-models", label = "Cursor Models", isSecondary = true)
        val grok = state(Provider.Grok, weekly, cursorMonthly)

        assertEquals("weekly", grok.primaryWindow?.id)
        assertNull(grok.monthlyWindow)
    }

    @Test
    fun `ignores unlimited and informational monthly windows`() {
        val unlimitedMonthly = monthly.copy(id = "unlimited", isUnlimited = true)
        val unknownMonthly = monthly.copy(id = "unknown", isRecognised = false)

        assertNull(
            state(Provider.OpenCodeGo, weekly, unlimitedMonthly, unknownMonthly).monthlyWindow
        )
    }

    @Test
    fun `excludes Claude monthly extra usage`() {
        val extraUsage =
            monthly.copy(
                id = "extra_usage",
                label = "Extra usage",
                resetsAt = null,
                isSecondary = true,
            )
        // Cached snapshots stored before extra_usage was marked secondary still carry
        // isSecondary=false.
        val cachedExtraUsage = extraUsage.copy(isSecondary = false)

        assertNull(state(Provider.Claude, session, weekly, extraUsage).monthlyWindow)
        assertNull(state(Provider.Claude, session, weekly, cachedExtraUsage).monthlyWindow)
        // Even without a weekly window, pay-as-you-go extra usage never leads ahead of the session.
        assertEquals("rolling", state(Provider.Claude, session, extraUsage).primaryWindow?.id)
        assertEquals("rolling", state(Provider.Claude, session, cachedExtraUsage).primaryWindow?.id)
        assertNull(state(Provider.Claude, session, extraUsage).monthlyWindow)
        // When extra usage is the only window, it still never becomes the overview primary.
        assertNull(state(Provider.Claude, extraUsage).primaryWindow)
        assertNull(state(Provider.Claude, cachedExtraUsage).primaryWindow)
    }

    @Test
    fun `excludes monthly windows from a different limit group`() {
        val generalWeekly = weekly.copy(group = "General usage limits")
        val generalMonthly = monthly.copy(group = "General usage limits")
        val featureMonthly = monthly.copy(id = "spark:monthly", group = "Spark usage limits")

        assertNull(state(Provider.Codex, generalWeekly, featureMonthly).monthlyWindow)
        assertEquals(
            "monthly",
            state(Provider.Codex, generalWeekly, generalMonthly).monthlyWindow?.id,
        )
    }

    @Test
    fun `returns null for separate-allowance providers`() {
        val jbWeekly = weekly.copy(id = "jb:a", label = "License A")
        val jbMonthly = monthly.copy(id = "jb:b", label = "License B")

        assertNull(state(Provider.JetBrains, jbWeekly, jbMonthly).monthlyWindow)
    }

    @Test
    fun `returns null when the account has no monthly window`() {
        assertNull(state(Provider.Claude, session, weekly).monthlyWindow)
        assertNull(
            AccountState(Account("a1", Provider.OpenCodeGo, "sam"), snapshot = null).monthlyWindow
        )
    }
}
