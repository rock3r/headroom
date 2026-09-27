package dev.sebastiano.headroom.data.account

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dev.sebastiano.headroom.auth.CredentialCodec
import dev.sebastiano.headroom.auth.StoredCredential
import dev.sebastiano.headroom.auth.TokenStore
import java.io.IOException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Encrypts and decrypts credentials. [associatedData] binds a ciphertext to its account. */
public interface CredentialCipher {
    public fun encrypt(plain: ByteArray, associatedData: ByteArray): ByteArray

    public fun decrypt(encrypted: ByteArray, associatedData: ByteArray): ByteArray
}

/** AES-256-GCM through Tink, with the key wrapped by a key in the Android Keystore. */
public class TinkCredentialCipher(context: Context) : CredentialCipher {
    private val aead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(context.applicationContext, KEYSET_NAME, KEYSET_FILE)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    override fun encrypt(plain: ByteArray, associatedData: ByteArray): ByteArray =
        aead.encrypt(plain, associatedData)

    override fun decrypt(encrypted: ByteArray, associatedData: ByteArray): ByteArray =
        aead.decrypt(encrypted, associatedData)

    private companion object {
        const val KEYSET_NAME = "credentials_keyset"
        const val KEYSET_FILE = "credentials_keyset_prefs"
        const val MASTER_KEY_URI = "android-keystore://headroom_credentials_master_key"
    }
}

/**
 * The on-device [TokenStore]. Each credential is encrypted with [cipher] and stored in private
 * shared preferences, which the app excludes from backup and device transfer.
 */
public class EncryptedTokenStore(
    context: Context,
    private val cipher: CredentialCipher,
    private val prefs: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE),
) : TokenStore {
    private val mutex = Mutex()

    override suspend fun load(accountId: String): StoredCredential? = mutex.withLock {
        read(accountId)
    }

    override suspend fun save(
        credential: StoredCredential,
        expectedRevision: Long?,
    ): StoredCredential? = mutex.withLock {
        if (read(credential.accountId)?.revision != expectedRevision) return@withLock null
        // Revisions never restart, even after a delete, so a refresh that started before a
        // sign-out cannot match a later sign-in of the same account.
        val next = prefs.getLong(revisionKey(credential.accountId), 0) + 1
        val saved = credential.copy(revision = next)
        val encrypted =
            cipher.encrypt(
                CredentialCodec.encode(saved).toByteArray(),
                saved.accountId.toByteArray(),
            )
        commitOrThrow {
            putString(saved.accountId, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            putLong(revisionKey(saved.accountId), next)
        }
        saved
    }

    override suspend fun delete(accountId: String) {
        mutex.withLock { commitOrThrow { remove(accountId) } }
    }

    /**
     * A credential that is reported as saved must survive a restart: a lost rotated refresh token
     * can leave only a spent one on disk, and a lost delete undoes a sign-out.
     */
    private fun commitOrThrow(changes: SharedPreferences.Editor.() -> Unit) {
        if (!prefs.edit().apply(changes).commit()) {
            throw IOException("Could not write the sign-in to storage")
        }
    }

    private fun revisionKey(accountId: String) = "$REVISION_PREFIX$accountId"

    private fun read(accountId: String): StoredCredential? {
        val stored = prefs.getString(accountId, null) ?: return null
        val plain = cipher.decrypt(Base64.decode(stored, Base64.NO_WRAP), accountId.toByteArray())
        return CredentialCodec.decode(String(plain))
    }

    public companion object {
        public const val FILE: String = "credentials"
        private const val REVISION_PREFIX = "revision:"
    }
}
