package dev.sebastiano.headroom.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.sebastiano.headroom.data.db.HeadroomDatabase
import dev.sebastiano.headroom.data.db.addAllMigrations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import okio.Path.Companion.toPath

/**
 * Opens the storage in [directory], such as the app's Application Support folder, with the SQLite
 * that ships with Room. The DataStore files sit next to the database.
 */
public fun openHeadroomStorage(directory: String, scope: CoroutineScope): HeadroomStorage {
    val database =
        Room.databaseBuilder<HeadroomDatabase>(name = "$directory/${HeadroomDatabase.NAME}")
            .addAllMigrations()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    return HeadroomStorage(
        database = database,
        settingsStore =
            PreferenceDataStoreFactory.createWithPath(scope = scope) {
                "$directory/settings.preferences_pb".toPath()
            },
        alertStore =
            PreferenceDataStoreFactory.createWithPath(scope = scope) {
                "$directory/alerts.preferences_pb".toPath()
            },
    )
}
