package dev.sebastiano.headroom.quota

import java.io.IOException
import java.time.Duration

/** One HTTP request made by a fetcher. */
public data class QuotaHttpRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    /** Overrides the client's default call timeout for this request. */
    val timeout: Duration? = null,
)

/** The response to a [QuotaHttpRequest]. Header names are lower case. */
public data class QuotaHttpResponse(
    val statusCode: Int,
    val headers: Map<String, String> = emptyMap(),
    val body: String = "",
)

/** The transport the fetchers use. Tests can swap it; production uses [OkHttpQuotaHttpClient]. */
public interface QuotaHttpClient {
    /**
     * Sends [request] and returns the response, whatever its status code.
     *
     * @throws IOException when the request cannot be sent or the response cannot be read.
     */
    public suspend fun execute(request: QuotaHttpRequest): QuotaHttpResponse
}
