package dev.sebastiano.headroom.ui.home

import androidx.compose.runtime.Immutable
import dev.sebastiano.headroom.designsystem.PaceChipState
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaBalance
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.UsagePoint
import dev.sebastiano.headroom.model.WindowKind
import java.time.Instant

/** Everything the overview, resets and detail screens show. */
@Immutable
data class HomeUiState(
    val now: Instant,
    val accounts: List<AccountSummary>,
    val isDemo: Boolean,
    val isRefreshing: Boolean,
    val lastSyncedAt: Instant?,
    val nextReset: NextResetSummary?,
    /**
     * False until the stored accounts have been read; until then demo data may be a placeholder.
     */
    val accountsLoaded: Boolean = true,
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
)

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
data class DetailUiState(val now: Instant, val account: AccountSummary, val chart: ChartSummary?)

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
