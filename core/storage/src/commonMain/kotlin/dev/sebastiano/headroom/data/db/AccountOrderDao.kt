package dev.sebastiano.headroom.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/** Where each account sits in the user's list: adding accounts at the end, and reordering. */
@Dao
internal interface AccountOrderDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAccount(account: AccountEntity)

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM accounts") suspend fun nextPosition(): Int

    @Query("SELECT id FROM accounts ORDER BY position, rowid")
    suspend fun orderedIds(): List<String>

    @Query("UPDATE accounts SET position = :position WHERE id = :id")
    suspend fun setPosition(id: String, position: Int)

    /** Adds the account at the end of the list, unless it is already there. */
    @Transaction
    suspend fun insertAccountLast(account: AccountEntity) {
        insertAccount(account.copy(position = nextPosition()))
    }

    /**
     * Puts the accounts in [orderedIds] first, in that order. Accounts it does not list, such as
     * one added meanwhile, follow in their current order. Ids of unknown accounts are ignored.
     */
    @Transaction
    suspend fun reorderAccounts(orderedIds: List<String>) {
        val current = orderedIds()
        val listed = orderedIds.distinct().filter { it in current }
        (listed + (current - listed.toSet())).forEachIndexed { position, id ->
            setPosition(id, position)
        }
    }
}
