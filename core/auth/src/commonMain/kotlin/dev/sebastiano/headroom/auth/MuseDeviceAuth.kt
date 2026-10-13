package dev.sebastiano.headroom.auth

import dev.sebastiano.headroom.model.Provider
import kotlin.time.Duration.Companion.seconds

/**
 * Muse sign-in: the Muse CLI's device-code flow against `auth.meta.com`, as Muse Code itself does
 * it.
 * 1. `POST /oidc/device/authorization/` with the public client id gives a user code to approve in
 *    the browser.
 * 2. `POST /oidc/device/token/` polls with the RFC 8628 device-code grant until Meta issues a
 *    device client access (DCA) token.
 * 3. `POST https://api.meta.ai/muse-code/key` with that token names the signed-in account. It is
 *    best effort at sign-in: the quota fetcher makes the same call on every sync, so a failure here
 *    only loses the account label, never the sign-in.
 *
 * Meta issues no refresh token and does not name an expiry, so the credential never expires on a
 * timer. The stored access token is the DCA token itself: it is the only credential the quota
 * endpoint accepts. The Muse API key the key call also returns is not stored, because Headroom
 * never makes model requests. When Meta revokes the DCA token the quota fetch answers 401 and the
 * account is shown as an expired sign-in.
 */
internal class MuseDeviceAuth(
    private val http: AuthHttpClient,
    authHost: String = AUTH_HOST,
    apiBaseUrl: String = API_BASE_URL,
) : DeviceCodeSpec {
    override val provider: Provider = Provider.Muse

    private val deviceAuthorizationUrl = "$authHost/oidc/device/authorization/"
    private val tokenUrl = "$authHost/oidc/device/token/"
    private val keyUrl = "$apiBaseUrl/muse-code/key"

    private val oauthHeaders = mapOf("Accept" to JSON, "User-Agent" to USER_AGENT)

    override suspend fun requestCode(): DeviceCodeGrant {
        val context = "Muse device code request"
        val json =
            http
                .postForm(
                    deviceAuthorizationUrl,
                    listOf("client_id" to CLIENT_ID),
                    oauthHeaders,
                )
                .successJson(context)
        val complete = json.string("verification_uri_complete")?.let(::trustedVerificationUri)
        return DeviceCodeGrant(
            userCode = json.requireString("user_code", context),
            deviceCode = json.requireString("device_code", context),
            verificationUri =
                json.string("verification_uri")?.let(::trustedVerificationUri)
                    ?: complete
                    ?: throw AuthException.InvalidResponse(
                        "$context response has no verification_uri"
                    ),
            verificationUriComplete = complete,
            interval = ((json.long("interval") ?: DEFAULT_INTERVAL).coerceAtLeast(1)).seconds,
            expiresIn = json.long("expires_in")?.seconds,
        )
    }

    override suspend fun poll(grant: DeviceCodeGrant): DevicePoll {
        val context = "Muse device sign-in"
        val response =
            http.postForm(
                tokenUrl,
                listOf(
                    "client_id" to CLIENT_ID,
                    "device_code" to grant.deviceCode,
                    "grant_type" to DEVICE_GRANT_TYPE,
                ),
                oauthHeaders,
            )
        val json = parseJsonObject(response.body, context)
        val error = json.string("error")
        return when {
            error == "authorization_pending" -> DevicePoll.Pending
            error == "slow_down" -> DevicePoll.SlowDown
            error == "expired_token" -> DevicePoll.Expired
            error == "access_denied" -> DevicePoll.Denied("Muse sign-in was denied")
            error != null -> throw response.rejected(context)
            !response.isSuccessful -> throw response.rejected(context)
            else -> DevicePoll.Authorized(tokens(json.requireString("access_token", context)))
        }
    }

    /** The sign-in's tokens, labelled from one key call when Meta answers it. */
    private suspend fun tokens(dcaToken: String): TokenSet {
        val (label, accountId) = accountIdentity(dcaToken)
        return TokenSet(
            provider = provider,
            kind = CredentialKind.OAuth,
            accessToken = dcaToken,
            refreshToken = null,
            expiresAt = null,
            providerAccountId = accountId,
            label = label,
        )
    }

    /**
     * The signed-in account's label and stable id from the key call: its email when Meta names one
     * (under any of the field names its clients use), else its full name. Both are null when the
     * call fails. The call mints a Muse API key as a side effect; Headroom keeps nothing of it.
     */
    private suspend fun accountIdentity(dcaToken: String): Pair<String?, String?> =
        try {
            val json =
                http
                    .execute(
                        AuthHttpRequest(
                            method = "POST",
                            url = keyUrl,
                            headers =
                                mapOf(
                                    "Accept" to JSON,
                                    "Authorization" to "Bearer $dcaToken",
                                    "User-Agent" to USER_AGENT,
                                    "x-api-version" to API_VERSION,
                                ),
                            body = EMPTY_JSON_BODY,
                            contentType = JSON,
                        )
                    )
                    .successJson("Muse account lookup")
            val email =
                json.string("user_email")
                    ?: json.string("user_email_address")
                    ?: json.string("email")
            val label = email ?: json.string("user_full_name") ?: json.string("name")
            label to (email ?: label)
        } catch (_: AuthException) {
            null to null
        }

    /** The page the user approves the code on: https on `auth.meta.com`, as the CLI opens it. */
    private fun trustedVerificationUri(value: String): String {
        val uri =
            WebUri.parseOrNull(value)
                ?: throw AuthException.InvalidResponse("Untrusted verification_uri")
        val host = uri.host?.lowercase()
        if (
            uri.scheme != "https" ||
                host == null ||
                (host != AUTH_HOST_NAME && !host.endsWith(".$AUTH_HOST_NAME"))
        ) {
            throw AuthException.InvalidResponse("Untrusted verification_uri")
        }
        return value
    }

    companion object {
        const val CLIENT_ID = "1031625952748946"
        const val AUTH_HOST = "https://auth.meta.com"
        const val API_BASE_URL = "https://api.meta.ai"
        const val DEVICE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"
        const val USER_AGENT = "muse-code/1.0.2"
        const val API_VERSION = "1.0.0"
        const val DEFAULT_INTERVAL = 5L
        private const val AUTH_HOST_NAME = "auth.meta.com"
        private const val EMPTY_JSON_BODY = "{}"
    }
}
