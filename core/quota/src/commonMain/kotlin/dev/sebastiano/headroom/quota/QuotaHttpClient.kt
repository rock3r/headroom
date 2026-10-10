package dev.sebastiano.headroom.quota

import kotlin.time.Duration
import kotlinx.io.IOException

/** One HTTP request made by a fetcher. */
public data class QuotaHttpRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    /** Overrides the client's default call timeout for this request. */
    val timeout: Duration? = null,
    /** A binary body, such as a gRPC-web frame. Sent instead of [body] when set. */
    val binaryBody: BinaryBody? = null,
)

/** The response to a [QuotaHttpRequest]. Header names are lower case. */
public data class QuotaHttpResponse(
    val statusCode: Int,
    val headers: Map<String, String> = emptyMap(),
    val body: String = "",
    /** The raw bytes of the body, for binary responses such as gRPC-web. */
    val binaryBody: BinaryBody? = null,
)

/** Bytes that compare by content, so requests and responses can stay data classes. */
public class BinaryBody(public val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is BinaryBody && other.bytes.contentEquals(bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "BinaryBody(${bytes.size} bytes)"
}

/** The transport the fetchers use. Tests can swap it; production uses [KtorQuotaHttpClient]. */
public interface QuotaHttpClient {
    /**
     * Sends [request] and returns the response, whatever its status code.
     *
     * @throws IOException when the request cannot be sent or the response cannot be read.
     */
    public suspend fun execute(request: QuotaHttpRequest): QuotaHttpResponse
}
