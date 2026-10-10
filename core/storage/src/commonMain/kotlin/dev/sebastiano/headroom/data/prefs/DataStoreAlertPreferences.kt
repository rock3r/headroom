package dev.sebastiano.headroom.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import dev.sebastiano.headroom.model.AlertPreferences
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.ResetPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Reset alert switches, one per account and window. Unset switches follow [ResetPolicy]. */
internal class DataStoreAlertPreferences(private val store: DataStore<Preferences>) :
    AlertPreferences {
    override fun isEnabled(accountId: String, window: QuotaWindow): Flow<Boolean> =
        store.data
            .map { prefs ->
                ResetPolicy.canAlert(window) &&
                    (prefs[key(accountId, window.id)] ?: ResetPolicy.alertsByDefault(window))
            }
            .distinctUntilChanged()

    override suspend fun setEnabled(accountId: String, windowId: String, enabled: Boolean) {
        store.edit { it[key(accountId, windowId)] = enabled }
    }

    private fun key(accountId: String, windowId: String) =
        booleanPreferencesKey("alert:$accountId:$windowId")
}
