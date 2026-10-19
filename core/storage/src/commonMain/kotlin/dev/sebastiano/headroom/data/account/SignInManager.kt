package dev.sebastiano.headroom.data.account

import dev.sebastiano.headroom.auth.AuthException
import dev.sebastiano.headroom.auth.CredentialKind
import dev.sebastiano.headroom.auth.CursorCredential
import dev.sebastiano.headroom.auth.StoredCredential
import dev.sebastiano.headroom.auth.TokenSet
import dev.sebastiano.headroom.auth.TokenStore
import dev.sebastiano.headroom.auth.ZCodeCredential
import dev.sebastiano.headroom.data.AccountsRepository
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AuthFailureReason
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.quota.ProviderCredentials
import dev.sebastiano.headroom.quota.QuotaFetchers
import kotlin.uuid.Uuid
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.IOException

/**
 * A sign-in to refresh an account finished as another account of the provider. Nothing was saved.
 */
public class DifferentAccountException(accountId: String) :
    Exception("The sign-in is not for account $accountId")

/**
 * An API key was rejected by [provider] before it could be saved. [reason] carries the specific
 * auth failure reason when the provider gave one, such as [AuthFailureReason.KeyExpired].
 */
public class InvalidApiKeyException(
    public val provider: Provider,
    public val reason: AuthFailureReason? = null,
) : Exception("The API key for $provider was rejected (${reason ?: "unauthorized"})")

/** Turns the tokens of a finished sign-in into an account, and signs accounts out. */
public class SignInManager(
    private val store: TokenStore,
    private val accounts: AccountsRepository,
    private val fetchers: QuotaFetchers? = null,
    private val newAccountId: () -> String = { Uuid.random().toString() },
) {
    /** Keeps a second sign-in (ZCode, Cursor) from saving while a sign-out runs. */
    private val secondaryLock = Mutex()

    public suspend fun complete(tokens: TokenSet): Account {
        val validated = validateApiKey(tokens)
        val account =
            Account(newAccountId(), tokens.provider, tokens.label ?: tokens.provider.displayName)
        store.save(tokens.toCredential(account.id), expectedRevision = null)
        accounts.addAccount(account)
        storeValidatedOrRefresh(account.id, validated)
        return account
    }

    /**
     * Stores the tokens of a new sign-in for the existing account [accountId], for example after
     * its sign-in expired. The account keeps its id, and so its history, name and alert switches.
     * When the account is gone, the sign-in adds a new account instead.
     *
     * @throws DifferentAccountException when the provider says the tokens belong to another of its
     *   accounts. The stored credential is left as it was.
     * @throws InvalidApiKeyException when [tokens] is an API key and the provider rejects it.
     * @throws AuthException when validating an API key fails because of a network or fetch error.
     * @throws IOException when the token store keeps refusing the save.
     */
    public suspend fun reauthenticate(accountId: String, tokens: TokenSet): Account {
        val account =
            accounts.current().firstOrNull { it.account.id == accountId }?.account
                ?: return complete(tokens)
        if (account.provider != tokens.provider) {
            throw DifferentAccountException(accountId)
        }
        val validated = validateApiKey(tokens)
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
                storeValidatedOrRefresh(accountId, validated)
                return if (label != null) account.copy(label = label) else account
            }
        }
        throw IOException("Could not save the new sign-in of account $accountId")
    }

    /**
     * Runs one fetch with an API key before storing it, so an expired or unauthorized key never
     * replaces a stored credential or reports a successful sign-in, and returns the fetched
     * snapshot so the caller can store it without fetching a second time.
     */
    private suspend fun validateApiKey(tokens: TokenSet): QuotaSnapshot? {
        if (tokens.kind != CredentialKind.ApiKey) return null
        val fetcher = fetchers?.getOrNull(tokens.provider) ?: return null
        return when (
            val result = fetcher.fetch(ProviderCredentials(accessToken = tokens.accessToken))
        ) {
            is QuotaResult.Success -> result.snapshot
            is QuotaResult.Failure ->
                when (result.kind) {
                    QuotaErrorKind.Auth ->
                        throw InvalidApiKeyException(tokens.provider, result.authReason)
                    QuotaErrorKind.Network -> throw AuthException.Network(result.message)
                    else -> throw AuthException.SignInFailed(result.message)
                }
        }
    }

    /**
     * Stores [validated] for [accountId] when validation already fetched the quota, or refreshes
     * the account otherwise.
     */
    private suspend fun storeValidatedOrRefresh(accountId: String, validated: QuotaSnapshot?) {
        if (validated != null) {
            accounts.storeSnapshot(validated.copy(accountId = accountId))
        } else {
            accounts.refresh(accountId)
        }
    }

    /**
     * Stores the ZCode sign-in of the Z.AI account [accountId], which its resets need, next to its
     * API key. It replaces an earlier ZCode sign-in, then refreshes the account so its resets show.
     * A sign-in that finishes after the account was removed is dropped: nothing would sign it out.
     * It never runs at the same time as [signOut], so a sign-out cannot miss the credential it
     * saves.
     *
     * @throws IOException when the token store keeps refusing the save.
     */
    public suspend fun signInToZCode(accountId: String, tokens: TokenSet) {
        val saved = secondaryLock.withLock {
            saveSecondary(accountId, ZCodeCredential.idFor(accountId), tokens, "ZCode")
        }
        if (saved) accounts.refresh(accountId)
    }

    /**
     * Stores the Cursor sign-in of the Grok account [accountId], which its Cursor model pools and
     * Grok Bot pool need, next to its xAI sign-in. It replaces an earlier Cursor sign-in, then
     * refreshes the account so its Cursor windows show. A sign-in that finishes after the account
     * was removed is dropped: nothing would sign it out. It never runs at the same time as
     * [signOut], so a sign-out cannot miss the credential it saves.
     *
     * @throws IOException when the token store keeps refusing the save.
     */
    public suspend fun signInToCursor(accountId: String, tokens: TokenSet) {
        val saved = secondaryLock.withLock {
            saveSecondary(accountId, CursorCredential.idFor(accountId), tokens, "Cursor")
        }
        if (saved) accounts.refresh(accountId)
    }

    /** Saves a second sign-in; false when the account is gone and nothing was saved. */
    private suspend fun saveSecondary(
        accountId: String,
        credentialId: String,
        tokens: TokenSet,
        name: String,
    ): Boolean {
        if (accounts.current().none { it.account.id == accountId }) return false
        val credential = tokens.toCredential(credentialId)
        repeat(SAVE_ATTEMPTS) {
            val current = store.load(credential.accountId)
            if (store.save(credential, expectedRevision = current?.revision) != null) return true
        }
        throw IOException("Could not save the $name sign-in of account $accountId")
    }

    public suspend fun signOut(accountId: String) {
        secondaryLock.withLock {
            store.delete(accountId)
            store.delete(ZCodeCredential.idFor(accountId))
            store.delete(CursorCredential.idFor(accountId))
            accounts.removeAccount(accountId)
        }
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
