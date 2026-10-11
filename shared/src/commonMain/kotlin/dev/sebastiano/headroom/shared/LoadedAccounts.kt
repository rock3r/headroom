package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.data.AccountsRepository
import dev.sebastiano.headroom.model.AccountState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * The repository's accounts, from the first time they include every stored account. Until storage
 * has been read, the repository's empty start only means "not read yet", not "no accounts": shown
 * as is, it would flash the demo accounts at someone who has real ones. Android's
 * `DemoModeQuotaRepository.isLoaded` guards against the same.
 */
internal fun AccountsRepository.loadedAccounts(): Flow<List<AccountState>> = flow {
    val stored = current().map { it.account.id }
    emitAll(accounts.dropWhile { shown -> !shown.map { it.account.id }.containsAll(stored) })
}
