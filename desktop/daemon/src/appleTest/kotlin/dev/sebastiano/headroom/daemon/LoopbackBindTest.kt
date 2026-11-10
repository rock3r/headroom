@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.sebastiano.headroom.daemon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import platform.posix.AF_INET
import platform.posix.INADDR_LOOPBACK
import platform.posix.SOCK_STREAM
import platform.posix.close
import platform.posix.connect
import platform.posix.sockaddr_in
import platform.posix.socket

/**
 * Proves the listener binds loopback and that [serveOne] returns when nobody connects. A full
 * request/response in this process would need a second thread; the command-line check covers that
 * with curl against the built binary.
 */
class LoopbackBindTest {
    @Test
    fun theListenerBindsALoopbackPort() {
        val server = LoopbackHttp { LocalResponse(200, "application/json", "{}") }
        val port = server.start(0)
        try {
            assertTrue(port > 0)
            assertEquals(0, connectLoopback(port))
        } finally {
            server.close()
        }
    }

    @Test
    fun acceptReturnsWhenNobodyConnects() {
        val server = LoopbackHttp { LocalResponse(200, "text/plain", "") }
        server.start(0)
        try {
            assertFalse(server.serveOne(timeoutMillis = 50))
        } finally {
            server.close()
        }
    }

    private fun connectLoopback(port: Int): Int = memScoped {
        val socketFd = socket(AF_INET, SOCK_STREAM, 0)
        val address = alloc<sockaddr_in>()
        address.sin_len = sizeOf<sockaddr_in>().convert()
        address.sin_family = AF_INET.convert()
        address.sin_port = network(port)
        address.sin_addr.s_addr = network32(INADDR_LOOPBACK)
        val connected =
            connect(socketFd, address.ptr.reinterpret(), sizeOf<sockaddr_in>().convert())
        close(socketFd)
        connected
    }

    private fun network(port: Int): UShort =
        (((port and BYTE_MASK) shl BYTE_BITS) or ((port shr BYTE_BITS) and BYTE_MASK)).toUShort()

    private fun network32(value: UInt): UInt =
        (0 until Int.SIZE_BYTES).fold(0u) { result, index ->
            (result shl BYTE_BITS) or ((value shr (index * BYTE_BITS)) and BYTE_MASK.toUInt())
        }

    private companion object {
        const val BYTE_MASK = 0xFF
        const val BYTE_BITS = 8
    }
}
