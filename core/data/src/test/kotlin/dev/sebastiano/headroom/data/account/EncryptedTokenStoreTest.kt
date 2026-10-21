package dev.sebastiano.headroom.data.account

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.headroom.auth.CredentialKind
import dev.sebastiano.headroom.auth.StoredCredential
import dev.sebastiano.headroom.model.Provider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EncryptedTokenStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** Reversible and obviously not plain text, which is all the store logic needs. */
    private object XorCipher : CredentialCipher {
        override fun encrypt(plain: ByteArray, associatedData: ByteArray) =
            plain.map { (it.toInt() xor 0x5a).toByte() }.toByteArray()

        override fun decrypt(encrypted: ByteArray, associatedData: ByteArray) =
            encrypt(encrypted, associatedData)
    }

    private fun credential(revision: Long = 0) =
        StoredCredential(
            Provider.Claude,
            "a1",
            CredentialKind.OAuth,
            "secret-access",
            "secret-refresh",
            null,
            revision = revision,
        )

    @Test
    fun `saves, loads and deletes a credential`() = runTest {
        val store = EncryptedTokenStore(context, XorCipher)
        val saved = assertNotNull(store.save(credential(), expectedRevision = null))
        assertEquals(1, saved.revision)
        assertEquals("secret-access", store.load("a1")?.accessToken)
        store.delete("a1")
        assertNull(store.load("a1"))
    }

    @Test
    fun `save is compare-and-swap on the revision`() = runTest {
        val store = EncryptedTokenStore(context, XorCipher)
        val first = store.save(credential(), expectedRevision = null)!!
        assertNull(store.save(credential(), expectedRevision = null))
        assertNull(store.save(first, expectedRevision = 7))
        assertEquals(2, store.save(first, expectedRevision = 1)!!.revision)
    }

    @Test
    fun `tokens are not stored in plain text`() = runTest {
        EncryptedTokenStore(context, XorCipher).save(credential(), expectedRevision = null)
        val raw =
            context
                .getSharedPreferences(EncryptedTokenStore.FILE, Context.MODE_PRIVATE)
                .all
                .values
                .joinToString()
        assertFalse(raw.contains("secret-access"))
        assertFalse(raw.contains("secret-refresh"))
    }

    @Test
    fun `revisions keep counting after a delete, so a stale refresh cannot win`() = runTest {
        val store = EncryptedTokenStore(context, XorCipher)
        val first = store.save(credential(), expectedRevision = null)!!
        store.delete("a1")
        val second = store.save(credential(), expectedRevision = null)!!
        assertEquals(2, second.revision)
        assertNull(store.save(first, expectedRevision = first.revision))
    }

    private fun failingPrefs(): android.content.SharedPreferences {
        val real = context.getSharedPreferences("failing", Context.MODE_PRIVATE)
        return object : android.content.SharedPreferences by real {
            override fun edit(): android.content.SharedPreferences.Editor {
                val delegate = real.edit()
                // Simulates a full disk: commit reports failure and writes nothing.
                return object : android.content.SharedPreferences.Editor by delegate {
                    override fun commit() = false

                    override fun apply() = delegate.apply()
                }
            }
        }
    }

    @Test
    fun `a write that does not reach disk is an error, not a success`() = runTest {
        val store = EncryptedTokenStore(context, XorCipher, failingPrefs())
        kotlin.test.assertFailsWith<java.io.IOException> {
            store.save(credential(), expectedRevision = null)
        }
        kotlin.test.assertFailsWith<java.io.IOException> { store.delete("a1") }
    }

    @Test
    fun `TinkCredentialCipher wraps its keyset with a restore key and unlocks backed-up credentials on a new device`() =
        runTest {
            val sourceKeysetPrefs =
                context.getSharedPreferences("source_keyset", Context.MODE_PRIVATE)
            val sourceCipher = TinkCredentialCipher(context, keysetPrefs = sourceKeysetPrefs)
            val sharedTokenPrefs =
                context.getSharedPreferences("migrated_tokens", Context.MODE_PRIVATE)
            val sourceStore = EncryptedTokenStore(context, sourceCipher, sharedTokenPrefs)
            sourceStore.save(credential(), expectedRevision = null)

            val restoreKey = ByteArray(32) { (it + 1).toByte() }
            val wrappedKeyset = sourceCipher.wrapKeyset(restoreKey)

            // On the destination device, the device-local keyset prefs are empty (excluded from
            // backup), while the encrypted token prefs were restored.
            val destKeysetPrefs = context.getSharedPreferences("dest_keyset", Context.MODE_PRIVATE)
            val destCipher =
                TinkCredentialCipher(
                    context,
                    keysetPrefs = destKeysetPrefs,
                    hasPendingRestore = {
                        !destKeysetPrefs.contains(TinkCredentialCipher.KEYSET_NAME)
                    },
                )
            val destStore = EncryptedTokenStore(context, destCipher, sharedTokenPrefs)

            // Before the restore keyset is unwrapped, reading returns null without polluting
            // destKeysetPrefs with a newly generated keyset.
            assertNull(destStore.load("a1"))
            assertFalse(destKeysetPrefs.contains(TinkCredentialCipher.KEYSET_NAME))

            // Once the restore key unwraps the keyset, both the active cipher and a fresh cipher
            // instance can decrypt the restored credentials.
            destCipher.restoreKeyset(wrappedKeyset, restoreKey)
            assertEquals("secret-access", destStore.load("a1")?.accessToken)

            val restartedCipher = TinkCredentialCipher(context, keysetPrefs = destKeysetPrefs)
            val restartedStore = EncryptedTokenStore(context, restartedCipher, sharedTokenPrefs)
            assertEquals("secret-access", restartedStore.load("a1")?.accessToken)
        }

    @Test
    fun `TinkCredentialCipher does not activate a keyset when persisting to keysetPrefs fails`() =
        runTest {
            val sourceCipher =
                TinkCredentialCipher(
                    context,
                    keysetPrefs = context.getSharedPreferences("ok_keyset", Context.MODE_PRIVATE),
                )
            val restoreKey = ByteArray(32) { (it + 1).toByte() }
            val wrappedKeyset = sourceCipher.wrapKeyset(restoreKey)

            val failingKeysetPrefs = failingPrefs()
            val cipher = TinkCredentialCipher(context, keysetPrefs = failingKeysetPrefs)
            val store =
                EncryptedTokenStore(
                    context,
                    cipher,
                    context.getSharedPreferences("tokens_ok", Context.MODE_PRIVATE),
                )

            kotlin.test.assertFailsWith<java.io.IOException> {
                store.save(credential(), expectedRevision = null)
            }
            assertFalse(cipher.hasLocalKeyset())

            kotlin.test.assertFailsWith<java.security.GeneralSecurityException> {
                cipher.restoreKeyset(wrappedKeyset, restoreKey)
            }
            assertFalse(cipher.hasLocalKeyset())
        }

    @Test
    fun `restoreKeyset does not overwrite a local keyset created by a concurrent sign-in`() =
        runTest {
            val sourceCipher =
                TinkCredentialCipher(
                    context,
                    keysetPrefs =
                        context.getSharedPreferences("race_src_keyset", Context.MODE_PRIVATE),
                )
            val restoreKey = ByteArray(32) { (it + 1).toByte() }
            val wrappedKeyset = sourceCipher.wrapKeyset(restoreKey)

            val destKeysetPrefs =
                context.getSharedPreferences("race_dst_keyset", Context.MODE_PRIVATE)
            val destTokenPrefs =
                context.getSharedPreferences("race_dst_tokens", Context.MODE_PRIVATE)
            val destCipher = TinkCredentialCipher(context, keysetPrefs = destKeysetPrefs)
            val destStore = EncryptedTokenStore(context, destCipher, destTokenPrefs)

            // User completes a sign-in while CredentialManager.getCredential() is still in flight.
            destStore.save(credential(), expectedRevision = null)

            // Late-arriving restoreKeyset must not overwrite the keyset used by that sign-in.
            assertFalse(destCipher.restoreKeyset(wrappedKeyset, restoreKey))
            assertEquals("secret-access", destStore.load("a1")?.accessToken)
        }
}
