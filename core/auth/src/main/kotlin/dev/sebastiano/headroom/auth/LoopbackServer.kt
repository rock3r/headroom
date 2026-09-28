package dev.sebastiano.headroom.auth

import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where and how long a [LoopbackServer] listens. */
public data class LoopbackConfig(
    /** The only path that counts as the OAuth callback. Other paths get 404. */
    val path: String = "/callback",
    /** Ports to try in order. `0..0` lets the OS pick a free port. */
    val ports: IntRange = 0..0,
    /** Also listen on `::1` with the same port, when IPv6 loopback is available. */
    val bindIpv6: Boolean = false,
    /** Web origins allowed to call the callback with `fetch` (CORS preflight). */
    val allowedOrigins: Set<String> = emptySet(),
    /** How long [LoopbackServer.awaitCallback] waits for the browser. */
    val timeout: Duration = DEFAULT_TIMEOUT,
) {
    private companion object {
        val DEFAULT_TIMEOUT: Duration = Duration.ofMinutes(10)
    }
}

/**
 * A minimal HTTP/1.1 listener on the loopback interface that receives one OAuth redirect. It uses
 * plain [ServerSocket]s, which work the same on Android and on the JVM.
 *
 * The listener checks the path and the `state`, answers invalid callbacks itself, and hands a valid
 * one to the caller with the browser connection still open. The caller finishes the token exchange
 * and then answers the browser with [LoopbackCallback.respond].
 */
public class LoopbackServer
private constructor(
    private val sockets: List<ServerSocket>,
    private val config: LoopbackConfig,
    private val expectedState: String,
    private val returnUrl: String?,
    private val ioDispatcher: CoroutineDispatcher,
) : AutoCloseable {

    /** The port the listener is bound to. */
    public val port: Int = sockets.first().localPort

    /** The redirect URI for [host], for example `http://localhost:1234/callback`. */
    public fun redirectUri(host: String = "localhost"): String = "http://$host:$port${config.path}"

    /**
     * Waits for the browser's callback. Returns only a callback with a code and the expected state.
     *
     * @throws AuthException.SignInFailed when the callback carries an error, no code, or another
     *   state. The browser has already been answered.
     * @throws AuthException.TimedOut when nothing arrives within [LoopbackConfig.timeout]. The
     *   listener is closed.
     */
    public suspend fun awaitCallback(): LoopbackCallback =
        withContext(ioDispatcher) {
            val outcome = CompletableDeferred<LoopbackCallback>()
            val reading = ConcurrentHashMap.newKeySet<Socket>()
            val deadline = System.nanoTime() + config.timeout.toNanos()
            val acceptor = launch { acceptUntil(deadline, outcome, reading) }
            try {
                outcome.await()
            } finally {
                acceptor.cancel()
                // Unblocks connections that never sent a request, such as browser preconnects.
                reading.forEach { it.closeQuietly() }
            }
        }

    override fun close() {
        sockets.forEach { runCatching { it.close() } }
    }

    /** Accepts connections and serves each one in its own coroutine, so none can block another. */
    private fun CoroutineScope.acceptUntil(
        deadline: Long,
        outcome: CompletableDeferred<LoopbackCallback>,
        reading: MutableSet<Socket>,
    ) {
        while (isActive && !outcome.isCompleted) {
            if (System.nanoTime() >= deadline) {
                close()
                outcome.completeExceptionally(
                    AuthException.TimedOut("Sign-in was not finished in time")
                )
                return
            }
            for (socket in sockets) {
                val client =
                    try {
                        acceptOrNull(socket)
                    } catch (e: AuthException) {
                        outcome.completeExceptionally(e)
                        return
                    } ?: continue
                reading += client
                launch { serve(client, reading, outcome) }
            }
        }
    }

    private fun serve(
        client: Socket,
        reading: MutableSet<Socket>,
        outcome: CompletableDeferred<LoopbackCallback>,
    ) {
        val request = readRequest(client)
        reading -= client
        if (request == null) {
            client.closeQuietly()
            return
        }
        try {
            val callback = handle(client, request) ?: return
            if (!outcome.complete(callback)) {
                callback.respond(CallbackPage.failure("Sign-in has already finished", returnUrl))
            }
        } catch (e: AuthException) {
            outcome.completeExceptionally(e)
        }
    }

    private fun readRequest(client: Socket): HttpRequestHead? =
        try {
            client.soTimeout = READ_TIMEOUT_MILLIS
            HttpRequestHead.read(client.getInputStream())
        } catch (_: IOException) {
            null
        }

    private fun acceptOrNull(socket: ServerSocket): Socket? =
        try {
            socket.accept()
        } catch (_: SocketTimeoutException) {
            null
        } catch (e: IOException) {
            if (socket.isClosed) throw AuthException.SignInFailed("Sign-in was canceled")
            throw AuthException.Network("The sign-in listener failed", e)
        }

    /** Answers everything that is not a valid callback, and returns the valid one. */
    private fun handle(client: Socket, request: HttpRequestHead): LoopbackCallback? {
        val corsOrigin = request.headers["origin"]?.takeIf { it in config.allowedOrigins }
        return when {
            request.path != config.path -> {
                client.answer(NOT_FOUND, "text/plain; charset=utf-8", "Not found", corsOrigin)
                null
            }
            request.method == "OPTIONS" -> {
                client.answer(NO_CONTENT, null, "", corsOrigin)
                null
            }
            else -> validate(client, request, corsOrigin)
        }
    }

    private fun validate(
        client: Socket,
        request: HttpRequestHead,
        corsOrigin: String?,
    ): LoopbackCallback {
        val params = request.query
        val error = params["error"]
        val code = params["code"]
        val failure =
            when {
                !error.isNullOrBlank() -> FailureReason(ERROR_MESSAGE, "$ERROR_MESSAGE: $error")
                code.isNullOrBlank() -> FailureReason(MISSING_CODE, MISSING_CODE)
                params["state"] != expectedState -> FailureReason(BAD_STATE, BAD_STATE)
                else -> null
            }
        if (failure != null) {
            val page = CallbackPage.failure(failure.page, returnUrl)
            client.answer(page.status, HTML, page.html, corsOrigin)
            throw AuthException.SignInFailed(failure.exception)
        }
        return LoopbackCallback(checkNotNull(code), params, client, corsOrigin)
    }

    private class FailureReason(val page: String, val exception: String)

    public companion object {
        private const val ACCEPT_POLL_MILLIS = 100
        private const val READ_TIMEOUT_MILLIS = 10_000
        private const val NOT_FOUND = 404
        private const val NO_CONTENT = 204
        private const val MAX_OS_PORT_ATTEMPTS = 5
        internal const val HTML = "text/html; charset=utf-8"
        private const val ERROR_MESSAGE = "Sign-in was canceled or failed"
        private const val MISSING_CODE = "Authorization code not found"
        private const val BAD_STATE = "Invalid state parameter"

        private val IPV4_LOOPBACK: InetAddress = InetAddress.getByName("127.0.0.1")
        private val IPV6_LOOPBACK: InetAddress = InetAddress.getByName("::1")

        /**
         * Binds a listener to `127.0.0.1` (and `::1` when [LoopbackConfig.bindIpv6] is set and
         * available) on the first free port of [LoopbackConfig.ports].
         *
         * @param expectedState the OAuth `state` sent in the authorize URL.
         * @param returnUrl a custom scheme URL, such as `headroom://signed-in`, linked from the
         *   pages the listener shows.
         * @throws AuthException.SignInFailed when no port can be bound.
         */
        public fun start(
            config: LoopbackConfig,
            expectedState: String,
            returnUrl: String?,
            ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        ): LoopbackServer {
            val withIpv6 = config.bindIpv6 && ipv6LoopbackAvailable()
            val candidates =
                if (config.ports == 0..0) List(MAX_OS_PORT_ATTEMPTS) { 0 }
                else config.ports.toList()
            val sockets =
                candidates.firstNotNullOfOrNull { port -> bindAll(port, withIpv6) }
                    ?: throw AuthException.SignInFailed(
                        "No free local port for the sign-in listener"
                    )
            return LoopbackServer(sockets, config, expectedState, returnUrl, ioDispatcher)
        }

        private fun bindAll(port: Int, withIpv6: Boolean): List<ServerSocket>? {
            val ipv4 = bind(IPV4_LOOPBACK, port) ?: return null
            if (!withIpv6) return listOf(ipv4)
            val ipv6 = bind(IPV6_LOOPBACK, ipv4.localPort)
            if (ipv6 == null) {
                ipv4.close()
                return null
            }
            return listOf(ipv4, ipv6)
        }

        private fun bind(address: InetAddress, port: Int): ServerSocket? =
            try {
                ServerSocket(port, 0, address).apply { soTimeout = ACCEPT_POLL_MILLIS }
            } catch (_: IOException) {
                null
            }

        private fun ipv6LoopbackAvailable(): Boolean =
            bind(IPV6_LOOPBACK, 0)?.let {
                it.close()
                true
            } ?: false
    }
}

/**
 * A valid OAuth callback. The browser connection stays open until [respond] is called, so the page
 * can tell the user whether the token exchange worked.
 */
public class LoopbackCallback
internal constructor(
    public val code: String,
    public val params: Map<String, String>,
    private val client: Socket,
    private val corsOrigin: String?,
) : AutoCloseable {
    /**
     * Sends [page] to the browser and closes the connection. Blocking; call it off the UI thread.
     */
    public fun respond(page: CallbackPage) {
        client.answer(page.status, LoopbackServer.HTML, page.html, corsOrigin, page.location)
    }

    /**
     * Drops the browser connection without an answer. The owner of a callback must call this (or
     * [respond]) on every path, including cancellation. Calling it again does nothing.
     */
    override fun close() {
        client.closeQuietly()
    }
}

internal class HttpRequestHead(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val headers: Map<String, String>,
) {
    companion object {
        private const val MAX_HEAD_BYTES = 16_384
        private const val CR = '\r'.code
        private const val LF = '\n'.code
        private const val REQUEST_LINE_PARTS = 3

        /** Reads the request line and headers. Returns null when they are not valid HTTP. */
        fun read(input: InputStream): HttpRequestHead? {
            val lines = readHeadLines(input) ?: return null
            val parts = lines.firstOrNull()?.split(' ').orEmpty()
            val (method, target, version) =
                parts.takeIf { it.size == REQUEST_LINE_PARTS } ?: return null
            if (!version.startsWith("HTTP/")) return null
            val headers =
                lines.drop(1).mapNotNull { line ->
                    val colon = line.indexOf(':')
                    if (colon <= 0) null
                    else line.take(colon).trim().lowercase() to line.substring(colon + 1).trim()
                }
            return HttpRequestHead(
                method = method,
                path = target.substringBefore('?'),
                query = parseQuery(target.substringAfter('?', "")),
                headers = headers.toMap(),
            )
        }

        private fun readHeadLines(input: InputStream): List<String>? {
            val lines = mutableListOf<String>()
            val line = StringBuilder()
            var total = 0
            while (true) {
                val byte = input.read()
                if (byte == -1 || ++total > MAX_HEAD_BYTES) return null
                when (byte) {
                    CR -> Unit
                    LF -> {
                        if (line.isEmpty()) return lines
                        lines += line.toString()
                        line.clear()
                    }
                    else -> line.append(byte.toChar())
                }
            }
        }
    }
}

internal fun parseQuery(query: String): Map<String, String> =
    query
        .split('&')
        .filter { it.isNotEmpty() }
        .associate { pair ->
            val key = pair.substringBefore('=')
            val value = pair.substringAfter('=', "")
            decode(key) to decode(value)
        }

private fun decode(value: String): String =
    try {
        URLDecoder.decode(value, Charsets.UTF_8)
    } catch (_: IllegalArgumentException) {
        value
    }

private fun Socket.answer(
    status: Int,
    contentType: String?,
    body: String,
    corsOrigin: String?,
    location: String? = null,
) {
    try {
        val bytes = body.toByteArray()
        val head = buildString {
            append("HTTP/1.1 $status ${reasonPhrase(status)}\r\n")
            if (contentType != null) append("Content-Type: $contentType\r\n")
            if (location != null) append("Location: $location\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n")
            if (corsOrigin != null) {
                append("Access-Control-Allow-Origin: $corsOrigin\r\n")
                append("Access-Control-Allow-Methods: GET, OPTIONS\r\n")
                append("Access-Control-Allow-Headers: Content-Type\r\n")
                append("Access-Control-Allow-Private-Network: true\r\n")
                append("Vary: Origin\r\n")
            }
            append("\r\n")
        }
        getOutputStream().apply {
            write(head.toByteArray())
            write(bytes)
            flush()
        }
    } catch (_: IOException) {
        // The browser went away; there is nobody left to answer.
    } finally {
        closeQuietly()
    }
}

private val REASON_PHRASES =
    mapOf(
        200 to "OK",
        204 to "No Content",
        302 to "Found",
        400 to "Bad Request",
        404 to "Not Found",
    )

private fun reasonPhrase(status: Int): String = REASON_PHRASES[status] ?: "Error"

private fun Socket.closeQuietly() {
    try {
        close()
    } catch (_: IOException) {
        // Already closed.
    }
}
