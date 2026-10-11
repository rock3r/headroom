package dev.sebastiano.headroom.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.ByteArrayContent
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
public class KtorAuthHttpClient internal constructor(internal val client: HttpClient) :
    AuthHttpClient {

    /**
     * A client whose call, and each of its connect, read and write steps, fails after [timeout].
     */
    public constructor(
        timeout: Duration = DEFAULT_TIMEOUT
    ) : this(if (timeout == DEFAULT_TIMEOUT) sharedClient else httpClient(timeout))

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

        val sharedClient: HttpClient by lazy { httpClient(DEFAULT_TIMEOUT) }

        fun httpClient(timeout: Duration): HttpClient =
            HttpClient(platformHttpEngine) {
                expectSuccess = false
                install(HttpTimeout) {
                    connectTimeoutMillis = timeout.inWholeMilliseconds
                    socketTimeoutMillis = timeout.inWholeMilliseconds
                    // iOS's engine has no connect timeout: this bounds a stalled connection there.
                    requestTimeoutMillis = timeout.inWholeMilliseconds
                }
            }
    }
}

/**
 * The platform's HTTP engine: OkHttp on the JVM, URLSession on iOS. Named rather than found through
 * `ServiceLoader`, so a minified Android build keeps it.
 */
internal expect val platformHttpEngine: HttpClientEngineFactory<*>
