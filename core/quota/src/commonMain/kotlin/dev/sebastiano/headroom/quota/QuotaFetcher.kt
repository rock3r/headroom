package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaResult

/**
 * Reads the current usage limits of one provider account.
 *
 * A fetcher never throws for HTTP, network or parsing problems. It reports them as a
 * [QuotaResult.Failure] instead. It does not refresh tokens: the caller passes a token that is
 * valid now.
 */
public interface QuotaFetcher {
    public val provider: Provider

    public suspend fun fetch(credentials: ProviderCredentials): QuotaResult
}

/**
 * What a fetcher needs to call a provider's usage endpoint.
 *
 * @property accessToken The OAuth access token or the API key, sent as a bearer token. See each
 *   fetcher for which token it expects.
 * @property accountId The provider's own account identifier, when the provider needs one (the
 *   ChatGPT account for Codex). Fetchers copy it into `QuotaSnapshot.accountId`.
 * @property baseUrl Replaces the provider's API base URL, for tests and private gateways. Blank
 *   means "use the default".
 * @property idToken The OpenID ID token of the sign-in, when the provider needs one. JetBrains
 *   trades it for a JetBrains AI token. Null for sign-ins that did not keep one.
 * @property refreshToken The OAuth refresh token of the sign-in, when the provider needs one to
 *   read the quota. JetBrains switches its audience to list the account's AI licenses. A fetcher
 *   never stores a token that such a call returns. Null for all other providers.
 * @property zCode The ZCode sign-in of a Z.AI account, which only its resets use. Null for all
 *   other providers.
 */
public class ProviderCredentials(
    public val accessToken: String,
    public val accountId: String? = null,
    public val baseUrl: String? = null,
    public val idToken: String? = null,
    public val refreshToken: String? = null,
    public val zCode: ZCodeSignIn? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is ProviderCredentials &&
            other.accessToken == accessToken &&
            other.accountId == accountId &&
            other.baseUrl == baseUrl &&
            other.idToken == idToken &&
            other.refreshToken == refreshToken &&
            other.zCode == zCode

    override fun hashCode(): Int {
        var result = accessToken.hashCode()
        result = HASH_MULTIPLIER * result + (accountId?.hashCode() ?: 0)
        result = HASH_MULTIPLIER * result + (baseUrl?.hashCode() ?: 0)
        result = HASH_MULTIPLIER * result + (idToken?.hashCode() ?: 0)
        result = HASH_MULTIPLIER * result + (refreshToken?.hashCode() ?: 0)
        result = HASH_MULTIPLIER * result + (zCode?.hashCode() ?: 0)
        return result
    }

    /** Never includes the tokens, so credentials can be logged safely. */
    override fun toString(): String =
        "ProviderCredentials(accessToken=<redacted>, accountId=$accountId, baseUrl=$baseUrl, " +
            "idToken=${redacted(idToken)}, refreshToken=${redacted(refreshToken)}, zCode=$zCode)"

    private fun redacted(token: String?): String = if (token == null) "null" else "<redacted>"

    private companion object {
        const val HASH_MULTIPLIER = 31
    }
}

/**
 * The ZCode sign-in of a Z.AI account. Z.AI serves its resets from `zcode.z.ai`, which does not
 * accept the API key, so the resets need this second sign-in.
 */
public sealed interface ZCodeSignIn {
    /** There is no ZCode sign-in, or it no longer works: the user has to sign in. */
    public data object Missing : ZCodeSignIn

    /** There is one, but it could not be made usable now, for example offline. */
    public data object Unavailable : ZCodeSignIn

    /** The ZCode JWT and the Z.AI business token that every reset call sends. */
    public class Ready(public val jwt: String, public val businessToken: String) : ZCodeSignIn {
        override fun equals(other: Any?): Boolean =
            other is Ready && other.jwt == jwt && other.businessToken == businessToken

        override fun hashCode(): Int = 31 * jwt.hashCode() + businessToken.hashCode()

        /** Never includes the tokens. */
        override fun toString(): String = "Ready(<redacted>)"
    }
}
