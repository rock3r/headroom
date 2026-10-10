package dev.sebastiano.headroom.auth

import kotlin.io.encoding.Base64
import kotlin.random.Random
import okio.ByteString.Companion.encodeUtf8

/**
 * A PKCE (Proof Key for Code Exchange, RFC 7636) pair. The [verifier] stays in the app; the
 * [challenge] goes into the authorize URL.
 */
public class Pkce(public val verifier: String, public val challenge: String) {
    public companion object {
        private const val VERIFIER_BYTES = 32
        private const val STATE_BYTES = 32
        private const val HEX_STATE_BYTES = 16
        private const val BYTE_MASK = 0xff
        private const val HEX_RADIX = 16

        /** A new verifier of 32 random bytes, with its S256 challenge. */
        public fun generate(random: Random = secureRandom()): Pkce {
            val verifier = randomBase64Url(random, VERIFIER_BYTES)
            return Pkce(verifier, challengeFor(verifier))
        }

        /** The S256 challenge: base64url(SHA-256(verifier)) without padding. */
        public fun challengeFor(verifier: String): String =
            base64Url(verifier.encodeUtf8().sha256().toByteArray())

        /** An OAuth `state` of 32 random bytes in base64url. It is never the PKCE verifier. */
        public fun randomState(random: Random = secureRandom()): String =
            randomBase64Url(random, STATE_BYTES)

        /** An OAuth `state` of 16 random bytes as lowercase hex. */
        public fun randomHexState(random: Random = secureRandom()): String {
            val bytes = random.nextBytes(HEX_STATE_BYTES)
            return bytes.joinToString("") {
                (it.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(2, '0')
            }
        }

        private fun randomBase64Url(random: Random, size: Int): String =
            base64Url(random.nextBytes(size))

        private fun base64Url(bytes: ByteArray): String =
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(bytes)
    }
}

/**
 * A cryptographically secure [Random]: `SecureRandom` on the JVM, `SecRandomCopyBytes` on iOS.
 * Tests can pass a seeded [Random] instead.
 */
internal expect fun secureRandom(): Random
