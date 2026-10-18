package dev.sebastiano.headroom.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ProviderTest {
    @Test
    fun `gemini is registered with expected properties`() {
        val provider = Provider.fromId("gemini")
        assertEquals(Provider.Gemini, provider)
        assertEquals("gemini", Provider.Gemini.id)
        assertEquals("Gemini", Provider.Gemini.displayName)
        assertFalse(Provider.Gemini.windowsAreSeparateAllowances)
        assertFalse(Provider.Gemini.usesApiKey)
    }
}
