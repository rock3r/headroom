package dev.sebastiano.headroom.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The platform's secure random source and the PKCE values built on it, on every target. */
class SecureRandomTest {
    @Test
    fun `fills whole arrays and ranges`() {
        val random = secureRandom()
        val bytes = random.nextBytes(64)
        assertEquals(64, bytes.size)
        assertTrue(bytes.any { it != 0.toByte() })
        assertNotEquals(bytes.toList(), random.nextBytes(64).toList())

        val partial = random.nextBytes(ByteArray(16), fromIndex = 4, toIndex = 8)
        assertTrue(partial.take(4).all { it == 0.toByte() })
        assertTrue(partial.drop(8).all { it == 0.toByte() })
    }

    @Test
    fun `stays within the bounds it is asked for`() {
        val random = secureRandom()
        repeat(1_000) { assertTrue(random.nextInt(10) in 0 until 10) }
        assertTrue(List(100) { random.nextInt(10) }.toSet().size > 1)
    }

    @Test
    fun `challenge matches the RFC 7636 example`() {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            Pkce.challengeFor("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun `states are random base64url and hex`() {
        val state = Pkce.randomState()
        assertTrue(Regex("^[A-Za-z0-9_-]{43}$").matches(state))
        assertNotEquals(state, Pkce.randomState())
        assertTrue(Regex("^[0-9a-f]{32}$").matches(Pkce.randomHexState()))
    }
}
