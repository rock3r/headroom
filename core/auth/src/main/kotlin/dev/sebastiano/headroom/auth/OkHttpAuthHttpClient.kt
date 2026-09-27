package dev.sebastiano.headroom.auth

import java.io.IOException
import java.time.Duration
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** [AuthHttpClient] backed by OkHttp. Cancelling the coroutine cancels the call. */
public class OkHttpAuthHttpClient internal constructor(private val client: OkHttpClient) :
    AuthHttpClient {

    public constructor(
        timeout: Duration = DEFAULT_TIMEOUT
    ) : this(
        OkHttpClient.Builder()
            .connectTimeout(timeout)
            .readTimeout(timeout)
            .writeTimeout(timeout)
            .build()
    )

    override suspend fun execute(request: AuthHttpRequest): AuthHttpResponse {
        val call = client.newCall(request.toOkHttp())
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(
                            AuthException.Network("Could not reach ${request.host()}", e)
                        )
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val result =
                            try {
                                response.use { AuthHttpResponse(it.code, it.body.string()) }
                            } catch (e: IOException) {
                                continuation.resumeWithException(
                                    AuthException.Network("Could not read ${request.host()}", e)
                                )
                                return
                            }
                        continuation.resume(result)
                    }
                }
            )
        }
    }

    private fun AuthHttpRequest.toOkHttp(): Request {
        val requestBody = body?.toByteArray()?.toRequestBody(contentType?.toMediaType())
        return Request.Builder()
            .url(url)
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .method(method, requestBody)
            .build()
    }

    private fun AuthHttpRequest.host(): String = url.substringAfter("://").substringBefore('/')

    private companion object {
        val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(30)
    }
}
