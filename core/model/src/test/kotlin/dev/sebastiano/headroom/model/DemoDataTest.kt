package dev.sebastiano.headroom.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
    fun `the soonest weekly reset in demo data is Grok`() {
        val next = NextReset.find(DemoData.accounts(now), now)
        assertEquals(Provider.Grok, next?.account?.provider)
        assertEquals(
            "15h 28m",
            Countdown.format(java.time.Duration.between(now, next!!.window.resetsAt)),
        )
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
