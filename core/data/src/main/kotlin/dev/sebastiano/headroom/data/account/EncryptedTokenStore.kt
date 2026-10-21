package dev.sebastiano.headroom.data.account

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
import com.google.crypto.tink.Aead
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeystoreKmsClient
import com.google.crypto.tink.subtle.AesGcmJce
import com.google.crypto.tink.subtle.Hex
import dev.sebastiano.headroom.auth.CredentialCodec
import dev.sebastiano.headroom.auth.StoredCredential
import dev.sebastiano.headroom.auth.TokenStore
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Encrypts and decrypts credentials. [associatedData] binds a ciphertext to its account. */
public interface CredentialCipher {
    public fun encrypt(plain: ByteArray, associatedData: ByteArray): ByteArray

    public fun decrypt(encrypted: ByteArray, associatedData: ByteArray): ByteArray
}

/**
 * AES-256-GCM through Tink, with the keyset wrapped locally by a key in the Android Keystore and
 * optionally wrapped for device migration by a 256-bit key in Android's Restore Credentials store.
 */
public class TinkCredentialCipher(
    context: Context,
    private val keysetPrefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(KEYSET_FILE, Context.MODE_PRIVATE),
    private val restorePrefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(
            RestoreCredentials.FILE,
            Context.MODE_PRIVATE,
        ),
    private val hasPendingRestore: () -> Boolean = {
        !keysetPrefs.contains(KEYSET_NAME) &&
            restorePrefs.contains(RestoreCredentials.KEY_WRAPPED_KEYSET)
    },
) : CredentialCipher {
    private val lock = Any()
    private var cachedHandle: KeysetHandle? = null
    private var cachedAead: Aead? = null

    init {
        AeadConfig.register()
    }

    override fun encrypt(plain: ByteArray, associatedData: ByteArray): ByteArray =
        aead(allowCreate = true).encrypt(plain, associatedData)

    override fun decrypt(encrypted: ByteArray, associatedData: ByteArray): ByteArray =
        aead(allowCreate = false).decrypt(encrypted, associatedData)

    internal fun hasLocalKeyset(): Boolean =
        synchronized(lock) { cachedHandle != null || keysetPrefs.contains(KEYSET_NAME) }

    /**
     * Encrypts the Tink keyset with [restoreKey] (32 bytes, AES-256-GCM) so it can be backed up in
     * preferences while [restoreKey] itself is stored only in Android's Restore Credentials.
     */
    internal fun wrapKeyset(restoreKey: ByteArray): ByteArray {
        require(restoreKey.size == RESTORE_KEY_BYTES) {
            "Restore key must be $RESTORE_KEY_BYTES bytes"
        }
        val handle = synchronized(lock) { getOrLoadKeysetHandle(allowCreate = true) }
        return TinkProtoKeysetFormat.serializeEncryptedKeyset(
            handle,
            AesGcmJce(restoreKey),
            KEYSET_AAD,
        )
    }

    /**
     * Unwraps [wrappedKeyset] with [restoreKey], persists it into the device-local [keysetPrefs]
     * wrapped by this device's Android Keystore master key, and activates it in memory.
     */
    internal fun restoreKeyset(wrappedKeyset: ByteArray, restoreKey: ByteArray) {
        require(restoreKey.size == RESTORE_KEY_BYTES) {
            "Restore key must be $RESTORE_KEY_BYTES bytes"
        }
        val handle =
            TinkProtoKeysetFormat.parseEncryptedKeyset(
                wrappedKeyset,
                AesGcmJce(restoreKey),
                KEYSET_AAD,
            )
        synchronized(lock) {
            writeKeysetToPrefs(handle)
            cachedHandle = handle
            cachedAead = handle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        }
    }

    private fun aead(allowCreate: Boolean): Aead =
        synchronized(lock) {
            cachedAead?.let {
                return it
            }
            val handle = getOrLoadKeysetHandle(allowCreate)
            val primitive = handle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
            cachedHandle = handle
            cachedAead = primitive
            primitive
        }

    private fun getOrLoadKeysetHandle(allowCreate: Boolean): KeysetHandle {
        cachedHandle?.let {
            return it
        }
        val existingHex = keysetPrefs.getString(KEYSET_NAME, null)
        if (existingHex != null) {
            return parseKeysetFromPrefs(Hex.decode(existingHex))
        }
        if (!allowCreate && hasPendingRestore()) {
            throw GeneralSecurityException("Waiting for Restore Credentials to unwrap the keyset")
        }
        val generated = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
        writeKeysetToPrefs(generated)
        return generated
    }

    private fun parseKeysetFromPrefs(bytes: ByteArray): KeysetHandle {
        val masterAead = masterKeyAead(generateIfMissing = false)
        if (masterAead != null) {
            try {
                return TinkProtoKeysetFormat.parseEncryptedKeyset(bytes, masterAead, EMPTY_AAD)
            } catch (_: GeneralSecurityException) {
                // Fall back to cleartext for test environments without a hardware Keystore.
            }
        }
        return TinkProtoKeysetFormat.parseKeyset(bytes, InsecureSecretKeyAccess.get())
    }

    private fun writeKeysetToPrefs(handle: KeysetHandle) {
        val masterAead = masterKeyAead(generateIfMissing = true)
        val serialized =
            if (masterAead != null) {
                TinkProtoKeysetFormat.serializeEncryptedKeyset(handle, masterAead, EMPTY_AAD)
            } else {
                TinkProtoKeysetFormat.serializeKeyset(handle, InsecureSecretKeyAccess.get())
            }
        keysetPrefs.edit(commit = true) { putString(KEYSET_NAME, Hex.encode(serialized)) }
    }

    private fun masterKeyAead(generateIfMissing: Boolean): Aead? =
        try {
            val client = AndroidKeystoreKmsClient()
            if (generateIfMissing) {
                AndroidKeystoreKmsClient.getOrGenerateNewAeadKey(MASTER_KEY_URI)
            } else {
                client.getAead(MASTER_KEY_URI)
            }
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: ProviderException) {
            null
        } catch (_: IOException) {
            null
        }

    internal companion object {
        const val KEYSET_NAME = "credentials_keyset"
        const val KEYSET_FILE = "credentials_keyset_prefs"
        const val MASTER_KEY_URI = "android-keystore://headroom_credentials_master_key"
        const val RESTORE_KEY_BYTES = 32
        private val EMPTY_AAD = ByteArray(0)
        private val KEYSET_AAD = "headroom-restore-keyset-v1".toByteArray()
    }
}

/**
 * The on-device [TokenStore]. Each credential is encrypted with [cipher] and stored in private
 * shared preferences. The device-local Android Keystore keyset is excluded from backup, while a
 * backup-wrapped keyset can be unlocked on a new phone through Android's Restore Credentials.
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
        val plain =
            try {
                cipher.decrypt(Base64.decode(stored, Base64.NO_WRAP), accountId.toByteArray())
            } catch (_: GeneralSecurityException) {
                return null
            }
        return CredentialCodec.decode(String(plain))
    }

    public companion object {
        public const val FILE: String = "credentials"
        private const val REVISION_PREFIX = "revision:"
    }
}
