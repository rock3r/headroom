package dev.sebastiano.headroom.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.sebastiano.headroom.model.AppSettings
import dev.sebastiano.headroom.model.MotionPreference
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.SettingsRepository
import dev.sebastiano.headroom.model.SyncFrequency
import dev.sebastiano.headroom.model.ThemeMode
import dev.sebastiano.headroom.model.ThemePalette
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
                    theme = prefs[THEME].toEnum<ThemeMode>() ?: defaults.theme,
                    motion = prefs[MOTION].toEnum<MotionPreference>() ?: defaults.motion,
                    palette = prefs[PALETTE].toEnum<ThemePalette>() ?: defaults.palette,
                )
            }
            .distinctUntilChanged()

    override suspend fun setQuotaDisplay(display: QuotaDisplay) {
        store.edit { it[QUOTA_DISPLAY] = display.name }
    }

    override suspend fun setSyncFrequency(frequency: SyncFrequency) {
        store.edit { it[SYNC_FREQUENCY] = frequency.name }
    }

    override suspend fun setTheme(theme: ThemeMode) {
        store.edit { it[THEME] = theme.name }
    }

    override suspend fun setMotion(motion: MotionPreference) {
        store.edit { it[MOTION] = motion.name }
    }

    override suspend fun setPalette(palette: ThemePalette) {
        store.edit { it[PALETTE] = palette.name }
    }

    private inline fun <reified T : Enum<T>> String?.toEnum(): T? =
        enumValues<T>().firstOrNull { it.name == this }

    private companion object {
        val QUOTA_DISPLAY = stringPreferencesKey("quota_display")
        val SYNC_FREQUENCY = stringPreferencesKey("sync_frequency")
        val THEME = stringPreferencesKey("theme")
        val MOTION = stringPreferencesKey("motion")
        val PALETTE = stringPreferencesKey("palette")
    }
}
