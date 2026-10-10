package dev.sebastiano.headroom.auth

import dev.sebastiano.headroom.model.Provider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class CredentialCodecTest {
    @Test
    fun `a credential survives a round trip`() {
        val credential =
            StoredCredential(
                provider = Provider.Copilot,
                accountId = "copilot-1",
                kind = CredentialKind.OAuth,
                accessToken = "a",
                refreshToken = "r",
                expiresAt = Instant.parse("2026-09-27T12:00:00.123Z"),
                providerAccountId = "p",
                label = "sam",
                extras = mapOf("k" to "v"),
                revision = 7,
            )

        assertEquals(credential, CredentialCodec.decode(CredentialCodec.encode(credential)))
    }

    @Test
    fun `optional fields may be missing`() {
        val decoded =
            CredentialCodec.decode(
                """{"provider":"zai","accountId":"z","kind":"ApiKey","accessToken":"key"}"""
            )
        assertEquals(Provider.ZAi, decoded.provider)
        assertEquals(null, decoded.expiresAt)
        assertEquals(0, decoded.revision)
    }

    @Test
    fun `unreadable data is an invalid response`() {
        assertFailsWith<AuthException.InvalidResponse> { CredentialCodec.decode("{") }
        assertFailsWith<AuthException.InvalidResponse> {
            CredentialCodec.decode(
                """{"provider":"nope","accountId":"z","kind":"ApiKey","accessToken":"key"}"""
            )
        }
    }
}
