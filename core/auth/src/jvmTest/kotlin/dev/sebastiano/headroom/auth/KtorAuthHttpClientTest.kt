package dev.sebastiano.headroom.auth

import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
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

    @Test
    fun `a call that keeps trickling in still ends at the timeout`() = runTest {
        // Data keeps arriving, so no socket timeout fires: only a timeout for the whole call can.
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("0123456789")
                .throttleBody(1, 300, TimeUnit.MILLISECONDS)
                .build()
        )
        val client = KtorAuthHttpClient(timeout = 1.seconds)

        try {
            assertFailsWith<AuthException.Network> {
                client.execute(
                    AuthHttpRequest(method = "GET", url = server.url("/slow").toString())
                )
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun `clients with the default timeout share one HttpClient`() {
        assertSame(KtorAuthHttpClient().client, KtorAuthHttpClient().client)
    }

    @Test
    fun `closing a custom-timeout wrapper releases its client`() = runTest {
        val owned = KtorAuthHttpClient(timeout = 5.seconds)
        val job = checkNotNull(owned.client.coroutineContext[Job])

        owned.close()
        owned.close()
        job.join()

        assertTrue(job.isCompleted)
    }

    @Test
    fun `closing default and injected wrappers leaves the borrowed client running`() {
        val shared = KtorAuthHttpClient()
        shared.close()
        KtorAuthHttpClient(shared.client).close()

        assertTrue(checkNotNull(shared.client.coroutineContext[Job]).isActive)
    }

    @Test
    fun `POST redirects retain the body only for 307 and 308`() = runTest {
        for (status in listOf(300, 301, 302, 303, 307, 308)) {
            server.enqueue(
                MockResponse.Builder().code(status).addHeader("Location", "/moved").build()
            )
            server.enqueue(MockResponse(code = 200, body = "done"))

            val response =
                client.execute(
                    AuthHttpRequest(
                        method = "POST",
                        url = server.url("/token").toString(),
                        body = "grant_type=refresh_token",
                        contentType = "application/x-www-form-urlencoded",
                    )
                )

            assertEquals(200, response.status)
            server.takeRequest()
            val followed = server.takeRequest()
            val keepsBody = status == 307 || status == 308
            assertEquals(if (keepsBody) "POST" else "GET", followed.method)
            assertEquals("/moved", followed.target)
            if (keepsBody) {
                assertEquals("grant_type=refresh_token", followed.body?.utf8())
                assertEquals("application/x-www-form-urlencoded", followed.headers["Content-Type"])
            } else {
                assertEquals(0L, followed.bodySize)
                assertNull(followed.headers["Content-Type"])
            }
        }
    }

    @Test
    fun `a POST redirected to a different port strips Authorization`() = runTest {
        MockWebServer().use { destination ->
            destination.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(307)
                    .addHeader("Location", destination.url("/token"))
                    .build()
            )
            destination.enqueue(MockResponse(code = 200, body = "done"))

            val response =
                client.execute(
                    AuthHttpRequest(
                        method = "POST",
                        url = server.url("/token").toString(),
                        headers = mapOf("Authorization" to "Bearer fixture-token"),
                        body = "{}",
                        contentType = "application/json",
                    )
                )

            assertEquals(200, response.status)
            assertNull(destination.takeRequest().headers["Authorization"])
        }
    }

    @Test
    fun `redirects retain the original request execution context`() = runTest {
        val redirected = KtorAuthHttpClient(timeout = 5.seconds)
        try {
            val contexts = mutableListOf<Job>()
            redirected.client.plugin(HttpSend).intercept { request ->
                contexts += request.executionContext
                execute(request)
            }
            server.enqueue(MockResponse.Builder().code(307).addHeader("Location", "/moved").build())
            server.enqueue(MockResponse(code = 200, body = "done"))

            val response =
                redirected.execute(
                    AuthHttpRequest(
                        method = "POST",
                        url = server.url("/token").toString(),
                        body = "{}",
                    )
                )

            assertEquals(200, response.status)
            assertEquals(2, contexts.size)
            assertSame(contexts[0], contexts[1])
        } finally {
            redirected.close()
        }
    }

    @Test
    fun `a client with its own timeout gets its own HttpClient`() {
        assertNotSame(KtorAuthHttpClient().client, KtorAuthHttpClient(timeout = 5.seconds).client)
    }
}
