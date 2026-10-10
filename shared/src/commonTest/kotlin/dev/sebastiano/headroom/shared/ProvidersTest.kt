package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.model.Provider
import kotlin.test.Test
import kotlin.test.assertEquals

class ProvidersTest {
    @Test
    fun `every provider is listed with how it signs in`() {
        val providers = allProviders().associate { it.id to it.signIn }

        assertEquals(Provider.entries.map { it.id }, allProviders().map { it.id })
        assertEquals("browser", providers["claude"])
        assertEquals("deviceCode", providers["copilot"])
        assertEquals("apiKey", providers["zai"])
    }
}
