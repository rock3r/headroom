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
import dev.sebastiano.headroom.quota.ResetClients
import dev.sebastiano.headroom.quota.ResetRead
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Fetches one account's quota with a credential that is valid now, refreshing it if needed. For
 * providers with resets, it reads them in parallel with the usage and adds them to the snapshot. A
 * reset read that fails never fails the fetch: the snapshot says so, and the app keeps the resets
 * it read last.
 */
public class AccountQuotaFetcher(
    private val credentials: CredentialProvider,
    private val fetchers: QuotaFetchers,
    private val resets: ResetClients = ResetClients.None,
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
                validCredential(account)
            } catch (failure: AuthException) {
                return QuotaResult.Failure(failure.toErrorKind(), failure.message.orEmpty())
            } catch (failure: IOException) {
                // The token store could not read or write the credential.
                return QuotaResult.Failure(QuotaErrorKind.Unknown, failure.message.orEmpty())
            }
        val providerCredentials = credential.toProviderCredentials()
        val reader = resets.reader(account.provider)
        return coroutineScope {
            val resetRead = reader?.let { async { it.read(providerCredentials) } }
            when (val result = fetcher.fetch(providerCredentials)) {
                is QuotaResult.Success -> {
                    val read = resetRead?.await()
                    QuotaResult.Success(
                        result.snapshot.copy(
                            accountId = account.id,
                            resets = (read as? ResetRead.Known)?.availability,
                            resetsReadFailed = read == ResetRead.Failed,
                        )
                    )
                }
                is QuotaResult.Failure -> {
                    resetRead?.cancel()
                    result
                }
            }
        }
    }

    /**
     * The credential of [account] as the providers need it, refreshed when needed. The reset
     * redeemers use it too.
     *
     * @throws AuthException when there is no working sign-in.
     * @throws IOException when the token store cannot be read or written.
     */
    internal suspend fun providerCredentials(account: Account): ProviderCredentials =
        validCredential(account).toProviderCredentials()

    private suspend fun validCredential(account: Account): StoredCredential =
        credentials.validCredential(account.id)

    private fun StoredCredential.toProviderCredentials() =
        ProviderCredentials(
            // Copilot's usage endpoint takes the long-lived GitHub token, not the Copilot token.
            accessToken =
                if (provider == Provider.Copilot) gitHubToken ?: accessToken else accessToken,
            // Codex sends the ChatGPT account id as a header.
            accountId = if (provider == Provider.Codex) chatGptAccountId else providerAccountId,
            // JetBrains trades the ID token for a JetBrains AI token to read the quota.
            idToken = if (provider == Provider.JetBrains) jetBrainsIdToken else null,
            // JetBrains switches the refresh token's audience to list the account's AI licenses.
            refreshToken = if (provider == Provider.JetBrains) refreshToken else null,
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
