package dev.sebastiano.headroom.appdata

import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.QuotaRepository
import dev.sebastiano.headroom.model.UsagePoint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * A repository that behaves like the Room one on a cold start: [accounts] starts empty, and
 * [current] answers only once the stored accounts are read, which a test triggers with [load].
 */
class LoadingQuotaRepository : QuotaRepository {
    private val stored = MutableStateFlow<List<AccountState>>(emptyList())
    private val read = CompletableDeferred<List<AccountState>>()

    override val accounts: StateFlow<List<AccountState>> = stored

    override suspend fun current(): List<AccountState> = read.await()

    override suspend fun refresh(accountId: String?) = Unit

    override fun history(accountId: String, windowId: String): Flow<List<UsagePoint>> =
        flowOf(emptyList())

    /** Finishes the first read, then publishes the accounts, in that order. */
    fun load(accounts: List<AccountState>) {
        read.complete(accounts)
        stored.value = accounts
    }
}
