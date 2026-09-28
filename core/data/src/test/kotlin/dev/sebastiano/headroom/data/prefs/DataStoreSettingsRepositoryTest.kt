package dev.sebastiano.headroom.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.sebastiano.headroom.model.AppSettings
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.SyncFrequency
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class DataStoreSettingsRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private fun TestScope.store() =
        PreferenceDataStoreFactory.create(scope = backgroundScope) {
            File(folder.root, "settings.preferences_pb")
        }

    @Test
    fun `nothing stored gives the defaults`() = runTest {
        val settings = DataStoreSettingsRepository(store())
        assertEquals(AppSettings(), settings.settings.first())
    }

    @Test
    fun `each choice is stored and read back`() = runTest {
        val store = store()
        val settings = DataStoreSettingsRepository(store)
        settings.setQuotaDisplay(QuotaDisplay.Left)
        settings.setSyncFrequency(SyncFrequency.OnOpen)
        assertEquals(
            AppSettings(QuotaDisplay.Left, SyncFrequency.OnOpen),
            DataStoreSettingsRepository(store).settings.first(),
        )
    }

    @Test
    fun `an unknown stored value falls back to the default`() = runTest {
        val store = store()
        store.edit {
            it[stringPreferencesKey("quota_display")] = "Sideways"
            it[stringPreferencesKey("sync_frequency")] = "Hourly"
        }
        assertEquals(AppSettings(), DataStoreSettingsRepository(store).settings.first())
    }
}
