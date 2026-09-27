package dev.sebastiano.headroom.auth

/** One HTTP request made by a sign-in or refresh step. */
public data class AuthHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val contentType: String? = null,
)

/** The status and body of an HTTP response. Error statuses are responses, not exceptions. */
public data class AuthHttpResponse(val status: Int, val body: String) {
    val isSuccessful: Boolean
        get() = status in SUCCESS_STATUSES

    private companion object {
        val SUCCESS_STATUSES = 200..299
    }
}

/**
 * The small HTTP surface this module needs. Implementations throw [AuthException.Network] when the
 * server cannot be reached, and return every HTTP status as an [AuthHttpResponse].
 */
public fun interface AuthHttpClient {
    public suspend fun execute(request: AuthHttpRequest): AuthHttpResponse
}
