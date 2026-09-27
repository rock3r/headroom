package dev.sebastiano.headroom.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [AccountEntity::class, WindowEntity::class, UsagePointEntity::class],
    version = 1,
    exportSchema = false,
)
internal abstract class HeadroomDatabase : RoomDatabase() {
    abstract fun quotaDao(): QuotaDao
}
