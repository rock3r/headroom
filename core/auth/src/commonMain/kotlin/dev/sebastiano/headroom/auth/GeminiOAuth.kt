package dev.sebastiano.headroom.auth

import dev.sebastiano.headroom.model.Provider
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Gemini Code Assist sign-in with Google OAuth 2.0 PKCE. Loopback redirect to
 * http://localhost:<port>/oauth2callback.
 */
internal class GeminiOAuth(
    private val http: AuthHttpClient,
    private val clock: Clock,
    private val authorizeEndpoint: String = AUTHORIZE_URL,
    private val tokenEndpoint: String = TOKEN_URL,
) : BrowserOAuthSpec, TokenRefresher {
    override val provider: Provider = Provider.Gemini
    override val loopback: LoopbackConfig = LoopbackConfig(path = CALLBACK_PATH, bindIpv6 = true)

    override fun newState(random: Random): String = Pkce.randomState(random)

    override fun loopbackRedirectUri(port: Int): String = "http://localhost:$port$CALLBACK_PATH"

    override fun authorizeUrl(redirectUri: String, pkce: Pkce, state: String): String =
        urlWithQuery(
            authorizeEndpoint,
            listOf(
                "client_id" to CLIENT_ID,
                "response_type" to "code",
                "redirect_uri" to redirectUri,
                "scope" to SCOPES,
                "code_challenge" to pkce.challenge,
                "code_challenge_method" to "S256",
                "state" to state,
                "access_type" to "offline",
                "prompt" to "consent",
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
            context = "Gemini token exchange",
            requireRefresh = true,
        )

    override suspend fun refresh(credential: StoredCredential): TokenSet =
        requestTokens(
            listOf(
                "grant_type" to "refresh_token",
                "refresh_token" to checkNotNull(credential.refreshToken),
                "client_id" to CLIENT_ID,
            ),
            context = "Gemini token refresh",
            requireRefresh = false,
        )

    private suspend fun requestTokens(
        form: List<Pair<String, String>>,
        context: String,
        requireRefresh: Boolean,
    ): TokenSet {
        val json = http.postForm(tokenEndpoint, form).successJson(context)
        val expiresIn =
            json.long("expires_in")?.takeIf { it > 0 }
                ?: throw AuthException.InvalidResponse("$context response has no expiry")
        val expiresAt = expiryWithSkew(clock.now(), expiresIn, EXPIRY_MARGIN)
        return json.toTokenSet(provider, context, expiresAt, requireRefresh)
    }

    companion object {
        const val CLIENT_ID =
            "681255809395-oo8ft2oprdrnp9e3aqf6av3hmdib135j.apps.googleusercontent.com"
        const val AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        const val CALLBACK_PATH = "/oauth2callback"
        const val SCOPES = "openid email profile https://www.googleapis.com/auth/cloud-platform"
        val EXPIRY_MARGIN: Duration = 5.minutes
    }
}
