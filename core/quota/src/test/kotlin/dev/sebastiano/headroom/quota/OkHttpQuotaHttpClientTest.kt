package dev.sebastiano.headroom.quota

import java.io.IOException
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OkHttpQuotaHttpClientTest {
    private val server = MockWebServer()
    private val client = OkHttpQuotaHttpClient()

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
    fun `applies the per-request timeout`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(200).body("{}").headersDelay(2, TimeUnit.SECONDS).build()
        )

        assertFailsWith<IOException> {
            client.execute(
                QuotaHttpRequest(
                    url = server.url("/").toString(),
                    timeout = Duration.ofMillis(200),
                )
            )
        }
    }
}
