package dev.sebastiano.headroom.ui.spatial

import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import dev.sebastiano.headroom.ui.LayoutWidth
import kotlin.test.Test
import kotlin.test.assertEquals

class SpatialTest {
    @Test
    fun `the navigation orbiter sits where the floating toolbar or the rail would be`() {
        assertEquals(NavigationEdge.Bottom, navigationEdge(LayoutWidth.Compact))
        assertEquals(NavigationEdge.Start, navigationEdge(LayoutWidth.Medium))
        assertEquals(NavigationEdge.Start, navigationEdge(LayoutWidth.Expanded))
    }

    @Test
    fun `the window has a navigation rail only when it is wide and not in Full Space`() {
        val rail = NavigationSuiteType.WideNavigationRailCollapsed
        assertEquals(NavigationSuiteType.None, navigationSuiteType(LayoutWidth.Compact, false))
        assertEquals(rail, navigationSuiteType(LayoutWidth.Medium, spatial = false))
        assertEquals(rail, navigationSuiteType(LayoutWidth.Expanded, spatial = false))
        // In Full Space the navigation orbiter carries the rail instead.
        assertEquals(NavigationSuiteType.None, navigationSuiteType(LayoutWidth.Medium, true))
        assertEquals(NavigationSuiteType.None, navigationSuiteType(LayoutWidth.Expanded, true))
    }

    @Test
    fun `outside Full Space the overview keeps its columns`() {
        assertEquals(1, overviewColumns(LayoutWidth.Compact, spatial = false))
        assertEquals(2, overviewColumns(LayoutWidth.Medium, spatial = false))
        // Expanded shares the window with the detail pane, so the list is narrow.
        assertEquals(1, overviewColumns(LayoutWidth.Expanded, spatial = false))
    }

    @Test
    fun `in Full Space the overview has the whole main panel, as the detail has its own`() {
        assertEquals(1, overviewColumns(LayoutWidth.Compact, spatial = true))
        assertEquals(2, overviewColumns(LayoutWidth.Medium, spatial = true))
        assertEquals(2, overviewColumns(LayoutWidth.Expanded, spatial = true))
    }
}
