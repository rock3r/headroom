@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.sebastiano.headroom.daemon

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.AF_INET
import platform.posix.F_SETFL
import platform.posix.INADDR_LOOPBACK
import platform.posix.POLLIN
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_NOSIGPIPE
import platform.posix.SO_REUSEADDR
import platform.posix.accept
import platform.posix.bind
import platform.posix.close
import platform.posix.fcntl
import platform.posix.getsockname
import platform.posix.listen
import platform.posix.poll
import platform.posix.pollfd
import platform.posix.recv
import platform.posix.send
import platform.posix.setsockopt
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.socklen_tVar

/**
 * An HTTP listener that can only bind to 127.0.0.1. A request that arrives here still has to pass
 * [LocalApi]: the socket is the network limit, not the authentication.
 */
public class LoopbackHttp(private val handler: (LocalRequest) -> LocalResponse) {
    private var fd: Int = -1
    public var port: Int = 0
        private set

    public fun start(port: Int = 0): Int {
        val socketFd = socket(AF_INET, SOCK_STREAM, 0)
        check(socketFd >= 0) { "socket failed" }
        memScoped {
            val on = alloc<platform.posix.int32_tVar>().apply { value = 1 }
            setsockopt(
                socketFd,
                SOL_SOCKET,
                SO_REUSEADDR,
                on.ptr,
                sizeOf<platform.posix.int32_tVar>().convert(),
            )
            val address = alloc<sockaddr_in>()
            address.sin_len = sizeOf<sockaddr_in>().convert()
            address.sin_family = AF_INET.convert()
            address.sin_port = networkOrder(port)
            address.sin_addr.s_addr = networkOrder32(INADDR_LOOPBACK)
            check(bind(socketFd, address.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) == 0) {
                "bind failed"
            }
            check(listen(socketFd, BACKLOG) == 0) { "listen failed" }
            val length = alloc<socklen_tVar>().apply { value = sizeOf<sockaddr_in>().convert() }
            check(getsockname(socketFd, address.ptr.reinterpret(), length.ptr) == 0)
            this@LoopbackHttp.port = hostOrder(address.sin_port)
        }
        fd = socketFd
        return this.port
    }

    /**
     * Serves one connection. Returns false when none arrives within [timeoutMillis], so a test
     * cannot hang on accept.
     */
    public fun serveOne(timeoutMillis: Int = ACCEPT_WAIT_MILLIS): Boolean {
        if (!awaitConnection(timeoutMillis)) return false
        val client = accept(fd, null, null)
        if (client < 0) return false
        fcntl(client, F_SETFL, 0)
        memScoped {
            val on = alloc<platform.posix.int32_tVar>().apply { value = 1 }
            setsockopt(
                client,
                SOL_SOCKET,
                SO_NOSIGPIPE,
                on.ptr,
                sizeOf<platform.posix.int32_tVar>().convert(),
            )
        }
        try {
            if (!awaitReadable(client, timeoutMillis)) return false
            val raw = readRequest(client, timeoutMillis)
            val request = parse(raw)
            val response = handler(request)
            writeResponse(client, response)
        } finally {
            close(client)
        }
        return true
    }

    private fun awaitConnection(timeoutMillis: Int): Boolean = awaitReadable(fd, timeoutMillis)

    private fun awaitReadable(socketFd: Int, timeoutMillis: Int): Boolean = memScoped {
        val request = alloc<pollfd>()
        request.fd = socketFd
        request.events = POLLIN.convert()
        poll(request.ptr, 1u, timeoutMillis) > 0
    }

    public fun close() {
        if (fd >= 0) close(fd)
        fd = -1
    }

    /**
     * Reads until the end of the header block: TCP does not preserve request boundaries, so a
     * request line or header may arrive in several writes. Bounded by the buffer size and the
     * socket deadline, so a client that never finishes cannot hold the listener.
     */
    private fun readRequest(client: Int, timeoutMillis: Int): String {
        val buffer = ByteArray(READ_BUFFER)
        var received = 0
        while (received < buffer.size && !hasHeaderEnd(buffer, received)) {
            val count =
                if (awaitReadable(client, timeoutMillis)) receive(client, buffer, received) else 0
            if (count <= 0) break
            received += count
        }
        return buffer.decodeToString(endIndex = received)
    }

    private fun receive(client: Int, buffer: ByteArray, received: Int): Int =
        buffer
            .usePinned {
                recv(client, it.addressOf(received), (buffer.size - received).convert(), 0)
            }
            .toInt()

    private fun writeResponse(client: Int, response: LocalResponse) {
        val body = response.body.encodeToByteArray()
        val head =
            "HTTP/1.1 ${response.status} ${reason(response.status)}\r\n" +
                "Content-Type: ${response.contentType}\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Connection: close\r\n\r\n"
        val bytes = head.encodeToByteArray() + body
        bytes.usePinned { pinned ->
            var sent = 0
            while (sent < bytes.size) {
                val count = send(client, pinned.addressOf(sent), (bytes.size - sent).convert(), 0)
                if (count <= 0) return
                sent += count.toInt()
            }
        }
    }

    public companion object {
        private const val OK = 200
        private const val UNAUTHORIZED = 401
        private const val FORBIDDEN = 403
        private const val NOT_FOUND = 404
        private const val ACCEPT_WAIT_MILLIS = 2_000
        private const val BACKLOG = 16
        private const val READ_BUFFER = 8192
        private const val BYTE_BITS = 8
        private const val BYTE_MASK = 0xFF
        private const val HEADER_TERMINATOR = "\r\n\r\n"

        /** True when the bytes received so far contain the blank line that ends the headers. */
        public fun hasHeaderEnd(buffer: ByteArray, received: Int): Boolean =
            buffer.decodeToString(endIndex = received).contains(HEADER_TERMINATOR)

        public fun parse(raw: String): LocalRequest {
            val lines = raw.split("\r\n")
            val request = lines.firstOrNull().orEmpty().split(' ')
            val headers =
                lines
                    .drop(1)
                    .takeWhile { it.isNotEmpty() }
                    .associate { line ->
                        val name = line.substringBefore(':').trim().lowercase()
                        val value = line.substringAfter(':', "").trim()
                        name to value
                    }
            return LocalRequest(
                method = request.getOrElse(0) { "" },
                path = request.getOrElse(1) { "" }.substringBefore('?'),
                host = headers["host"],
                origin = headers["origin"],
                authorization = headers["authorization"],
            )
        }

        private fun reason(status: Int): String =
            when (status) {
                OK -> "OK"
                UNAUTHORIZED -> "Unauthorized"
                FORBIDDEN -> "Forbidden"
                NOT_FOUND -> "Not Found"
                else -> "Error"
            }

        private fun networkOrder(port: Int): UShort =
            (((port and BYTE_MASK) shl BYTE_BITS) or ((port shr BYTE_BITS) and BYTE_MASK))
                .toUShort()

        private fun hostOrder(port: UShort): Int = networkOrder(port.toInt()).toInt()

        private fun networkOrder32(value: UInt): UInt =
            (0 until Int.SIZE_BYTES).fold(0u) { result, index ->
                (result shl BYTE_BITS) or ((value shr (index * BYTE_BITS)) and BYTE_MASK.toUInt())
            }
    }
}
