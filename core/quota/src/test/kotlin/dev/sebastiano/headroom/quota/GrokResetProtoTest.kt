package dev.sebastiano.headroom.quota

import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class GrokResetProtoTest {
    @Test
    fun `the list request is an empty message`() {
        assertEquals(0, GrokResetProto.listRequest().size)
    }

    @Test
    fun `the redeem request carries the token id as field 10`() {
        // Tag 10, wire type 2 (length-delimited) = 0x52, then the length and the UTF-8 bytes.
        assertEquals(
            listOf<Byte>(0x52, 3, 'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte()),
            GrokResetProto.redeemRequest("abc").toList(),
        )
    }

    @Test
    fun `reads the tokens and their validity end`() {
        val bytes =
            GrokResetProto.tokensMessage(
                listOf(
                    GrokResetToken("tok-1", Instant.parse("2026-04-10T00:00:00Z")),
                    GrokResetToken("tok-2", Instant.parse("2026-04-20T12:30:00.5Z")),
                )
            )

        val tokens = GrokResetProto.readTokens(bytes)

        assertEquals(listOf("tok-1", "tok-2"), tokens.map { it.id })
        assertEquals(Instant.parse("2026-04-10T00:00:00Z"), tokens[0].validUntil)
        assertEquals(Instant.parse("2026-04-20T12:30:00.5Z"), tokens[1].validUntil)
    }

    @Test
    fun `a token with no validity end has none`() {
        val token = byteArrayOf(0x52, 3, 'x'.code.toByte(), 'y'.code.toByte(), 'z'.code.toByte())
        val bytes = byteArrayOf(0x52, token.size.toByte()) + token

        val tokens = GrokResetProto.readTokens(bytes)

        assertEquals("xyz", tokens.single().id)
        assertNull(tokens.single().validUntil)
    }

    @Test
    fun `unknown fields are skipped`() {
        // Field 1 varint 150, field 2 fixed64, field 3 fixed32, then the tokens.
        val unknown =
            byteArrayOf(0x08, 0x96.toByte(), 0x01) +
                byteArrayOf(0x11, 1, 2, 3, 4, 5, 6, 7, 8) +
                byteArrayOf(0x1D, 1, 2, 3, 4)
        val bytes = unknown + GrokResetProto.tokensMessage(listOf(GrokResetToken("t", null)))

        assertEquals(listOf("t"), GrokResetProto.readTokens(bytes).map { it.id })
    }

    @Test
    fun `an empty message has no tokens`() {
        assertEquals(emptyList(), GrokResetProto.readTokens(ByteArray(0)))
    }

    @Test
    fun `a cut message is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            GrokResetProto.readTokens(byteArrayOf(0x52, 10, 1))
        }
    }
}
