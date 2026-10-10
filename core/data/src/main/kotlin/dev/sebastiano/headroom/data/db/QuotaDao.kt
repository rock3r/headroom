package dev.sebastiano.headroom.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal interface QuotaDao : ResetEventQueries {
    @Transaction
    @Query("SELECT * FROM accounts ORDER BY position, rowid")
    fun observeAccounts(): Flow<List<AccountWithWindows>>

    @Query("SELECT * FROM accounts ORDER BY position, rowid")
    suspend fun accounts(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id") suspend fun account(id: String): AccountEntity?

    @Upsert suspend fun upsertAccount(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id") suspend fun deleteAccount(id: String)

    @Query("UPDATE accounts SET nickname = :nickname WHERE id = :id")
    suspend fun setNickname(id: String, nickname: String?)

    @Query("UPDATE accounts SET label = :label WHERE id = :id")
    suspend fun setLabel(id: String, label: String)

    @Query("DELETE FROM windows WHERE accountId = :accountId")
    suspend fun deleteWindows(accountId: String)

    @Upsert suspend fun upsertWindows(windows: List<WindowEntity>)

    @Upsert suspend fun upsertPoints(points: List<UsagePointEntity>)

    @Query(
        "SELECT * FROM usage_points WHERE accountId = :accountId AND windowId = :windowId " +
            "ORDER BY atEpochMs"
    )
    fun observeHistory(accountId: String, windowId: String): Flow<List<UsagePointEntity>>

    @Query("DELETE FROM usage_points WHERE atEpochMs < :beforeEpochMs")
    suspend fun pruneHistory(beforeEpochMs: Long)

    @Transaction
    @Query("SELECT * FROM accounts ORDER BY position, rowid")
    suspend fun accountsWithWindows(): List<AccountWithWindows>

    @Query("UPDATE accounts SET lastError = :error WHERE id = :id")
    suspend fun updateError(id: String, error: String)

    /**
     * Stores a fetch result, unless the account was removed while the fetch was running. In the
     * same transaction, [resetEvents] compares the account as stored before with this result, and
     * the redeems that no sync has settled yet, and says which resets were used or expired.
     */
    @Transaction
    suspend fun storeSnapshot(
        account: AccountEntity,
        windows: List<WindowEntity>,
        points: List<UsagePointEntity>,
        pruneBeforeEpochMs: Long,
        resetEvents: ResetEventFinder = ResetEventFinder.None,
    ) {
        val previous = accountWithWindows(account.id) ?: return
        val found = resetEvents.find(previous, pendingRedeems(account.id))
        upsertAccount(account)
        deleteWindows(account.id)
        upsertWindows(windows)
        upsertPoints(points)
        pruneHistory(pruneBeforeEpochMs)
        if (found.settledIds.isNotEmpty()) settleRedeems(found.settledIds)
        if (found.events.isNotEmpty()) insertResetEvents(found.events)
        pruneResetEvents(resetEvents.pruneBeforeEpochMs)
    }
}

/** What a sync stores in the reset history: see [QuotaDao.storeSnapshot]. */
internal interface ResetEventFinder {
    /** Events older than this are deleted. */
    val pruneBeforeEpochMs: Long

    fun find(previous: AccountWithWindows, pending: List<ResetEventEntity>): FoundResetEvents

    object None : ResetEventFinder {
        override val pruneBeforeEpochMs: Long = Long.MIN_VALUE

        override fun find(previous: AccountWithWindows, pending: List<ResetEventEntity>) =
            FoundResetEvents(emptyList(), emptyList())
    }
}

internal data class FoundResetEvents(val events: List<ResetEventEntity>, val settledIds: List<Long>)

/**
 * The reset history's queries. [QuotaDao] extends them, so a sync stores both in one transaction.
 */
internal interface ResetEventQueries {
    @Transaction
    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun accountWithWindows(id: String): AccountWithWindows?

    /** Inserts a reset event. A redeem whose attempt key is already stored is left out. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertResetEvent(event: ResetEventEntity): Long

    @Insert suspend fun insertResetEvents(events: List<ResetEventEntity>)

    /**
     * The redeems that no sync has matched yet. They stay pending until one does, however long that
     * takes, so a reset is never counted twice; the yearly pruning removes them in the end.
     */
    @Query(
        "SELECT * FROM reset_events WHERE accountId = :accountId AND settled = 0 " +
            "ORDER BY atEpochMs, id"
    )
    suspend fun pendingRedeems(accountId: String): List<ResetEventEntity>

    @Query("UPDATE reset_events SET settled = 1 WHERE id IN (:ids)")
    suspend fun settleRedeems(ids: List<Long>)

    @Query("SELECT * FROM reset_events WHERE atEpochMs >= :sinceEpochMs ORDER BY atEpochMs, id")
    fun observeResetEvents(sinceEpochMs: Long): Flow<List<ResetEventEntity>>

    @Query("DELETE FROM reset_events WHERE atEpochMs < :beforeEpochMs")
    suspend fun pruneResetEvents(beforeEpochMs: Long)

    @Query("SELECT COUNT(*) FROM reset_events WHERE attemptKey = :attemptKey")
    suspend fun attemptCount(attemptKey: String): Int

    @Query(
        "SELECT * FROM reset_events WHERE accountId = :accountId AND poolId = :poolId " +
            "AND kind = 'Used' AND source = 'Elsewhere' AND attemptKey IS NULL " +
            "AND atEpochMs >= :sinceEpochMs ORDER BY atEpochMs DESC, id DESC LIMIT 1"
    )
    suspend fun recentUseElsewhere(
        accountId: String,
        poolId: String,
        sinceEpochMs: Long,
    ): ResetEventEntity?

    @Query("UPDATE reset_events SET source = 'Headroom', attemptKey = :attemptKey WHERE id = :id")
    suspend fun claimUseElsewhere(id: Long, attemptKey: String)

    /**
     * Records a redeem that worked in Headroom, in one transaction with respect to the syncs. A
     * second call with the same attempt key records nothing. When a sync stored after the provider
     * used the reset but before this call, it recorded that reset as used elsewhere since
     * [claimSinceEpochMs]: the redeem takes that use over, keeping its expiry and its estimate of
     * the usage before the reset, instead of counting the reset twice.
     */
    @Transaction
    suspend fun recordRedeem(
        accountId: String,
        claimSinceEpochMs: Long,
        event: (AccountWithWindows) -> ResetEventEntity,
    ) {
        val stored = accountWithWindows(accountId) ?: return
        val redeem = event(stored)
        val key = redeem.attemptKey ?: return
        if (attemptCount(key) > 0) return
        val elsewhere = recentUseElsewhere(accountId, redeem.poolId, claimSinceEpochMs)
        if (elsewhere != null) claimUseElsewhere(elsewhere.id, key) else insertResetEvent(redeem)
    }
}
