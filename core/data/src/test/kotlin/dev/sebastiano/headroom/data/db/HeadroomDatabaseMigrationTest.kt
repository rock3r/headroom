package dev.sebastiano.headroom.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.ResetAttemptKey
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
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

        val db = openCurrent()
        try {
            val account = db.quotaDao().accounts().single()
            assertEquals("a1", account.id)
            assertEquals("sam@example.com", account.label)
            assertNull(account.nickname)
        } finally {
            db.close()
        }
    }

    @Test
    fun `windows saved before credit amounts existed survive the upgrade`() = runTest {
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { v2 ->
            VERSION_1_SCHEMA.forEach(v2::execSQL)
            v2.execSQL("ALTER TABLE accounts ADD COLUMN nickname TEXT")
            v2.execSQL(
                "INSERT INTO accounts (id, provider, label, nickname) " +
                    "VALUES ('a1', 'jetbrains', 'sam@example.com', 'Work')"
            )
            v2.execSQL(
                "INSERT INTO windows (accountId, windowId, position, label, kind, usedPercent, " +
                    "isUnlimited) VALUES ('a1', 'ai_credits', 0, 'Monthly', 'Monthly', 25.0, 0)"
            )
            v2.version = 2
        }

        val db = openCurrent()
        try {
            val stored = db.quotaDao().accounts().single()
            assertEquals("Work", stored.nickname)
            val window = db.quotaDao().observeAccounts().first().single().windows.single()
            assertEquals("ai_credits", window.windowId)
            assertEquals(25.0, window.usedPercent)
            assertNull(window.usedAmount)
            assertNull(window.limitAmount)
            assertNull(window.amountUnit)
        } finally {
            db.close()
        }
    }

    @Test
    fun `accounts saved before they could be reordered keep the order they were added in`() =
        runTest {
            file.parentFile?.mkdirs()
            SQLiteDatabase.openOrCreateDatabase(file, null).use { v3 ->
                VERSION_1_SCHEMA.forEach(v3::execSQL)
                v3.execSQL("ALTER TABLE accounts ADD COLUMN nickname TEXT")
                v3.execSQL("ALTER TABLE windows ADD COLUMN usedAmount REAL")
                v3.execSQL("ALTER TABLE windows ADD COLUMN limitAmount REAL")
                v3.execSQL("ALTER TABLE windows ADD COLUMN amountUnit TEXT")
                // Not in id order, so the test tells rowid order from id order.
                listOf("zeta", "alpha", "mid").forEach { id ->
                    v3.execSQL(
                        "INSERT INTO accounts (id, provider, label) VALUES ('$id', 'claude', '$id')"
                    )
                }
                v3.version = 3
            }

            val db = openCurrent()
            try {
                val accounts = db.quotaDao().accounts()
                assertEquals(listOf("zeta", "alpha", "mid"), accounts.map { it.id })
                assertEquals(listOf(0, 1, 2), accounts.map { it.position })
            } finally {
                db.close()
            }
        }

    @Test
    fun `windows saved before credits and unknown windows existed are recognised, with no expiry`() =
        runTest {
            file.parentFile?.mkdirs()
            SQLiteDatabase.openOrCreateDatabase(file, null).use { v4 ->
                VERSION_1_SCHEMA.forEach(v4::execSQL)
                v4.execSQL("ALTER TABLE accounts ADD COLUMN nickname TEXT")
                v4.execSQL("ALTER TABLE windows ADD COLUMN usedAmount REAL")
                v4.execSQL("ALTER TABLE windows ADD COLUMN limitAmount REAL")
                v4.execSQL("ALTER TABLE windows ADD COLUMN amountUnit TEXT")
                v4.execSQL("ALTER TABLE accounts ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
                v4.execSQL(
                    "INSERT INTO accounts (id, provider, label) VALUES ('a1', 'claude', 'a1')"
                )
                v4.execSQL(
                    "INSERT INTO windows (accountId, windowId, position, label, kind, " +
                        "usedPercent, isUnlimited) " +
                        "VALUES ('a1', 'seven_day', 0, 'Weekly', 'Weekly', 71.0, 0)"
                )
                v4.version = 4
            }

            val db = openCurrent()
            try {
                val window = db.quotaDao().observeAccounts().first().single().windows.single()
                assertEquals("seven_day", window.windowId)
                assertNull(window.expiresAtEpochMs)
                assertEquals(true, window.isRecognised)
            } finally {
                db.close()
            }
        }

    @Test
    fun `accounts saved before resets were stored have none`() = runTest {
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { v5 ->
            VERSION_1_SCHEMA.forEach(v5::execSQL)
            v5.execSQL("ALTER TABLE accounts ADD COLUMN nickname TEXT")
            v5.execSQL("ALTER TABLE windows ADD COLUMN usedAmount REAL")
            v5.execSQL("ALTER TABLE windows ADD COLUMN limitAmount REAL")
            v5.execSQL("ALTER TABLE windows ADD COLUMN amountUnit TEXT")
            v5.execSQL("ALTER TABLE accounts ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
            v5.execSQL("ALTER TABLE windows ADD COLUMN expiresAtEpochMs INTEGER")
            v5.execSQL("ALTER TABLE windows ADD COLUMN isRecognised INTEGER NOT NULL DEFAULT 1")
            v5.execSQL("INSERT INTO accounts (id, provider, label) VALUES ('a1', 'grok', 'a1')")
            v5.version = 5
        }

        val db = openCurrent()
        try {
            val account = db.quotaDao().observeAccounts().first().single().account
            assertEquals("a1", account.id)
            assertNull(account.resetsJson)
        } finally {
            db.close()
        }
    }

    @Test
    fun `accounts saved before the reset history existed start with an empty one`() = runTest {
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { v6 ->
            VERSION_1_SCHEMA.forEach(v6::execSQL)
            v6.execSQL("ALTER TABLE accounts ADD COLUMN nickname TEXT")
            v6.execSQL("ALTER TABLE windows ADD COLUMN usedAmount REAL")
            v6.execSQL("ALTER TABLE windows ADD COLUMN limitAmount REAL")
            v6.execSQL("ALTER TABLE windows ADD COLUMN amountUnit TEXT")
            v6.execSQL("ALTER TABLE accounts ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
            v6.execSQL("ALTER TABLE windows ADD COLUMN expiresAtEpochMs INTEGER")
            v6.execSQL("ALTER TABLE windows ADD COLUMN isRecognised INTEGER NOT NULL DEFAULT 1")
            v6.execSQL("ALTER TABLE accounts ADD COLUMN resetsJson TEXT")
            v6.execSQL("INSERT INTO accounts (id, provider, label) VALUES ('a1', 'codex', 'a1')")
            v6.version = 6
        }

        val db = openCurrent()
        try {
            val log = RoomResetEventLog(db.quotaDao(), clock = { Instant.EPOCH })
            log.redeemed(Account("a1", Provider.Codex, "a1"), "credits", ResetAttemptKey("k"))
            assertEquals(1, log.events(Instant.EPOCH).first().size)
        } finally {
            db.close()
        }
    }

    private fun openCurrent(): HeadroomDatabase =
        Room.databaseBuilder(context, HeadroomDatabase::class.java, NAME)
            .addMigrations(
                HeadroomDatabase.MIGRATION_1_2,
                HeadroomDatabase.MIGRATION_2_3,
                HeadroomDatabase.MIGRATION_3_4,
                HeadroomDatabase.MIGRATION_4_5,
                HeadroomDatabase.MIGRATION_5_6,
                HeadroomDatabase.MIGRATION_6_7,
            )
            .allowMainThreadQueries()
            .build()

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
