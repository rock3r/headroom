package dev.sebastiano.headroom.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(
    entities = [AccountEntity::class, WindowEntity::class, UsagePointEntity::class],
    version = 6,
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
    }
}
