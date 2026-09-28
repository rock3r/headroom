package dev.sebastiano.headroom.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProviderLogoTest {
    @Test
    fun `every provider has its own logo`() {
        val paths = Provider.entries.map { it.logo.pathData }
        assertEquals(paths.size, paths.toSet().size)
    }

    @Test
    fun `every logo is a single path that starts with a move`() {
        Provider.entries.forEach { provider ->
            val data = provider.logo.pathData
            assertTrue(data.startsWith("M"), "$provider starts with ${data.take(1)}")
            assertTrue(data.none { it == '"' || it == '<' }, "$provider has markup in its data")
        }
    }

    @Test
    fun `the box adds the inset on every side of the viewport`() {
        val logo = ProviderLogo(pathData = "M0 0H1V1Z", viewportSize = 24f, inset = 5f)
        assertEquals(34f, logo.boxSize)
    }

    @Test
    fun `logos that fill their viewport get an inset so they match the others`() {
        // The JetBrains artwork fills its whole 24 unit box. The models.dev logos leave about 4.5
        // of their 40 units empty on each side, so the JetBrains logo needs a margin to match.
        val jetBrains = Provider.JetBrains.logo
        val share = jetBrains.viewportSize / jetBrains.boxSize
        assertTrue(share in 0.65f..0.8f, "JetBrains fills $share of its box")
        Provider.entries
            .filter { it != Provider.JetBrains }
            .forEach { assertEquals(0f, it.logo.inset, "$it should not need an inset") }
    }
}
