package dev.sebastiano.headroom.appdata

import dev.sebastiano.headroom.model.DemoUsage
import dev.sebastiano.headroom.model.QuotaRepository
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.UsagePoint
import java.time.ZoneId
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.datetime.toKotlinTimeZone

/** The recorded usage of an account's window, oldest point first, for the Stats tab. */
fun interface UsageHistory {
    fun points(accountId: String, window: QuotaWindow): Flow<List<UsagePoint>>
}

/** The usage history that the data layer records on every sync. */
class RepositoryUsageHistory(private val repository: QuotaRepository) : UsageHistory {
    override fun points(accountId: String, window: QuotaWindow): Flow<List<UsagePoint>> =
        repository.history(accountId, window.id)
}

/**
 * Plausible history for the demo accounts: one cycle for each of the [resets] of the window, then
 * the current one, with most of the use on weekday working hours in [zone].
 */
class DemoUsageHistory(
    private val resets: ResetHistory,
    private val clock: () -> Instant,
    private val zone: ZoneId,
) : UsageHistory {
    override fun points(accountId: String, window: QuotaWindow): Flow<List<UsagePoint>> =
        resets.usedAtReset(accountId, window.id).map { peaks ->
            demoUsage(window, peaks, clock(), zone)
        }
}

/** Follows demo mode: demo history while the demo accounts show, recorded history otherwise. */
@OptIn(ExperimentalCoroutinesApi::class)
class DemoAwareUsageHistory(
    private val isDemo: StateFlow<Boolean>,
    private val real: UsageHistory,
    private val demo: UsageHistory,
) : UsageHistory {
    override fun points(accountId: String, window: QuotaWindow): Flow<List<UsagePoint>> =
        isDemo.flatMapLatest { demoMode ->
            (if (demoMode) demo else real).points(accountId, window)
        }
}

/**
 * Hourly usage of [window] for the past cycles, which peaked at [peaks], and for the current cycle,
 * with most of the use on weekday working hours in [zone]. See [DemoUsage.points].
 */
internal fun demoUsage(
    window: QuotaWindow,
    peaks: List<Double>,
    now: Instant,
    zone: ZoneId,
): List<UsagePoint> = DemoUsage.points(window, peaks, now, zone.toKotlinTimeZone())
