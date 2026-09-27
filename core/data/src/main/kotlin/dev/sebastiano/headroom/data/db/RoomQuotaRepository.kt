package dev.sebastiano.headroom.data.db

import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.QuotaRepository
import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.model.UsagePoint
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The real repository: accounts, their latest windows and the usage history live in Room. A refresh
 * calls [fetch] for each account; a second refresh of an account that is already refreshing waits
 * for the first one instead of calling the provider again.
 */
internal class RoomQuotaRepository(
    private val dao: QuotaDao,
    private val fetch: suspend (Account) -> QuotaResult,
    private val clock: () -> Instant,
    scope: CoroutineScope,
) : QuotaRepository {
    private val refreshing = MutableStateFlow<Set<String>>(emptySet())
    private val inFlight = mutableMapOf<String, CompletableDeferred<Unit>>()
    private val inFlightLock = Mutex()

    override val accounts: StateFlow<List<AccountState>> =
        combine(dao.observeAccounts(), refreshing) { rows, busy ->
                rows.mapNotNull { it.toState(refreshing = it.account.id in busy) }
            }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    suspend fun addAccount(account: Account) {
        dao.insertAccount(
            AccountEntity(
                id = account.id,
                provider = account.provider.id,
                label = account.label,
                planLabel = null,
                fetchedAtEpochMs = null,
                lastError = null,
            )
        )
    }

    suspend fun removeAccount(accountId: String) {
        dao.deleteAccount(accountId)
    }

    override suspend fun refresh(accountId: String?) {
        val targets = dao.accounts().filter { accountId == null || it.id == accountId }
        coroutineScope { targets.map { async { refreshOne(it) } }.awaitAll() }
    }

    private suspend fun refreshOne(entity: AccountEntity) {
        val (job, owner) =
            inFlightLock.withLock {
                val existing = inFlight[entity.id]
                if (existing != null) {
                    existing to false
                } else {
                    CompletableDeferred<Unit>().also { inFlight[entity.id] = it } to true
                }
            }
        if (!owner) {
            job.await()
            return
        }
        refreshing.update { it + entity.id }
        try {
            store(entity, fetchAccount(entity))
        } finally {
            refreshing.update { it - entity.id }
            inFlightLock.withLock { inFlight.remove(entity.id) }
            job.complete(Unit)
        }
    }

    private suspend fun fetchAccount(entity: AccountEntity): QuotaResult? {
        val provider = dev.sebastiano.headroom.model.Provider.fromId(entity.provider) ?: return null
        return fetch(Account(entity.id, provider, entity.label))
    }

    private suspend fun store(entity: AccountEntity, result: QuotaResult?) {
        val now = clock()
        when (result) {
            is QuotaResult.Success -> {
                val snapshot = result.snapshot
                dao.storeSnapshot(
                    account =
                        entity.copy(
                            planLabel = snapshot.planLabel ?: entity.planLabel,
                            fetchedAtEpochMs = snapshot.fetchedAt.toEpochMilli(),
                            lastError = null,
                        ),
                    windows =
                        snapshot.windows.mapIndexed { index, window ->
                            window.toEntity(entity.id, index)
                        },
                    points =
                        snapshot.windows.map { window ->
                            UsagePointEntity(
                                entity.id,
                                window.id,
                                now.toEpochMilli(),
                                window.usedPercent,
                            )
                        },
                    pruneBeforeEpochMs = now.minus(HISTORY_RETENTION).toEpochMilli(),
                )
            }
            is QuotaResult.Failure -> dao.upsertAccount(entity.copy(lastError = result.kind.name))
            null -> Unit
        }
    }

    override fun history(accountId: String, windowId: String): Flow<List<UsagePoint>> =
        dao.observeHistory(accountId, windowId).map { points ->
            points.map { UsagePoint(Instant.ofEpochMilli(it.atEpochMs), it.usedPercent) }
        }

    private companion object {
        val HISTORY_RETENTION: Duration = Duration.ofDays(60)
    }
}
