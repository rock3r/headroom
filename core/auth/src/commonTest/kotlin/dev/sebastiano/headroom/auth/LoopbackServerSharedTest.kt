package dev.sebastiano.headroom.auth

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest

/**
 * The loopback listener on every platform, over real sockets and the platform's HTTP stack: OkHttp
 * on the JVM, URLSession on iOS. `LoopbackServerTest` covers the JVM-only cases.
 */
class LoopbackServerSharedTest {
    private val browser =
        HttpClient(platformHttpEngine) {
            followRedirects = false
            expectSuccess = false
        }
    private val servers = mutableListOf<LoopbackServer>()

    @AfterTest
    fun tearDown() {
        servers.forEach { it.close() }
        browser.close()
    }

    private fun start(config: LoopbackConfig = LoopbackConfig()): LoopbackServer =
        LoopbackServer.start(config, expectedState = STATE, returnUrl = RETURN_URL).also {
            servers += it
        }

    private suspend fun get(port: Int, target: String): Pair<Int, String> {
        val response = browser.get("http://127.0.0.1:$port$target")
        return response.status.value to response.bodyAsText()
    }

    @Test
    fun `a valid callback holds the browser until the caller answers`() = runTest {
        val server = start()
        val reply = async { get(server.port, "/callback?code=a%2Bb&state=$STATE") }

        val callback = server.awaitCallback()
        assertEquals("a+b", callback.code)
        delay(100.milliseconds)
        assertFalse(reply.isCompleted)

        callback.respond(CallbackPage.success(RETURN_URL))
        val (status, body) = reply.await()
        assertEquals(200, status)
        assertContains(body, """<a href="$RETURN_URL">Return to Headroom</a>""")
    }

    @Test
    fun `other paths get 404 and the server keeps waiting`() = runTest {
        val server = start()
        val callback = async { server.awaitCallback() }

        assertEquals(404, get(server.port, "/favicon.ico").first)
        assertFalse(callback.isCompleted)

        val reply = async { get(server.port, "/callback?code=c&state=$STATE") }
        callback.await().respond(CallbackPage.success(RETURN_URL))
        assertEquals(200, reply.await().first)
    }

    @Test
    fun `a callback with another state is rejected`() = runTest {
        val server = start()
        val reply = async { get(server.port, "/callback?code=abc&state=forged") }

        val error = assertFailsWith<AuthException.SignInFailed> { server.awaitCallback() }

        assertEquals("Invalid state parameter", error.message)
        assertEquals(400, reply.await().first)
    }

    @Test
    fun `waiting too long times out and frees the port`() = runTest {
        val server = start(LoopbackConfig(timeout = 300.milliseconds))

        assertFailsWith<AuthException.TimedOut> { server.awaitCallback() }

        assertPortFree(server.port)
    }

    @Test
    fun `close ends the wait and frees the port`() = runTest {
        val server = start()
        val wait = async { runCatching { server.awaitCallback() } }
        delay(200.milliseconds)

        server.close()

        assertTrue(wait.await().exceptionOrNull() is AuthException.SignInFailed)
        assertPortFree(server.port)
    }

    @Test
    fun `a fixed port range skips ports that are taken`() {
        val taken = assertNotNull(bindLoopback(LoopbackAddress.Ipv4, 0, POLL_MILLIS, POLL_MILLIS))
        try {
            val first = taken.port
            val server = start(LoopbackConfig(ports = first..(first + 5)))
            assertNotEquals(first, server.port)
            assertTrue(server.port in first..(first + 5))
        } finally {
            taken.close()
        }
    }

    @Test
    fun `binds IPv6 on the same port when asked`() = runTest {
        val server = start(LoopbackConfig(bindIpv6 = true))
        val reply = async { get(server.port, "/callback?code=v6&state=$STATE") }

        server.awaitCallback().respond(CallbackPage.success(RETURN_URL))

        assertEquals(200, reply.await().first)
        val ipv6 = bindLoopback(LoopbackAddress.Ipv6, server.port, POLL_MILLIS, POLL_MILLIS)
        assertEquals(null, ipv6, "the IPv6 port should still be taken")
    }

    private fun assertPortFree(port: Int) {
        val again = bindLoopback(LoopbackAddress.Ipv4, port, POLL_MILLIS, POLL_MILLIS)
        assertNotNull(again, "port $port should be free").close()
    }

    private companion object {
        const val STATE = "state-123"
        const val RETURN_URL = "headroom://signed-in"
        const val POLL_MILLIS = 100
    }
}
