package dev.sebastiano.headroom.appdata

import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.ResetAttemptKey
import dev.sebastiano.headroom.model.ResetEvent
import dev.sebastiano.headroom.model.ResetEventKind
import dev.sebastiano.headroom.model.ResetEventLog
import dev.sebastiano.headroom.model.ResetUseSource
import dev.sebastiano.headroom.model.WindowKind
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * Follows demo mode: the demo history while the demo accounts show, the recorded one otherwise. A
 * redeem always goes to [real], which records nothing for an account it does not store.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DemoAwareResetEvents(
    private val isDemo: StateFlow<Boolean>,
    private val real: ResetEventLog,
    private val demo: ResetEventLog,
) : ResetEventLog {
    override fun events(since: Instant): Flow<List<ResetEvent>> = isDemo.flatMapLatest { demoMode ->
        (if (demoMode) demo else real).events(since)
    }

    override suspend fun redeemed(account: Account, poolId: String, attemptKey: ResetAttemptKey) =
        real.redeemed(account, poolId, attemptKey)
}

/** A plausible reset history for the demo accounts: a few months of Codex, Grok and Claude. */
class DemoResetEvents(private val clock: () -> Instant) : ResetEventLog {
    override fun events(since: Instant): Flow<List<ResetEvent>> {
        val now = clock()
        return flowOf(demo(now).filter { it.at >= since }.sortedBy { it.at })
    }

    override suspend fun redeemed(account: Account, poolId: String, attemptKey: ResetAttemptKey) =
        Unit

    private fun demo(now: Instant): List<ResetEvent> {
        fun used(
            provider: Provider,
            daysAgo: Long,
            weekly: Double,
            source: ResetUseSource = ResetUseSource.Headroom,
        ) =
            ResetEvent(
                accountId = "demo-${provider.id}",
                provider = provider,
                poolId = "demo",
                poolLabel = "Resets",
                kind = ResetEventKind.Used,
                at = now.minus(Duration.ofDays(daysAgo)),
                source = source,
                givenBack = mapOf(WindowKind.Weekly to weekly),
                givenBackEstimated = source == ResetUseSource.Elsewhere,
            )

        fun expired(provider: Provider, daysAgo: Long) =
            used(provider, daysAgo, weekly = 0.0)
                .copy(kind = ResetEventKind.Expired, source = null, givenBack = emptyMap())

        return listOf(
            used(Provider.Codex, daysAgo = 3, weekly = 92.0),
            used(Provider.Codex, daysAgo = 12, weekly = 85.0),
            used(Provider.Codex, daysAgo = 20, weekly = 64.0, source = ResetUseSource.Elsewhere),
            used(Provider.Grok, daysAgo = 9, weekly = 97.0),
            expired(Provider.Claude, daysAgo = 15),
            used(Provider.Codex, daysAgo = 45, weekly = 78.0),
            used(Provider.Grok, daysAgo = 60, weekly = 100.0),
            expired(Provider.Grok, daysAgo = 75),
            used(Provider.Claude, daysAgo = 130, weekly = 100.0, source = ResetUseSource.Elsewhere),
            expired(Provider.Codex, daysAgo = 200),
        )
    }
}
