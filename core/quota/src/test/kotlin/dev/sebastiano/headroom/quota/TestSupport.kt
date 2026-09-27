package dev.sebastiano.headroom.quota

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer

internal val FIXED_NOW: Instant = Instant.parse("2026-04-03T12:00:00Z")
internal val FIXED_CLOCK: Clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC)

/** Reads a JSON fixture from `src/test/resources`. */
internal fun fixture(path: String): String =
    checkNotNull(TestResources::class.java.classLoader.getResource(path)) {
            "Missing fixture $path"
        }
        .readText()

private object TestResources

internal fun MockWebServer.enqueueJson(body: String, code: Int = 200) {
    enqueue(
        MockResponse.Builder()
            .code(code)
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()
    )
}

internal fun MockWebServer.enqueueStatus(code: Int, body: String = "") {
    enqueue(MockResponse(code = code, body = body))
}

/** The server root without a trailing slash, to use as a credentials base URL override. */
internal fun MockWebServer.baseUrl(): String = url("/").toString().trimEnd('/')

/** An HTTP client that answers every request the same way and records what it was sent. */
internal class FakeQuotaHttpClient(private val respond: (QuotaHttpRequest) -> QuotaHttpResponse) :
    QuotaHttpClient {
    val requests = mutableListOf<QuotaHttpRequest>()

    override suspend fun execute(request: QuotaHttpRequest): QuotaHttpResponse {
        requests += request
        return respond(request)
    }
}
