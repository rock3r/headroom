package dev.sebastiano.headroom.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.sebastiano.headroom.data.db.TestDirectory
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.WindowKind
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class DataStoreAlertPreferencesTest {
    private val folder = TestDirectory()

    @AfterTest
    fun tearDown() {
        folder.close()
    }

    private fun window(kind: WindowKind, id: String = "w") =
        QuotaWindow(id, id, kind, 10.0, null, null)

    private fun kotlinx.coroutines.test.TestScope.prefs() =
        DataStoreAlertPreferences(
            PreferenceDataStoreFactory.createWithPath(scope = backgroundScope) {
                folder.path / "alerts.preferences_pb"
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
    fun `a stored choice overrides the default per account and window`() = runTest {
        val prefs = prefs()
        prefs.setEnabled("a", "w", false)
        prefs.setEnabled("b", "m", true)
        assertEquals(false, prefs.isEnabled("a", window(WindowKind.Weekly)).first())
        assertEquals(true, prefs.isEnabled("b", window(WindowKind.Monthly, "m")).first())
        assertEquals(true, prefs.isEnabled("b", window(WindowKind.Weekly)).first())
    }
}
