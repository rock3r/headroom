package dev.sebastiano.headroom.appdata

import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.AskOutcome
import dev.sebastiano.headroom.model.RedeemOutcome
import dev.sebastiano.headroom.model.ResetAttemptKey
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetProvider
import dev.sebastiano.headroom.model.ResetRefresh
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The resets of every account, for the UI. It reads them from [provider] whenever [accounts]
 * change, which includes each sync, and again after each redeem and each ask, so the counts stay
 * current. An account whose provider has no resets has no entry.
 *
 * After a reset works, it waits [refreshDelay] ([ResetRefresh.DELAY] for every provider), then
 * refreshes that account's usage through [refreshUsage], as any refresh does: the new numbers come
 * from the provider, and the screens animate to them. [refreshing] holds the accounts from the
 * success until their refresh ends.
 *
 * A reset that works is first written to the reset history through [recordRedeem], before the
 * refresh, so the history measures what it gave back from the usage before it.
 *
 * The real provider answers from the resets each sync stores with the snapshot, so a read here
 * makes no network call.
 */
class ResetCenter(
    private val provider: ResetProvider,
    accounts: Flow<List<AccountState>>,
    private val scope: CoroutineScope,
    private val refreshUsage: suspend (accountId: String) -> Unit = {},
    private val refreshDelay: Duration = ResetRefresh.DELAY,
    private val recordRedeem:
        suspend (account: Account, poolId: String, attemptKey: ResetAttemptKey) -> Unit =
        { _, _, _ ->
        },
) : ResetProvider {
    private val state = MutableStateFlow<Map<String, ResetAvailability>>(emptyMap())
    private val refreshingIds = MutableStateFlow<Set<String>>(emptySet())

    /** The accounts whose usage is being refreshed after a reset. */
    val refreshing: StateFlow<Set<String>> = refreshingIds.asStateFlow()

    /** The resets of each account, by account id. */
    val availability: StateFlow<Map<String, ResetAvailability>> = state.asStateFlow()

    init {
        scope.launch {
            accounts.distinctUntilChanged().collectLatest { list ->
                val ids = list.map { it.account.id }.toSet()
                state.update { current -> current.filterKeys { it in ids } }
                list.forEach { refresh(it.account) }
            }
        }
    }

    /** Reads the resets of [account] again. */
    suspend fun refresh(account: Account) {
        availability(account)
    }

    override suspend fun availability(account: Account): ResetAvailability? {
        val read = provider.availability(account)
        state.update { current ->
            if (read == null) current - account.id else current + (account.id to read)
        }
        return read
    }

    override suspend fun redeem(
        account: Account,
        poolId: String,
        attemptKey: ResetAttemptKey,
    ): RedeemOutcome =
        provider.redeem(account, poolId, attemptKey).also {
            settle(account, poolId, attemptKey, it)
        }

    override suspend fun check(
        account: Account,
        poolId: String,
        attemptKey: ResetAttemptKey,
    ): RedeemOutcome =
        provider.check(account, poolId, attemptKey).also { settle(account, poolId, attemptKey, it) }

    /**
     * Reads the resets again after an attempt that ended, and refreshes the usage after one that
     * worked. The refresh runs on its own, so the sheet can say "Usage reset" while it runs.
     */
    private suspend fun settle(
        account: Account,
        poolId: String,
        attemptKey: ResetAttemptKey,
        outcome: RedeemOutcome,
    ) {
        if (!outcome.isSettled) return
        if (outcome is RedeemOutcome.Success) recordRedeem(account, poolId, attemptKey)
        refresh(account)
        if (outcome !is RedeemOutcome.Success) return
        refreshingIds.update { it + account.id }
        scope.launch {
            try {
                delay(refreshDelay.inWholeMilliseconds)
                refreshUsage(account.id)
            } finally {
                refreshingIds.update { it - account.id }
            }
        }
    }

    /**
     * Asks for another reset. A granted one only shows in the resets after the account's next sync,
     * so a grant refreshes the account before this returns: "Use it now" then finds it.
     */
    override suspend fun askForMore(account: Account): AskOutcome =
        provider.askForMore(account).also { outcome ->
            if (outcome is AskOutcome.Granted) refreshUsage(account.id)
            refresh(account)
        }
}
