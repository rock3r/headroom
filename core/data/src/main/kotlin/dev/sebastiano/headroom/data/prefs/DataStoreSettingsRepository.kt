package dev.sebastiano.headroom.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.sebastiano.headroom.model.AppSettings
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.SettingsRepository
import dev.sebastiano.headroom.model.SyncFrequency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The app settings in DataStore, stored by enum name. A value the app does not know, for example
 * one written by a newer version, reads as the default.
 */
internal class DataStoreSettingsRepository(private val store: DataStore<Preferences>) :
    SettingsRepository {
    override val settings: Flow<AppSettings> =
        store.data
            .map { prefs ->
                val defaults = AppSettings()
                AppSettings(
                    quotaDisplay =
                        prefs[QUOTA_DISPLAY].toEnum<QuotaDisplay>() ?: defaults.quotaDisplay,
                    syncFrequency =
                        prefs[SYNC_FREQUENCY].toEnum<SyncFrequency>() ?: defaults.syncFrequency,
                )
            }
            .distinctUntilChanged()

    override suspend fun setQuotaDisplay(display: QuotaDisplay) {
        store.edit { it[QUOTA_DISPLAY] = display.name }
    }

    override suspend fun setSyncFrequency(frequency: SyncFrequency) {
        store.edit { it[SYNC_FREQUENCY] = frequency.name }
    }

    private inline fun <reified T : Enum<T>> String?.toEnum(): T? =
        enumValues<T>().firstOrNull { it.name == this }

    private companion object {
        val QUOTA_DISPLAY = stringPreferencesKey("quota_display")
        val SYNC_FREQUENCY = stringPreferencesKey("sync_frequency")
    }
}
