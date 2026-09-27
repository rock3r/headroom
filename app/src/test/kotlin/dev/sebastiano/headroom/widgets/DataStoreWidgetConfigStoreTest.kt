package dev.sebastiano.headroom.widgets

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.sebastiano.headroom.widget.ColourMode
import dev.sebastiano.headroom.widget.WidgetConfig
import dev.sebastiano.headroom.widget.WidgetStyle
import dev.sebastiano.headroom.widget.WidgetWindow
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreWidgetConfigStoreTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun `stores, reads and removes a widget config`() = runTest {
        val store =
            DataStoreWidgetConfigStore(
                PreferenceDataStoreFactory.create(scope = backgroundScope) {
                    File(folder.root, "widgets.preferences_pb")
                }
            )
        val config =
            WidgetConfig(WidgetStyle.Bars, listOf("a", "b"), WidgetWindow.Session, ColourMode.Mono)
        assertNull(store.get(7))
        store.set(7, config)
        assertEquals(config, store.get(7))
        store.remove(7)
        assertNull(store.get(7))
    }
}
