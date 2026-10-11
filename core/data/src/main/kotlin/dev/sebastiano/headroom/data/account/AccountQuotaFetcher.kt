package dev.sebastiano.headroom.data.account

import dev.sebastiano.headroom.auth.AuthException
import dev.sebastiano.headroom.auth.CredentialProvider
import dev.sebastiano.headroom.auth.StoredCredential
import dev.sebastiano.headroom.auth.ZCodeCredential
import dev.sebastiano.headroom.auth.ZCodeTokens
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.quota.ProviderCredentials
import dev.sebastiano.headroom.quota.QuotaFetchers
import dev.sebastiano.headroom.quota.ResetClients
import dev.sebastiano.headroom.quota.ResetRead
import dev.sebastiano.headroom.quota.ZCodeSignIn
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Fetches one account's quota with a credential that is valid now, refreshing it if needed. For
 * providers with resets, it reads them in parallel with the usage and adds them to the snapshot. A
 * reset read that fails never fails the fetch: the snapshot says so, and the app keeps the resets
 * it read last.
 *
 * A Z.AI account's resets need its ZCode sign-in, which is saved next to its API key under
 * [ZCodeCredential.idFor] its id. The reset read's credentials carry it as
 * [ProviderCredentials.zCode], refreshed when needed, while the usage fetch runs with the API key
 * alone. A ZCode sign-in that does not work never fails the usage fetch, and a sync waits for it at
 * most [ZCODE_SIGN_IN_WAIT].
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
        val reader = resets.reader(account.provider)
        return coroutineScope {
            // Only the reset read needs the ZCode sign-in. A sync waits for it only so long, so a
            // token endpoint that does not answer never holds up the usage.
            val resetRead = reader?.let {
                async { it.read(credential.toProviderCredentials(syncZCodeSignIn(account))) }
            }
            when (val result = fetcher.fetch(credential.toProviderCredentials(zCode = null))) {
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
        validCredential(account).toProviderCredentials(zCodeSignIn(account))

    /** [zCodeSignIn] for a sync: unavailable when it takes longer than [ZCODE_SIGN_IN_WAIT]. */
    private suspend fun syncZCodeSignIn(account: Account): ZCodeSignIn? {
        if (account.provider != Provider.ZAi) return null
        return withTimeoutOrNull(ZCODE_SIGN_IN_WAIT.inWholeMilliseconds) { zCodeSignIn(account) }
            ?: ZCodeSignIn.Unavailable
    }

    /** The ZCode sign-in of a Z.AI account, refreshed when needed. Null for other providers. */
    private suspend fun zCodeSignIn(account: Account): ZCodeSignIn? {
        if (account.provider != Provider.ZAi) return null
        return try {
            val credential = credentials.validCredential(ZCodeCredential.idFor(account.id))
            val tokens = credential.refreshToken?.let(ZCodeTokens::decode)
            if (tokens == null) ZCodeSignIn.Missing
            else ZCodeSignIn.Ready(tokens.zCodeJwt, credential.accessToken)
        } catch (failure: AuthException) {
            when (failure) {
                is AuthException.NotSignedIn,
                is AuthException.SignInExpired,
                is AuthException.SignInFailed -> ZCodeSignIn.Missing
                is AuthException.Rejected ->
                    if (failure.requiresSignIn) ZCodeSignIn.Missing else ZCodeSignIn.Unavailable
                is AuthException.Network,
                is AuthException.TimedOut,
                is AuthException.InvalidResponse -> ZCodeSignIn.Unavailable
            }
        } catch (_: IOException) {
            ZCodeSignIn.Unavailable
        }
    }

    private suspend fun validCredential(account: Account): StoredCredential =
        credentials.validCredential(account.id)

    private fun StoredCredential.toProviderCredentials(zCode: ZCodeSignIn?) =
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
            zCode = zCode,
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

    public companion object {
        /**
         * How long a sync waits for the ZCode sign-in, refresh included. A token endpoint that
         * answers does so well within it. Past it, the sync reads no resets and keeps the stored
         * ones.
         */
        public val ZCODE_SIGN_IN_WAIT: Duration = 5.seconds
    }
}
