package dev.sebastiano.headroom.auth

import java.util.concurrent.Executors
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** A real thread pool for socket work in tests. Close it after the test. */
internal fun testIoDispatcher(): ExecutorCoroutineDispatcher =
    Executors.newCachedThreadPool().asCoroutineDispatcher()

internal data class BrowserReply(val status: Int, val body: String, val location: String? = null)

/** Plays the browser following a redirect to the loopback listener. */
internal suspend fun browserGet(
    io: ExecutorCoroutineDispatcher,
    url: String,
    headers: Map<String, String> = emptyMap(),
): BrowserReply =
    withContext(io) {
        OkHttpClient.Builder()
            .followRedirects(false)
            .build()
            .newCall(
                Request.Builder()
                    .url(url)
                    .apply { headers.forEach { (name, value) -> header(name, value) } }
                    .build()
            )
            .execute()
            .use { BrowserReply(it.code, it.body.string(), it.header("Location")) }
    }

/** The query parameters of [url] in order, decoded. */
internal fun queryPairs(url: String): List<Pair<String, String>> =
    url.substringAfter('?').split('&').map { pair ->
        java.net.URLDecoder.decode(pair.substringBefore('='), Charsets.UTF_8) to
            java.net.URLDecoder.decode(pair.substringAfter('='), Charsets.UTF_8)
    }
