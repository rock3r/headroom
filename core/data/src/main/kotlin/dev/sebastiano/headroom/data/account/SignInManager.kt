package dev.sebastiano.headroom.data.account

import dev.sebastiano.headroom.auth.StoredCredential
import dev.sebastiano.headroom.auth.TokenSet
import dev.sebastiano.headroom.auth.TokenStore
import dev.sebastiano.headroom.data.AccountsRepository
import dev.sebastiano.headroom.model.Account
import java.io.IOException
import java.util.UUID

/**
 * A sign-in to refresh an account finished as another account of the provider. Nothing was saved.
 */
public class DifferentAccountException(accountId: String) :
    Exception("The sign-in is not for account $accountId")

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

    /**
     * Stores the tokens of a new sign-in for the existing account [accountId], for example after
     * its sign-in expired. The account keeps its id, and so its history, name and alert switches.
     * When the account is gone, the sign-in adds a new account instead.
     *
     * @throws DifferentAccountException when the provider says the tokens belong to another of its
     *   accounts. The stored credential is left as it was.
     * @throws IOException when the token store keeps refusing the save.
     */
    public suspend fun reauthenticate(accountId: String, tokens: TokenSet): Account {
        val account =
            accounts.current().firstOrNull { it.account.id == accountId }?.account
                ?: return complete(tokens)
        val credential = tokens.toCredential(accountId)
        // A concurrent refresh can bump the revision between the load and the save: try again.
        repeat(SAVE_ATTEMPTS) {
            val current = store.load(accountId)
            if (
                current?.isAnotherAccountThan(tokens) == true || account.provider != tokens.provider
            ) {
                throw DifferentAccountException(accountId)
            }
            if (store.save(credential, expectedRevision = current?.revision) != null) {
                accounts.refresh(accountId)
                return account
            }
        }
        throw IOException("Could not save the new sign-in of account $accountId")
    }

    public suspend fun signOut(accountId: String) {
        store.delete(accountId)
        accounts.removeAccount(accountId)
    }

    private fun StoredCredential.isAnotherAccountThan(tokens: TokenSet): Boolean {
        val stored = providerAccountId ?: return false
        val signedIn = tokens.providerAccountId ?: return false
        return stored != signedIn
    }

    private companion object {
        const val SAVE_ATTEMPTS = 3
    }
}
