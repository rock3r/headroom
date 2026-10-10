@file:OptIn(ExperimentalForeignApi::class)

package dev.sebastiano.headroom.auth

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.io.IOException
import platform.posix.AF_INET
import platform.posix.AF_INET6
import platform.posix.EINTR
import platform.posix.INADDR_LOOPBACK
import platform.posix.IPPROTO_IPV6
import platform.posix.IPV6_V6ONLY
import platform.posix.POLLIN
import platform.posix.SHUT_RDWR
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_NOSIGPIPE
import platform.posix.SO_REUSEADDR
import platform.posix.accept
import platform.posix.bind
import platform.posix.close
import platform.posix.errno
import platform.posix.getsockname
import platform.posix.listen
import platform.posix.poll
import platform.posix.pollfd
import platform.posix.recv
import platform.posix.send
import platform.posix.setsockopt
import platform.posix.shutdown
import platform.posix.sockaddr_in
import platform.posix.sockaddr_in6
import platform.posix.socket
import platform.posix.socklen_tVar

internal actual fun bindLoopback(
    address: LoopbackAddress,
    port: Int,
    acceptPollMillis: Int,
    readTimeoutMillis: Int,
): LoopbackListener? {
    val family = if (address == LoopbackAddress.Ipv4) AF_INET else AF_INET6
    val fd = socket(family, SOCK_STREAM, 0)
    if (fd < 0) return null
    val bound =
        setOption(fd, SOL_SOCKET, SO_REUSEADDR) &&
            (address == LoopbackAddress.Ipv4 || setOption(fd, IPPROTO_IPV6, IPV6_V6ONLY)) &&
            bindTo(fd, address, port) &&
            listen(fd, BACKLOG) == 0
    val boundPort = if (bound) localPort(fd, address) else -1
    if (boundPort <= 0) {
        close(fd)
        return null
    }
    return PosixLoopbackListener(fd, boundPort, acceptPollMillis, readTimeoutMillis)
}

/** The backlog `java.net.ServerSocket` uses when given 0. */
private const val BACKLOG = 50
private const val READ_BUFFER_BYTES = 4_096
private const val BYTE_BITS = 8
private const val BYTE_MASK = 0xFF
private const val IPV6_LAST_BYTE = 15

private fun setOption(fd: Int, level: Int, option: Int): Boolean = memScoped {
    val on = alloc<platform.posix.int32_tVar>().apply { value = 1 }
    setsockopt(fd, level, option, on.ptr, sizeOf<platform.posix.int32_tVar>().convert()) == 0
}

/** Binds [fd] to the loopback address only, never to every interface. */
private fun bindTo(fd: Int, address: LoopbackAddress, port: Int): Boolean = memScoped {
    when (address) {
        LoopbackAddress.Ipv4 -> {
            val addr = alloc<sockaddr_in>()
            addr.sin_len = sizeOf<sockaddr_in>().convert()
            addr.sin_family = AF_INET.convert()
            addr.sin_port = networkOrder(port)
            addr.sin_addr.s_addr = networkOrder32(INADDR_LOOPBACK)
            bind(fd, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) == 0
        }
        LoopbackAddress.Ipv6 -> {
            val addr = alloc<sockaddr_in6>()
            addr.sin6_len = sizeOf<sockaddr_in6>().convert()
            addr.sin6_family = AF_INET6.convert()
            addr.sin6_port = networkOrder(port)
            // ::1 is fifteen zero bytes and a one; alloc zeroes the rest.
            addr.sin6_addr.__u6_addr.__u6_addr8[IPV6_LAST_BYTE] = 1u
            bind(fd, addr.ptr.reinterpret(), sizeOf<sockaddr_in6>().convert()) == 0
        }
    }
}

private fun localPort(fd: Int, address: LoopbackAddress): Int = memScoped {
    val length = alloc<socklen_tVar>()
    when (address) {
        LoopbackAddress.Ipv4 -> {
            val addr = alloc<sockaddr_in>()
            length.value = sizeOf<sockaddr_in>().convert()
            if (getsockname(fd, addr.ptr.reinterpret(), length.ptr) != 0) return -1
            hostOrder(addr.sin_port)
        }
        LoopbackAddress.Ipv6 -> {
            val addr = alloc<sockaddr_in6>()
            length.value = sizeOf<sockaddr_in6>().convert()
            if (getsockname(fd, addr.ptr.reinterpret(), length.ptr) != 0) return -1
            hostOrder(addr.sin6_port)
        }
    }
}

/** Ports are big-endian on the wire; Apple's CPUs are little-endian. */
private fun networkOrder(port: Int): UShort =
    (((port and BYTE_MASK) shl BYTE_BITS) or ((port shr BYTE_BITS) and BYTE_MASK)).toUShort()

private fun hostOrder(port: UShort): Int = networkOrder(port.toInt()).toInt()

/** [value] with its bytes reversed, from the CPU's little-endian order to network order. */
private fun networkOrder32(value: UInt): UInt =
    (0 until Int.SIZE_BYTES).fold(0u) { result, index ->
        (result shl BYTE_BITS) or ((value shr (index * BYTE_BITS)) and BYTE_MASK.toUInt())
    }

/**
 * Waits until [fd] can be read, for at most [timeoutMillis].
 *
 * @return false when the time ran out.
 * @throws IOException when polling fails.
 */
private fun awaitReadable(fd: Int, timeoutMillis: Int): Boolean = memScoped {
    val request = alloc<pollfd>()
    request.fd = fd
    request.events = POLLIN.convert()
    while (true) {
        val ready = poll(request.ptr, 1u, timeoutMillis)
        if (ready > 0) return true
        if (ready == 0) return false
        if (errno != EINTR) throw IOException("poll failed: errno $errno")
    }
    @Suppress("UNREACHABLE_CODE") false
}

/** Closes a socket once, however many threads ask. */
private class SocketHandle(val fd: Int) {
    private val lock = SynchronizedObject()
    private var closed = false

    val isClosed: Boolean
        get() = synchronized(lock) { closed }

    /**
     * Closes the socket; [wake] first shuts it down, so a blocked read on another thread returns.
     */
    fun close(wake: Boolean) {
        synchronized(lock) {
            if (closed) return
            closed = true
        }
        if (wake) shutdown(fd, SHUT_RDWR)
        close(fd)
    }
}

private class PosixLoopbackListener(
    fd: Int,
    override val port: Int,
    private val acceptPollMillis: Int,
    private val readTimeoutMillis: Int,
) : LoopbackListener {
    private val handle = SocketHandle(fd)

    override val isClosed: Boolean
        get() = handle.isClosed

    override fun accept(): LoopbackConnection? {
        if (handle.isClosed) throw IOException("The listener is closed")
        if (!awaitReadable(handle.fd, acceptPollMillis)) return null
        if (handle.isClosed) throw IOException("The listener is closed")
        val client = accept(handle.fd, null, null)
        if (client < 0) {
            if (errno == EINTR) return null
            throw IOException("accept failed: errno $errno")
        }
        // A browser that leaves mid-answer must not kill the app with SIGPIPE.
        setOption(client, SOL_SOCKET, SO_NOSIGPIPE)
        return PosixLoopbackConnection(SocketHandle(client), readTimeoutMillis)
    }

    override fun close() {
        handle.close(wake = false)
    }
}

private class PosixLoopbackConnection(
    private val handle: SocketHandle,
    private val readTimeoutMillis: Int,
) : LoopbackConnection {
    private val buffer = ByteArray(READ_BUFFER_BYTES)
    private var position = 0
    private var limit = 0

    override fun read(): Int {
        if (position == limit && !fill()) return -1
        return buffer[position++].toInt() and BYTE_MASK
    }

    /** Reads what the browser sent so far. False at the end of the stream. */
    private fun fill(): Boolean {
        if (handle.isClosed) throw IOException("The connection is closed")
        if (!awaitReadable(handle.fd, readTimeoutMillis)) throw IOException("Read timed out")
        val count = buffer.usePinned { recv(handle.fd, it.addressOf(0), buffer.size.convert(), 0) }
        if (count < 0) throw IOException("recv failed: errno $errno")
        position = 0
        limit = count.toInt()
        return limit > 0
    }

    override fun write(bytes: ByteArray) {
        var sent = 0
        bytes.usePinned { pinned ->
            while (sent < bytes.size) {
                val count =
                    send(handle.fd, pinned.addressOf(sent), (bytes.size - sent).convert(), 0)
                if (count < 0) {
                    if (errno == EINTR) continue
                    throw IOException("send failed: errno $errno")
                }
                sent += count.toInt()
            }
        }
    }

    override fun close() {
        handle.close(wake = true)
    }
}
