package dev.sebastiano.headroom.appdata

import dev.sebastiano.headroom.model.QuotaRepository
import dev.sebastiano.headroom.model.UsagePoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** Reads "how much was used when each window reset" out of the stored usage history. */
internal object ResetPeaks {
    /** A drop smaller than this is noise, not a reset. */
    private const val MIN_DROP_POINTS = 5.0

    /** The highest usage before each drop, oldest first. The window still running is left out. */
    fun usedAtResets(points: List<UsagePoint>): List<Double> {
        val peaks = mutableListOf<Double>()
        var peak: Double? = null
        for (point in points) {
            val current = peak
            if (current != null && current - point.usedPercent >= MIN_DROP_POINTS) {
                peaks += current
                peak = point.usedPercent
            } else {
                peak = maxOf(current ?: point.usedPercent, point.usedPercent)
            }
        }
        return peaks
    }
}

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
