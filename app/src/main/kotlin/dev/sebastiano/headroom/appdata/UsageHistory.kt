package dev.sebastiano.headroom.appdata

import dev.sebastiano.headroom.model.DemoUsage
import dev.sebastiano.headroom.model.HistoryRetention
import dev.sebastiano.headroom.model.InMemorySettingsRepository
import dev.sebastiano.headroom.model.QuotaRepository
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.SettingsRepository
import dev.sebastiano.headroom.model.UsagePoint
import java.time.ZoneId
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
 * Plausible history for the demo accounts: past cycles that repeat the [resets] of the window back
 * to the retention horizon from [settings], then the current cycle, with most of the use on weekday
 * working hours in [zone].
 */
class DemoUsageHistory(
    private val resets: ResetHistory,
    private val clock: () -> Instant,
    private val zone: ZoneId,
    private val settings: SettingsRepository = InMemorySettingsRepository(),
) : UsageHistory {
    override fun points(accountId: String, window: QuotaWindow): Flow<List<UsagePoint>> =
        combine(
            resets.usedAtReset(accountId, window.id),
            settings.settings.map { it.historyRetention }.distinctUntilChanged(),
        ) { peaks, retention ->
            demoUsage(window, peaks, clock(), zone, retention)
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
    retention: HistoryRetention = HistoryRetention.LongTerm,
): List<UsagePoint> = DemoUsage.points(window, peaks, now, zone.toKotlinTimeZone(), retention)
