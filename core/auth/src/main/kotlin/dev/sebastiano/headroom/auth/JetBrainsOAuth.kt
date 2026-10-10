package dev.sebastiano.headroom.auth

import dev.sebastiano.headroom.model.Provider
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * JetBrains AI sign-in with the public `junie-cli` client. The registered redirect is
 * `http://localhost:<port>` with no path, on one of the ports 62345 to 62364. The listener binds
 * both loopback families, because `localhost` may resolve to either.
 */
internal class JetBrainsOAuth(
    private val http: AuthHttpClient,
    private val clock: Clock,
    private val authorizeEndpoint: String = AUTHORIZE_URL,
    private val tokenEndpoint: String = TOKEN_URL,
) : BrowserOAuthSpec, TokenRefresher {
    override val provider: Provider = Provider.JetBrains
    override val loopback: LoopbackConfig =
        LoopbackConfig(path = "/", ports = FIRST_PORT..LAST_PORT, bindIpv6 = true)

    override fun loopbackRedirectUri(port: Int): String = "http://localhost:$port"

    override fun authorizeUrl(redirectUri: String, pkce: Pkce, state: String): String =
        urlWithQuery(
            authorizeEndpoint,
            listOf(
                "client_id" to CLIENT_ID,
                "scope" to SCOPE,
                "state" to state,
                "code_challenge" to pkce.challenge,
                "code_challenge_method" to "S256",
                "redirect_uri" to redirectUri,
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
            context = "JetBrains token exchange",
            requireRefresh = true,
        )

    override suspend fun refresh(credential: StoredCredential): TokenSet =
        requestTokens(
            listOf(
                "grant_type" to "refresh_token",
                "refresh_token" to checkNotNull(credential.refreshToken),
                "client_id" to CLIENT_ID,
            ),
            context = "JetBrains token refresh",
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
        val tokens = json.toTokenSet(provider, context, expiresAt, requireRefresh)
        // The quota fetcher trades the ID token for a JetBrains AI token. A refresh without one
        // leaves no extra, so the stored token stays.
        val idToken = json.string("id_token")?.takeIf { it.isNotBlank() }
        return tokens.copy(
            extras = idToken?.let { mapOf(CredentialExtras.JETBRAINS_ID_TOKEN to it) }.orEmpty()
        )
    }

    companion object {
        const val CLIENT_ID = "junie-cli"
        const val AUTHORIZE_URL = "https://junie.jetbrains.com/cli-auth"
        const val TOKEN_URL = "https://oauth.account.jetbrains.com/oauth2/token"
        const val SCOPE = "offline_access openid jb-authn-service"
        const val FIRST_PORT = 62345
        const val LAST_PORT = 62364
        val EXPIRY_MARGIN: Duration = 5.minutes
    }
}
