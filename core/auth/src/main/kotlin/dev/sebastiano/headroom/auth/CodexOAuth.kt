package dev.sebastiano.headroom.auth

import dev.sebastiano.headroom.model.Provider
import java.time.Clock

/**
 * ChatGPT Codex sign-in, the same way the official Codex CLI does it: PKCE with a redirect to
 * `http://localhost:1455/auth/callback`. OpenAI allows only that redirect for this client, so the
 * port is fixed and the host must be `localhost`. The listener binds both loopback families,
 * because `localhost` may resolve to either.
 */
internal class CodexOAuth(
    private val http: AuthHttpClient,
    private val clock: Clock,
    private val authorizeEndpoint: String = AUTHORIZE_URL,
    private val tokenEndpoint: String = TOKEN_URL,
    callbackPort: Int = CALLBACK_PORT,
) : BrowserOAuthSpec, TokenRefresher {
    override val provider: Provider = Provider.Codex
    override val loopback: LoopbackConfig =
        LoopbackConfig(path = CALLBACK_PATH, ports = callbackPort..callbackPort, bindIpv6 = true)

    override fun loopbackRedirectUri(port: Int): String = "http://localhost:$port$CALLBACK_PATH"

    override fun authorizeUrl(redirectUri: String, pkce: Pkce, state: String): String =
        urlWithQuery(
            authorizeEndpoint,
            listOf(
                "response_type" to "code",
                "client_id" to CLIENT_ID,
                "redirect_uri" to redirectUri,
                "scope" to SCOPE,
                "code_challenge" to pkce.challenge,
                "code_challenge_method" to "S256",
                "state" to state,
                "id_token_add_organizations" to "true",
                "codex_cli_simplified_flow" to "true",
                "originator" to ORIGINATOR,
            ),
        )

    override suspend fun exchange(
        code: String,
        redirectUri: String,
        pkce: Pkce,
        state: String,
    ): TokenSet =
        requestTokens(
            listOf(
                "grant_type" to "authorization_code",
                "client_id" to CLIENT_ID,
                "code" to code,
                "code_verifier" to pkce.verifier,
                "redirect_uri" to redirectUri,
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
        val json = http.postForm(tokenEndpoint, form).successJson(context)
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
        const val AUTHORIZE_URL = "https://auth.openai.com/oauth/authorize"
        const val TOKEN_URL = "https://auth.openai.com/oauth/token"
        const val CALLBACK_PORT = 1455
        const val CALLBACK_PATH = "/auth/callback"
        const val SCOPE = "openid profile email offline_access"
        const val ORIGINATOR = "codex_cli_rs"
        const val AUTH_CLAIM = "https://api.openai.com/auth"
        const val ACCOUNT_ID_CLAIM = "chatgpt_account_id"
        const val DEFAULT_EXPIRES_IN_SECONDS = 3600L
    }
}
