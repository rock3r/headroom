package dev.sebastiano.headroom.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(
    entities = [AccountEntity::class, WindowEntity::class, UsagePointEntity::class],
    version = 2,
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
    }
}
