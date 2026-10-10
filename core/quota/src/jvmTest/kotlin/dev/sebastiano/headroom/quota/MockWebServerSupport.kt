package dev.sebastiano.headroom.quota

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest

/** Reads a JSON fixture from `src/jvmTest/resources`. */
internal fun fixture(path: String): String =
    checkNotNull(TestResources::class.java.classLoader.getResource(path)) {
            "Missing fixture $path"
        }
        .readText()

private object TestResources

internal fun MockWebServer.enqueueJson(body: String, code: Int = 200) {
    enqueue(jsonResponse(body, code))
}

internal fun MockWebServer.enqueueStatus(code: Int, body: String = "") {
    enqueue(MockResponse(code = code, body = body))
}

internal fun jsonResponse(body: String, code: Int = 200): MockResponse =
    MockResponse.Builder()
        .code(code)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

/**
 * Answers each request by its target (path and query). Unknown targets get a 404, so a request the
 * test did not expect shows up as a failure.
 */
internal fun MockWebServer.respondByTarget(routes: Map<String, MockResponse>) {
    dispatcher =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                routes[request.target] ?: MockResponse(code = 404)
        }
}

/** The server root without a trailing slash, to use as a credentials base URL override. */
internal fun MockWebServer.baseUrl(): String = url("/").toString().trimEnd('/')
