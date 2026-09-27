package dev.sebastiano.headroom.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal interface QuotaDao {
    @Transaction
    @Query("SELECT * FROM accounts ORDER BY rowid")
    fun observeAccounts(): Flow<List<AccountWithWindows>>

    @Query("SELECT * FROM accounts ORDER BY rowid") suspend fun accounts(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id") suspend fun account(id: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAccount(account: AccountEntity)

    @Upsert suspend fun upsertAccount(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id") suspend fun deleteAccount(id: String)

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
    suspend fun storeSnapshot(
        account: AccountEntity,
        windows: List<WindowEntity>,
        points: List<UsagePointEntity>,
        pruneBeforeEpochMs: Long,
    ) {
        upsertAccount(account)
        deleteWindows(account.id)
        upsertWindows(windows)
        upsertPoints(points)
        pruneHistory(pruneBeforeEpochMs)
    }
}
