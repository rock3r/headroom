package dev.sebastiano.headroom.appdata

import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.QuotaRepository
import dev.sebastiano.headroom.model.UsagePoint
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Shows the [real] accounts, or the [demo] accounts while there are no real ones. The UI reads
 * [isDemo] to label demo data clearly. Refreshing demo data waits [simulatedLatency], so the
 * loading indicators are visible as they would be with a real network call.
 */
class DemoModeQuotaRepository(
    private val real: QuotaRepository,
    private val demo: QuotaRepository,
    scope: CoroutineScope,
    private val simulatedLatency: Duration = Duration.ofMillis(DEFAULT_LATENCY_MILLIS),
) : QuotaRepository {
    val isDemo: StateFlow<Boolean> =
        real.accounts
            .map { it.isEmpty() }
            .stateIn(scope, SharingStarted.Eagerly, real.accounts.value.isEmpty())

    override val accounts: StateFlow<List<AccountState>> =
        combine(real.accounts, demo.accounts, ::pick)
            .stateIn(scope, SharingStarted.Eagerly, pick(real.accounts.value, demo.accounts.value))

    override suspend fun refresh(accountId: String?) {
        if (real.accounts.value.isEmpty()) {
            delay(simulatedLatency.toMillis())
            demo.refresh(accountId)
        } else {
            real.refresh(accountId)
        }
    }

    override fun history(accountId: String, windowId: String): Flow<List<UsagePoint>> =
        if (real.accounts.value.any { it.account.id == accountId }) {
            real.history(accountId, windowId)
        } else {
            demo.history(accountId, windowId)
        }

    private fun pick(real: List<AccountState>, demo: List<AccountState>) = real.ifEmpty { demo }

    private companion object {
        const val DEFAULT_LATENCY_MILLIS = 900L
    }
}
