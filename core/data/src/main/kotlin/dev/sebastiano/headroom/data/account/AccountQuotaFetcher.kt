package dev.sebastiano.headroom.data.account

import dev.sebastiano.headroom.auth.AuthException
import dev.sebastiano.headroom.auth.CredentialProvider
import dev.sebastiano.headroom.auth.StoredCredential
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.quota.ProviderCredentials
import dev.sebastiano.headroom.quota.QuotaFetchers
import java.io.IOException

/** Fetches one account's quota with a credential that is valid now, refreshing it if needed. */
public class AccountQuotaFetcher(
    private val credentials: CredentialProvider,
    private val fetchers: QuotaFetchers,
) {
    public suspend fun fetch(account: Account): QuotaResult {
        val fetcher =
            fetchers.getOrNull(account.provider)
                ?: return QuotaResult.Failure(
                    QuotaErrorKind.Unknown,
                    "No quota fetcher for ${account.provider}",
                )
        val credential =
            try {
                credentials.validCredential(account.id)
            } catch (failure: AuthException) {
                return QuotaResult.Failure(failure.toErrorKind(), failure.message.orEmpty())
            } catch (failure: IOException) {
                // The token store could not read or write the credential.
                return QuotaResult.Failure(QuotaErrorKind.Unknown, failure.message.orEmpty())
            }
        return when (val result = fetcher.fetch(credential.toProviderCredentials())) {
            is QuotaResult.Success ->
                QuotaResult.Success(result.snapshot.copy(accountId = account.id))
            is QuotaResult.Failure -> result
        }
    }

    private fun StoredCredential.toProviderCredentials() =
        ProviderCredentials(
            // Copilot's usage endpoint takes the long-lived GitHub token, not the Copilot token.
            accessToken =
                if (provider == Provider.Copilot) gitHubToken ?: accessToken else accessToken,
            // Codex sends the ChatGPT account id as a header.
            accountId = if (provider == Provider.Codex) chatGptAccountId else providerAccountId,
            // JetBrains trades the ID token for a JetBrains AI token to read the quota.
            idToken = if (provider == Provider.JetBrains) jetBrainsIdToken else null,
        )

    private fun AuthException.toErrorKind(): QuotaErrorKind =
        when (this) {
            is AuthException.Network,
            is AuthException.TimedOut -> QuotaErrorKind.Network
            is AuthException.Rejected ->
                if (requiresSignIn) QuotaErrorKind.Auth else QuotaErrorKind.Unknown
            is AuthException.NotSignedIn,
            is AuthException.SignInExpired,
            is AuthException.SignInFailed -> QuotaErrorKind.Auth
            is AuthException.InvalidResponse -> QuotaErrorKind.Parse
        }
}
