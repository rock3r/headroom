package dev.sebastiano.headroom.island

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IslandGeometryTest {
    // A tall phone: 1080 x 2400 px at 2.5 px per dp, so 432 x 960 dp.
    private val width = 1080
    private val height = 2400
    private val density = 2.5f

    /** A round camera hole, 30 dp across, 10 dp from the top, in the middle. */
    private val hole = PxRect(left = 502, top = 25, right = 577, bottom = 100)

    private fun withHole() = islandGeometry(hole, width, height, density)

    private fun withoutHole() = islandGeometry(null, width, height, density)

    private fun windowOf(geometry: IslandGeometry) =
        PxRect(0, 0, geometry.window.width, geometry.window.height)

    @Test
    fun `the pill is 72 percent of the screen wide and 40 dp high`() {
        val pill = withHole().expanded
        assertEquals(778, pill.width) // 1080 * 0.72 = 777.6
        assertEquals(100, pill.height) // 40 dp * 2.5
    }

    @Test
    fun `the pill is centred on the camera hole`() {
        val geometry = withHole()
        assertEquals(geometry.collapsed.centerX, geometry.expanded.centerX, 1f)
    }

    @Test
    fun `the pill grows from the size of the hole`() {
        val geometry = withHole()
        assertEquals(hole.width, geometry.collapsed.width)
        assertEquals(hole.height, geometry.collapsed.height)
    }

    @Test
    fun `the hole lies inside the pill, so the pill hides it`() {
        val geometry = withHole()
        assertTrue(geometry.expanded.contains(geometry.collapsed))
    }

    @Test
    fun `the pill is centred on a hole that has room above it`() {
        // A hole 40 dp down: the pill is centred on it, not pushed to the top.
        val low = PxRect(left = 502, top = 100, right = 577, bottom = 175)
        val geometry = islandGeometry(low, width, height, density)
        assertEquals(low.centerY, geometry.window.top + geometry.expanded.centerY, 1f)
    }

    @Test
    fun `the pill never starts above the screen`() {
        val geometry = withHole()
        assertTrue(geometry.window.top + geometry.expanded.top >= 0)
        assertTrue(geometry.window.top >= 0)
    }

    @Test
    fun `a hole that is taller than the pill is still covered`() {
        val big = PxRect(left = 480, top = 0, right = 600, bottom = 160)
        val geometry = islandGeometry(big, width, height, density)
        assertTrue(geometry.expanded.contains(geometry.collapsed))
    }

    @Test
    fun `the window fits the pill and its overshoot, not the screen`() {
        val geometry = withHole()
        assertTrue(geometry.window.width < width)
        assertTrue(geometry.window.height < height / 10)
        // Everything the surface can draw lies inside the window.
        assertTrue(windowOf(geometry).contains(geometry.expanded))
        assertTrue(windowOf(geometry).contains(geometry.collapsed))
    }

    @Test
    fun `the window is a little larger than the pill, for the spring's overshoot`() {
        val geometry = withHole()
        assertTrue(geometry.window.width > geometry.expanded.width)
        assertTrue(geometry.window.height > geometry.expanded.height)
    }

    @Test
    fun `the window stays on the screen`() {
        val geometry = withHole()
        assertTrue(geometry.window.left >= 0)
        assertTrue(geometry.window.right <= width)
        assertTrue(geometry.window.bottom <= height)
    }

    @Test
    fun `rects are relative to the window`() {
        val geometry = withHole()
        val screenPillLeft = geometry.window.left + geometry.expanded.left
        assertTrue(abs((width - 778) / 2 - screenPillLeft) <= 1)
    }

    @Test
    fun `with no cutout the pill sits at the top centre, a small margin down`() {
        val geometry = withoutHole()
        val pill = geometry.expanded
        assertEquals(20, geometry.window.top + pill.top) // an 8 dp margin at 2.5 px per dp
        assertEquals(width / 2f, geometry.window.left + pill.centerX, 1f)
    }

    @Test
    fun `with no cutout the pill grows from a small dot in its middle`() {
        val geometry = withoutHole()
        assertTrue(geometry.collapsed.width < geometry.expanded.height)
        assertTrue(geometry.expanded.contains(geometry.collapsed))
        assertEquals(geometry.expanded.centerX, geometry.collapsed.centerX, 1f)
        assertEquals(geometry.expanded.centerY, geometry.collapsed.centerY, 1f)
    }

    @Test
    fun `a hole near the left edge keeps the pill on the screen`() {
        val left = PxRect(left = 20, top = 25, right = 95, bottom = 100)
        val geometry = islandGeometry(left, width, height, density)
        assertTrue(geometry.window.left >= 0)
        assertTrue(geometry.expanded.contains(geometry.collapsed))
    }

    // The overlay island: a phone with a 100 px status bar, below which the pill hangs.
    private val statusBar = 100

    private fun overlay() = overlayIslandGeometry(statusBar, width, height, density)

    @Test
    fun `the overlay pill is as large as the accessibility pill`() {
        val pill = overlay().expanded
        assertEquals(778, pill.width)
        assertEquals(100, pill.height)
    }

    @Test
    fun `the overlay window is exactly the pill, so it covers nothing else`() {
        val geometry = overlay()
        assertEquals(geometry.window.width, geometry.expanded.width)
        assertEquals(geometry.window.height, geometry.expanded.height)
        assertEquals(PxRect(0, 0, geometry.window.width, geometry.window.height), geometry.expanded)
    }

    @Test
    fun `the overlay window sits below the status bar, with a small margin`() {
        val geometry = overlay()
        assertEquals(statusBar + 10, geometry.window.top) // 4 dp * 2.5 px per dp
        assertTrue(geometry.window.top > statusBar)
    }

    @Test
    fun `the overlay pill is centred on the screen`() {
        val window = overlay().window
        assertEquals(width / 2f, window.centerX, 1f)
    }

    @Test
    fun `the overlay pill grows from a small dot at its own centre`() {
        val geometry = overlay()
        assertEquals(40, geometry.collapsed.width) // 16 dp * 2.5 px per dp
        assertEquals(40, geometry.collapsed.height)
        assertEquals(geometry.expanded.centerX, geometry.collapsed.centerX, 1f)
        assertEquals(geometry.expanded.centerY, geometry.collapsed.centerY, 1f)
        assertTrue(geometry.expanded.contains(geometry.collapsed))
    }

    @Test
    fun `the overlay window stays on the screen`() {
        val geometry = overlayIslandGeometry(statusBar, 300, 300, density)
        assertTrue(geometry.window.left >= 0)
        assertTrue(geometry.window.right <= 300)
        assertTrue(geometry.window.bottom <= 300)
    }
}
