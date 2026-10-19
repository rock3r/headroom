package dev.sebastiano.headroom.data

import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.QuotaRepository
import dev.sebastiano.headroom.model.QuotaSnapshot

/** A [QuotaRepository] that also manages which accounts exist. */
public interface AccountsRepository : QuotaRepository {
    public suspend fun addAccount(account: Account)

    /**
     * Stores [snapshot] as the latest snapshot of its account without fetching again, for example
     * when signing in with an API key already fetched the quota to validate the key.
     */
    public suspend fun storeSnapshot(snapshot: QuotaSnapshot)

    public suspend fun removeAccount(accountId: String)

    /** Names the account. A blank or null [nickname] removes the name. */
    public suspend fun renameAccount(accountId: String, nickname: String?)

    /** Stores what the provider calls the account, for example a changed email address. */
    public suspend fun relabelAccount(accountId: String, label: String)

    /** Puts the accounts in the order of [orderedIds]. Every list of accounts follows it. */
    public suspend fun reorderAccounts(orderedIds: List<String>)
}
