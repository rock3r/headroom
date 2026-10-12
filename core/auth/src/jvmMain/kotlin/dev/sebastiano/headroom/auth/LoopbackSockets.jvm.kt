package dev.sebastiano.headroom.auth

import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

internal actual fun bindLoopback(
    address: LoopbackAddress,
    port: Int,
    acceptPollMillis: Int,
    readTimeoutMillis: Int,
): LoopbackListener? =
    try {
        val inet =
            when (address) {
                LoopbackAddress.Ipv4 -> IPV4_LOOPBACK
                LoopbackAddress.Ipv6 -> IPV6_LOOPBACK
            }
        val socket = ServerSocket(port, 0, inet).apply { soTimeout = acceptPollMillis }
        JvmLoopbackListener(socket, readTimeoutMillis)
    } catch (_: IOException) {
        null
    }

private val IPV4_LOOPBACK: InetAddress = InetAddress.getByName("127.0.0.1")
private val IPV6_LOOPBACK: InetAddress = InetAddress.getByName("::1")

/** Plain [ServerSocket]s work the same on Android and on the JVM. */
private class JvmLoopbackListener(
    private val socket: ServerSocket,
    private val readTimeoutMillis: Int,
) : LoopbackListener {
    override val port: Int = socket.localPort

    override val isClosed: Boolean
        get() = socket.isClosed

    override fun accept(): LoopbackConnection? =
        try {
            JvmLoopbackConnection(socket.accept().apply { soTimeout = readTimeoutMillis })
        } catch (_: SocketTimeoutException) {
            null
        }

    override fun close() {
        runCatching { socket.close() }
    }
}

private class JvmLoopbackConnection(private val socket: Socket) : LoopbackConnection {
    private val input: InputStream by lazy { socket.getInputStream() }

    override fun read(): Int = input.read()

    override fun write(bytes: ByteArray) {
        socket.getOutputStream().apply {
            write(bytes)
            flush()
        }
    }

    override fun close() {
        try {
            socket.close()
        } catch (_: IOException) {
            // Already closed.
        }
    }
}
