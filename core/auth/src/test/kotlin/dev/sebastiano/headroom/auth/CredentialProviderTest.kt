package dev.sebastiano.headroom.auth

import dev.sebastiano.headroom.model.Provider
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield

class CredentialProviderTest {
    private val now = Instant.parse("2026-09-27T12:00:00Z")
    private val clock = fixedClock(now)
    private val store = InMemoryTokenStore()

    private fun credential(
        expiresAt: Instant? = now.plus(1.hours),
        refreshToken: String? = "refresh-1",
    ) =
        StoredCredential(
            provider = Provider.Claude,
            accountId = "acc",
            kind = CredentialKind.OAuth,
            accessToken = "access-1",
            refreshToken = refreshToken,
            expiresAt = expiresAt,
            extras = mapOf("org" to "o1"),
        )

    private suspend fun saved(credential: StoredCredential) =
        checkNotNull(store.save(credential, expectedRevision = null))

    private fun provider(refresher: TokenRefresher) =
        CredentialProvider(store, mapOf(Provider.Claude to refresher), clock)

    private val rotating = TokenRefresher { old ->
        TokenSet(
            provider = old.provider,
            kind = CredentialKind.OAuth,
            accessToken = "access-2",
            refreshToken = "refresh-2",
            expiresAt = now.plus(8.hours),
        )
    }

    @Test
    fun `a token that has not expired is returned as it is`() = runTest {
        val stored = saved(credential())
        val result = provider { error("must not refresh") }.validCredential("acc")
        assertEquals(stored, result)
    }

    @Test
    fun `a credential without expiry never refreshes`() = runTest {
        saved(credential(expiresAt = null))
        assertEquals("access-1", provider { error("must not refresh") }.validAccessToken("acc"))
    }

    @Test
    fun `an expired token is refreshed and the rotated token is saved`() = runTest {
        saved(credential(expiresAt = now))

        val result = provider(rotating).validCredential("acc")

        assertEquals("access-2", result.accessToken)
        assertEquals("refresh-2", result.refreshToken)
        assertEquals(now.plus(8.hours), result.expiresAt)
        assertEquals(mapOf("org" to "o1"), result.extras)
        assertEquals(result, store.load("acc"))
        assertEquals(2, result.revision)
    }

    @Test
    fun `a refresh without a new refresh token keeps the old one`() = runTest {
        saved(credential(expiresAt = now - 1.seconds))
        val refresher = TokenRefresher {
            TokenSet(Provider.Claude, CredentialKind.OAuth, "access-2", null, now + 60.seconds)
        }

        val result = provider(refresher).validCredential("acc")

        assertEquals("refresh-1", result.refreshToken)
    }

    @Test
    fun `concurrent callers share one refresh`() = runTest {
        saved(credential(expiresAt = now))
        val calls = AtomicInteger()
        val credentials = provider { old ->
            calls.incrementAndGet()
            yield()
            rotating.refresh(old)
        }

        val results = List(5) { async { credentials.validAccessToken("acc") } }.awaitAll()

        assertEquals(List(5) { "access-2" }, results)
        assertEquals(1, calls.get())
    }

    @Test
    fun `an unknown account is not signed in`() = runTest {
        assertFailsWith<AuthException.NotSignedIn> { provider(rotating).validCredential("nobody") }
    }

    @Test
    fun `an expired token without a refresh token needs a new sign-in`() = runTest {
        saved(credential(expiresAt = now, refreshToken = null))
        assertFailsWith<AuthException.SignInExpired> { provider(rotating).validCredential("acc") }
    }

    @Test
    fun `an expired token without a refresher needs a new sign-in`() = runTest {
        saved(credential(expiresAt = now))
        val credentials = CredentialProvider(store, emptyMap(), clock)
        assertFailsWith<AuthException.SignInExpired> { credentials.validCredential("acc") }
    }

    @Test
    fun `a failed refresh uses a newer credential that another writer saved`() = runTest {
        val first = saved(credential(expiresAt = now))
        val credentials = provider {
            // Another process rotated the single-use refresh token first.
            store.save(
                first.copy(accessToken = "fresh", expiresAt = now + 600.seconds),
                first.revision,
            )
            throw AuthException.Rejected(400, "invalid_grant", "Refresh token already used")
        }

        assertEquals("fresh", credentials.validAccessToken("acc"))
    }

    @Test
    fun `a failed refresh with nothing newer is reported`() = runTest {
        saved(credential(expiresAt = now))
        val failure = AuthException.Rejected(400, "invalid_grant", "Refresh token revoked")
        val credentials = provider { throw failure }

        val error = assertFailsWith<AuthException.Rejected> { credentials.validCredential("acc") }
        assertSame(failure, error)
    }

    @Test
    fun `a refresh that loses the save race returns the newer saved credential`() = runTest {
        val first = saved(credential(expiresAt = now))
        val credentials = provider { old ->
            store.save(
                first.copy(accessToken = "winner", expiresAt = now + 600.seconds),
                first.revision,
            )
            rotating.refresh(old)
        }

        assertEquals("winner", credentials.validAccessToken("acc"))
        assertEquals("winner", store.load("acc")?.accessToken)
    }

    @Test
    fun `an account signed out during a refresh stays signed out`() = runTest {
        saved(credential(expiresAt = now))
        val credentials = provider { old ->
            store.delete("acc")
            rotating.refresh(old)
        }

        assertFailsWith<AuthException.NotSignedIn> { credentials.validAccessToken("acc") }
        assertEquals(null, store.load("acc"))
    }

    @Test
    fun `an account signed out during a failed refresh stays signed out`() = runTest {
        saved(credential(expiresAt = now))
        val credentials = provider {
            store.delete("acc")
            throw AuthException.Rejected(400, "invalid_grant", "gone")
        }

        assertFailsWith<AuthException.NotSignedIn> { credentials.validAccessToken("acc") }
    }
}
