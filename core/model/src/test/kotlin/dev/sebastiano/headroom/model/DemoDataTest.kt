package dev.sebastiano.headroom.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class DemoDataTest {
    private val now = Instant.parse("2026-09-27T12:32:00Z")

    @Test
    fun `demo data has one account per showcased provider`() {
        val providers = DemoData.accounts(now).map { it.account.provider }
        assertEquals(
            listOf(Provider.Claude, Provider.Codex, Provider.Grok, Provider.Copilot),
            providers,
        )
    }

    @Test
    fun `the long demo list adds one account for every other provider`() {
        val accounts = DemoData.manyAccounts(now)

        assertEquals(DemoData.accounts(now), accounts.take(4))
        assertEquals(Provider.entries.toSet(), accounts.map { it.account.provider }.toSet())
        assertEquals(accounts.size, accounts.map { it.account.id }.toSet().size)
        assertTrue(accounts.all { it.primaryWindow != null })
    }

    @Test
    fun `the expired sign-in scenario has Claude two hours stale and the others fresh`() {
        val accounts = DemoData.accountsWithExpiredSignIn(now)

        assertEquals(DemoData.accounts(now).map { it.account }, accounts.map { it.account })
        val claude = accounts.first()
        assertTrue(claude.isSignInExpired)
        assertEquals(now.minus(2.hours), claude.snapshot?.fetchedAt)
        assertEquals(DemoData.accounts(now).drop(1), accounts.drop(1))
    }

    @Test
    fun `the soonest weekly reset in demo data is Grok`() {
        val next = NextReset.find(DemoData.accounts(now), now)
        assertEquals(Provider.Grok, next?.account?.provider)
        assertEquals("15h 28m", Countdown.format(next!!.window.resetsAt!! - now))
    }

    @Test
    fun `Claude and Grok need attention in demo data, Codex does not`() {
        val byProvider = DemoData.accounts(now).associateBy { it.account.provider }
        assertTrue(
            byProvider.getValue(Provider.Claude).primaryWindow!!.let {
                Pace.needsAttention(it, now)
            }
        )
        assertTrue(
            byProvider.getValue(Provider.Grok).primaryWindow!!.let { Pace.needsAttention(it, now) }
        )
        assertEquals(
            false,
            byProvider.getValue(Provider.Codex).primaryWindow!!.let {
                Pace.needsAttention(it, now)
            },
        )
    }

    @Test
    fun `fake repository refresh bumps usage and marks the sync time`() = runTest {
        val repo = FakeQuotaRepository(clock = { now })
        val before =
            repo.accounts.value.first { it.account.provider == Provider.Claude }.primaryWindow!!
        repo.refresh()
        val after = repo.accounts.first().first { it.account.provider == Provider.Claude }
        assertTrue(after.primaryWindow!!.usedPercent > before.usedPercent)
        assertEquals(now, after.snapshot!!.fetchedAt)
    }
}
