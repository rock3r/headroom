package dev.sebastiano.headroom.ui.home

import androidx.compose.runtime.Immutable
import dev.sebastiano.headroom.designsystem.PaceChipState
import dev.sebastiano.headroom.model.OverviewSort
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaBalance
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.UsagePoint
import dev.sebastiano.headroom.model.WindowKind
import kotlin.time.Instant

/** Everything the overview, resets and detail screens show. */
@Immutable
data class HomeUiState(
    val now: Instant,
    /** The accounts in the overview's order: see [overviewSort]. */
    val accounts: List<AccountSummary>,
    val isDemo: Boolean,
    val isRefreshing: Boolean,
    val lastSyncedAt: Instant?,
    val nextReset: NextResetSummary?,
    /**
     * False until the stored accounts have been read; until then demo data may be a placeholder.
     */
    val accountsLoaded: Boolean = true,
    /** Whether percentages, rings and bars show how much is used or how much is left. */
    val display: QuotaDisplay = QuotaDisplay.Used,
    /** How the overview orders [accounts]. */
    val overviewSort: OverviewSort = OverviewSort.YourOrder,
    /**
     * False until the stored [overviewSort] has been read. Until then [accounts] may be in the
     * repository's order, and the overview must not animate the cards into the stored order.
     */
    val sortLoaded: Boolean = true,
    /** The accounts in the repository's order, for the screens that do not follow the sort. */
    val accountsInYourOrder: List<AccountSummary> = accounts,
    /** Resets seen while the app was open whose confetti has not played yet. */
    val resetBursts: List<ResetBurst> = emptyList(),
)

@Immutable
data class AccountSummary(
    val id: String,
    val provider: Provider,
    val label: String,
    val plan: String?,
    /** The weekly window, or the longest one. The card and the ring lead with it. */
    val primary: WindowSummary?,
    val session: WindowSummary?,
    val windows: List<WindowSummary>,
    val pace: PaceChipState?,
    val needsAttention: Boolean,
    val error: QuotaErrorKind?,
    /** Used percent of the primary window at each past reset, oldest first. */
    val pastResets: List<Double>,
    /** The weekly window reset while the app was open. The card says so for the session. */
    val justReset: Boolean = false,
    /** The name the user gave the account, or the provider's name. */
    val name: String = provider.displayName,
    /** A remaining balance, for providers that report one. */
    val balance: QuotaBalance? = null,
    /**
     * Every allowance, primary first, for providers whose windows are separate allowances. Empty
     * for the others. The card lists these in place of the primary window.
     */
    val allowances: List<WindowSummary> = emptyList(),
    /**
     * The sign-in expired: the numbers are stale until the user signs in again. The screens fade
     * them, never draw them wavy, and ask the user to sign in.
     */
    val signInExpired: Boolean = false,
    /** When the numbers were fetched, or null before the first good sync. */
    val dataFrom: Instant? = null,
) {
    /**
     * The instant the numbers describe: now for fresh data, the last good sync for stale data. Pace
     * and projections are worked out at this instant, so they match the numbers next to them.
     */
    fun asOf(now: Instant): Instant = if (signInExpired) dataFrom ?: now else now
}

@Immutable
data class WindowSummary(
    val id: String,
    val label: String,
    val kind: WindowKind,
    val usedPercent: Double,
    /** Where even pace is now, or null when the window has no known start. */
    val expectedPercent: Double?,
    val resetsAt: Instant?,
    val canAlert: Boolean,
    val alertEnabled: Boolean,
    /** How much is used, in [amountUnit], for windows that count an amount such as credits. */
    val usedAmount: Double? = null,
    /** The limit, in [amountUnit]. */
    val limitAmount: Double? = null,
    val amountUnit: String? = null,
    /** Used percent at each past reset of this window, oldest first. */
    val pastResets: List<Double> = emptyList(),
    /** When a [WindowKind.Credit] expires. Credits have no [resetsAt]. */
    val expiresAt: Instant? = null,
    /** False for a quota Headroom does not know. The detail explains it next to the label. */
    val isRecognised: Boolean = true,
)

@Immutable
data class NextResetSummary(
    val accountId: String,
    val provider: Provider,
    val windowId: String,
    val kind: WindowKind,
    val resetsAt: Instant,
    val alertEnabled: Boolean,
    /** The name the user gave the account, or the provider's name. */
    val name: String = provider.displayName,
)

/** The detail screen: one account plus the chart of its primary window. */
@Immutable
data class DetailUiState(
    val now: Instant,
    val account: AccountSummary,
    val chart: ChartSummary?,
    /** The windows the chart can show, when there is more than one to pick from. */
    val chartWindows: List<WindowSummary> = emptyList(),
    /** The window the chart shows. */
    val chartWindowId: String? = null,
    /** Whether percentages, rings and chart text show how much is used or how much is left. */
    val display: QuotaDisplay = QuotaDisplay.Used,
)

@Immutable
data class ChartSummary(
    val start: Instant,
    val end: Instant,
    val usedPercent: Double,
    /** Where even pace is now. */
    val expectedPercent: Double,
    val kind: WindowKind,
    val points: List<UsagePoint>,
    /** When the window reaches 100% at the current rate, or null when that is after the reset. */
    val projectedLimitAt: Instant?,
    /** Where the window ends at the current rate, when it does not reach the limit. */
    val projectedEndPercent: Double?,
)
