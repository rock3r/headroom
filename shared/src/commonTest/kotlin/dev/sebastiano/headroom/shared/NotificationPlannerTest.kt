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
    fun `a reset already reminded about is not reminded again`() {
        val expires = now + 3.days
        val withResets = accounts.map { state ->
            if (state.account.provider != Provider.Codex) state
            else state.copy(snapshot = state.snapshot!!.copy(resets = resets(expires)))
        }

        val first = plan(accounts = withResets)
        val second = plan(accounts = withResets, reminded = first.reminded.toSet())

        assertTrue(second.reminders.isEmpty())
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
