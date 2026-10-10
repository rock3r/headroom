package dev.sebastiano.headroom.auth

import dev.sebastiano.headroom.model.Provider
import java.security.SecureRandom
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Claude sign-in, the same way the official Claude Code CLI does it: a loopback redirect to
 * `http://localhost:<port>/callback` and, in parallel, a manual redirect whose page shows a
 * `code#state` to paste. Tokens come from a JSON token endpoint.
 */
internal class ClaudeOAuth(
    private val http: AuthHttpClient,
    private val clock: Clock,
    private val authorizeEndpoint: String = AUTHORIZE_URL,
    private val tokenEndpoint: String = TOKEN_URL,
) : BrowserOAuthSpec, TokenRefresher {
    override val provider: Provider = Provider.Claude
    override val loopback: LoopbackConfig = LoopbackConfig(path = "/callback")
    override val manualRedirectUri: String = MANUAL_REDIRECT_URI
    override val pasteRequiresState: Boolean = true

    override fun newState(random: SecureRandom): String = Pkce.randomState(random)

    override fun loopbackRedirectUri(port: Int): String = "http://localhost:$port/callback"

    override fun authorizeUrl(redirectUri: String, pkce: Pkce, state: String): String =
        urlWithQuery(
            authorizeEndpoint,
            listOf(
                "code" to "true",
                "client_id" to CLIENT_ID,
                "response_type" to "code",
                "redirect_uri" to redirectUri,
                "scope" to SCOPES,
                "code_challenge" to pkce.challenge,
                "code_challenge_method" to "S256",
                "state" to state,
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
                "state" to state,
            ),
            context = "Claude token exchange",
        )

    override suspend fun refresh(credential: StoredCredential): TokenSet =
        requestTokens(
            listOf(
                "grant_type" to "refresh_token",
                "refresh_token" to checkNotNull(credential.refreshToken),
                "client_id" to CLIENT_ID,
            ),
            context = "Claude token refresh",
        )

    private suspend fun requestTokens(body: List<Pair<String, String>>, context: String): TokenSet {
        val json = http.postJson(tokenEndpoint, jsonOf(body)).successJson(context)
        val expiresIn =
            json.long("expires_in")
                ?: throw AuthException.InvalidResponse("$context response has no expires_in")
        val account = json.obj("account")
        return TokenSet(
            provider = provider,
            kind = CredentialKind.OAuth,
            accessToken = json.requireString("access_token", context),
            refreshToken = json.string("refresh_token"),
            expiresAt = clock.now() + expiresIn.seconds - EXPIRY_MARGIN,
            providerAccountId = account?.string("uuid"),
            label = account?.string("email_address"),
            extras =
                json
                    .obj("organization")
                    ?.string("uuid")
                    ?.let { mapOf(CredentialExtras.CLAUDE_ORGANIZATION_ID to it) }
                    .orEmpty(),
        )
    }

    companion object {
        const val CLIENT_ID = "9d1c250a-e61b-44d9-88ed-5944d1962f5e"
        const val AUTHORIZE_URL = "https://claude.com/cai/oauth/authorize"
        const val TOKEN_URL = "https://platform.claude.com/v1/oauth/token"
        const val MANUAL_REDIRECT_URI = "https://platform.claude.com/oauth/code/callback"
        const val SCOPES =
            "org:create_api_key user:profile user:inference user:sessions:claude_code " +
                "user:mcp_servers user:file_upload user:plugins"
        val EXPIRY_MARGIN: Duration = 5.minutes
    }
}
