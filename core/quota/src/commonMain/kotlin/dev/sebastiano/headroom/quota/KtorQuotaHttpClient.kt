package dev.sebastiano.headroom.quota

import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.Sender
import io.ktor.client.plugins.plugin
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.utils.EmptyContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.OutgoingContent
import io.ktor.http.isSecure
import io.ktor.http.takeFrom
import io.ktor.utils.io.InternalAPI
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.io.IOException

/**
 * [QuotaHttpClient] backed by Ktor. The engine is the platform's: OkHttp on Android and the JVM,
 * URLSession on iOS. The call is cancelled when the calling coroutine is cancelled.
 *
 * Without an [httpClient], every instance shares one for the whole process: a Ktor client owns its
 * engine's threads and connections until it is closed, so one per instance would leak them.
 */
public class KtorQuotaHttpClient(internal val httpClient: HttpClient = sharedHttpClient) :
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

        private val sharedHttpClient: HttpClient by lazy { defaultHttpClient() }

        private const val MAX_REDIRECTS = 20
        private val REDIRECTS = setOf(300, 301, 302, 303, 307, 308)
        private val KEEPS_METHOD = setOf(307, 308)
        private val BODYLESS_METHODS = setOf(HttpMethod.Get, HttpMethod.Head)

        /**
         * A client with the platform's engine and a [DEFAULT_TIMEOUT] for each call. It follows
         * redirects as OkHttp did, for every method: Ktor only follows them for GET and HEAD.
         */
        public fun defaultHttpClient(): HttpClient =
            HttpClient(platformHttpEngine) {
                    expectSuccess = false
                    followRedirects = false
                    install(HttpTimeout) {
                        requestTimeoutMillis = DEFAULT_TIMEOUT.inWholeMilliseconds
                    }
                }
                .apply {
                    plugin(HttpSend).intercept { request ->
                        followRedirects(request, execute(request))
                    }
                }

        /**
         * Follows the redirects of [origin], as OkHttp did: 307 and 308 send the request again, any
         * other redirect turns a body-bearing method into a GET without the body. Like Ktor, it
         * does not follow a redirect from HTTPS to HTTP, and drops Authorization when the scheme,
         * host or port changes.
         */
        // Ktor's context-preserving clone is internal, but is required to keep the original call's
        // timeout and cancellation attached to every follow-up, as Ktor's own redirect plugin does.
        @OptIn(InternalAPI::class)
        private suspend fun Sender.followRedirects(
            request: HttpRequestBuilder,
            origin: HttpClientCall,
        ): HttpClientCall {
            var call = origin
            var builder = request
            repeat(MAX_REDIRECTS) {
                val status = call.response.status.value
                val location = call.response.headers[HttpHeaders.Location]
                if (status !in REDIRECTS || location == null) return call
                val from = call.request.url
                val next =
                    HttpRequestBuilder().apply {
                        takeFromWithExecutionContext(builder)
                        url.parameters.clear()
                        url.takeFrom(location)
                        if (status !in KEEPS_METHOD && method !in BODYLESS_METHODS) {
                            method = HttpMethod.Get
                            setBody(EmptyContent)
                            headers.remove(HttpHeaders.ContentType)
                            headers.remove(HttpHeaders.ContentLength)
                            headers.remove(HttpHeaders.TransferEncoding)
                        }
                        if (!url.build().hasSameOriginAs(from)) {
                            headers.remove(HttpHeaders.Authorization)
                        }
                    }
                if (from.protocol.isSecure() && !next.url.protocol.isSecure()) return call
                builder = next
                call = execute(next)
            }
            return call
        }

        private fun Url.hasSameOriginAs(other: Url): Boolean =
            protocol == other.protocol &&
                host.equals(other.host, ignoreCase = true) &&
                port == other.port

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
