package dev.sebastiano.headroom.auth

import dev.sebastiano.headroom.model.Provider
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthMethodsTest {
    private val io = testIoDispatcher()
    private val methods =
        AuthMethods(
            http = { error("no network in this test") },
            ioDispatcher = io,
        )

    @AfterTest
    fun tearDown() {
        io.close()
    }

    @Test
    fun `every provider has a sign-in method`() {
        val kinds = Provider.entries.associateWith { methods.forProvider(it)::class }
        assertEquals(
            mapOf(
                Provider.Claude to AuthMethod.Browser::class,
                Provider.Codex to AuthMethod.Browser::class,
                Provider.Copilot to AuthMethod.DeviceCode::class,
                Provider.Grok to AuthMethod.Browser::class,
                Provider.Kimi to AuthMethod.DeviceCode::class,
                Provider.ZAi to AuthMethod.ApiKey::class,
                Provider.OpenCodeGo to AuthMethod.ApiKey::class,
                Provider.JetBrains to AuthMethod.Browser::class,
            ),
            kinds,
        )
        Provider.entries.forEach { assertEquals(it, methods.forProvider(it).provider) }
    }

    @Test
    fun `only Claude offers a page with a code to paste`() {
        assertTrue(
            assertIs<AuthMethod.Browser>(methods.forProvider(Provider.Claude)).offersCodePage
        )
        assertFalse(assertIs<AuthMethod.Browser>(methods.forProvider(Provider.Grok)).offersCodePage)
        assertFalse(
            assertIs<AuthMethod.Browser>(methods.forProvider(Provider.Codex)).offersCodePage
        )
        assertFalse(
            assertIs<AuthMethod.Browser>(methods.forProvider(Provider.JetBrains)).offersCodePage
        )
    }

    @Test
    fun `flows belong to their provider`() {
        assertEquals(
            Provider.Claude,
            assertIs<AuthMethod.Browser>(methods.forProvider(Provider.Claude)).flow.provider,
        )
        assertEquals(
            Provider.Kimi,
            assertIs<AuthMethod.DeviceCode>(methods.forProvider(Provider.Kimi)).flow.provider,
        )
    }

    @Test
    fun `every OAuth provider can refresh and API key providers cannot`() {
        assertEquals(
            setOf(
                Provider.Claude,
                Provider.Codex,
                Provider.Copilot,
                Provider.Grok,
                Provider.Kimi,
                Provider.JetBrains,
            ),
            methods.refreshers.keys,
        )
    }

    @Test
    fun `an API key becomes a credential that never expires`() {
        val method = assertIs<AuthMethod.ApiKey>(methods.forProvider(Provider.ZAi))

        val tokens = method.tokens("  zai-key-123 \n", label = "Work")

        assertEquals(Provider.ZAi, tokens.provider)
        assertEquals(CredentialKind.ApiKey, tokens.kind)
        assertEquals("zai-key-123", tokens.accessToken)
        assertNull(tokens.refreshToken)
        assertNull(tokens.expiresAt)
        assertEquals("Work", tokens.label)
    }

    @Test
    fun `a blank or broken API key is refused`() {
        val method = assertIs<AuthMethod.ApiKey>(methods.forProvider(Provider.OpenCodeGo))
        assertFailsWith<AuthException.SignInFailed> { method.tokens("   ") }
        assertFailsWith<AuthException.SignInFailed> { method.tokens("two words") }
    }
}
