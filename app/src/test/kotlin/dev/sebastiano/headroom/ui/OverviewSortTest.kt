package dev.sebastiano.headroom.ui

import dev.sebastiano.headroom.model.OverviewSort
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.WindowKind
import dev.sebastiano.headroom.ui.home.AccountSummary
import dev.sebastiano.headroom.ui.home.WindowSummary
import dev.sebastiano.headroom.ui.home.sortedFor
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class OverviewSortTest {
    private val now = Instant.parse("2026-09-27T12:00:00Z")

    private val accounts =
        listOf(
            account("claude", "Claude", used = 71.0, resetsInHours = 66),
            account("empty", "Alpha", used = null, resetsInHours = null),
            account("codex", "Codex", used = 34.0, resetsInHours = 99),
            account("grok", "Grok", used = 88.0, resetsInHours = 15),
            account("copilot", "Copilot", used = 58.0, resetsInHours = 83),
        )

    @Test
    fun `your order keeps the repository's order`() {
        assertEquals(
            listOf("claude", "empty", "codex", "grok", "copilot"),
            accounts.sortedFor(OverviewSort.YourOrder).ids(),
        )
    }

    @Test
    fun `most used first puts the fullest weekly window on top and accounts without data last`() {
        assertEquals(
            listOf("grok", "claude", "copilot", "codex", "empty"),
            accounts.sortedFor(OverviewSort.MostUsedFirst).ids(),
        )
    }

    @Test
    fun `least used first still puts accounts without data last`() {
        assertEquals(
            listOf("codex", "copilot", "claude", "grok", "empty"),
            accounts.sortedFor(OverviewSort.LeastUsedFirst).ids(),
        )
    }

    @Test
    fun `soonest reset first puts accounts without a reset time last`() {
        assertEquals(
            listOf("grok", "claude", "copilot", "codex", "empty"),
            accounts.sortedFor(OverviewSort.SoonestResetFirst).ids(),
        )
    }

    @Test
    fun `latest reset first still puts accounts without a reset time last`() {
        assertEquals(
            listOf("codex", "copilot", "claude", "grok", "empty"),
            accounts.sortedFor(OverviewSort.LatestResetFirst).ids(),
        )
    }

    @Test
    fun `a window without a reset time goes last even when the account has data`() {
        val noReset = account("zai", "Z.AI", used = 99.0, resetsInHours = null)
        assertEquals(
            listOf("grok", "claude", "zai"),
            listOf(noReset, accounts[0], accounts[3])
                .sortedFor(OverviewSort.SoonestResetFirst)
                .ids(),
        )
    }

    @Test
    fun `ties are broken by name from A to Z, in both directions`() {
        val tied =
            listOf(
                account("b", "beta", used = 50.0, resetsInHours = 10),
                account("c", "Gamma", used = 50.0, resetsInHours = 10),
                account("a", "Alpha", used = 50.0, resetsInHours = 10),
                account("x", "Zulu", used = null, resetsInHours = null),
                account("y", "Echo", used = null, resetsInHours = null),
            )
        val expected = listOf("a", "b", "c", "y", "x")
        OverviewSort.entries
            .filter { it != OverviewSort.YourOrder }
            .forEach { sort -> assertEquals(expected, tied.sortedFor(sort).ids(), "$sort") }
    }

    private fun List<AccountSummary>.ids() = map { it.id }

    private fun account(id: String, name: String, used: Double?, resetsInHours: Long?) =
        AccountSummary(
            id = id,
            provider = Provider.Claude,
            label = "sam@example.com",
            plan = null,
            primary =
                used?.let {
                    WindowSummary(
                        id = "weekly",
                        label = "Weekly",
                        kind = WindowKind.Weekly,
                        usedPercent = it,
                        expectedPercent = null,
                        resetsAt = resetsInHours?.let { hours -> now.plusSeconds(hours * 3600) },
                        canAlert = true,
                        alertEnabled = true,
                    )
                },
            session = null,
            windows = emptyList(),
            pace = null,
            needsAttention = false,
            error = null,
            pastResets = emptyList(),
            name = name,
        )
}
