package dev.sebastiano.headroom.auth

import java.net.InetAddress
import java.net.ServerSocket
import java.time.Duration
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class LoopbackServerTest {
    private val client = OkHttpClient.Builder().followRedirects(false).build()
    private val servers = mutableListOf<LoopbackServer>()
    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()

    @AfterTest
    fun tearDown() {
        servers.forEach { it.close() }
        io.close()
    }

    private fun start(
        config: LoopbackConfig = LoopbackConfig(),
        state: String = STATE,
    ): LoopbackServer =
        LoopbackServer.start(
                config,
                expectedState = state,
                returnUrl = RETURN_URL,
                ioDispatcher = io,
            )
            .also { servers += it }

    private data class Reply(
        val status: Int,
        val body: String,
        val headers: Map<String, String?> = emptyMap(),
    )

    private suspend fun get(
        port: Int,
        target: String,
        method: String = "GET",
        origin: String? = null,
    ) =
        withContext(io) {
            val request =
                Request.Builder()
                    .url("http://127.0.0.1:$port$target")
                    .method(method, null)
                    .apply { if (origin != null) header("Origin", origin) }
                    .build()
            client.newCall(request).execute().use { response ->
                Reply(
                    response.code,
                    response.body.string(),
                    mapOf(
                        "allow-origin" to response.header("Access-Control-Allow-Origin"),
                        "content-type" to response.header("Content-Type"),
                    ),
                )
            }
        }

    @Test
    fun `binds to the IPv4 loopback on a port the OS picks`() {
        val server = start()
        assertTrue(server.port > 0)
        assertEquals("http://localhost:${server.port}/callback", server.redirectUri("localhost"))
    }

    @Test
    fun `a valid callback holds the browser until the caller answers`() = runTest {
        val server = start()
        val browser = async(io) { get(server.port, "/callback?code=abc&state=$STATE") }

        val callback = server.awaitCallback()
        assertEquals("abc", callback.code)
        delay(100)
        assertFalse(browser.isCompleted)

        callback.respond(CallbackPage.success(RETURN_URL))
        val reply = browser.await()
        assertEquals(200, reply.status)
        assertEquals("text/html; charset=utf-8", reply.headers["content-type"])
        assertContains(reply.body, """<a href="$RETURN_URL">Return to Headroom</a>""")
    }

    @Test
    fun `other paths get 404 and the server keeps waiting`() = runTest {
        val server = start()
        val favicon = async(io) { get(server.port, "/favicon.ico") }
        val callback = async { server.awaitCallback() }

        assertEquals(404, favicon.await().status)
        assertFalse(callback.isCompleted)

        val browser = async(io) { get(server.port, "/callback?code=c&state=$STATE") }
        callback.await().respond(CallbackPage.success(RETURN_URL))
        assertEquals(200, browser.await().status)
    }

    @Test
    fun `a callback without a code is rejected`() = runTest {
        val server = start()
        val browser = async(io) { get(server.port, "/callback?state=$STATE") }

        val error = assertFailsWith<AuthException.SignInFailed> { server.awaitCallback() }

        assertEquals("Authorization code not found", error.message)
        val reply = browser.await()
        assertEquals(400, reply.status)
        assertContains(reply.body, "Authorization code not found")
        assertContains(reply.body, RETURN_URL)
    }

    @Test
    fun `a callback with another state is rejected`() = runTest {
        val server = start()
        val browser = async(io) { get(server.port, "/callback?code=abc&state=forged") }

        val error = assertFailsWith<AuthException.SignInFailed> { server.awaitCallback() }

        assertEquals("Invalid state parameter", error.message)
        assertEquals(400, browser.await().status)
    }

    @Test
    fun `an error from the provider is reported as a failed sign-in`() = runTest {
        val server = start()
        val browser = async(io) { get(server.port, "/callback?error=access_denied&state=$STATE") }

        val error = assertFailsWith<AuthException.SignInFailed> { server.awaitCallback() }

        assertContains(error.message.orEmpty(), "access_denied")
        val reply = browser.await()
        assertEquals(400, reply.status)
        assertContains(reply.body, "Sign-in was canceled or failed")
    }

    @Test
    fun `waiting too long times out and frees the port`() = runTest {
        val server = start(LoopbackConfig(timeout = Duration.ofMillis(300)))
        val port = server.port

        assertFailsWith<AuthException.TimedOut> { server.awaitCallback() }

        ServerSocket(port, 1, InetAddress.getByName("127.0.0.1")).close()
    }

    @Test
    fun `close frees the port`() {
        val server = start()
        server.close()
        ServerSocket(server.port, 1, InetAddress.getByName("127.0.0.1")).close()
    }

    @Test
    fun `a fixed port range skips ports that are taken`() {
        val taken = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        taken.use {
            val first = it.localPort
            val server = start(LoopbackConfig(ports = first..(first + 5)))
            assertNotEquals(first, server.port)
            assertTrue(server.port in first..(first + 5))
        }
    }

    @Test
    fun `no free port in the range is a clear error`() {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use {
            assertFailsWith<AuthException.SignInFailed> {
                start(LoopbackConfig(ports = it.localPort..it.localPort))
            }
        }
    }

    @Test
    fun `allowed origins get CORS preflight answers`() = runTest {
        val server = start(LoopbackConfig(allowedOrigins = setOf("https://auth.example.com")))
        val waiting = async { server.awaitCallback() }

        val preflight =
            get(server.port, "/callback", method = "OPTIONS", origin = "https://auth.example.com")
        assertEquals(204, preflight.status)
        assertEquals("https://auth.example.com", preflight.headers["allow-origin"])

        val stranger =
            get(server.port, "/callback", method = "OPTIONS", origin = "https://evil.example.com")
        assertEquals(204, stranger.status)
        assertEquals(null, stranger.headers["allow-origin"])
        assertFalse(waiting.isCompleted)
        waiting.cancel()
    }

    @Test
    fun `a custom path is honoured`() = runTest {
        val server = start(LoopbackConfig(path = "/"))
        val browser = async(io) { get(server.port, "/?code=root&state=$STATE") }

        server.awaitCallback().respond(CallbackPage.success(RETURN_URL))

        assertEquals(200, browser.await().status)
    }

    @Test
    fun `pages escape their text`() {
        val page = CallbackPage.failure("<b>bad</b> & worse", "headroom://x?a=1&b=2")
        assertContains(page.html, "&lt;b&gt;bad&lt;/b&gt; &amp; worse")
        assertContains(page.html, """href="headroom://x?a=1&amp;b=2"""")
        assertEquals(400, page.status)
    }

    private companion object {
        const val STATE = "state-123"
        const val RETURN_URL = "headroom://signed-in"
    }
}
