package dev.sebastiano.headroom.auth

import dev.sebastiano.headroom.model.Provider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class InMemoryTokenStoreTest {
    private val store = InMemoryTokenStore()
    private val credential =
        StoredCredential(
            provider = Provider.ZAi,
            accountId = "zai-1",
            kind = CredentialKind.ApiKey,
            accessToken = "key",
            refreshToken = null,
            expiresAt = null,
        )

    @Test
    fun `the first save needs no expected revision and starts at revision 1`() = runTest {
        val saved = store.save(credential, expectedRevision = null)
        assertEquals(1, saved?.revision)
        assertEquals(saved, store.load("zai-1"))
    }

    @Test
    fun `a save with a stale revision changes nothing`() = runTest {
        val first = checkNotNull(store.save(credential, null))
        checkNotNull(store.save(first.copy(accessToken = "second"), first.revision))

        assertNull(store.save(first.copy(accessToken = "stale"), first.revision))
        assertNull(store.save(first.copy(accessToken = "new"), expectedRevision = null))
        assertEquals("second", store.load("zai-1")?.accessToken)
        assertEquals(2, store.load("zai-1")?.revision)
    }

    @Test
    fun `delete removes the account`() = runTest {
        store.save(credential, null)
        store.delete("zai-1")
        assertNull(store.load("zai-1"))
    }
}
