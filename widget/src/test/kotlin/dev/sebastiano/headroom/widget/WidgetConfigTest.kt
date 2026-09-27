package dev.sebastiano.headroom.widget

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.Test

class WidgetConfigTest {
    @Test
    fun `a new widget shows every account, weekly, in the wallpaper colour`() {
        val config = WidgetConfig.defaultFor(WidgetStyle.Shape)

        assertEquals(WidgetConfig(WidgetStyle.Shape), config)
        assertEquals(emptyList(), config.accountIds)
        assertEquals(WidgetWindow.Weekly, config.window)
        assertEquals(ColourMode.Wallpaper, config.colourMode)
    }

    @Test
    fun `enum ids are stable and round trip`() {
        assertEquals(
            listOf("rings", "bars", "shape", "countdown"),
            WidgetStyle.entries.map { it.id },
        )
        assertEquals(listOf("weekly", "session"), WidgetWindow.entries.map { it.id })
        assertEquals(listOf("wallpaper", "account", "mono"), ColourMode.entries.map { it.id })
        WidgetStyle.entries.forEach { assertEquals(it, WidgetStyle.fromId(it.id)) }
        WidgetWindow.entries.forEach { assertEquals(it, WidgetWindow.fromId(it.id)) }
        ColourMode.entries.forEach { assertEquals(it, ColourMode.fromId(it.id)) }
        assertNull(WidgetStyle.fromId("nope"))
    }

    @Test
    fun `in memory store saves, replaces and removes configs per widget id`() = runTest {
        val store = InMemoryWidgetConfigStore()
        val rings = WidgetConfig(WidgetStyle.Rings, accountIds = listOf("a"))

        assertNull(store.get(1))
        store.set(1, rings)
        store.set(2, WidgetConfig(WidgetStyle.Bars))
        store.set(1, rings.copy(colourMode = ColourMode.Mono))
        store.remove(2)

        assertEquals(rings.copy(colourMode = ColourMode.Mono), store.get(1))
        assertNull(store.get(2))
    }
}
