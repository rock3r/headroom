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

internal data class BrowserReply(val status: Int, val body: String)

/** Plays the browser following a redirect to the loopback listener. */
internal suspend fun browserGet(io: ExecutorCoroutineDispatcher, url: String): BrowserReply =
    withContext(io) {
        OkHttpClient().newCall(Request.Builder().url(url).build()).execute().use {
            BrowserReply(it.code, it.body.string())
        }
    }

/** The query parameters of [url] in order, decoded. */
internal fun queryPairs(url: String): List<Pair<String, String>> =
    url.substringAfter('?').split('&').map { pair ->
        java.net.URLDecoder.decode(pair.substringBefore('='), Charsets.UTF_8) to
            java.net.URLDecoder.decode(pair.substringAfter('='), Charsets.UTF_8)
    }

/** An unsigned JWT with [claimsJson] as its payload. */
internal fun fakeJwt(claimsJson: String): String {
    val encoder = java.util.Base64.getUrlEncoder().withoutPadding()
    val header = encoder.encodeToString("""{"alg":"none"}""".toByteArray())
    return "$header.${encoder.encodeToString(claimsJson.toByteArray())}.sig"
}
