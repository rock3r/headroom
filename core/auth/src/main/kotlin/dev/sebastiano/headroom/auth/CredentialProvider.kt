package dev.sebastiano.headroom.auth

import dev.sebastiano.headroom.model.Provider
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Gets new tokens for an expired credential. One per provider that can refresh. */
public fun interface TokenRefresher {
    /**
     * @throws AuthException.Rejected when the provider refuses the refresh token.
     * @throws AuthException.Network when the provider cannot be reached.
     */
    public suspend fun refresh(credential: StoredCredential): TokenSet
}

/**
 * Hands out credentials that work now. When a credential has expired it refreshes it once, saves
 * the rotated tokens, and returns them. Calls for the same account wait for each other, so a
 * single-use refresh token is only spent once per process.
 */
public class CredentialProvider(
    private val store: TokenStore,
    private val refreshers: Map<Provider, TokenRefresher>,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val mutexes = ConcurrentHashMap<String, Mutex>()

    /** The access token (or API key) of [accountId], refreshed when needed. */
    public suspend fun validAccessToken(accountId: String): String =
        validCredential(accountId).accessToken

    /**
     * The credential of [accountId], refreshed when needed.
     *
     * @throws AuthException.NotSignedIn when nothing is saved for the account.
     * @throws AuthException.SignInExpired when it expired and cannot be refreshed.
     * @throws AuthException when the refresh fails; see [TokenRefresher.refresh].
     */
    public suspend fun validCredential(accountId: String): StoredCredential =
        mutexes
            .computeIfAbsent(accountId) { Mutex() }
            .withLock {
                val current = store.load(accountId) ?: throw AuthException.NotSignedIn(accountId)
                if (!current.isExpired(clock.instant())) current else refresh(current)
            }

    private suspend fun refresh(current: StoredCredential): StoredCredential {
        val refresher = refreshers[current.provider]
        if (refresher == null || current.refreshToken == null) {
            throw AuthException.SignInExpired(current.accountId)
        }
        val tokens =
            try {
                refresher.refresh(current)
            } catch (failure: AuthException) {
                // Another process may have spent the refresh token first and saved the result.
                return newerValid(current) ?: throw failure
            }
        val refreshed = current.refreshedWith(tokens)
        return store.save(refreshed, expectedRevision = current.revision)
            ?: newerValid(current)
            ?: refreshed
    }

    private suspend fun newerValid(current: StoredCredential): StoredCredential? =
        store.load(current.accountId)?.takeIf {
            it.revision != current.revision && !it.isExpired(clock.instant())
        }
}
