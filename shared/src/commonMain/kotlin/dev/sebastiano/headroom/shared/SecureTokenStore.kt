package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.auth.CredentialCodec
import dev.sebastiano.headroom.auth.StoredCredential
import dev.sebastiano.headroom.auth.TokenStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.IOException

/**
 * Text values kept safe by the platform. On iOS the app implements it with the Keychain, available
 * after the first unlock, on this device only, so the values never reach a backup or another
 * device.
 */
public interface SecureValueStore {
    public fun read(key: String): String?

    /** @return false when the value could not be stored. */
    public fun write(key: String, value: String): Boolean

    /** @return false when the value could not be removed. Removing a missing value succeeds. */
    public fun delete(key: String): Boolean
}

/**
 * The [TokenStore] of the iOS app: each credential is one entry of a [SecureValueStore]. Like the
 * Android store, saves are compare-and-swap on the revision, and revisions keep counting after a
 * delete, so a refresh that started before a sign-out cannot overwrite a later sign-in.
 */
public class SecureTokenStore(private val values: SecureValueStore) : TokenStore {
    private val mutex = Mutex()

    override suspend fun load(accountId: String): StoredCredential? = mutex.withLock {
        read(accountId)
    }

    override suspend fun save(
        credential: StoredCredential,
        expectedRevision: Long?,
    ): StoredCredential? = mutex.withLock {
        if (read(credential.accountId)?.revision != expectedRevision) return@withLock null
        val next = (values.read(revisionKey(credential.accountId))?.toLongOrNull() ?: 0) + 1
        val saved = credential.copy(revision = next)
        // The revision goes first: a crash between the two writes then only skips a number.
        writeOrThrow(revisionKey(saved.accountId), next.toString())
        writeOrThrow(credentialKey(saved.accountId), CredentialCodec.encode(saved))
        saved
    }

    override suspend fun delete(accountId: String) {
        mutex.withLock {
            if (!values.delete(credentialKey(accountId))) {
                throw IOException("Could not remove the sign-in from the keychain")
            }
        }
    }

    private fun read(accountId: String): StoredCredential? =
        values.read(credentialKey(accountId))?.let { stored ->
            // A credential that cannot be read is treated as missing: the user signs in again.
            runCatching { CredentialCodec.decode(stored) }.getOrNull()
        }

    /**
     * A credential reported as saved must survive a restart: a lost rotated refresh token can leave
     * only a spent one behind.
     */
    private fun writeOrThrow(key: String, value: String) {
        if (!values.write(key, value))
            throw IOException("Could not write the sign-in to the keychain")
    }

    private fun credentialKey(accountId: String) = "credential.$accountId"

    private fun revisionKey(accountId: String) = "revision.$accountId"
}
