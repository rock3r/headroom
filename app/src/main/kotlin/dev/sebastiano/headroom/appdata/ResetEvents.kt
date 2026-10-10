package dev.sebastiano.headroom.appdata

import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.ResetAttemptKey
import dev.sebastiano.headroom.model.ResetEvent
import dev.sebastiano.headroom.model.ResetEventLog
import kotlin.time.Instant
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

    private fun demo(now: Instant): List<ResetEvent> = DemoData.resetEvents(now)
}
