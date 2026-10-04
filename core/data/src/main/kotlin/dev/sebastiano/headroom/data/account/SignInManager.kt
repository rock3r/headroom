package dev.sebastiano.headroom.data.account

import dev.sebastiano.headroom.auth.StoredCredential
import dev.sebastiano.headroom.auth.TokenSet
import dev.sebastiano.headroom.auth.TokenStore
import dev.sebastiano.headroom.auth.ZCodeCredential
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
            if (isAnotherAccount(account, current, tokens)) {
                throw DifferentAccountException(accountId)
            }
            if (store.save(credential, expectedRevision = current?.revision) != null) {
                val label = tokens.label?.takeIf { newLabel -> newLabel != account.label }
                if (label != null) accounts.relabelAccount(accountId, label)
                accounts.refresh(accountId)
                return if (label != null) account.copy(label = label) else account
            }
        }
        throw IOException("Could not save the new sign-in of account $accountId")
    }

    /**
     * Stores the ZCode sign-in of the Z.AI account [accountId], which its resets need, next to its
     * API key. It replaces an earlier ZCode sign-in, then refreshes the account so its resets show.
     *
     * @throws IOException when the token store keeps refusing the save.
     */
    public suspend fun signInToZCode(accountId: String, tokens: TokenSet) {
        val credential = tokens.toCredential(ZCodeCredential.idFor(accountId))
        repeat(SAVE_ATTEMPTS) {
            val current = store.load(credential.accountId)
            if (store.save(credential, expectedRevision = current?.revision) != null) {
                accounts.refresh(accountId)
                return
            }
        }
        throw IOException("Could not save the ZCode sign-in of account $accountId")
    }

    public suspend fun signOut(accountId: String) {
        store.delete(accountId)
        store.delete(ZCodeCredential.idFor(accountId))
        accounts.removeAccount(accountId)
    }

    /**
     * True when [tokens] are for another account than [account]. The provider's own account id
     * decides when both sides have one: Claude and Codex always send it, and Grok, Kimi and
     * JetBrains do when they send an ID token. Otherwise the email or login decides, when both
     * sides have one. A sign-in that names no account is accepted: Copilot, and the API keys of
     * Z.AI and OpenCode Go. Nothing tells those apart, and a key the user pastes is theirs to pick.
     */
    private fun isAnotherAccount(
        account: Account,
        current: StoredCredential?,
        tokens: TokenSet,
    ): Boolean {
        if (account.provider != tokens.provider) return true
        val storedId = current?.providerAccountId
        val signedInId = tokens.providerAccountId
        if (storedId != null && signedInId != null) return storedId != signedInId
        val storedLabel = current?.label ?: return false
        val signedInLabel = tokens.label ?: return false
        return !storedLabel.equals(signedInLabel, ignoreCase = true)
    }

    private companion object {
        const val SAVE_ATTEMPTS = 3
    }
}
