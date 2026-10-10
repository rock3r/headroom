package dev.sebastiano.headroom.appdata

import dev.sebastiano.headroom.model.QuotaRepository
import dev.sebastiano.headroom.model.stats.ResetPeaks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** [ResetHistory] from the usage history that the data layer records on every sync. */
class HistoryResetHistory(private val repository: QuotaRepository) : ResetHistory {
    override fun usedAtReset(accountId: String, windowId: String): Flow<List<Double>> =
        repository.history(accountId, windowId).map(ResetPeaks::usedAtResets)
}

/** Follows demo mode: demo resets while the demo accounts show, recorded ones otherwise. */
@OptIn(ExperimentalCoroutinesApi::class)
class DemoAwareResetHistory(
    private val isDemo: StateFlow<Boolean>,
    private val real: ResetHistory,
    private val demo: ResetHistory,
) : ResetHistory {
    override fun usedAtReset(accountId: String, windowId: String): Flow<List<Double>> =
        isDemo.flatMapLatest { demoMode ->
            (if (demoMode) demo else real).usedAtReset(accountId, windowId)
        }
}
