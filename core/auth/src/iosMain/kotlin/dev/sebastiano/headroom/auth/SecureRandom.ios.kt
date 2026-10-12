package dev.sebastiano.headroom.auth

import kotlin.random.Random
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault

internal actual fun secureRandom(): Random = SystemSecureRandom

/** Random bits from the system's cryptographically secure generator. */
private object SystemSecureRandom : Random() {
    private const val INT_BITS = 32

    override fun nextBits(bitCount: Int): Int {
        val bytes = ByteArray(Int.SIZE_BYTES)
        fill(bytes)
        val value =
            bytes.fold(0) { acc, byte -> (acc shl Byte.SIZE_BITS) or (byte.toInt() and 0xFF) }
        return if (bitCount == 0) 0 else value ushr (INT_BITS - bitCount)
    }

    override fun nextBytes(array: ByteArray, fromIndex: Int, toIndex: Int): ByteArray {
        if (toIndex > fromIndex) {
            val chunk = ByteArray(toIndex - fromIndex)
            fill(chunk)
            chunk.copyInto(array, fromIndex)
        }
        return array
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun fill(bytes: ByteArray) {
        val status = bytes.usePinned {
            SecRandomCopyBytes(kSecRandomDefault, bytes.size.toULong(), it.addressOf(0))
        }
        check(status == errSecSuccess) { "SecRandomCopyBytes failed: $status" }
    }
}
