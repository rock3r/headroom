package dev.sebastiano.headroom.daemon

import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.quota.ProviderCredentials
import dev.sebastiano.headroom.quota.QuotaFetcher
import dev.sebastiano.headroom.quota.ResetRead
import dev.sebastiano.headroom.quota.ResetReader

/**
 * One signed-in account the daemon may read. The token stays in memory for the refresh. It is not
 * written to the cache, the API, or a log.
 */
public data class LiveAccount(
    val provider: Provider,
    val localId: String,
    val accessToken: String,
    val accountId: String? = null,
)

public data class RefreshOutcome(val readings: List<AccountReading>, val notes: List<String>)

/**
 * Reads usage with the shared fetchers, then resets with the shared readers. A failed reset read
 * does not erase the usage, and a failed usage read keeps the last good numbers from [previous],
 * exactly like the in-process cache. This object has no redeem method.
 */
public class LiveRefresh(
    private val fetchers: Map<Provider, QuotaFetcher>,
    private val readers: Map<Provider, ResetReader>,
) {
    public suspend fun refresh(
        accounts: List<LiveAccount>,
        previous: List<AccountReading> = emptyList(),
    ): RefreshOutcome {
        val notes = mutableListOf<String>()
        val lastGood = previous.associateBy { it.key() }
        val readings = accounts.map { account -> read(account, lastGood, notes) }
        return RefreshOutcome(readings, notes)
    }

    private suspend fun read(
        account: LiveAccount,
        previous: Map<String, AccountReading>,
        notes: MutableList<String>,
    ): AccountReading {
        val fetcher = fetchers[account.provider]
        if (fetcher == null) {
            notes += "No fetcher for ${account.provider.id}."
            return keepOrEmpty(account, null, previous, QuotaErrorKind.Unknown)
        }
        val credentials = ProviderCredentials(account.accessToken, accountId = account.accountId)
        return when (val usage = fetcher.fetch(credentials)) {
            is QuotaResult.Failure -> {
                notes += "${account.provider.id} usage failed: ${usage.kind.name}."
                keepOrEmpty(account, null, previous, usage.kind)
            }
            is QuotaResult.Success -> withResets(account, usage.snapshot, credentials, notes)
        }
    }

    private fun keepOrEmpty(
        account: LiveAccount,
        snapshot: dev.sebastiano.headroom.model.QuotaSnapshot?,
        previous: Map<String, AccountReading>,
        error: QuotaErrorKind,
    ): AccountReading {
        val kept = previous[account.key()]?.snapshot
        return AccountReading(
            provider = account.provider,
            accountId = account.localId,
            snapshot = snapshot ?: kept,
            error = error,
        )
    }

    private suspend fun withResets(
        account: LiveAccount,
        snapshot: QuotaSnapshot,
        credentials: ProviderCredentials,
        notes: MutableList<String>,
    ): AccountReading {
        val reader = readers[account.provider]
        if (reader == null) {
            return AccountReading(
                provider = account.provider,
                accountId = account.localId,
                snapshot = snapshot.copy(resets = null, resetsReadFailed = false),
            )
        }
        return when (val read = reader.read(credentials)) {
            is ResetRead.Known ->
                AccountReading(
                    provider = account.provider,
                    accountId = account.localId,
                    snapshot = snapshot.copy(resets = read.availability ?: ResetAvailability.None),
                )
            ResetRead.Failed -> {
                notes += "${account.provider.id} resets were not read."
                AccountReading(
                    provider = account.provider,
                    accountId = account.localId,
                    snapshot = snapshot.copy(resets = null, resetsReadFailed = true),
                )
            }
        }
    }
}

/**
 * The stable identity of a reading, so a failed refresh keeps the previous numbers of the same
 * account.
 */
private fun AccountReading.key(): String = "${provider.id}:$accountId"

private fun LiveAccount.key(): String = "${provider.id}:$localId"
