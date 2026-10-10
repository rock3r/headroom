package dev.sebastiano.headroom.glasses

import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.WindowKind
import dev.sebastiano.headroom.ui.home.AccountSummary
import dev.sebastiano.headroom.ui.home.HomeUiState
import dev.sebastiano.headroom.ui.home.NextResetSummary
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class GlassesPagesTest {
    private val now = Instant.parse("2026-10-10T12:00:00Z")

    @Test
    fun `the next reset comes first, then one page per account in the overview's order`() {
        val next =
            NextResetSummary(
                accountId = "grok",
                provider = Provider.Grok,
                windowId = "weekly",
                kind = WindowKind.Weekly,
                resetsAt = now.plusSeconds(3_600),
                alertEnabled = true,
            )
        val grok = account("grok", Provider.Grok)
        val claude = account("claude", Provider.Claude)
        val state = state(accounts = listOf(grok, claude), nextReset = next)

        assertEquals(
            listOf(
                GlassesPage.NextReset(next),
                GlassesPage.Account(grok),
                GlassesPage.Account(claude),
            ),
            glassesPages(state),
        )
    }

    @Test
    fun `without accounts there is only the next reset page, with no reset`() {
        assertEquals(
            listOf(GlassesPage.NextReset(null)),
            glassesPages(state(accounts = emptyList(), nextReset = null)),
        )
    }

    private fun state(accounts: List<AccountSummary>, nextReset: NextResetSummary?) =
        HomeUiState(
            now = now,
            accounts = accounts,
            isDemo = false,
            isRefreshing = false,
            lastSyncedAt = null,
            nextReset = nextReset,
        )

    private fun account(id: String, provider: Provider) =
        AccountSummary(
            id = id,
            provider = provider,
            label = provider.displayName,
            plan = null,
            primary = null,
            session = null,
            windows = emptyList(),
            pace = null,
            needsAttention = false,
            error = null,
            pastResets = emptyList(),
        )
}
