package dev.sebastiano.headroom.data.account

import dev.sebastiano.headroom.auth.AuthException
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.AskOutcome
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.RedeemOutcome
import dev.sebastiano.headroom.model.ResetAttemptKey
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetProvider
import dev.sebastiano.headroom.model.canRedeemResets
import dev.sebastiano.headroom.quota.ProviderCredentials
import dev.sebastiano.headroom.quota.ResetClients
import dev.sebastiano.headroom.quota.ResetLog
import dev.sebastiano.headroom.quota.ResetLogRedaction
import java.io.IOException

/**
 * The resets of the signed-in accounts. Their availability comes from the last sync, which reads it
 * with the usage and stores it with the snapshot; this class makes no call to read it. Redeeming
 * goes to the provider's [dev.sebastiano.headroom.quota.ResetRedeemer], and asking for another
 * reset to its [dev.sebastiano.headroom.quota.ResetAsker], with a credential that works now. Both
 * only run for the providers whose resets Headroom can use
 * ([dev.sebastiano.headroom.model.canRedeemResets]).
 */
public class SyncedResetProvider(
    /**
     * The accounts as storage holds them now, such as
     * [dev.sebastiano.headroom.model.QuotaRepository.current].
     */
    private val accounts: suspend () -> List<AccountState>,
    private val fetcher: AccountQuotaFetcher,
    private val clients: ResetClients,
    private val log: ResetLog = ResetLog.None,
) : ResetProvider {
    override suspend fun availability(account: Account): ResetAvailability? =
        accounts().firstOrNull { it.account.id == account.id }?.snapshot?.resets

    override suspend fun redeem(
        account: Account,
        poolId: String,
        attemptKey: ResetAttemptKey,
    ): RedeemOutcome {
        val redeemer = clients.redeemer(account.provider)?.takeIf { redeems(account) }
        val outcome =
            if (redeemer == null) {
                RedeemOutcome.Unsupported
            } else {
                when (val credentials = credentialsOf(account)) {
                    is Credentials.Ready ->
                        redeemer.redeem(credentials.value, poolId, attemptKey.value)
                    is Credentials.Missing -> credentials.outcome
                }
            }
        log.debug(
            "${account.provider.id} use account ${ResetLogRedaction.shortHash(account.id)} " +
                "pool $poolId key ${attemptKey.value}: $outcome"
        )
        return outcome
    }

    override suspend fun askForMore(account: Account): AskOutcome {
        val asker =
            clients.asker(account.provider)?.takeIf { redeems(account) }
                ?: return AskOutcome.Unsupported
        val outcome =
            when (val credentials = credentialsOf(account)) {
                is Credentials.Ready -> asker.ask(credentials.value)
                is Credentials.Missing ->
                    when (val missing = credentials.outcome) {
                        is RedeemOutcome.Failed -> AskOutcome.Failed(missing.kind)
                        else -> AskOutcome.Failed(QuotaErrorKind.Auth)
                    }
            }
        log.debug(
            "${account.provider.id} ask account ${ResetLogRedaction.shortHash(account.id)}: $outcome"
        )
        return outcome
    }

    private fun redeems(account: Account): Boolean = account.provider.canRedeemResets

    private suspend fun credentialsOf(account: Account): Credentials =
        try {
            Credentials.Ready(fetcher.providerCredentials(account))
        } catch (failure: AuthException) {
            Credentials.Missing(failure.toOutcome())
        } catch (_: IOException) {
            Credentials.Missing(RedeemOutcome.Failed(QuotaErrorKind.Unknown))
        }

    private fun AuthException.toOutcome(): RedeemOutcome =
        when (this) {
            is AuthException.Network,
            is AuthException.TimedOut -> RedeemOutcome.Failed(QuotaErrorKind.Network)
            is AuthException.Rejected ->
                if (requiresSignIn) RedeemOutcome.SignInAgain
                else RedeemOutcome.Failed(QuotaErrorKind.Unknown)
            is AuthException.NotSignedIn,
            is AuthException.SignInExpired,
            is AuthException.SignInFailed -> RedeemOutcome.SignInAgain
            is AuthException.InvalidResponse -> RedeemOutcome.Failed(QuotaErrorKind.Parse)
        }

    private sealed interface Credentials {
        data class Ready(val value: ProviderCredentials) : Credentials

        data class Missing(val outcome: RedeemOutcome) : Credentials
    }
}
