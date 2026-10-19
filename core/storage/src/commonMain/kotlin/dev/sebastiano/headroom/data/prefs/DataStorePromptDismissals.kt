package dev.sebastiano.headroom.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import dev.sebastiano.headroom.model.PromptDismissals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Dismissed prompts, one flag per account. Unset flags mean the prompt still shows. */
internal class DataStorePromptDismissals(private val store: DataStore<Preferences>) :
    PromptDismissals {
    override fun cursorDismissed(accountId: String): Flow<Boolean> =
        store.data.map { prefs -> prefs[cursorKey(accountId)] ?: false }.distinctUntilChanged()

    override suspend fun dismissCursor(accountId: String) {
        store.edit { it[cursorKey(accountId)] = true }
    }

    private fun cursorKey(accountId: String) = booleanPreferencesKey("cursor-dismissed:$accountId")
}
