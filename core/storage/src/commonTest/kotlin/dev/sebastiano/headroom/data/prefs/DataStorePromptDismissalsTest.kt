package dev.sebastiano.headroom.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.sebastiano.headroom.data.db.TestDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class DataStorePromptDismissalsTest {
    private val folder = TestDirectory()

    @AfterTest
    fun tearDown() {
        folder.close()
    }

    private fun kotlinx.coroutines.test.TestScope.dismissals() =
        DataStorePromptDismissals(
            PreferenceDataStoreFactory.createWithPath(scope = backgroundScope) {
                folder.path / "prompts.preferences_pb"
            }
        )

    @Test
    fun `a prompt shows until it is dismissed`() = runTest {
        val prefs = dismissals()
        assertEquals(false, prefs.cursorDismissed("a").first())
        prefs.dismissCursor("a")
        assertEquals(true, prefs.cursorDismissed("a").first())
    }

    @Test
    fun `dismissing one account leaves the others alone`() = runTest {
        val prefs = dismissals()
        prefs.dismissCursor("a")
        assertEquals(true, prefs.cursorDismissed("a").first())
        assertEquals(false, prefs.cursorDismissed("b").first())
    }
}
