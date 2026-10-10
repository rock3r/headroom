package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.auth.CredentialKind
import dev.sebastiano.headroom.auth.StoredCredential
import dev.sebastiano.headroom.model.Provider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException

class SecureTokenStoreTest {
    private val values = MemorySecureValueStore()
    private val store = SecureTokenStore(values)
    private val credential =
        StoredCredential(Provider.Claude, "acc", CredentialKind.OAuth, "access", "refresh", null)

    @Test
    fun `the first save needs no expected revision and starts at revision 1`() = runTest {
        val saved = store.save(credential, expectedRevision = null)

        assertEquals(1, saved?.revision)
        assertEquals(saved, store.load("acc"))
    }

    @Test
    fun `a save with a stale revision changes nothing`() = runTest {
        val first = store.save(credential, expectedRevision = null)!!

        assertNull(store.save(credential.copy(accessToken = "new"), expectedRevision = null))
        assertNull(store.save(credential.copy(accessToken = "new"), expectedRevision = 7))
        assertEquals(first, store.load("acc"))
    }

    @Test
    fun `revisions keep counting after a delete so a stale save cannot win`() = runTest {
        store.save(credential, expectedRevision = null)
        store.delete("acc")

        assertNull(store.load("acc"))
        assertEquals(2, store.save(credential, expectedRevision = null)?.revision)
    }

    @Test
    fun `nothing is stored in clear outside the credential entry`() = runTest {
        store.save(credential, expectedRevision = null)

        assertEquals(setOf("credential.acc", "revision.acc"), values.entries.keys)
    }

    @Test
    fun `a write the device refuses is an IOException and nothing is reported saved`() = runTest {
        values.refuseWrites = true

        assertFailsWith<IOException> { store.save(credential, expectedRevision = null) }
        assertNull(store.load("acc"))
    }
}

/** A [SecureValueStore] in memory that can refuse writes, as a locked keychain does. */
internal class MemorySecureValueStore : SecureValueStore {
    val entries = mutableMapOf<String, String>()
    var refuseWrites = false

    override fun read(key: String): String? = entries[key]

    override fun write(key: String, value: String): Boolean {
        if (refuseWrites) return false
        entries[key] = value
        return true
    }

    override fun delete(key: String): Boolean {
        if (refuseWrites) return false
        entries.remove(key)
        return true
    }
}
