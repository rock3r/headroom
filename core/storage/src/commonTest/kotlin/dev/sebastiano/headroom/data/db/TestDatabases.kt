package dev.sebastiano.headroom.data.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlin.uuid.Uuid
import okio.FileSystem
import okio.Path

/** A database in memory, on the SQLite that ships with Room, so it runs on every target. */
internal fun inMemoryDatabase(): HeadroomDatabase =
    Room.inMemoryDatabaseBuilder<HeadroomDatabase>().setDriver(BundledSQLiteDriver()).build()

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
