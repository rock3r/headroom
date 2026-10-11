package dev.sebastiano.headroom.quota

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * [QuotaHttpClient] backed by OkHttp. The call runs on OkHttp's own threads and is cancelled when
 * the calling coroutine is cancelled.
 */
public class OkHttpQuotaHttpClient(
    private val okHttpClient: OkHttpClient =
        OkHttpClient.Builder().callTimeout(DEFAULT_TIMEOUT).build()
) : QuotaHttpClient {

    override suspend fun execute(request: QuotaHttpRequest): QuotaHttpResponse {
        val call = okHttpClient.newCall(request.toOkHttpRequest())
        request.timeout?.let {
            call.timeout().timeout(it.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        }
        return call.await()
    }

    private fun QuotaHttpRequest.toOkHttpRequest(): Request {
        val httpUrl = url.toHttpUrlOrNull() ?: throw IOException("Malformed request URL")
        val contentType = headers.entries.firstOrNull { it.key.equals("Content-Type", true) }
        val requestBody =
            binaryBody?.bytes?.toRequestBody(contentType?.value?.toMediaTypeOrNull())
                ?: body?.toRequestBody()
        return Request.Builder()
            .url(httpUrl)
            .headers(Headers.Builder().apply { headers.forEach { (k, v) -> add(k, v) } }.build())
            .method(method, requestBody)
            .build()
    }

    private suspend fun Call.await(): QuotaHttpResponse = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val result =
                        try {
                            response.use { it.toQuotaHttpResponse() }
                        } catch (e: IOException) {
                            cont.resumeWithException(e)
                            return
                        }
                    cont.resume(result)
                }
            }
        )
    }

    private fun Response.toQuotaHttpResponse(): QuotaHttpResponse {
        val bytes = body.bytes()
        return QuotaHttpResponse(
            statusCode = code,
            headers =
                headers.names().associate { name ->
                    name.lowercase() to headers.values(name).joinToString(", ")
                },
            body = String(bytes, Charsets.UTF_8),
            binaryBody = BinaryBody(bytes),
        )
    }

    private companion object {
        val DEFAULT_TIMEOUT: Duration = 30.seconds
    }
}
