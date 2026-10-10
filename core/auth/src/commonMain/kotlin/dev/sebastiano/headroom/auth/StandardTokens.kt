package dev.sebastiano.headroom.auth

import dev.sebastiano.headroom.model.Provider
import kotlin.time.Instant
import kotlinx.serialization.json.JsonObject

/**
 * Reads a standard OAuth token response (`access_token`, `refresh_token`, optional `id_token`). The
 * account id and label come from the `sub` and `email` claims of the ID token, when present.
 *
 * @param requireRefresh true for a first sign-in, where a missing refresh token is an error. A
 *   refresh may leave it out; the old one then stays.
 */
internal fun JsonObject.toTokenSet(
    provider: Provider,
    context: String,
    expiresAt: Instant,
    requireRefresh: Boolean,
): TokenSet {
    val refresh = string("refresh_token")
    if (requireRefresh && refresh == null) {
        throw AuthException.InvalidResponse("$context response has no refresh_token")
    }
    val identity = string("id_token")?.let(::jwtClaims)
    return TokenSet(
        provider = provider,
        kind = CredentialKind.OAuth,
        accessToken = requireString("access_token", context),
        refreshToken = refresh,
        expiresAt = expiresAt,
        providerAccountId = identity?.string("sub"),
        label = identity?.string("email"),
    )
}
