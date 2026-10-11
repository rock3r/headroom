package dev.sebastiano.headroom.quota

import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class KtorQuotaHttpClientTest {
    private val server = MockWebServer()
    private val client = KtorQuotaHttpClient()

    @BeforeEach
    fun setUp() {
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    @Test
    fun `sends a GET request with the given headers`() = runTest {
        server.enqueue(MockResponse(code = 200, body = """{"ok":true}"""))

        client.execute(
            QuotaHttpRequest(
                url = server.url("/v1/usage").toString(),
                headers = mapOf("Authorization" to "Bearer test-token", "Accept" to "text/plain"),
            )
        )

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/v1/usage", recorded.target)
        assertEquals("Bearer test-token", recorded.headers["Authorization"])
        assertEquals("text/plain", recorded.headers["Accept"])
    }

    @Test
    fun `sends a binary body and reads the binary response`() = runTest {
        val answer = byteArrayOf(0, 0, 0, 0, 2, 0x52, 0x00)
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/grpc-web+proto")
                .body(okio.Buffer().write(answer))
                .build()
        )
        val sent = byteArrayOf(0, 0, 0, 0, 0)

        val response =
            client.execute(
                QuotaHttpRequest(
                    url = server.url("/svc/Method").toString(),
                    method = "POST",
                    headers = mapOf("Content-Type" to "application/grpc-web+proto"),
                    binaryBody = BinaryBody(sent),
                )
            )

        val recorded = server.takeRequest()
        assertEquals("application/grpc-web+proto", recorded.headers["Content-Type"])
        assertEquals(sent.toList(), recorded.body?.toByteArray()?.toList())
        assertEquals(answer.toList(), response.binaryBody?.bytes?.toList())
    }

    @Test
    fun `returns the status code, headers and body`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(429)
                .addHeader("Retry-After", "30")
                .body("""{"error":"slow down"}""")
                .build()
        )

        val response = client.execute(QuotaHttpRequest(url = server.url("/").toString()))

        assertEquals(429, response.statusCode)
        assertEquals("30", response.headers["retry-after"])
        assertEquals("""{"error":"slow down"}""", response.body)
    }

    @Test
    fun `sends a Content-Type header on a request without a body`() = runTest {
        server.enqueue(MockResponse(code = 200, body = "{}"))

        client.execute(
            QuotaHttpRequest(
                url = server.url("/profile").toString(),
                headers = mapOf("Content-Type" to "application/json"),
            )
        )

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("application/json", recorded.headers["Content-Type"])
        assertEquals(0L, recorded.bodySize)
    }

    @Test
    fun `sends a POST body`() = runTest {
        server.enqueue(MockResponse(code = 200, body = "{}"))

        client.execute(
            QuotaHttpRequest(
                url = server.url("/post").toString(),
                method = "POST",
                headers = mapOf("Content-Type" to "application/json"),
                body = """{"hello":"world"}""",
            )
        )

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("""{"hello":"world"}""", recorded.body?.utf8())
    }

    @Test
    fun `throws IOException when the server cannot be reached`() = runTest {
        val url = server.url("/").toString()
        server.close()

        assertFailsWith<IOException> { client.execute(QuotaHttpRequest(url = url)) }
    }

    @Test
    fun `throws IOException for a malformed URL`() = runTest {
        assertFailsWith<IOException> { client.execute(QuotaHttpRequest(url = "not a url")) }
    }

    @Test
    fun `waits in real time even under a test dispatcher`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("{}")
                .headersDelay(500, TimeUnit.MILLISECONDS)
                .build()
        )

        val response = client.execute(QuotaHttpRequest(url = server.url("/").toString()))

        assertEquals(200, response.statusCode)
    }

    @Test
    fun `applies the per-request timeout`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(200).body("{}").headersDelay(2, TimeUnit.SECONDS).build()
        )

        assertFailsWith<IOException> {
            client.execute(
                QuotaHttpRequest(
                    url = server.url("/").toString(),
                    timeout = 200.milliseconds,
                )
            )
        }
    }

    @Test
    fun `a POST redirected with 302 follows as a GET without the body, as OkHttp did`() = runTest {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/moved").build())
        server.enqueue(MockResponse(code = 200, body = "done"))

        val response =
            client.execute(
                QuotaHttpRequest(
                    url = server.url("/redeem").toString(),
                    method = "POST",
                    headers = mapOf("Content-Type" to "application/json"),
                    body = "{}",
                )
            )

        assertEquals(200, response.statusCode)
        assertEquals("done", response.body)
        server.takeRequest()
        val followed = server.takeRequest()
        assertEquals("GET", followed.method)
        assertEquals("/moved", followed.target)
        assertEquals(0L, followed.bodySize)
        assertNull(followed.headers["Content-Type"])
    }

    @Test
    fun `a POST redirected with 307 is sent again with its body`() = runTest {
        server.enqueue(MockResponse.Builder().code(307).addHeader("Location", "/moved").build())
        server.enqueue(MockResponse(code = 200, body = "done"))

        val response =
            client.execute(
                QuotaHttpRequest(
                    url = server.url("/redeem").toString(),
                    method = "POST",
                    headers = mapOf("Content-Type" to "application/json"),
                    body = "{\"a\":1}",
                )
            )

        assertEquals(200, response.statusCode)
        server.takeRequest()
        val followed = server.takeRequest()
        assertEquals("POST", followed.method)
        assertEquals("/moved", followed.target)
        assertEquals("{\"a\":1}", followed.body?.utf8())
        assertEquals("application/json", followed.headers["Content-Type"]?.substringBefore(';'))
    }

    @Test
    fun `clients made without an HttpClient share one`() {
        assertSame(KtorQuotaHttpClient().httpClient, KtorQuotaHttpClient().httpClient)
    }

    @Test
    fun `redirects to a different port do not forward Authorization`() = runTest {
        MockWebServer().use { destination ->
            destination.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(307)
                    .addHeader("Location", destination.url("/moved"))
                    .build()
            )
            destination.enqueue(MockResponse(code = 200, body = "done"))

            val response =
                client.execute(
                    QuotaHttpRequest(
                        url = server.url("/redeem").toString(),
                        method = "POST",
                        headers = mapOf("Authorization" to "Bearer fixture-token"),
                        body = "{}",
                    )
                )

            assertEquals(200, response.statusCode)
            assertEquals("Bearer fixture-token", server.takeRequest().headers["Authorization"])
            assertNull(destination.takeRequest().headers["Authorization"])
        }
    }

    @Test
    fun `redirects on the same authority retain Authorization`() = runTest {
        server.enqueue(MockResponse.Builder().code(307).addHeader("Location", "/moved").build())
        server.enqueue(MockResponse(code = 200, body = "done"))

        client.execute(
            QuotaHttpRequest(
                url = server.url("/redeem").toString(),
                method = "POST",
                headers = mapOf("Authorization" to "Bearer fixture-token"),
                body = "{}",
            )
        )

        server.takeRequest()
        assertEquals("Bearer fixture-token", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `GET HEAD and POST requests follow HTTP 300 redirects`() = runTest {
        for (method in listOf("GET", "HEAD", "POST")) {
            server.enqueue(MockResponse.Builder().code(300).addHeader("Location", "/moved").build())
            server.enqueue(MockResponse(code = 200, body = if (method == "HEAD") "" else "done"))

            val response =
                client.execute(
                    QuotaHttpRequest(
                        url = server.url("/original").toString(),
                        method = method,
                        body = if (method == "POST") "{}" else null,
                    )
                )

            assertEquals(200, response.statusCode)
            server.takeRequest()
            val followed = server.takeRequest()
            assertEquals(if (method == "HEAD") "HEAD" else "GET", followed.method)
            assertEquals("/moved", followed.target)
            assertEquals(0L, followed.bodySize)
        }
    }

    @Test
    fun `a redirected GET retains its Content-Type header`() = runTest {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/moved").build())
        server.enqueue(MockResponse(code = 200, body = "{}"))

        client.execute(
            QuotaHttpRequest(
                url = server.url("/profile").toString(),
                headers = mapOf("Content-Type" to "application/json"),
            )
        )

        server.takeRequest()
        val followed = server.takeRequest()
        assertEquals("GET", followed.method)
        assertEquals("application/json", followed.headers["Content-Type"])
        assertEquals(0L, followed.bodySize)
    }

    @Test
    fun `redirects retain the original request execution context`() = runTest {
        val httpClient = KtorQuotaHttpClient.defaultHttpClient()
        try {
            val contexts = mutableListOf<Job>()
            httpClient.plugin(HttpSend).intercept { request ->
                contexts += request.executionContext
                execute(request)
            }
            server.enqueue(MockResponse.Builder().code(307).addHeader("Location", "/moved").build())
            server.enqueue(MockResponse(code = 200, body = "done"))

            KtorQuotaHttpClient(httpClient)
                .execute(
                    QuotaHttpRequest(
                        url = server.url("/redeem").toString(),
                        method = "POST",
                        body = "{}",
                    )
                )

            assertEquals(2, contexts.size)
            assertSame(contexts[0], contexts[1])
        } finally {
            httpClient.close()
        }
    }
}
