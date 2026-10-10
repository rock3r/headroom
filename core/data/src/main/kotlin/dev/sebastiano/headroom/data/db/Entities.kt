package dev.sebastiano.headroom.data.db

import androidx.room.ColumnInfo
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
    /** Where the user put the account in their list. Lists read accounts in this order. */
    @ColumnInfo(defaultValue = "0") val position: Int = 0,
    /** The resets read in the last sync that could read them, as JSON: see [ResetsCodec]. */
    val resetsJson: String? = null,
    /**
     * True when the last sync that worked could not read the resets, so [resetsJson] holds older
     * ones.
     */
    @ColumnInfo(defaultValue = "0") val resetsReadFailed: Boolean = false,
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
    /** When a credit expires. Null for every other window. */
    val expiresAtEpochMs: Long? = null,
    /** False for a window the provider sent that the app does not know. */
    @ColumnInfo(defaultValue = "1") val isRecognised: Boolean = true,
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

/**
 * One usage-limit reset that was used or expired: see [RoomResetEventLog]. A redeem in Headroom is
 * stored with its [attemptKey], unique, so a retry never records it twice, and [settled] false
 * until a sync finds its reset gone. Every other row is settled. The given-back columns hold the
 * used percent of each kind of limit the reset restored, or null when it is unknown.
 */
@Entity(
    tableName = "reset_events",
    foreignKeys =
        [
            ForeignKey(
                entity = AccountEntity::class,
                parentColumns = ["id"],
                childColumns = ["accountId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices =
        [Index("accountId"), Index("atEpochMs"), Index(value = ["attemptKey"], unique = true)],
)
internal data class ResetEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: String,
    val provider: String,
    val poolId: String,
    val poolLabel: String,
    val kind: String,
    val atEpochMs: Long,
    val expiresAtEpochMs: Long?,
    val source: String?,
    val givenBackSession: Double?,
    val givenBackDaily: Double?,
    val givenBackWeekly: Double?,
    val givenBackMonthly: Double?,
    val givenBackEstimated: Boolean,
    val attemptKey: String?,
    val settled: Boolean,
)

internal data class AccountWithWindows(
    @Embedded val account: AccountEntity,
    @Relation(parentColumn = "id", entityColumn = "accountId") val windows: List<WindowEntity>,
)
