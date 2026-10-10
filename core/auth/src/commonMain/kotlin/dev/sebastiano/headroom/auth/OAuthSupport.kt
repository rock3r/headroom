package dev.sebastiano.headroom.auth

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okio.Buffer

internal const val FORM = "application/x-www-form-urlencoded"
internal const val JSON = "application/json"

/**
 * Encodes [value] for a query or an `application/x-www-form-urlencoded` body, as
 * `java.net.URLEncoder` does: letters, digits and `.-*_` stay, a space becomes `+`, and every other
 * UTF-8 byte is `%XX`.
 */
internal fun urlEncode(value: String): String = buildString {
    for (byte in value.encodeToByteArray()) {
        val unsigned = byte.toInt() and BYTE_MASK
        val char = unsigned.toChar()
        when {
            char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char in FORM_SAFE ->
                append(char)
            char == ' ' -> append('+')
            else -> {
                append('%')
                append(UPPER_HEX[unsigned shr HEX_SHIFT])
                append(UPPER_HEX[unsigned and LOW_NIBBLE])
            }
        }
    }
}

private const val FORM_SAFE = ".-*_"
private const val UPPER_HEX = "0123456789ABCDEF"
private const val BYTE_MASK = 0xFF
private const val LOW_NIBBLE = 0x0F
private const val HEX_SHIFT = 4

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

/**
 * Decodes a query component as `java.net.URLDecoder` does: `+` is a space and `%XX` a byte, and the
 * bytes are UTF-8.
 *
 * @throws IllegalArgumentException for a `%` without two hex digits.
 */
internal fun decodeQueryComponent(value: String): String = percentDecode(value, plusIsSpace = true)

/**
 * Decodes the `%XX` escapes of [value] as UTF-8 bytes. A `+` is a space when [plusIsSpace], as in a
 * query, and stays a `+` otherwise, as in a path.
 *
 * @throws IllegalArgumentException for a `%` without two hex digits.
 */
internal fun percentDecode(value: String, plusIsSpace: Boolean): String {
    if ('%' !in value && (!plusIsSpace || '+' !in value)) return value
    val out = StringBuilder(value.length)
    var index = 0
    while (index < value.length) {
        when (val char = value[index]) {
            '+' if plusIsSpace -> {
                out.append(' ')
                index++
            }
            '%' -> {
                // A run of escapes is one UTF-8 sequence.
                val bytes = Buffer()
                while (index < value.length && value[index] == '%') {
                    require(index + 2 < value.length) { "Incomplete escape in $value" }
                    val byte =
                        value.substring(index + 1, index + ESCAPE_LENGTH).toIntOrNull(HEX_RADIX)
                    require(byte != null && byte >= 0) { "Bad escape in $value" }
                    bytes.writeByte(byte)
                    index += ESCAPE_LENGTH
                }
                out.append(bytes.readUtf8())
            }
            else -> {
                out.append(char)
                index++
            }
        }
    }
    return out.toString()
}

private const val ESCAPE_LENGTH = 3
private const val HEX_RADIX = 16
