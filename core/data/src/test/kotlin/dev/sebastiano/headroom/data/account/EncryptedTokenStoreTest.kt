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
}
