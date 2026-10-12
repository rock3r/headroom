package dev.sebastiano.headroom.auth

import dev.sebastiano.headroom.model.Provider
import kotlin.time.Instant

/** How a credential was obtained. */
public enum class CredentialKind {
    /** OAuth tokens from a browser or device-code sign-in. They expire and refresh. */
    OAuth,

    /** An API key the user pasted. It does not expire and never refreshes. */
    ApiKey,
}

/** Keys for [StoredCredential.extras] and [TokenSet.extras]. */
public object CredentialExtras {
    /** ChatGPT account id from the `https://api.openai.com/auth` JWT claim. Codex sends it. */
    public const val CHATGPT_ACCOUNT_ID: String = "chatgpt_account_id"

    /** Claude organization UUID from the token response. */
    public const val CLAUDE_ORGANIZATION_ID: String = "claude_organization_id"

    /**
     * The OpenID `id_token` of a JetBrains sign-in. It is the bearer that obtains the JetBrains AI
     * (Grazie) token, which the quota endpoints need. Each refresh replaces it.
     */
    public const val JETBRAINS_ID_TOKEN: String = "jetbrains_id_token"
}

/** What a sign-in returns, before the app picks an account id for it. */
public data class TokenSet(
    val provider: Provider,
    val kind: CredentialKind,
    val accessToken: String,
    /** For Copilot this is the GitHub token, which mints new Copilot tokens. */
    val refreshToken: String?,
    /** When [accessToken] stops working, already shortened by the provider's safety margin. */
    val expiresAt: Instant?,
    /** The provider's own id for the signed-in account, when it reports one. */
    val providerAccountId: String? = null,
    /** An email address or login to show the user, when the provider reports one. */
    val label: String? = null,
    val extras: Map<String, String> = emptyMap(),
) {
    /** A new credential for the app's account [accountId], not saved yet (revision 0). */
    public fun toCredential(accountId: String): StoredCredential =
        StoredCredential(
            provider = provider,
            accountId = accountId,
            kind = kind,
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAt = expiresAt,
            providerAccountId = providerAccountId,
            label = label,
            extras = extras,
        )

    override fun toString(): String =
        "TokenSet(provider=$provider, kind=$kind, expiresAt=$expiresAt, label=$label)"
}

/**
 * The saved sign-in of one account. The [TokenStore] sets [revision] on every save, so concurrent
 * writers can detect each other.
 */
public data class StoredCredential(
    val provider: Provider,
    val accountId: String,
    val kind: CredentialKind,
    /** The OAuth access token, or the API key. */
    val accessToken: String,
    val refreshToken: String?,
    val expiresAt: Instant?,
    val providerAccountId: String? = null,
    val label: String? = null,
    val extras: Map<String, String> = emptyMap(),
    val revision: Long = 0,
) {
    /** True once [expiresAt] has been reached. Credentials without an expiry never expire. */
    public fun isExpired(now: Instant): Boolean = expiresAt?.let { it <= now } ?: false

    /** The ChatGPT account id Codex requests need, when this is a Codex credential. */
    val chatGptAccountId: String?
        get() = extras[CredentialExtras.CHATGPT_ACCOUNT_ID]

    /**
     * The OpenID ID token of a JetBrains credential. Null for sign-ins made before Headroom kept
     * it; the next refresh fills it in.
     */
    val jetBrainsIdToken: String?
        get() = extras[CredentialExtras.JETBRAINS_ID_TOKEN]

    /**
     * The long-lived GitHub token of a Copilot credential. [accessToken] is the short-lived Copilot
     * token minted from it.
     */
    val gitHubToken: String?
        get() = refreshToken.takeIf { provider == Provider.Copilot }

    /** This credential with the tokens of a refresh. Missing values keep their old value. */
    public fun refreshedWith(tokens: TokenSet): StoredCredential =
        copy(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken ?: refreshToken,
            expiresAt = tokens.expiresAt,
            providerAccountId = tokens.providerAccountId ?: providerAccountId,
            label = tokens.label ?: label,
            extras = extras + tokens.extras,
        )

    override fun toString(): String =
        "StoredCredential(provider=$provider, accountId=$accountId, kind=$kind, " +
            "expiresAt=$expiresAt, label=$label, revision=$revision)"
}
