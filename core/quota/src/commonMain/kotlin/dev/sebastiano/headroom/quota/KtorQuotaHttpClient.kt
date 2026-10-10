package dev.sebastiano.headroom.quota

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.OutgoingContent
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.io.IOException

/**
 * [QuotaHttpClient] backed by Ktor. The engine is the platform's: OkHttp on Android and the JVM,
 * URLSession on iOS. The call is cancelled when the calling coroutine is cancelled.
 */
public class KtorQuotaHttpClient(private val httpClient: HttpClient = defaultHttpClient()) :
    QuotaHttpClient {

    override suspend fun execute(request: QuotaHttpRequest): QuotaHttpResponse {
        val url = httpUrlOrNull(request.url) ?: throw IOException("Malformed request URL")
        val contentType =
            request.headers.entries
                .firstOrNull { it.key.equals(HttpHeaders.ContentType, ignoreCase = true) }
                ?.let { ContentType.parse(it.value) }
        val bytes = request.binaryBody?.bytes ?: request.body?.encodeToByteArray()
        val response =
            httpClient.request(url) {
                method = HttpMethod.parse(request.method)
                request.timeout?.let { timeout { requestTimeoutMillis = it.inWholeMilliseconds } }
                headers {
                    request.headers
                        .filterKeys { it.lowercase() !in BODY_HEADERS }
                        .forEach { (name, value) -> append(name, value) }
                }
                // Ktor sends Content-Type with the body, never as a plain header. A request without
                // a body can still carry one, as Claude's profile request does.
                when {
                    bytes != null -> setBody(ByteArrayContent(bytes, contentType))
                    contentType != null -> setBody(NoBody(contentType))
                }
            }
        return response.toQuotaHttpResponse()
    }

    private suspend fun HttpResponse.toQuotaHttpResponse(): QuotaHttpResponse {
        val bytes = bodyAsBytes()
        return QuotaHttpResponse(
            statusCode = status.value,
            headers =
                headers.names().associate { name ->
                    name.lowercase() to headers.getAll(name).orEmpty().joinToString(", ")
                },
            body = bytes.decodeToString(),
            binaryBody = BinaryBody(bytes),
        )
    }

    private class NoBody(override val contentType: ContentType) : OutgoingContent.NoContent()

    public companion object {
        private val DEFAULT_TIMEOUT: Duration = 30.seconds
        private val BODY_HEADERS = setOf("content-type", "content-length")

        /** A client with the platform's engine and a [DEFAULT_TIMEOUT] for each call. */
        public fun defaultHttpClient(): HttpClient =
            HttpClient(platformHttpEngine) {
                expectSuccess = false
                install(HttpTimeout) { requestTimeoutMillis = DEFAULT_TIMEOUT.inWholeMilliseconds }
            }

        /** [url] when it is an absolute http or https URL with a host, as OkHttp required. */
        private fun httpUrlOrNull(url: String): Url? {
            val scheme = url.substringBefore("://", missingDelimiterValue = "").lowercase()
            if (scheme != "http" && scheme != "https") return null
            return runCatching { Url(url) }.getOrNull()?.takeIf { it.host.isNotEmpty() }
        }
    }
}

/**
 * The platform's HTTP engine: OkHttp on the JVM, URLSession on iOS. Named rather than found through
 * `ServiceLoader`, so a minified Android build keeps it.
 */
internal expect val platformHttpEngine: HttpClientEngineFactory<*>
