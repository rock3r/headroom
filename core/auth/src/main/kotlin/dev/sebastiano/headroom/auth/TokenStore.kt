package dev.sebastiano.headroom.auth

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Persists one [StoredCredential] per account. The Android implementation encrypts it and keeps it
 * out of backups.
 *
 * Saves are compare-and-swap on [StoredCredential.revision]. A refresh started from an old revision
 * can never overwrite a newer sign-in, a newer refresh, or a sign-out.
 *
 * Revisions must only grow for an account id, also across [delete]: a sign-in after a sign-out gets
 * a higher revision than anything saved before, so a stale save cannot match it.
 */
public interface TokenStore {
    public suspend fun load(accountId: String): StoredCredential?

    /**
     * Saves [credential] under its account id, but only if the stored revision still equals
     * [expectedRevision]. Pass null when nothing may be stored for the account yet.
     *
     * @return the credential as stored, with a revision higher than any this account had before,
     *   even before a [delete]; or null when the stored revision did not match. On null nothing
     *   changed.
     */
    public suspend fun save(
        credential: StoredCredential,
        expectedRevision: Long?,
    ): StoredCredential?

    public suspend fun delete(accountId: String)
}

/**
 * A [TokenStore] in memory, for tests and demo mode. Revisions count up from 1 per account and keep
 * counting after a delete.
 */
public class InMemoryTokenStore : TokenStore {
    private val mutex = Mutex()
    private val credentials = mutableMapOf<String, StoredCredential>()
    private val lastRevisions = mutableMapOf<String, Long>()

    override suspend fun load(accountId: String): StoredCredential? = mutex.withLock {
        credentials[accountId]
    }

    override suspend fun save(
        credential: StoredCredential,
        expectedRevision: Long?,
    ): StoredCredential? = mutex.withLock {
        val current = credentials[credential.accountId]
        if (current?.revision != expectedRevision) return@withLock null
        val revision = (lastRevisions[credential.accountId] ?: 0) + 1
        val saved = credential.copy(revision = revision)
        credentials[credential.accountId] = saved
        lastRevisions[credential.accountId] = revision
        saved
    }

    override suspend fun delete(accountId: String) {
        mutex.withLock { credentials.remove(accountId) }
    }
}
