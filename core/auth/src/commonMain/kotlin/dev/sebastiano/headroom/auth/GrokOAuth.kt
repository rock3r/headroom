package dev.sebastiano.headroom.auth

import dev.sebastiano.headroom.model.Provider
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

/**
 * Grok (xAI) sign-in: PKCE with a redirect to a fixed loopback port, `127.0.0.1:56121`. xAI's page
 * may call the callback with `fetch`, so the listener answers CORS preflights from xAI origins.
 */
internal class GrokOAuth(
    private val http: AuthHttpClient,
    private val clock: Clock,
    private val authorizeEndpoint: String = AUTHORIZE_URL,
    private val tokenEndpoint: String = TOKEN_URL,
    callbackPort: Int = CALLBACK_PORT,
) : BrowserOAuthSpec, TokenRefresher {
    override val provider: Provider = Provider.Grok
    override val loopback: LoopbackConfig =
        LoopbackConfig(
            path = CALLBACK_PATH,
            ports = callbackPort..callbackPort,
            allowedOrigins = setOf("https://accounts.x.ai", "https://auth.x.ai"),
        )

    override fun loopbackRedirectUri(port: Int): String = "http://127.0.0.1:$port$CALLBACK_PATH"

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
                "nonce" to Uuid.random().toHexString(),
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
                "code" to code,
                "redirect_uri" to redirectUri,
                "client_id" to CLIENT_ID,
                "code_verifier" to pkce.verifier,
            ),
            context = "xAI token exchange",
            requireRefresh = true,
        )

    override suspend fun refresh(credential: StoredCredential): TokenSet =
        requestTokens(
            listOf(
                "grant_type" to "refresh_token",
                "refresh_token" to checkNotNull(credential.refreshToken),
                "client_id" to CLIENT_ID,
            ),
            context = "xAI token refresh",
            requireRefresh = false,
        )

    private suspend fun requestTokens(
        form: List<Pair<String, String>>,
        context: String,
        requireRefresh: Boolean,
    ): TokenSet {
        val json = http.postForm(tokenEndpoint, form).successJson(context)
        val expiresIn = json.long("expires_in") ?: DEFAULT_EXPIRES_IN_SECONDS
        if (expiresIn <= 0) throw AuthException.InvalidResponse("$context has an invalid expiry")
        val expiresAt = expiryWithSkew(clock.now(), expiresIn, EXPIRY_MARGIN)
        return json.toTokenSet(provider, context, expiresAt, requireRefresh)
    }

    companion object {
        const val CLIENT_ID = "b1a00492-073a-47ea-816f-4c329264a828"
        const val AUTHORIZE_URL = "https://auth.x.ai/oauth2/authorize"
        const val TOKEN_URL = "https://auth.x.ai/oauth2/token"
        const val CALLBACK_PORT = 56121
        const val CALLBACK_PATH = "/callback"
        const val SCOPE = "openid profile email offline_access grok-cli:access api:access"
        const val DEFAULT_EXPIRES_IN_SECONDS = 3600L
        val EXPIRY_MARGIN: Duration = 2.minutes
    }
}
