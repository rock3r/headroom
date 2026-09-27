package dev.sebastiano.headroom.auth

import dev.sebastiano.headroom.model.Provider
import java.time.Clock
import java.time.Duration

/**
 * ChatGPT Codex sign-in with OpenAI's device-code flow. Polling returns an authorization code
 * together with a server-made PKCE verifier; that pair is exchanged for tokens.
 */
internal class CodexDeviceAuth(
    private val http: AuthHttpClient,
    private val clock: Clock,
    issuer: String = ISSUER,
) : DeviceCodeSpec, TokenRefresher {
    override val provider: Provider = Provider.Codex

    private val userCodeUrl = "$issuer/api/accounts/deviceauth/usercode"
    private val deviceTokenUrl = "$issuer/api/accounts/deviceauth/token"
    private val tokenUrl = "$issuer/oauth/token"

    override suspend fun requestCode(): DeviceCodeGrant {
        val context = "Codex device code request"
        val json =
            http
                .postJson(userCodeUrl, jsonOf(listOf("client_id" to CLIENT_ID)))
                .successJson(context)
        return DeviceCodeGrant(
            userCode =
                json.string("user_code")
                    ?: json.string("usercode")
                    ?: throw AuthException.InvalidResponse("$context response has no user_code"),
            deviceCode = json.requireString("device_auth_id", context),
            verificationUri = VERIFY_URL,
            verificationUriComplete = null,
            interval =
                Duration.ofSeconds(json.long("interval")?.takeIf { it > 0 } ?: DEFAULT_INTERVAL),
            expiresIn = MAX_WAIT,
        )
    }

    override suspend fun poll(grant: DeviceCodeGrant): DevicePoll {
        val context = "Codex device sign-in"
        val body =
            jsonOf(listOf("device_auth_id" to grant.deviceCode, "user_code" to grant.userCode))
        val response = http.postJson(deviceTokenUrl, body)
        return when {
            response.isSuccessful -> {
                val json = parseJsonObject(response.body, context)
                DevicePoll.Authorized(
                    exchange(
                        code = json.requireString("authorization_code", context),
                        verifier = json.requireString("code_verifier", context),
                    )
                )
            }
            response.status == PENDING_STATUS -> DevicePoll.Pending
            response.status == NOT_FOUND_STATUS ->
                throw AuthException.SignInFailed(
                    "The sign-in session was not found. The code may have expired; start again."
                )
            else -> throw response.rejected(context)
        }
    }

    private suspend fun exchange(code: String, verifier: String): TokenSet =
        requestTokens(
            listOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to DEVICE_REDIRECT_URI,
                "client_id" to CLIENT_ID,
                "code_verifier" to verifier,
            ),
            context = "Codex token exchange",
            firstSignIn = true,
        )

    override suspend fun refresh(credential: StoredCredential): TokenSet =
        requestTokens(
            listOf(
                "grant_type" to "refresh_token",
                "refresh_token" to checkNotNull(credential.refreshToken),
                "client_id" to CLIENT_ID,
            ),
            context = "Codex token refresh",
            firstSignIn = false,
        )

    private suspend fun requestTokens(
        form: List<Pair<String, String>>,
        context: String,
        firstSignIn: Boolean,
    ): TokenSet {
        val json = http.postForm(tokenUrl, form).successJson(context)
        val expiresAt =
            clock.instant().plusSeconds(json.long("expires_in") ?: DEFAULT_EXPIRES_IN_SECONDS)
        val tokens = json.toTokenSet(provider, context, expiresAt, requireRefresh = firstSignIn)
        val accountId =
            json.string("id_token")?.let(::chatGptAccountId) ?: chatGptAccountId(tokens.accessToken)
        if (accountId == null && firstSignIn) {
            throw AuthException.InvalidResponse("$context returned no ChatGPT account id")
        }
        return tokens.copy(
            providerAccountId = accountId,
            extras = accountId?.let { mapOf(CredentialExtras.CHATGPT_ACCOUNT_ID to it) }.orEmpty(),
        )
    }

    /** `chatgpt_account_id` from the OpenAI auth claim of a JWT, or at its top level. */
    private fun chatGptAccountId(jwt: String): String? {
        val claims = jwtClaims(jwt) ?: return null
        return claims.obj(AUTH_CLAIM)?.string(ACCOUNT_ID_CLAIM) ?: claims.string(ACCOUNT_ID_CLAIM)
    }

    companion object {
        const val CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
        const val ISSUER = "https://auth.openai.com"
        const val VERIFY_URL = "$ISSUER/codex/device"
        const val DEVICE_REDIRECT_URI = "$ISSUER/deviceauth/callback"
        const val AUTH_CLAIM = "https://api.openai.com/auth"
        const val ACCOUNT_ID_CLAIM = "chatgpt_account_id"
        const val DEFAULT_EXPIRES_IN_SECONDS = 3600L
        const val DEFAULT_INTERVAL = 5L
        const val PENDING_STATUS = 403
        const val NOT_FOUND_STATUS = 404
        val MAX_WAIT: Duration = Duration.ofMinutes(15)
    }
}
