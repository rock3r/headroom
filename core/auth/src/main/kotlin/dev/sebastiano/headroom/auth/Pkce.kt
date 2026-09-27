package dev.sebastiano.headroom.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

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
        public fun generate(random: SecureRandom = secureRandom()): Pkce {
            val verifier = randomBase64Url(random, VERIFIER_BYTES)
            return Pkce(verifier, challengeFor(verifier))
        }

        /** The S256 challenge: base64url(SHA-256(verifier)) without padding. */
        public fun challengeFor(verifier: String): String =
            base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))

        /** An OAuth `state` of 32 random bytes in base64url. It is never the PKCE verifier. */
        public fun randomState(random: SecureRandom = secureRandom()): String =
            randomBase64Url(random, STATE_BYTES)

        /** An OAuth `state` of 16 random bytes as lowercase hex. */
        public fun randomHexState(random: SecureRandom = secureRandom()): String {
            val bytes = ByteArray(HEX_STATE_BYTES).also(random::nextBytes)
            return bytes.joinToString("") {
                (it.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(2, '0')
            }
        }

        private fun randomBase64Url(random: SecureRandom, size: Int): String =
            base64Url(ByteArray(size).also(random::nextBytes))

        private fun base64Url(bytes: ByteArray): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

/**
 * The one place this module creates a [SecureRandom]. Lint's TrulyRandom check is about a PRNG
 * seeding bug on Android 4.3 and older; Headroom runs on Android 16 and later only.
 */
@Suppress("TrulyRandom") internal fun secureRandom(): SecureRandom = SecureRandom()
