package dev.sebastiano.headroom.auth

import dev.sebastiano.headroom.model.Provider
import java.net.URI
import java.net.URISyntaxException
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * The ZCode sign-in of a Z.AI account. Z.AI serves its usage-limit resets from `zcode.z.ai`, which
 * does not accept the API key the account signs in with, so the resets need this second sign-in. It
 * is the browser sign-in of ZCode's own CLI:
 * 1. `POST https://zcode.z.ai/api/v1/oauth/cli/init` with a random poll token gives a flow id and
 *    the `chat.z.ai` page to open.
 * 2. `GET …/oauth/cli/poll/{flow_id}` answers `pending`, `ready` or `failed`. A `ready` answer
 *    carries the ZCode JWT and a Z.AI OAuth access token.
 * 3. `POST https://api.z.ai/api/auth/z/login` trades the Z.AI token for a short-lived business
 *    token.
 *
 * The credential keeps the business token as its access token, and both long-lived tokens in its
 * refresh token ([ZCodeTokens]), so [refresh] can mint a new business token without the browser. It
 * is saved under [ZCodeCredential.idFor] the Z.AI account's id.
 */
internal class ZCodeAuth(
    private val http: AuthHttpClient,
    private val clock: Clock,
    zCodeHost: String = ZCODE_HOST,
    apiHost: String = API_HOST,
    private val newPollToken: () -> String = ::randomPollToken,
) : DeviceCodeSpec, TokenRefresher {
    override val provider: Provider = Provider.ZAi

    private val initUrl = "$zCodeHost/api/v1/oauth/cli/init"
    private val pollUrl = "$zCodeHost/api/v1/oauth/cli/poll/"
    private val loginUrl = "$apiHost/api/auth/z/login"

    override suspend fun requestCode(): DeviceCodeGrant {
        val context = "ZCode sign-in start"
        val clientToken = newPollToken()
        val data =
            http
                .postJson(
                    initUrl,
                    """{"provider":"zai"}""",
                    headers = mapOf("Accept" to JSON, "Authorization" to "Bearer $clientToken"),
                )
                .successJson(context)
                .envelopeData(context)
        val authorizeUrl = data.requireString("authorize_url", context)
        if (!authorizeUrl.isHttps()) {
            throw AuthException.InvalidResponse(
                "$context returned a sign-in page that is not https"
            )
        }
        val expiresIn =
            data.long("expires_at")?.let { at ->
                Duration.between(clock.instant(), Instant.ofEpochSecond(at)).takeIf {
                    !it.isNegative && !it.isZero
                }
            }
        return DeviceCodeGrant(
            userCode = "",
            deviceCode = data.requireString("flow_id", context),
            verificationUri = authorizeUrl,
            verificationUriComplete = null,
            interval =
                Duration.ofSeconds(
                    (data.long("poll_interval_sec") ?: DEFAULT_INTERVAL).coerceAtLeast(1)
                ),
            expiresIn = expiresIn,
            pollToken = data.string("poll_token") ?: clientToken,
        )
    }

    override suspend fun poll(grant: DeviceCodeGrant): DevicePoll {
        val context = "ZCode sign-in"
        val response =
            http.execute(
                AuthHttpRequest(
                    method = "GET",
                    url = pollUrl + urlEncode(grant.deviceCode),
                    headers =
                        mapOf("Accept" to JSON, "Authorization" to "Bearer ${grant.pollToken}"),
                )
            )
        if (!response.isSuccessful) {
            return if (response.status in RETRYABLE_STATUSES || response.status >= SERVER_ERROR) {
                DevicePoll.Pending
            } else {
                DevicePoll.Denied("ZCode sign-in was refused (HTTP ${response.status})")
            }
        }
        val data =
            try {
                parseJsonObject(response.body, context).envelopeData(context)
            } catch (failure: AuthException.InvalidResponse) {
                return DevicePoll.Denied(failure.message.orEmpty())
            }
        return when (data.string("status")) {
            "pending" -> DevicePoll.Pending
            "ready" -> ready(data)
            "failed" -> DevicePoll.Denied("ZCode sign-in was not authorized")
            else -> DevicePoll.Denied("ZCode sign-in answered with an unknown status")
        }
    }

    override suspend fun refresh(credential: StoredCredential): TokenSet {
        val tokens =
            credential.refreshToken?.let(ZCodeTokens::decode)
                ?: throw AuthException.Rejected(
                    HTTP_UNAUTHORIZED,
                    null,
                    "The saved ZCode sign-in is incomplete",
                )
        return tokensFor(tokens, label = null)
    }

    private suspend fun ready(data: JsonObject): DevicePoll {
        val zCodeJwt = data.string("token")
        val zAiToken = data.obj("zai")?.string("access_token")
        if (zCodeJwt == null || zAiToken == null) {
            return DevicePoll.Denied("ZCode sign-in finished without the account's tokens")
        }
        val user = data.obj("user")
        val label = user?.string("email") ?: user?.string("name")
        return DevicePoll.Authorized(tokensFor(ZCodeTokens(zAiToken, zCodeJwt), label))
    }

    /** Trades the Z.AI OAuth token of [tokens] for a business token. */
    private suspend fun tokensFor(tokens: ZCodeTokens, label: String?): TokenSet {
        val context = "Z.AI account token exchange"
        val response = http.postJson(loginUrl, jsonOf(listOf("token" to tokens.zAiAccessToken)))
        val root = response.successJson(context)
        val code = root.long("code")
        val success = (root["success"] as? JsonPrimitive)?.booleanOrNull
        if (
            (code != null && code != ENVELOPE_OK && code != ENVELOPE_OK_HTTP_STYLE) ||
                success == false
        ) {
            // The Z.AI token was refused: only a new sign-in can get another one.
            throw AuthException.Rejected(HTTP_UNAUTHORIZED, code?.toString(), "$context failed")
        }
        val data = root.obj("data")
        val business =
            data?.string("access_token")
                ?: data?.string("accessToken")
                ?: throw AuthException.InvalidResponse("$context response has no access_token")
        val now = clock.instant()
        val lifetime = data?.long("expires_in")?.takeIf { it > 0 } ?: DEFAULT_LIFETIME_SECONDS
        return TokenSet(
            provider = provider,
            kind = CredentialKind.OAuth,
            accessToken = business,
            refreshToken = tokens.encode(),
            expiresAt = maxOf(expiryWithSkew(now, lifetime, EXPIRY_MARGIN), now.plus(MIN_LIFETIME)),
            label = label,
        )
    }

    /** The `data` of a `{code, msg, data}` envelope. Only a numeric `code` of 0 is a success. */
    private fun JsonObject.envelopeData(context: String): JsonObject {
        val code = long("code")
        if (code != ENVELOPE_OK) {
            val message = string("msg") ?: "code ${code ?: "missing"}"
            throw AuthException.InvalidResponse("$context failed: $message")
        }
        return obj("data") ?: throw AuthException.InvalidResponse("$context returned no data")
    }

    private fun String.isHttps(): Boolean =
        try {
            URI(this).scheme == "https"
        } catch (_: URISyntaxException) {
            false
        }

    companion object {
        const val ZCODE_HOST = "https://zcode.z.ai"
        const val API_HOST = "https://api.z.ai"
        private const val DEFAULT_INTERVAL = 2L
        private const val DEFAULT_LIFETIME_SECONDS = 3_600L
        private const val ENVELOPE_OK = 0L
        private const val ENVELOPE_OK_HTTP_STYLE = 200L
        private const val HTTP_UNAUTHORIZED = 401
        private const val SERVER_ERROR = 500
        private const val POLL_TOKEN_BYTES = 32
        private val RETRYABLE_STATUSES = setOf(408, 429)
        private val EXPIRY_MARGIN: Duration = Duration.ofMinutes(5)
        private val MIN_LIFETIME: Duration = Duration.ofSeconds(30)

        private fun randomPollToken(): String {
            val bytes = ByteArray(POLL_TOKEN_BYTES).also(SecureRandom()::nextBytes)
            return bytes.joinToString("") { "%02x".format(Locale.ROOT, it) }
        }
    }
}

/** The two long-lived tokens of a ZCode sign-in, kept as its credential's refresh token. */
@Serializable
public data class ZCodeTokens(val zAiAccessToken: String, val zCodeJwt: String) {
    public fun encode(): String = json.encodeToString(serializer(), this)

    override fun toString(): String = "ZCodeTokens(…)"

    public companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** The tokens saved in [value], or null when they are missing or unreadable. */
        public fun decode(value: String): ZCodeTokens? =
            try {
                json.decodeFromString(serializer(), value).takeIf {
                    it.zAiAccessToken.isNotBlank() && it.zCodeJwt.isNotBlank()
                }
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
    }
}

/**
 * Where a Z.AI account's ZCode sign-in is saved: in the same [TokenStore], next to the account's
 * own credential (its API key), under a derived id. Signing the account out removes both.
 */
public object ZCodeCredential {
    private const val SUFFIX = "#zcode"

    public fun idFor(accountId: String): String = accountId + SUFFIX
}
