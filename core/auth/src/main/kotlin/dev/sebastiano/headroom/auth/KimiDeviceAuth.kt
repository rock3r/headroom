package dev.sebastiano.headroom.auth

import dev.sebastiano.headroom.model.Provider
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.JsonObject

/** Kimi Code sign-in with the device-code flow of `auth.kimi.com`. */
internal class KimiDeviceAuth(
    private val http: AuthHttpClient,
    private val clock: Clock,
    oauthHost: String = OAUTH_HOST,
) : DeviceCodeSpec, TokenRefresher {
    override val provider: Provider = Provider.Kimi

    private val deviceAuthorizationUrl = "$oauthHost/api/oauth/device_authorization"
    private val tokenUrl = "$oauthHost/api/oauth/token"

    override suspend fun requestCode(): DeviceCodeGrant {
        val context = "Kimi device code request"
        val json =
            http
                .postForm(deviceAuthorizationUrl, listOf("client_id" to CLIENT_ID))
                .successJson(context)
        val complete = json.requireString("verification_uri_complete", context)
        return DeviceCodeGrant(
            userCode = json.requireString("user_code", context),
            deviceCode = json.requireString("device_code", context),
            verificationUri = json.string("verification_uri") ?: complete,
            verificationUriComplete = complete,
            interval = ((json.long("interval") ?: DEFAULT_INTERVAL).coerceAtLeast(1)).seconds,
            expiresIn = json.long("expires_in")?.seconds,
        )
    }

    override suspend fun poll(grant: DeviceCodeGrant): DevicePoll {
        val context = "Kimi device sign-in"
        val response =
            http.postForm(
                tokenUrl,
                listOf(
                    "client_id" to CLIENT_ID,
                    "device_code" to grant.deviceCode,
                    "grant_type" to DEVICE_GRANT_TYPE,
                ),
            )
        val json = parseJsonObject(response.body, context)
        if (response.isSuccessful) {
            return DevicePoll.Authorized(tokensFrom(json, context, requireRefresh = true))
        }
        return when (json.string("error")) {
            "authorization_pending" -> DevicePoll.Pending
            "slow_down" -> DevicePoll.SlowDown
            "expired_token" -> DevicePoll.Expired
            "access_denied" -> DevicePoll.Denied("Kimi sign-in was denied")
            else -> throw response.rejected(context)
        }
    }

    override suspend fun refresh(credential: StoredCredential): TokenSet {
        val context = "Kimi token refresh"
        val json =
            http
                .postForm(
                    tokenUrl,
                    listOf(
                        "client_id" to CLIENT_ID,
                        "grant_type" to "refresh_token",
                        "refresh_token" to checkNotNull(credential.refreshToken),
                    ),
                )
                .successJson(context)
        return tokensFrom(json, context, requireRefresh = false)
    }

    private fun tokensFrom(
        json: JsonObject,
        context: String,
        requireRefresh: Boolean,
    ): TokenSet {
        val now = clock.now()
        val expiresAt =
            maxOf(
                expiryWithSkew(now, json.long("expires_in") ?: 0L, EXPIRY_MARGIN),
                now.plus(MIN_LIFETIME),
            )
        return json.toTokenSet(provider, context, expiresAt, requireRefresh)
    }

    companion object {
        const val CLIENT_ID = "17e5f671-d194-4dfb-9706-5516cb48c098"
        const val OAUTH_HOST = "https://auth.kimi.com"
        const val DEVICE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"
        const val DEFAULT_INTERVAL = 5L
        val EXPIRY_MARGIN: Duration = 5.minutes
        val MIN_LIFETIME: Duration = 30.seconds
    }
}
