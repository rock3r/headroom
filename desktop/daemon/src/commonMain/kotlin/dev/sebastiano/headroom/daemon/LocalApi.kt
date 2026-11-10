package dev.sebastiano.headroom.daemon

public data class LocalRequest(
    val method: String,
    val path: String,
    val host: String?,
    val origin: String? = null,
    val authorization: String? = null,
)

public data class LocalResponse(val status: Int, val contentType: String, val body: String)

public enum class LocalRoute {
    Quotas,
    Health,
}

/**
 * Request checks that do not depend on the socket. Loopback binding is enforced by the listener;
 * these checks stop a browser page, or a rebinding DNS name, from reading the report through that
 * socket. The token is a local secret, not a provider credential, and it never appears in a body.
 */
public object LocalApi {
    public fun route(method: String, path: String): LocalRoute? =
        when {
            method == "GET" && path == "/v1/quotas" -> LocalRoute.Quotas
            method == "GET" && path == "/health" -> LocalRoute.Health
            else -> null
        }

    public fun handle(request: LocalRequest, token: String, report: String): LocalResponse {
        if (!isLocalHost(request.host)) return forbidden()
        if (request.origin != null && !isLocalOrigin(request.origin)) return forbidden()
        val route =
            route(request.method, request.path) ?: return LocalResponse(NOT_FOUND, JSON, EMPTY)
        if (request.authorization != "Bearer $token")
            return LocalResponse(UNAUTHORIZED, JSON, EMPTY)
        val body = if (route == LocalRoute.Health) """{"ok":true}""" else report
        return LocalResponse(OK, JSON, body)
    }

    public fun isLocalHost(host: String?): Boolean {
        val name = host?.substringBefore(':')?.lowercase() ?: return false
        return name == "127.0.0.1" || name == "localhost" || name == "[::1]" || name == "::1"
    }

    public fun isLocalOrigin(origin: String): Boolean {
        val host = origin.substringAfter("://").substringBefore('/').substringBefore(':')
        return isLocalHost(host)
    }

    private fun forbidden(): LocalResponse = LocalResponse(FORBIDDEN, JSON, EMPTY)

    private const val OK = 200
    private const val UNAUTHORIZED = 401
    private const val FORBIDDEN = 403
    private const val NOT_FOUND = 404
    private const val JSON = "application/json"
    private const val EMPTY = "{}"
}
