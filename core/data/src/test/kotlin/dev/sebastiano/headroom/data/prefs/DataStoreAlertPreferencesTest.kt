package dev.sebastiano.headroom.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.WindowKind
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class DataStoreAlertPreferencesTest {
    @get:Rule val folder = TemporaryFolder()

    private fun window(kind: WindowKind, id: String = "w") =
        QuotaWindow(id, id, kind, 10.0, null, null)

    private fun kotlinx.coroutines.test.TestScope.prefs() =
        DataStoreAlertPreferences(
            PreferenceDataStoreFactory.create(scope = backgroundScope) {
                File(folder.root, "alerts.preferences_pb")
            }
        )

    @Test
    fun `weekly windows are on and monthly windows off until changed`() = runTest {
        val prefs = prefs()
        assertEquals(true, prefs.isEnabled("a", window(WindowKind.Weekly)).first())
        assertEquals(false, prefs.isEnabled("a", window(WindowKind.Monthly)).first())
    }

    @Test
    fun `session windows can never be enabled`() = runTest {
        val prefs = prefs()
        prefs.setEnabled("a", "s", true)
        assertEquals(false, prefs.isEnabled("a", window(WindowKind.Session, "s")).first())
    }

    @Test
    fun `a stored choice overrides the default, per account and window`() = runTest {
        val prefs = prefs()
        prefs.setEnabled("a", "w", false)
        prefs.setEnabled("b", "m", true)
        assertEquals(false, prefs.isEnabled("a", window(WindowKind.Weekly)).first())
        assertEquals(true, prefs.isEnabled("b", window(WindowKind.Monthly, "m")).first())
        assertEquals(true, prefs.isEnabled("b", window(WindowKind.Weekly)).first())
    }
}
