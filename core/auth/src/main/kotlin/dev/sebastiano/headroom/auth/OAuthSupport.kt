package dev.sebastiano.headroom.auth

import java.net.URLEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal const val FORM = "application/x-www-form-urlencoded"
internal const val JSON = "application/json"

internal fun urlEncode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)

/** `a=1&b=2`, form-encoded, in the given order. */
internal fun formEncode(pairs: List<Pair<String, String>>): String =
    pairs.joinToString("&") { (key, value) -> "${urlEncode(key)}=${urlEncode(value)}" }

internal fun urlWithQuery(base: String, pairs: List<Pair<String, String>>): String =
    "$base?${formEncode(pairs)}"

/** A flat JSON object of strings, in the given order. */
internal fun jsonOf(pairs: List<Pair<String, String>>): String =
    JsonObject(pairs.associate { (key, value) -> key to JsonPrimitive(value) }).toString()

internal suspend fun AuthHttpClient.postForm(
    url: String,
    pairs: List<Pair<String, String>>,
    headers: Map<String, String> = mapOf("Accept" to JSON),
): AuthHttpResponse = execute(AuthHttpRequest("POST", url, headers, formEncode(pairs), FORM))

internal suspend fun AuthHttpClient.postJson(
    url: String,
    body: String,
    headers: Map<String, String> = mapOf("Accept" to JSON),
): AuthHttpResponse = execute(AuthHttpRequest("POST", url, headers, body, JSON))

/** The JSON body of a successful response. Error statuses become [AuthException.Rejected]. */
internal fun AuthHttpResponse.successJson(context: String): JsonObject {
    if (!isSuccessful) throw rejected(context)
    return parseJsonObject(body, context)
}

internal fun AuthHttpResponse.rejected(context: String): AuthException.Rejected {
    val error = runCatching { parseJsonObject(body, context).errorCode() }.getOrNull()
    val detail = error?.let { ": $it" }.orEmpty()
    return AuthException.Rejected(status, error, "$context failed (HTTP $status)$detail")
}

/** The OAuth `error` code of an error body, as a string or as `{"error":{"type":…}}`. */
internal fun JsonObject.errorCode(): String? =
    string("error") ?: obj("error")?.let { it.string("type") ?: it.string("code") }
