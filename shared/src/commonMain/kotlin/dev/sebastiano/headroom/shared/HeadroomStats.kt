package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.model.stats.ResetPeriod
import dev.sebastiano.headroom.model.stats.StatsSource
import dev.sebastiano.headroom.model.stats.resetUsage
import dev.sebastiano.headroom.model.stats.stats
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.datetime.TimeZone

/**
 * The Stats tab, from the history of each account's main limit and from the reset history, worked
 * out as on Android. The maths runs off the main thread: the history covers weeks of syncs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class HeadroomStats
internal constructor(
    private val sources: Sources,
    private val scope: CoroutineScope,
    private val clock: () -> Instant,
    private val zone: () -> TimeZone,
    private val compute: CoroutineDispatcher,
) {
    /**
     * Calls [onChange] with the stats now and whenever the history changes, until [Watch.cancel].
     */
    public fun watch(onChange: (StatsUi) -> Unit): Watch = scope.watch(stats(), onChange)

    private fun stats(): Flow<StatsUi> {
        val mains =
            sources.accounts
                .map { accounts ->
                    accounts.mapNotNull { state ->
                        state.primaryWindow?.let { state.account to it }
                    }
                }
                .distinctUntilChanged()
        val history = mains.flatMapLatest { list ->
            if (list.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(
                    list.map { (account, window) ->
                        sources.history(account.id, window).map { StatsSource(account, window, it) }
                    }
                ) {
                    it.toList()
                }
            }
        }
        val resets =
            combine(
                sources.accounts.map { accounts -> accounts.any { it.snapshot?.resets != null } },
                sources.resetEvents(clock() - ResetPeriod.TwelveMonths.length),
            ) { hasResets, events ->
                hasResets to events
            }
        return combine(history, sources.isDemo, resets) { list, demo, reset ->
                Triple(list, demo, reset)
            }
            .mapLatest { (list, demo, reset) ->
                val (hasResets, events) = reset
                val now = clock()
                val stats =
                    stats(list, now, zone()).copy(resetUsage = resetUsage(events, now, hasResets))
                StatsMapping.stats(stats, demo)
            }
            .flowOn(compute)
    }
}
