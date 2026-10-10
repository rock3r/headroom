package dev.sebastiano.headroom.data.db

import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.QuotaErrorKind
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
    private val order: AccountOrderDao,
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
        order.insertAccountLast(
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

    /** Names the account. A blank [nickname] removes the name, so the provider's name shows. */
    suspend fun renameAccount(accountId: String, nickname: String?) {
        dao.setNickname(accountId, nickname?.trim()?.ifEmpty { null })
    }

    /** Stores what the provider now calls the account, for example a changed email address. */
    suspend fun relabelAccount(accountId: String, label: String) {
        dao.setLabel(accountId, label)
    }

    /** Stores the order the user put the accounts in, in one transaction. */
    suspend fun reorderAccounts(orderedIds: List<String>) {
        order.reorderAccounts(orderedIds)
    }

    suspend fun removeAccount(accountId: String) {
        dao.deleteAccount(accountId)
    }

    override suspend fun refresh(accountId: String?) {
        val targets = if (accountId != null) listOf(accountId) else dao.accounts().map { it.id }
        coroutineScope { targets.map { async { refreshOne(it) } }.awaitAll() }
    }

    /** Claims the account's in-flight slot before touching the database, so callers coalesce. */
    private suspend fun refreshOne(accountId: String) {
        val (job, owner) =
            inFlightLock.withLock {
                val existing = inFlight[accountId]
                if (existing != null) {
                    existing to false
                } else {
                    CompletableDeferred<Unit>().also { inFlight[accountId] = it } to true
                }
            }
        if (!owner) {
            job.await()
            return
        }
        refreshing.update { it + accountId }
        try {
            val entity = dao.account(accountId) ?: return
            store(entity, fetchAccount(entity))
        } finally {
            refreshing.update { it - accountId }
            inFlightLock.withLock { inFlight.remove(accountId) }
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
                            balanceAmount = snapshot.balance?.amount,
                            balanceUnit = snapshot.balance?.unit,
                            // Resets that could not be read keep the ones read last.
                            resetsJson =
                                if (snapshot.resetsReadFailed) entity.resetsJson
                                else snapshot.resets?.let(ResetsCodec::encode),
                            resetsReadFailed = snapshot.resetsReadFailed,
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
                    // Resets that could not be read are compared with nothing: they record no
                    // event.
                    resetEvents =
                        RoomResetEventLog.finder(
                            provider = snapshot.provider,
                            current = snapshot.resets.takeUnless { snapshot.resetsReadFailed },
                            now = now,
                        ),
                )
            }
            // An update, not an upsert: a removed account must stay removed. An expired sign-in
            // stays expired through later errors, such as a network error: only a sync that
            // works shows the user can stop worrying about it.
            is QuotaResult.Failure -> {
                val expired = entity.lastError == QuotaErrorKind.Auth.name
                dao.updateError(entity.id, if (expired) entity.lastError else result.kind.name)
            }
            null -> Unit
        }
    }

    override suspend fun current(): List<AccountState> {
        val busy = refreshing.value
        return dao.accountsWithWindows().mapNotNull {
            it.toState(refreshing = it.account.id in busy)
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
