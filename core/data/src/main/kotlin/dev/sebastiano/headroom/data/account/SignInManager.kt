package dev.sebastiano.headroom.data.account

import dev.sebastiano.headroom.auth.TokenSet
import dev.sebastiano.headroom.auth.TokenStore
import dev.sebastiano.headroom.data.AccountsRepository
import dev.sebastiano.headroom.model.Account
import java.util.UUID

/** Turns the tokens of a finished sign-in into an account, and signs accounts out. */
public class SignInManager(
    private val store: TokenStore,
    private val accounts: AccountsRepository,
    private val newAccountId: () -> String = { UUID.randomUUID().toString() },
) {
    public suspend fun complete(tokens: TokenSet): Account {
        val account =
            Account(newAccountId(), tokens.provider, tokens.label ?: tokens.provider.displayName)
        store.save(tokens.toCredential(account.id), expectedRevision = null)
        accounts.addAccount(account)
        accounts.refresh(account.id)
        return account
    }

    public suspend fun signOut(accountId: String) {
        store.delete(accountId)
        accounts.removeAccount(accountId)
    }
}
