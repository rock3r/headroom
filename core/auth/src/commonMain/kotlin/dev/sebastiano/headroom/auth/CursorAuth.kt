package dev.sebastiano.headroom.auth

import dev.sebastiano.headroom.model.Provider
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * The Cursor sign-in of a Grok account. Cursor serves its model pools and the Grok Bot pool from
 * its own backend, which does not accept the xAI token the account signs in with, so those windows
 * need this second sign-in. It is the browser sign-in of the Cursor desktop app:
 * 1. The app builds a PKCE challenge and a random uuid locally and opens
 *    `https://cursor.com/loginDeepControl` with them. There is no code to type.
 * 2. `GET https://api2.cursor.sh/auth/poll` with the uuid and the verifier answers 404 while the
 *    user signs in, then 200 with the session token and a refresh token.
 * 3. `POST https://api2.cursor.sh/oauth/token` trades the refresh token for a new session token.
 *
 * The session token is a JWT; its `exp` claim is the credential's expiry. The credential belongs to
 * the Grok account and is saved under [CursorCredential.idFor] its id.
 */
internal class CursorAuth(
    private val http: AuthHttpClient,
    private val clock: Clock,
    websiteUrl: String = WEBSITE_URL,
    apiUrl: String = API_URL,
    private val newPkce: () -> Pkce = { Pkce.generate() },
    private val newUuid: () -> String = { Uuid.random().toString() },
) : DeviceCodeSpec, TokenRefresher {
    override val provider: Provider = Provider.Grok

    private val loginUrl = "$websiteUrl/loginDeepControl"
    private val pollUrl = "$apiUrl/auth/poll"
    private val tokenUrl = "$apiUrl/oauth/token"

    override suspend fun requestCode(): DeviceCodeGrant {
        val pkce = newPkce()
        val uuid = newUuid()
        return DeviceCodeGrant(
            userCode = "",
            deviceCode = uuid,
            verificationUri = loginUrl,
            verificationUriComplete =
                urlWithQuery(
                    loginUrl,
                    listOf(
                        "challenge" to pkce.challenge,
                        "uuid" to uuid,
                        "mode" to "login",
                        "supportsSelectedTeamLogin" to "true",
                    ),
                ),
            interval = POLL_INTERVAL,
            expiresIn = null,
            pollToken = pkce.verifier,
        )
    }

    override suspend fun poll(grant: DeviceCodeGrant): DevicePoll {
        val context = "Cursor sign-in"
        val response =
            http.execute(
                AuthHttpRequest(
                    method = "GET",
                    url =
                        urlWithQuery(
                            pollUrl,
                            listOf(
                                "uuid" to grant.deviceCode,
                                "verifier" to grant.pollToken.orEmpty(),
                            ),
                        ),
                    headers = mapOf("Accept" to JSON),
                )
            )
        if (!response.isSuccessful) {
            return if (
                response.status == HTTP_NOT_FOUND ||
                    response.status in RETRYABLE_STATUSES ||
                    response.status >= SERVER_ERROR
            ) {
                DevicePoll.Pending
            } else {
                DevicePoll.Denied("Cursor sign-in was refused (HTTP ${response.status})")
            }
        }
        val data =
            try {
                parseJsonObject(response.body, context)
            } catch (failure: AuthException.InvalidResponse) {
                return DevicePoll.Denied(failure.message.orEmpty())
            }
        val accessToken = data.string("accessToken")
        val refreshToken = data.string("refreshToken")
        if (accessToken == null || refreshToken == null) {
            return DevicePoll.Denied("Cursor sign-in finished without the account's tokens")
        }
        return DevicePoll.Authorized(
            tokens(accessToken, refreshToken, data.string("authId"), context)
        )
    }

    override suspend fun refresh(credential: StoredCredential): TokenSet {
        val context = "Cursor token refresh"
        val response =
            http.postJson(
                tokenUrl,
                jsonOf(
                    listOf(
                        "grant_type" to "refresh_token",
                        "client_id" to CLIENT_ID,
                        "refresh_token" to checkNotNull(credential.refreshToken),
                    )
                ),
            )
        val root = response.successJson(context)
        if ((root["shouldLogout"] as? JsonPrimitive)?.booleanOrNull == true) {
            // The refresh token was revoked: only a new sign-in can get another one.
            throw AuthException.Rejected(
                HTTP_UNAUTHORIZED,
                root.string("error"),
                "$context was refused",
            )
        }
        val accessToken =
            root.string("access_token")
                ?: throw AuthException.InvalidResponse("$context response has no access_token")
        return tokens(accessToken, root.string("refresh_token"), null, context)
    }

    private fun tokens(
        accessToken: String,
        refreshToken: String?,
        authId: String?,
        context: String,
    ): TokenSet {
        val claims = jwtClaims(accessToken)
        val expiresAt =
            claims
                ?.long("exp")
                ?.let { Instant.fromEpochSeconds(it).minus(EXPIRY_MARGIN) }
                ?.coerceAtLeast(clock.now())
                ?: throw AuthException.InvalidResponse("$context returned a token with no expiry")
        return TokenSet(
            provider = provider,
            kind = CredentialKind.OAuth,
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAt = expiresAt,
            providerAccountId = authId ?: claims.string("sub"),
            label = claims.string("email"),
        )
    }

    companion object {
        const val CLIENT_ID = "KbZUR41cY7W6zRSdpSUJ7I7mLYBKOCmB"
        const val WEBSITE_URL = "https://cursor.com"
        const val API_URL = "https://api2.cursor.sh"
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_UNAUTHORIZED = 401
        private const val SERVER_ERROR = 500
        private val RETRYABLE_STATUSES = setOf(408, 429)
        private val POLL_INTERVAL: Duration = 2.seconds
        private val EXPIRY_MARGIN: Duration = 2.minutes
    }
}

/**
 * Where a Grok account's Cursor sign-in is saved: in the same [TokenStore], next to the account's
 * own credential (its xAI sign-in), under a derived id. Signing the account out removes both.
 */
public object CursorCredential {
    private const val SUFFIX = "#cursor"

    public fun idFor(accountId: String): String = accountId + SUFFIX

    public fun isCursorId(accountId: String): Boolean = accountId.endsWith(SUFFIX)
}
