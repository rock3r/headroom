package dev.sebastiano.headroom.quota

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class GrpcWebTest {
    @Test
    fun `a data frame is a zero flag, a big-endian length and the message`() {
        val frame = GrpcWeb.dataFrame(byteArrayOf(1, 2, 3))

        assertEquals(listOf<Byte>(0, 0, 0, 0, 3, 1, 2, 3), frame.toList())
    }

    @Test
    fun `an empty message still gets a frame header`() {
        assertEquals(listOf<Byte>(0, 0, 0, 0, 0), GrpcWeb.dataFrame(ByteArray(0)).toList())
    }

    @Test
    fun `decodes a data frame followed by a trailer frame`() {
        val body =
            GrpcWeb.dataFrame(byteArrayOf(9, 8)) +
                GrpcWeb.trailerFrame("grpc-status:0\r\ngrpc-message:OK\r\n")

        val decoded = GrpcWeb.decode(body)

        assertEquals(listOf<Byte>(9, 8), decoded.message.toList())
        assertEquals(0, decoded.status)
        assertEquals("OK", decoded.statusMessage)
    }

    @Test
    fun `reads an error status from the trailer frame`() {
        val body = GrpcWeb.trailerFrame("grpc-status: 16\r\ngrpc-message: unauthenticated\r\n")

        val decoded = GrpcWeb.decode(body)

        assertEquals(0, decoded.message.size)
        assertEquals(16, decoded.status)
        assertEquals("unauthenticated", decoded.statusMessage)
    }

    @Test
    fun `a trailers-only answer carries its status in the HTTP headers`() {
        val decoded =
            GrpcWeb.decode(
                ByteArray(0),
                headers = mapOf("grpc-status" to "7", "grpc-message" to "denied"),
            )

        assertEquals(7, decoded.status)
        assertEquals("denied", decoded.statusMessage)
    }

    @Test
    fun `a body with no trailer and no header status is OK`() {
        val decoded = GrpcWeb.decode(GrpcWeb.dataFrame(byteArrayOf(5)))

        assertEquals(0, decoded.status)
        assertNull(decoded.statusMessage)
    }

    @Test
    fun `a truncated frame is rejected`() {
        assertFailsWith<IllegalArgumentException> { GrpcWeb.decode(byteArrayOf(0, 0, 0, 0, 9, 1)) }
    }

    @Test
    fun `an empty body with no header status is rejected`() {
        assertFailsWith<IllegalArgumentException> { GrpcWeb.decode(ByteArray(0)) }
    }
}
