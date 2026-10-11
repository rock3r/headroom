package dev.sebastiano.headroom.data.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okio.FileSystem
import okio.Path

/**
 * A database in memory, on the SQLite that ships with Room, so it runs on every target. Queries run
 * on the caller's thread rather than Room's own, so none is still running when a test closes the
 * database: closing the native SQLite under a running query crashes the test process.
 */
internal fun inMemoryDatabase(
    queryDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
): HeadroomDatabase =
    Room.inMemoryDatabaseBuilder<HeadroomDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(queryDispatcher)
        .build()

/** The database in the file at [path], upgraded with every migration. */
internal fun databaseAt(path: Path): HeadroomDatabase =
    Room.databaseBuilder<HeadroomDatabase>(name = path.toString())
        .addAllMigrations()
        .setDriver(BundledSQLiteDriver())
        .build()

/** A new empty folder in the system's temporary files. [close] deletes it. */
internal class TestDirectory : AutoCloseable {
    val path: Path =
        (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "headroom-test-${Uuid.random()}").also {
            FileSystem.SYSTEM.createDirectories(it)
        }

    override fun close() {
        FileSystem.SYSTEM.deleteRecursively(path)
    }
}
