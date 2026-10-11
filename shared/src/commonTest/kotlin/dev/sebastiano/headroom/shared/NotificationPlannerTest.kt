package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.AppSettings
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetPool
import dev.sebastiano.headroom.model.ResetScope
import dev.sebastiano.headroom.model.WindowKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

class NotificationPlannerTest {
    private val now = Instant.parse("2026-09-27T12:32:00Z")
    private val accounts = DemoData.accounts(now)

    private fun plan(
        accounts: List<AccountState> = this.accounts,
        settings: AppSettings = AppSettings(),
        signInNotified: Set<String> = emptySet(),
        reminded: Set<String> = emptySet(),
        now: Instant = this.now,
    ) =
        NotificationPlanner.plan(
            accounts,
            settings,
            AlertStates.Defaults,
            now,
            TimeZone.UTC,
            signInNotified,
            reminded,
        )

    @Test
    fun `weekly windows alert at their reset and monthly ones stay off by default`() {
        val alerts = plan().resetAlerts

        assertTrue(alerts.isNotEmpty())
        assertTrue(alerts.none { it.monthly }, "Copilot's monthly window is off by default")
        val grok = alerts.first { it.accountId == "demo-grok" }
        val window = accounts.first { it.account.id == "demo-grok" }.snapshot!!.windows.first()
        assertEquals(window.resetsAt!!.epochSeconds + 10, grok.fireAtEpochSeconds)
        assertEquals((window.resetsAt!! + 7.days).epochSeconds, grok.nextResetAtEpochSeconds)
        assertEquals("reset:demo-grok/weekly", grok.id)
    }

    @Test
    fun `an expired sign-in is warned about once and cleared when it syncs again`() {
        val expired = DemoData.accountsWithExpiredSignIn(now)

        val first = plan(accounts = expired)
        assertEquals(listOf("demo-claude"), first.signInAlerts.map { it.accountId })
        assertTrue(first.resetAlerts.none { it.accountId == "demo-claude" })

        val again = plan(accounts = expired, signInNotified = first.signInNotified.toSet())
        assertTrue(again.signInAlerts.isEmpty())

        val fixed = plan(signInNotified = first.signInNotified.toSet())
        assertEquals(listOf("demo-claude"), fixed.signInCancels)
    }

    @Test
    fun `resets that expire soon get one reminder a day before`() {
        val expires = now + 3.days
        val withResets = accounts.map { state ->
            if (state.account.provider != Provider.Codex) state
            else
                state.copy(
                    snapshot = state.snapshot!!.copy(resets = resets(expires, expires + 2.hours))
                )
        }

        val reminders = plan(accounts = withResets).reminders

        assertEquals(1, reminders.size)
        assertEquals((expires - 1.days).epochSeconds, reminders.single().fireAtEpochSeconds)
        assertEquals(2, reminders.single().resets.size)
    }

    @Test
    fun `a reminder stays planned until it goes out`() {
        val withResets = withCodexResets(now + 3.days)

        val first = plan(accounts = withResets)
        val second = plan(accounts = withResets, reminded = first.reminded.toSet())

        assertEquals(1, first.reminders.size)
        assertEquals(first.reminders, second.reminders)
    }

    @Test
    fun `a reset already reminded about is not reminded again`() {
        val withResets = withCodexResets(now + 3.days)
        val first = plan(accounts = withResets)
        // The reminder went out at its time, a day before the reset expires.
        val later = now + 2.days + 1.hours

        val second = plan(accounts = withResets, reminded = first.reminded.toSet(), now = later)

        assertTrue(second.reminders.isEmpty())
    }

    @Test
    fun `reminders turned off and on again come back`() {
        val withResets = withCodexResets(now + 3.days)
        val first = plan(accounts = withResets)

        val off =
            plan(
                accounts = withResets,
                settings = AppSettings(resetExpiryReminders = false),
                reminded = first.reminded.toSet(),
            )
        val on = plan(accounts = withResets, reminded = off.reminded.toSet())

        assertTrue(off.reminders.isEmpty())
        assertEquals(first.reminders, on.reminders)
    }

    @Test
    fun `no reminders when the user turned them off`() {
        val withResets = accounts.map { state ->
            if (state.account.provider != Provider.Codex) state
            else state.copy(snapshot = state.snapshot!!.copy(resets = resets(now + 3.days)))
        }

        val plan = plan(accounts = withResets, settings = AppSettings(resetExpiryReminders = false))

        assertTrue(plan.reminders.isEmpty())
    }

    private fun withCodexResets(vararg expiries: Instant) = accounts.map { state ->
        if (state.account.provider != Provider.Codex) state
        else state.copy(snapshot = state.snapshot!!.copy(resets = resets(*expiries)))
    }

    private fun resets(vararg expiries: Instant) =
        ResetAvailability(
            listOf(
                ResetPool(
                    id = "pool",
                    label = "Resets",
                    available = expiries.size,
                    scope = ResetScope.of(WindowKind.Weekly),
                    expiries = expiries.toList(),
                )
            )
        )
}
