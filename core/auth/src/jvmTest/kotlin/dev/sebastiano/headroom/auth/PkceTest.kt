package dev.sebastiano.headroom.auth

import java.security.MessageDigest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PkceTest {
    private val base64Url = Regex("^[A-Za-z0-9_-]+$")

    @Test
    fun `verifier is 32 random bytes in unpadded base64url`() {
        val pkce = Pkce.generate()
        assertEquals(43, pkce.verifier.length)
        assertTrue(base64Url.matches(pkce.verifier))
    }

    @Test
    fun `challenge is the S256 hash of the verifier`() {
        val pkce = Pkce.generate()
        val expected =
            Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(pkce.verifier.toByteArray())
                )
        assertEquals(expected, pkce.challenge)
    }

    @Test
    fun `challenge matches the RFC 7636 example`() {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            Pkce.challengeFor("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun `state is its own 32 random bytes in base64url`() {
        val random = secureRandom()
        val pkce = Pkce.generate(random)
        val state = Pkce.randomState(random)
        assertEquals(43, state.length)
        assertTrue(base64Url.matches(state))
        assertNotEquals(pkce.verifier, state)
        assertNotEquals(state, Pkce.randomState(random))
    }

    @Test
    fun `hex state is 16 random bytes as lowercase hex`() {
        val state = Pkce.randomHexState()
        assertTrue(Regex("^[0-9a-f]{32}$").matches(state))
    }
}
