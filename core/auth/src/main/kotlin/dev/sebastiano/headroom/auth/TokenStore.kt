package dev.sebastiano.headroom.auth

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Persists one [StoredCredential] per account. The Android implementation encrypts it and keeps it
 * out of backups.
 *
 * Saves are compare-and-swap on [StoredCredential.revision]. A refresh started from an old revision
 * can never overwrite a newer sign-in, a newer refresh, or a sign-out.
 */
public interface TokenStore {
    public suspend fun load(accountId: String): StoredCredential?

    /**
     * Saves [credential] under its account id, but only if the stored revision still equals
     * [expectedRevision]. Pass null when nothing may be stored for the account yet.
     *
     * @return the credential as stored, with a new, higher revision; or null when the stored
     *   revision did not match. On null nothing changed.
     */
    public suspend fun save(
        credential: StoredCredential,
        expectedRevision: Long?,
    ): StoredCredential?

    public suspend fun delete(accountId: String)
}

/** A [TokenStore] in memory, for tests and demo mode. Revisions count up from 1. */
public class InMemoryTokenStore : TokenStore {
    private val mutex = Mutex()
    private val credentials = mutableMapOf<String, StoredCredential>()

    override suspend fun load(accountId: String): StoredCredential? = mutex.withLock {
        credentials[accountId]
    }

    override suspend fun save(
        credential: StoredCredential,
        expectedRevision: Long?,
    ): StoredCredential? = mutex.withLock {
        val current = credentials[credential.accountId]
        if (current?.revision != expectedRevision) return@withLock null
        val saved = credential.copy(revision = (expectedRevision ?: 0) + 1)
        credentials[credential.accountId] = saved
        saved
    }

    override suspend fun delete(accountId: String) {
        mutex.withLock { credentials.remove(accountId) }
    }
}
