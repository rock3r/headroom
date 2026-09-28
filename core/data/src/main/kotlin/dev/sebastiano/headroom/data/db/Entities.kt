package dev.sebastiano.headroom.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "accounts")
internal data class AccountEntity(
    @PrimaryKey val id: String,
    val provider: String,
    val label: String,
    val planLabel: String?,
    val fetchedAtEpochMs: Long?,
    val lastError: String?,
    val balanceAmount: Double? = null,
    val balanceUnit: String? = null,
    val nickname: String? = null,
)

@Entity(
    tableName = "windows",
    primaryKeys = ["accountId", "windowId"],
    foreignKeys =
        [
            ForeignKey(
                entity = AccountEntity::class,
                parentColumns = ["id"],
                childColumns = ["accountId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
)
internal data class WindowEntity(
    val accountId: String,
    val windowId: String,
    val position: Int,
    val label: String,
    val kind: String,
    val usedPercent: Double,
    val resetsAtEpochMs: Long?,
    val lengthSeconds: Long?,
    val groupLabel: String?,
    val isUnlimited: Boolean,
    val usedAmount: Double? = null,
    val limitAmount: Double? = null,
    val amountUnit: String? = null,
)

@Entity(
    tableName = "usage_points",
    primaryKeys = ["accountId", "windowId", "atEpochMs"],
    foreignKeys =
        [
            ForeignKey(
                entity = AccountEntity::class,
                parentColumns = ["id"],
                childColumns = ["accountId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index("atEpochMs")],
)
internal data class UsagePointEntity(
    val accountId: String,
    val windowId: String,
    val atEpochMs: Long,
    val usedPercent: Double,
)

internal data class AccountWithWindows(
    @Embedded val account: AccountEntity,
    @Relation(parentColumn = "id", entityColumn = "accountId") val windows: List<WindowEntity>,
)
