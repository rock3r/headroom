package dev.sebastiano.headroom.auth

import kotlinx.io.IOException

/** The loopback address a [LoopbackListener] binds to. */
internal enum class LoopbackAddress {
    /** `127.0.0.1`. */
    Ipv4,

    /** `::1`, IPv6 only. */
    Ipv6,
}

/** A socket listening on a loopback address, for [LoopbackServer]. Blocking: use it off the UI. */
internal interface LoopbackListener {
    /** The port it is bound to. */
    val port: Int

    val isClosed: Boolean

    /**
     * The next connection, or null when none arrives within the accept poll time.
     *
     * @throws IOException when the listener fails, or was closed.
     */
    fun accept(): LoopbackConnection?

    /** Stops listening. Calling it again does nothing. */
    fun close()
}

/** A browser connection accepted by a [LoopbackListener]. Blocking: use it off the UI. */
internal interface LoopbackConnection {
    /**
     * The next byte of the request, or -1 at its end.
     *
     * @throws IOException when reading fails, or nothing arrives within the read timeout.
     */
    fun read(): Int

    /** @throws IOException when the browser went away. */
    fun write(bytes: ByteArray)

    /** Closes the connection, and wakes a [read] that is waiting. Calling it again does nothing. */
    fun close()
}

/**
 * Binds a listener to [address] on [port], where 0 lets the system pick one.
 *
 * @param acceptPollMillis how long [LoopbackListener.accept] waits before it returns null.
 * @param readTimeoutMillis how long [LoopbackConnection.read] waits for a byte.
 * @return null when the port is taken or the address is not available.
 */
internal expect fun bindLoopback(
    address: LoopbackAddress,
    port: Int,
    acceptPollMillis: Int,
    readTimeoutMillis: Int,
): LoopbackListener?
