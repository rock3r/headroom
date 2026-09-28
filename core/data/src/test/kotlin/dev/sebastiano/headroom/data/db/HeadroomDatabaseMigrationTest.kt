package dev.sebastiano.headroom.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HeadroomDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val file = context.getDatabasePath(NAME)

    @AfterTest
    fun tearDown() {
        context.deleteDatabase(NAME)
    }

    @Test
    fun `accounts saved before nicknames existed survive the upgrade`() = runTest {
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { v1 ->
            VERSION_1_SCHEMA.forEach(v1::execSQL)
            v1.execSQL(
                "INSERT INTO accounts (id, provider, label) VALUES ('a1', 'claude', 'sam@example.com')"
            )
            v1.version = 1
        }

        val db =
            Room.databaseBuilder(context, HeadroomDatabase::class.java, NAME)
                .addMigrations(HeadroomDatabase.MIGRATION_1_2)
                .allowMainThreadQueries()
                .build()
        try {
            val account = db.quotaDao().accounts().single()
            assertEquals("a1", account.id)
            assertEquals("sam@example.com", account.label)
            assertNull(account.nickname)
        } finally {
            db.close()
        }
    }

    private companion object {
        const val NAME = "migration-test.db"

        /** The schema Room created for version 1, copied from its generated code. */
        val VERSION_1_SCHEMA =
            listOf(
                "CREATE TABLE IF NOT EXISTS `accounts` (`id` TEXT NOT NULL, " +
                    "`provider` TEXT NOT NULL, `label` TEXT NOT NULL, `planLabel` TEXT, " +
                    "`fetchedAtEpochMs` INTEGER, `lastError` TEXT, `balanceAmount` REAL, " +
                    "`balanceUnit` TEXT, PRIMARY KEY(`id`))",
                "CREATE TABLE IF NOT EXISTS `windows` (`accountId` TEXT NOT NULL, " +
                    "`windowId` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                    "`label` TEXT NOT NULL, `kind` TEXT NOT NULL, `usedPercent` REAL NOT NULL, " +
                    "`resetsAtEpochMs` INTEGER, `lengthSeconds` INTEGER, `groupLabel` TEXT, " +
                    "`isUnlimited` INTEGER NOT NULL, PRIMARY KEY(`accountId`, `windowId`), " +
                    "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
                "CREATE TABLE IF NOT EXISTS `usage_points` (`accountId` TEXT NOT NULL, " +
                    "`windowId` TEXT NOT NULL, `atEpochMs` INTEGER NOT NULL, " +
                    "`usedPercent` REAL NOT NULL, " +
                    "PRIMARY KEY(`accountId`, `windowId`, `atEpochMs`), " +
                    "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
                "CREATE INDEX IF NOT EXISTS `index_usage_points_atEpochMs` " +
                    "ON `usage_points` (`atEpochMs`)",
                "CREATE TABLE IF NOT EXISTS room_master_table " +
                    "(id INTEGER PRIMARY KEY,identity_hash TEXT)",
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) " +
                    "VALUES(42, 'c9d7de0e82970a28b73024ef140953c2')",
            )
    }
}
