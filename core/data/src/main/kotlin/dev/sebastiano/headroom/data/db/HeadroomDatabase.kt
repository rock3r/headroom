package dev.sebastiano.headroom.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(
    entities = [AccountEntity::class, WindowEntity::class, UsagePointEntity::class],
    version = 3,
    exportSchema = false,
)
internal abstract class HeadroomDatabase : RoomDatabase() {
    abstract fun quotaDao(): QuotaDao

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
    }
}
