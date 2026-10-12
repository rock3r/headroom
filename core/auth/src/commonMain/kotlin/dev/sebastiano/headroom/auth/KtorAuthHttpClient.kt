package dev.sebastiano.headroom.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.Sender
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.utils.EmptyContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.isSecure
import io.ktor.http.takeFrom
import io.ktor.utils.io.InternalAPI
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.io.IOException

/**
 * [AuthHttpClient] backed by Ktor: OkHttp on Android and the JVM, URLSession on iOS. Cancelling the
 * coroutine cancels the call.
 *
 * Clients with the default timeout share one Ktor client for the whole process: a Ktor client owns
 * its engine's threads and connections until it is closed, so one per instance would leak them.
 */
public class KtorAuthHttpClient
private constructor(internal val client: HttpClient, private val ownsClient: Boolean) :
    AuthHttpClient {

    internal constructor(client: HttpClient) : this(client, ownsClient = false)

    /**
     * A client whose call, and each of its connect, read and write steps, fails after [timeout].
     */
    public constructor(
        timeout: Duration = DEFAULT_TIMEOUT
    ) : this(
        if (timeout == DEFAULT_TIMEOUT) sharedClient else httpClient(timeout),
        ownsClient = timeout != DEFAULT_TIMEOUT,
    )

    /**
     * Releases a custom-timeout client's engine. Shared or injected clients stay with their owner.
     */
    public fun close() {
        if (ownsClient) client.close()
    }

    override suspend fun execute(request: AuthHttpRequest): AuthHttpResponse {
        val response =
            network("Could not reach ${request.host()}") {
                client.request(request.url) {
                    method = HttpMethod.parse(request.method)
                    headers {
                        request.headers
                            .filterKeys { !it.equals(HttpHeaders.ContentType, ignoreCase = true) }
                            .forEach { (name, value) -> set(name, value) }
                    }
                    request.body?.let { body ->
                        setBody(
                            ByteArrayContent(
                                body.encodeToByteArray(),
                                request.contentType?.let(ContentType::parse),
                            )
                        )
                    }
                }
            }
        return network("Could not read ${request.host()}") { response.toAuthHttpResponse() }
    }

    private suspend fun HttpResponse.toAuthHttpResponse(): AuthHttpResponse =
        AuthHttpResponse(status.value, bodyAsText())

    /** Runs [block], turning a transport failure into [AuthException.Network]. */
    private inline fun <T> network(message: String, block: () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw AuthException.Network(message, e)
        }

    private fun AuthHttpRequest.host(): String = url.substringAfter("://").substringBefore('/')

    private companion object {
        val DEFAULT_TIMEOUT: Duration = 30.seconds
        const val MAX_REDIRECTS = 20
        val REDIRECTS = setOf(300, 301, 302, 303, 307, 308)
        val KEEPS_METHOD = setOf(307, 308)
        val BODYLESS_METHODS = setOf(HttpMethod.Get, HttpMethod.Head)

        val sharedClient: HttpClient by lazy { httpClient(DEFAULT_TIMEOUT) }

        fun httpClient(timeout: Duration): HttpClient =
            HttpClient(platformHttpEngine) {
                    expectSuccess = false
                    followRedirects = false
                    install(HttpTimeout) {
                        connectTimeoutMillis = timeout.inWholeMilliseconds
                        socketTimeoutMillis = timeout.inWholeMilliseconds
                        // iOS's engine has no connect timeout: this bounds a stalled connection
                        // there.
                        requestTimeoutMillis = timeout.inWholeMilliseconds
                    }
                }
                .apply {
                    plugin(HttpSend).intercept { request ->
                        followRedirects(request, execute(request))
                    }
                }

        // Ktor's context-preserving clone is internal, but keeps the original call's timeout and
        // cancellation attached to follow-ups, as Ktor's own redirect plugin does.
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
    }
}

/**
 * The platform's HTTP engine: OkHttp on the JVM, URLSession on iOS. Named rather than found through
 * `ServiceLoader`, so a minified Android build keeps it.
 */
internal expect val platformHttpEngine: HttpClientEngineFactory<*>
