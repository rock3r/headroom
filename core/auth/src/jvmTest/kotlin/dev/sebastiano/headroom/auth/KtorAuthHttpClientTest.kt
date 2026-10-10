package dev.sebastiano.headroom.auth

import java.net.ServerSocket
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer

class KtorAuthHttpClientTest {
    private val server = MockWebServer()
    private val client = KtorAuthHttpClient()

    @BeforeTest
    fun setUp() {
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.close()
    }

    @Test
    fun `posts the body with its content type and headers`() = runTest {
        server.enqueue(MockResponse(code = 201, body = """{"ok":true}"""))

        val response =
            client.execute(
                AuthHttpRequest(
                    method = "POST",
                    url = server.url("/token").toString(),
                    headers = mapOf("Accept" to "application/json"),
                    body = "a=1&b=2",
                    contentType = "application/x-www-form-urlencoded",
                )
            )

        assertEquals(201, response.status)
        assertEquals("""{"ok":true}""", response.body)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/token", recorded.url.encodedPath)
        assertEquals("a=1&b=2", recorded.body?.utf8())
        assertEquals("application/json", recorded.headers["Accept"])
        assertEquals("application/x-www-form-urlencoded", recorded.headers["Content-Type"])
    }

    @Test
    fun `gets without a body and returns error statuses as responses`() = runTest {
        server.enqueue(MockResponse(code = 403, body = "nope"))

        val response =
            client.execute(AuthHttpRequest(method = "GET", url = server.url("/x").toString()))

        assertEquals(403, response.status)
        assertEquals("nope", response.body)
        assertEquals("GET", server.takeRequest().method)
    }

    @Test
    fun `transport failures become network errors`() = runTest {
        val closedPort = ServerSocket(0).use { it.localPort }

        assertFailsWith<AuthException.Network> {
            client.execute(AuthHttpRequest(method = "GET", url = "http://127.0.0.1:$closedPort/"))
        }
    }
}
