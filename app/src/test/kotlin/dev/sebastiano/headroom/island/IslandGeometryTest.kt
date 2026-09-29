package dev.sebastiano.headroom.island

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

    private fun withHole(tight: Boolean = false) =
        islandGeometry(hole, width, height, density, tight)

    private fun withoutHole() = islandGeometry(null, width, height, density)

    private fun onScreen(geometry: IslandGeometry, rect: PxRect) =
        rect.offset(geometry.window.left, geometry.window.top)

    @Test
    fun `the hole sits in the middle of the pill, with equal sides`() {
        val geometry = withHole()
        val pill = geometry.expanded
        val camera = geometry.collapsed
        assertEquals(camera.left - pill.left, pill.right - camera.right)
    }

    @Test
    fun `the pill is compact, at most 40 percent of the screen wide`() {
        assertTrue(withHole().expanded.width <= width * 0.4f)
    }

    @Test
    fun `the pill is a little larger than the hole all round, so it hides it`() {
        val pill = onScreen(withHole(), withHole().expanded)
        assertTrue(pill.top <= hole.top - 10) // at least 4 dp above
        assertTrue(pill.bottom >= hole.bottom + 10) // and below
    }

    @Test
    fun `the pill grows from the hole, through a capsule around it`() {
        val geometry = withHole()
        assertEquals(onScreen(geometry, geometry.collapsed), hole)
        val capsule = geometry.capsule
        assertTrue(capsule.contains(geometry.collapsed))
        assertEquals(geometry.expanded.height, capsule.height)
        assertTrue(capsule.width < geometry.expanded.width)
        assertEquals(geometry.collapsed.centerX, capsule.centerX, 1f)
    }

    @Test
    fun `the pill never starts above the screen`() {
        val geometry = islandGeometry(PxRect(502, 0, 577, 75), width, height, density)
        assertTrue(geometry.window.top + geometry.expanded.top >= 0)
        assertTrue(geometry.window.top >= 0)
    }

    @Test
    fun `the accessibility window has room for the spring's overshoot`() {
        val geometry = withHole()
        assertTrue(geometry.window.width > geometry.expanded.width)
        assertTrue(geometry.window.height > geometry.expanded.height)
    }

    @Test
    fun `the overlay window is exactly the pill, because it takes the touches in its area`() {
        val geometry = withHole(tight = true)
        assertEquals(PxRect(0, 0, geometry.window.width, geometry.window.height), geometry.expanded)
        assertEquals(hole.centerX, geometry.window.left + geometry.collapsed.centerX, 1f)
    }

    @Test
    fun `the window stays on the screen, even for a hole near the edge`() {
        val left = PxRect(left = 20, top = 25, right = 95, bottom = 100)
        for (geometry in listOf(withHole(), islandGeometry(left, width, height, density))) {
            assertTrue(geometry.window.left >= 0)
            assertTrue(geometry.window.right <= width)
            assertTrue(geometry.expanded.contains(geometry.collapsed))
        }
    }

    @Test
    fun `with no cutout the pill sits at the top centre, a small margin down`() {
        val geometry = withoutHole()
        val pill = onScreen(geometry, geometry.expanded)
        assertEquals(20, pill.top) // an 8 dp margin at 2.5 px per dp
        assertEquals(width / 2f, pill.centerX, 1f)
        assertTrue(geometry.expanded.contains(geometry.collapsed))
        assertEquals(geometry.expanded.centerX, geometry.collapsed.centerX, 1f)
    }
}
