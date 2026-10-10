package dev.sebastiano.headroom.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(
    entities =
        [
            AccountEntity::class,
            WindowEntity::class,
            UsagePointEntity::class,
            ResetEventEntity::class,
        ],
    version = 8,
    exportSchema = false,
)
internal abstract class HeadroomDatabase : RoomDatabase() {
    abstract fun quotaDao(): QuotaDao

    abstract fun accountOrderDao(): AccountOrderDao

    companion object {
        /** Version 2 adds the name the user gives an account. */
        val MIGRATION_1_2: Migration =
            object : Migration(1, 2) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL("ALTER TABLE accounts ADD COLUMN nickname TEXT")
                }
            }

        /** Version 3 adds a window's used and limit amounts, such as AI credits, and their unit. */
        val MIGRATION_2_3: Migration =
            object : Migration(2, 3) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL("ALTER TABLE windows ADD COLUMN usedAmount REAL")
                    connection.execSQL("ALTER TABLE windows ADD COLUMN limitAmount REAL")
                    connection.execSQL("ALTER TABLE windows ADD COLUMN amountUnit TEXT")
                }
            }

        /**
         * Version 4 adds the account's place in the user's list. Existing accounts keep the order
         * they were added in.
         */
        val MIGRATION_3_4: Migration =
            object : Migration(3, 4) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL(
                        "ALTER TABLE accounts ADD COLUMN position INTEGER NOT NULL DEFAULT 0"
                    )
                    connection.execSQL(
                        "UPDATE accounts SET position = " +
                            "(SELECT COUNT(*) FROM accounts AS earlier " +
                            "WHERE earlier.rowid < accounts.rowid)"
                    )
                }
            }

        /**
         * Version 5 adds when a credit expires, and whether the app recognises a window. Existing
         * windows are all recognised and have no expiry.
         */
        val MIGRATION_4_5: Migration =
            object : Migration(4, 5) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL("ALTER TABLE windows ADD COLUMN expiresAtEpochMs INTEGER")
                    connection.execSQL(
                        "ALTER TABLE windows ADD COLUMN isRecognised INTEGER NOT NULL DEFAULT 1"
                    )
                }
            }

        /**
         * Version 6 stores the usage-limit resets read in the last sync. Existing accounts have
         * none.
         */
        val MIGRATION_5_6: Migration =
            object : Migration(5, 6) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL("ALTER TABLE accounts ADD COLUMN resetsJson TEXT")
                }
            }

        /** Version 7 adds the history of usage-limit resets that were used or expired. */
        val MIGRATION_6_7: Migration =
            object : Migration(6, 7) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL(
                        "CREATE TABLE IF NOT EXISTS `reset_events` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`accountId` TEXT NOT NULL, `provider` TEXT NOT NULL, " +
                            "`poolId` TEXT NOT NULL, `poolLabel` TEXT NOT NULL, " +
                            "`kind` TEXT NOT NULL, `atEpochMs` INTEGER NOT NULL, " +
                            "`expiresAtEpochMs` INTEGER, `source` TEXT, " +
                            "`givenBackSession` REAL, `givenBackDaily` REAL, " +
                            "`givenBackWeekly` REAL, `givenBackMonthly` REAL, " +
                            "`givenBackEstimated` INTEGER NOT NULL, `attemptKey` TEXT, " +
                            "`settled` INTEGER NOT NULL, " +
                            "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) " +
                            "ON UPDATE NO ACTION ON DELETE CASCADE )"
                    )
                    connection.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_reset_events_accountId` " +
                            "ON `reset_events` (`accountId`)"
                    )
                    connection.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_reset_events_atEpochMs` " +
                            "ON `reset_events` (`atEpochMs`)"
                    )
                    connection.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_reset_events_attemptKey` " +
                            "ON `reset_events` (`attemptKey`)"
                    )
                }
            }

        /**
         * Version 8 stores whether the last sync could not read the resets. Existing accounts count
         * as read.
         */
        val MIGRATION_7_8: Migration =
            object : Migration(7, 8) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL(
                        "ALTER TABLE accounts ADD COLUMN resetsReadFailed INTEGER NOT NULL DEFAULT 0"
                    )
                }
            }
    }
}
