package dev.sebastiano.headroom.auth

import java.time.Duration
import java.time.Instant
import java.util.Base64
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

private val authJson = Json { ignoreUnknownKeys = true }

internal fun parseJsonObject(body: String, context: String): JsonObject =
    try {
        authJson.parseToJsonElement(body) as? JsonObject
            ?: throw AuthException.InvalidResponse("$context returned something other than JSON")
    } catch (e: SerializationException) {
        throw AuthException.InvalidResponse("$context returned invalid JSON", e)
    } catch (e: IllegalArgumentException) {
        throw AuthException.InvalidResponse("$context returned invalid JSON", e)
    }

internal fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

internal fun JsonObject.requireString(key: String, context: String): String =
    string(key) ?: throw AuthException.InvalidResponse("$context response has no $key")

internal fun JsonObject.long(key: String): Long? =
    (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

/** Reads the claims of a JWT without checking its signature. Null when it is not a JWT. */
internal fun jwtClaims(token: String): JsonObject? {
    val parts = token.split('.')
    if (parts.size != JWT_PARTS) return null
    return try {
        val payload = String(Base64.getUrlDecoder().decode(parts[1].trimEnd('=')))
        parseJsonObject(payload, "JWT")
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: AuthException.InvalidResponse) {
        null
    }
}

private const val JWT_PARTS = 3

/** `now + ttl - min(maxSkew, ttl / 2)`: refresh a little early, but never before half the ttl. */
internal fun expiryWithSkew(now: Instant, expiresInSeconds: Long, maxSkew: Duration): Instant {
    val ttl = Duration.ofSeconds(expiresInSeconds)
    val skew = minOf(maxSkew, ttl.dividedBy(2))
    return now.plus(ttl).minus(skew)
}
