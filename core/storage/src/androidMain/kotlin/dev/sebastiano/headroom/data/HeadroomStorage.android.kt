package dev.sebastiano.headroom.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dev.sebastiano.headroom.data.db.HeadroomDatabase
import dev.sebastiano.headroom.data.db.addAllMigrations
import kotlinx.coroutines.CoroutineScope

/**
 * Opens the storage in the app's private files, with Android's own SQLite. The DataStore files live
 * in `files/datastore`.
 */
public fun openHeadroomStorage(context: Context, scope: CoroutineScope): HeadroomStorage {
    val appContext = context.applicationContext
    val database =
        Room.databaseBuilder(appContext, HeadroomDatabase::class.java, HeadroomDatabase.NAME)
            .addAllMigrations()
            .build()
    return HeadroomStorage(
        database = database,
        settingsStore =
            PreferenceDataStoreFactory.create(scope = scope) {
                appContext.preferencesDataStoreFile("settings")
            },
        alertStore =
            PreferenceDataStoreFactory.create(scope = scope) {
                appContext.preferencesDataStoreFile("alerts")
            },
    )
}
