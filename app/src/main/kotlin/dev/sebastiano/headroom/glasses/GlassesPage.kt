package dev.sebastiano.headroom.glasses

import androidx.compose.runtime.Immutable
import dev.sebastiano.headroom.ui.home.AccountSummary
import dev.sebastiano.headroom.ui.home.HomeUiState
import dev.sebastiano.headroom.ui.home.NextResetSummary

/** One page of the glasses pager. The glasses show one card at a time. */
@Immutable
sealed interface GlassesPage {
    /** The next reset, as the overview shows it, or null when no reset is coming. */
    data class NextReset(val next: NextResetSummary?) : GlassesPage

    /** One account, as its overview card shows it. */
    data class Account(val account: AccountSummary) : GlassesPage
}

/** The next reset first, then one page per account in the overview's order. */
fun glassesPages(state: HomeUiState): List<GlassesPage> =
    listOf(GlassesPage.NextReset(state.nextReset)) + state.accounts.map(GlassesPage::Account)
