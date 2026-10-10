package dev.sebastiano.headroom.auth

import dev.sebastiano.headroom.model.Provider
import java.net.URI
import java.net.URISyntaxException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * GitHub Copilot sign-in: GitHub's device flow gives a GitHub token, which is then exchanged for a
 * short-lived Copilot token. The GitHub token is kept as the refresh token, because it mints new
 * Copilot tokens. Only github.com accounts are supported.
 */
internal class CopilotDeviceAuth(
    private val http: AuthHttpClient,
    private val gitHubBaseUrl: String = GITHUB_BASE_URL,
    private val gitHubApiBaseUrl: String = GITHUB_API_BASE_URL,
) : DeviceCodeSpec, TokenRefresher {
    override val provider: Provider = Provider.Copilot

    private val gitHubHeaders =
        mapOf("Accept" to JSON, "User-Agent" to CopilotClientIdentity.USER_AGENT)

    override suspend fun requestCode(): DeviceCodeGrant {
        val context = "GitHub device code request"
        val json =
            http
                .postForm(
                    "$gitHubBaseUrl/login/device/code",
                    listOf("client_id" to CLIENT_ID, "scope" to SCOPE),
                    gitHubHeaders,
                )
                .successJson(context)
        return DeviceCodeGrant(
            userCode = json.requireString("user_code", context),
            deviceCode = json.requireString("device_code", context),
            verificationUri = trustedWebUri(json.requireString("verification_uri", context)),
            verificationUriComplete = null,
            interval = ((json.long("interval") ?: DEFAULT_INTERVAL).coerceAtLeast(1)).seconds,
            expiresIn = json.long("expires_in")?.seconds,
        )
    }

    override suspend fun poll(grant: DeviceCodeGrant): DevicePoll {
        val context = "GitHub device sign-in"
        val response =
            http.postForm(
                "$gitHubBaseUrl/login/oauth/access_token",
                listOf(
                    "client_id" to CLIENT_ID,
                    "device_code" to grant.deviceCode,
                    "grant_type" to DEVICE_GRANT_TYPE,
                ),
                gitHubHeaders,
            )
        val json = parseJsonObject(response.body, context)
        val error = json.string("error")
        return when {
            error == "authorization_pending" -> DevicePoll.Pending
            error == "slow_down" -> DevicePoll.SlowDown
            error == "expired_token" -> DevicePoll.Expired
            error == "access_denied" -> DevicePoll.Denied("GitHub sign-in was denied")
            error != null ->
                throw AuthException.SignInFailed(
                    "GitHub sign-in failed: ${json.string("error_description") ?: error}"
                )
            !response.isSuccessful -> throw response.rejected(context)
            else -> DevicePoll.Authorized(copilotToken(json.requireString("access_token", context)))
        }
    }

    override suspend fun refresh(credential: StoredCredential): TokenSet =
        copilotToken(checkNotNull(credential.refreshToken))

    private suspend fun copilotToken(gitHubToken: String): TokenSet {
        val context = "Copilot token exchange"
        val json =
            http
                .execute(
                    AuthHttpRequest(
                        method = "GET",
                        url = "$gitHubApiBaseUrl/copilot_internal/v2/token",
                        headers =
                            mapOf(
                                "Accept" to JSON,
                                "Authorization" to "Bearer $gitHubToken",
                                "User-Agent" to CopilotClientIdentity.USER_AGENT,
                                "Editor-Version" to CopilotClientIdentity.EDITOR_VERSION,
                                "Editor-Plugin-Version" to
                                    CopilotClientIdentity.EDITOR_PLUGIN_VERSION,
                                "Copilot-Integration-Id" to CopilotClientIdentity.INTEGRATION_ID,
                            ),
                    )
                )
                .successJson(context)
        val expiresAt =
            json.long("expires_at")?.let(Instant::fromEpochSeconds)
                ?: throw AuthException.InvalidResponse("$context response has no expires_at")
        return TokenSet(
            provider = provider,
            kind = CredentialKind.OAuth,
            accessToken = json.requireString("token", context),
            refreshToken = gitHubToken,
            expiresAt = expiresAt.minus(EXPIRY_MARGIN),
        )
    }

    private fun trustedWebUri(value: String): String {
        val uri =
            try {
                URI(value)
            } catch (e: URISyntaxException) {
                throw AuthException.InvalidResponse("Untrusted verification_uri", e)
            }
        if (uri.scheme !in setOf("https", "http") || uri.host.isNullOrBlank()) {
            throw AuthException.InvalidResponse("Untrusted verification_uri")
        }
        return uri.toString()
    }

    companion object {
        const val CLIENT_ID = "Iv1.b507a08c87ecfe98"
        const val SCOPE = "read:user"
        const val GITHUB_BASE_URL = "https://github.com"
        const val GITHUB_API_BASE_URL = "https://api.github.com"
        const val DEVICE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"
        const val DEFAULT_INTERVAL = 5L
        val EXPIRY_MARGIN: Duration = 5.minutes
    }
}

/**
 * The editor identity Copilot endpoints expect. Every Copilot request, including the quota fetch,
 * must send the same four values, so they move together on a version bump.
 */
public object CopilotClientIdentity {
    public const val USER_AGENT: String = "GitHubCopilotChat/0.35.0"
    public const val EDITOR_VERSION: String = "vscode/1.107.0"
    public const val EDITOR_PLUGIN_VERSION: String = "copilot-chat/0.35.0"
    public const val INTEGRATION_ID: String = "vscode-chat"
}
