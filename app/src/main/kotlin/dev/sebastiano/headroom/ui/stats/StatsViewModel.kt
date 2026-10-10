package dev.sebastiano.headroom.ui.stats

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sebastiano.headroom.appdata.UsageHistory
import dev.sebastiano.headroom.model.QuotaRepository
import dev.sebastiano.headroom.model.ResetEventLog
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

/** What the Stats tab shows. */
@Immutable
data class StatsUiState(
    /** True until the history has been read once. */
    val loading: Boolean = true,
    val isDemo: Boolean = false,
    val stats: Stats = Stats(),
)

/**
 * Stats from the history of each account's main limit, and from the reset history. The history
 * covers every sync of the last weeks, so the maths runs on [computeDispatcher], off the main
 * thread.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    repository: QuotaRepository,
    history: UsageHistory,
    isDemo: StateFlow<Boolean>,
    clock: () -> Instant,
    zone: ZoneId,
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** The resets used and expired, kept for [ResetPeriod.TwelveMonths]. */
    resetEvents: ResetEventLog = ResetEventLog.None,
) : ViewModel() {
    private val resets =
        combine(
            repository.accounts.map { accounts -> accounts.any { it.snapshot?.resets != null } },
            resetEvents.events(clock().minus(ResetPeriod.TwelveMonths.length)),
        ) { hasResets, events ->
            hasResets to events
        }

    val state: StateFlow<StatsUiState> =
        repository.accounts
            .map { accounts ->
                accounts.mapNotNull { state -> state.primaryWindow?.let { state.account to it } }
            }
            .distinctUntilChanged()
            .flatMapLatest { mains ->
                if (mains.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(
                        mains.map { (account, window) ->
                            history.points(account.id, window).map {
                                StatsSource(account, window, it)
                            }
                        }
                    ) {
                        it.toList()
                    }
                }
            }
            .combine(isDemo) { sources, demo -> sources to demo }
            .combine(resets) { (sources, demo), resets -> Triple(sources, demo, resets) }
            .mapLatest { (sources, demo, resets) ->
                val (hasResets, events) = resets
                val now = clock()
                StatsUiState(
                    loading = false,
                    isDemo = demo,
                    stats =
                        stats(sources, now, zone)
                            .copy(resetUsage = resetUsage(events, now, hasResets)),
                )
            }
            .flowOn(computeDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), StatsUiState())

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
