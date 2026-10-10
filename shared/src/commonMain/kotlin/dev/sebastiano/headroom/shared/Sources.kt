package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.data.AccountsRepository
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.DemoUsage
import dev.sebastiano.headroom.model.FakeQuotaRepository
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.ResetEvent
import dev.sebastiano.headroom.model.ResetEventLog
import dev.sebastiano.headroom.model.UsagePoint
import dev.sebastiano.headroom.model.stats.ResetPeaks
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone

/**
 * The real accounts and their history, or, while no account is signed in, the demo ones, as the
 * Android app shows them: demo usage history, demo reset peaks and demo reset events.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class Sources(
    val real: AccountsRepository,
    private val events: ResetEventLog,
    private val clock: () -> Instant,
    private val zone: () -> TimeZone,
) {
    private val demo = FakeQuotaRepository(clock)

    val isDemo: Flow<Boolean> = real.accounts.map { it.isEmpty() }.distinctUntilChanged()

    val accounts: Flow<List<AccountState>> =
        combine(real.accounts, demo.accounts) { realAccounts, demoAccounts ->
            realAccounts.ifEmpty { demoAccounts }
        }

    /** The accounts shown now. */
    suspend fun current(): List<AccountState> = real.current().ifEmpty { demo.current() }

    suspend fun refresh(accountId: String?) {
        if (real.current().isEmpty()) demo.refresh(accountId) else real.refresh(accountId)
    }

    /** The usage history of [window], oldest point first. */
    fun history(accountId: String, window: QuotaWindow): Flow<List<UsagePoint>> =
        isDemo.flatMapLatest { demoMode ->
            if (demoMode) {
                flowOf(
                    DemoUsage.points(
                        window,
                        DemoData.resetPeaks(accountId, window.id),
                        clock(),
                        zone(),
                    )
                )
            } else {
                real.history(accountId, window.id)
            }
        }

    /** How much of the window was used when it reset, oldest first. */
    fun resetPeaks(accountId: String, windowId: String): Flow<List<Double>> =
        isDemo.flatMapLatest { demoMode ->
            if (demoMode) flowOf(DemoData.resetPeaks(accountId, windowId))
            else real.history(accountId, windowId).map(ResetPeaks::usedAtResets)
        }

    /** The resets used and expired since [since]. */
    fun resetEvents(since: Instant): Flow<List<ResetEvent>> = isDemo.flatMapLatest { demoMode ->
        if (demoMode) flowOf(DemoData.resetEvents(clock()).filter { it.at >= since })
        else events.events(since)
    }
}
