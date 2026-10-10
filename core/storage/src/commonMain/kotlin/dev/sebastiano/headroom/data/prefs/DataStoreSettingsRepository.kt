package dev.sebastiano.headroom.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.sebastiano.headroom.model.AppSettings
import dev.sebastiano.headroom.model.MotionPreference
import dev.sebastiano.headroom.model.OverviewSort
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.SettingsRepository
import dev.sebastiano.headroom.model.SyncFrequency
import dev.sebastiano.headroom.model.ThemeMode
import dev.sebastiano.headroom.model.ThemePalette
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The app settings in DataStore: choices by enum name, switches as booleans. A value the app does
 * not know, for example one written by a newer version, reads as the default.
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
                    overviewSort =
                        prefs[OVERVIEW_SORT].toEnum<OverviewSort>() ?: defaults.overviewSort,
                    refreshShimmer = prefs.switch(REFRESH_SHIMMER) ?: defaults.refreshShimmer,
                    resetConfetti = prefs.switch(RESET_CONFETTI) ?: defaults.resetConfetti,
                    resetIsland = prefs.switch(RESET_ISLAND) ?: defaults.resetIsland,
                    redeemClaudeResets =
                        prefs.switch(REDEEM_CLAUDE_RESETS) ?: defaults.redeemClaudeResets,
                    resetExpiryReminders =
                        prefs.switch(RESET_EXPIRY_REMINDERS) ?: defaults.resetExpiryReminders,
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

    override suspend fun setOverviewSort(sort: OverviewSort) {
        store.edit { it[OVERVIEW_SORT] = sort.name }
    }

    override suspend fun setRefreshShimmer(enabled: Boolean) {
        store.edit { it[REFRESH_SHIMMER] = enabled }
    }

    override suspend fun setResetConfetti(enabled: Boolean) {
        store.edit { it[RESET_CONFETTI] = enabled }
    }

    override suspend fun setResetIsland(enabled: Boolean) {
        store.edit { it[RESET_ISLAND] = enabled }
    }

    override suspend fun setRedeemClaudeResets(enabled: Boolean) {
        store.edit { it[REDEEM_CLAUDE_RESETS] = enabled }
    }

    override suspend fun setResetExpiryReminders(enabled: Boolean) {
        store.edit { it[RESET_EXPIRY_REMINDERS] = enabled }
    }

    /** The switch under [key], or null when it is missing or not a boolean. */
    private fun Preferences.switch(key: Preferences.Key<Boolean>): Boolean? =
        asMap()[key] as? Boolean

    private inline fun <reified T : Enum<T>> String?.toEnum(): T? =
        enumValues<T>().firstOrNull { it.name == this }

    private companion object {
        val QUOTA_DISPLAY = stringPreferencesKey("quota_display")
        val SYNC_FREQUENCY = stringPreferencesKey("sync_frequency")
        val THEME = stringPreferencesKey("theme")
        val MOTION = stringPreferencesKey("motion")
        val PALETTE = stringPreferencesKey("palette")
        val OVERVIEW_SORT = stringPreferencesKey("overview_sort")
        val REFRESH_SHIMMER = booleanPreferencesKey("refresh_shimmer")
        val RESET_CONFETTI = booleanPreferencesKey("reset_confetti")
        val RESET_ISLAND = booleanPreferencesKey("reset_island")
        val REDEEM_CLAUDE_RESETS = booleanPreferencesKey("redeem_claude_resets")
        val RESET_EXPIRY_REMINDERS = booleanPreferencesKey("reset_expiry_reminders")
    }
}
