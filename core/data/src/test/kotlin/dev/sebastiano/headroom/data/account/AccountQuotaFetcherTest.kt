package dev.sebastiano.headroom.data.account

import dev.sebastiano.headroom.auth.AuthException
import dev.sebastiano.headroom.auth.CredentialExtras
import dev.sebastiano.headroom.auth.CredentialKind
import dev.sebastiano.headroom.auth.CredentialProvider
import dev.sebastiano.headroom.auth.InMemoryTokenStore
import dev.sebastiano.headroom.auth.StoredCredential
import dev.sebastiano.headroom.auth.TokenRefresher
import dev.sebastiano.headroom.auth.TokenSet
import dev.sebastiano.headroom.auth.ZCodeCredential
import dev.sebastiano.headroom.auth.ZCodeTokens
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetPool
import dev.sebastiano.headroom.model.ResetScope
import dev.sebastiano.headroom.model.WindowKind
import dev.sebastiano.headroom.quota.ProviderCredentials
import dev.sebastiano.headroom.quota.QuotaFetcher
import dev.sebastiano.headroom.quota.QuotaFetchers
import dev.sebastiano.headroom.quota.ResetClients
import dev.sebastiano.headroom.quota.ResetRead
import dev.sebastiano.headroom.quota.ResetReader
import dev.sebastiano.headroom.quota.ZCodeSignIn
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest

class AccountQuotaFetcherTest {
    private val now = Instant.parse("2026-09-27T12:32:00Z")
    private val store = InMemoryTokenStore()

    private class RecordingFetcher(override val provider: Provider) : QuotaFetcher {
        val seen = mutableListOf<ProviderCredentials>()

        override suspend fun fetch(credentials: ProviderCredentials): QuotaResult {
            seen += credentials
            return QuotaResult.Success(
                QuotaSnapshot(provider, "provider-side-id", "Pro", emptyList(), Instant.EPOCH)
            )
        }
    }

    private suspend fun saveCredential(
        accountId: String,
        provider: Provider,
        refreshToken: String? = null,
        extras: Map<String, String> = emptyMap(),
        expiresAt: Instant? = null,
    ) {
        store.save(
            StoredCredential(
                provider,
                accountId,
                CredentialKind.OAuth,
                "access-$accountId",
                refreshToken,
                expiresAt,
                extras = extras,
            ),
            expectedRevision = null,
        )
    }

    private fun fetcherFor(
        vararg fetchers: QuotaFetcher,
        refreshers: Map<Provider, TokenRefresher> = emptyMap(),
    ) = AccountQuotaFetcher(CredentialProvider(store, refreshers), QuotaFetchers(fetchers.toList()))

    @Test
    fun `passes the access token and labels the snapshot with our account id`() = runTest {
        val claude = RecordingFetcher(Provider.Claude)
        saveCredential("a1", Provider.Claude)
        val result = fetcherFor(claude).fetch(Account("a1", Provider.Claude, "sam"))
        assertEquals("access-a1", claude.seen.single().accessToken)
        assertEquals("a1", assertIs<QuotaResult.Success>(result).snapshot.accountId)
    }

    @Test
    fun `Codex sends the ChatGPT account id`() = runTest {
        val codex = RecordingFetcher(Provider.Codex)
        saveCredential(
            "c1",
            Provider.Codex,
            extras = mapOf(CredentialExtras.CHATGPT_ACCOUNT_ID to "chatgpt-42"),
        )
        fetcherFor(codex).fetch(Account("c1", Provider.Codex, "sam"))
        assertEquals("chatgpt-42", codex.seen.single().accountId)
    }

    @Test
    fun `JetBrains sends the ID token, and other providers never do`() = runTest {
        val jetBrains = RecordingFetcher(Provider.JetBrains)
        val claude = RecordingFetcher(Provider.Claude)
        saveCredential(
            "j1",
            Provider.JetBrains,
            extras = mapOf(CredentialExtras.JETBRAINS_ID_TOKEN to "id-token-j1"),
        )
        saveCredential(
            "a1",
            Provider.Claude,
            extras = mapOf(CredentialExtras.JETBRAINS_ID_TOKEN to "stray"),
        )
        val fetcher = fetcherFor(jetBrains, claude)

        fetcher.fetch(Account("j1", Provider.JetBrains, "sam"))
        fetcher.fetch(Account("a1", Provider.Claude, "sam"))

        assertEquals("id-token-j1", jetBrains.seen.single().idToken)
        assertNull(claude.seen.single().idToken)
    }

    @Test
    fun `JetBrains sends the refresh token, and other providers never do`() = runTest {
        val jetBrains = RecordingFetcher(Provider.JetBrains)
        val claude = RecordingFetcher(Provider.Claude)
        saveCredential("j1", Provider.JetBrains, refreshToken = "refresh-j1")
        saveCredential("a1", Provider.Claude, refreshToken = "refresh-a1")
        val fetcher = fetcherFor(jetBrains, claude)

        fetcher.fetch(Account("j1", Provider.JetBrains, "sam"))
        fetcher.fetch(Account("a1", Provider.Claude, "sam"))

        assertEquals("refresh-j1", jetBrains.seen.single().refreshToken)
        assertNull(claude.seen.single().refreshToken)
    }

    @Test
    fun `a JetBrains sign-in from before ID tokens were kept sends none`() = runTest {
        val jetBrains = RecordingFetcher(Provider.JetBrains)
        saveCredential("j1", Provider.JetBrains)

        fetcherFor(jetBrains).fetch(Account("j1", Provider.JetBrains, "sam"))

        assertNull(jetBrains.seen.single().idToken)
    }

    @Test
    fun `Copilot uses the GitHub token, not the Copilot token`() = runTest {
        val copilot = RecordingFetcher(Provider.Copilot)
        saveCredential("g1", Provider.Copilot, refreshToken = "github-token")
        fetcherFor(copilot).fetch(Account("g1", Provider.Copilot, "sam"))
        assertEquals("github-token", copilot.seen.single().accessToken)
    }

    @Test
    fun `a missing sign-in is an auth failure`() = runTest {
        val result =
            fetcherFor(RecordingFetcher(Provider.Claude))
                .fetch(Account("nope", Provider.Claude, "sam"))
        assertEquals(QuotaErrorKind.Auth, assertIs<QuotaResult.Failure>(result).kind)
    }

    @Test
    fun `a rejected refresh is an auth failure and a network error stays a network failure`() =
        runTest {
            saveCredential(
                "a1",
                Provider.Claude,
                refreshToken = "r",
                expiresAt = now.minusSeconds(1),
            )
            val rejected = TokenRefresher {
                throw AuthException.Rejected(401, "invalid_grant", "no")
            }
            val offline = TokenRefresher { throw AuthException.Network("offline") }
            val account = Account("a1", Provider.Claude, "sam")
            val fetcher = RecordingFetcher(Provider.Claude)
            assertEquals(
                QuotaErrorKind.Auth,
                assertIs<QuotaResult.Failure>(
                        fetcherFor(fetcher, refreshers = mapOf(Provider.Claude to rejected))
                            .fetch(account)
                    )
                    .kind,
            )
            assertEquals(
                QuotaErrorKind.Network,
                assertIs<QuotaResult.Failure>(
                        fetcherFor(fetcher, refreshers = mapOf(Provider.Claude to offline))
                            .fetch(account)
                    )
                    .kind,
            )
        }

    @Test
    fun `a provider without a fetcher is an unknown failure`() = runTest {
        saveCredential("k1", Provider.Kimi)
        val result =
            fetcherFor(RecordingFetcher(Provider.Claude)).fetch(Account("k1", Provider.Kimi, "sam"))
        assertEquals(QuotaErrorKind.Unknown, assertIs<QuotaResult.Failure>(result).kind)
    }

    @Test
    fun `a storage failure while refreshing is a failure result, not a crash`() = runTest {
        val broken =
            object : dev.sebastiano.headroom.auth.TokenStore {
                override suspend fun load(accountId: String) = throw java.io.IOException("disk")

                override suspend fun save(credential: StoredCredential, expectedRevision: Long?) =
                    null

                override suspend fun delete(accountId: String) = Unit
            }
        val fetcher =
            AccountQuotaFetcher(
                CredentialProvider(broken, emptyMap()),
                QuotaFetchers(listOf(RecordingFetcher(Provider.Claude))),
            )
        val result = fetcher.fetch(Account("a1", Provider.Claude, "sam"))
        assertEquals(QuotaErrorKind.Unknown, assertIs<QuotaResult.Failure>(result).kind)
    }

    private class FixedReader(override val provider: Provider, private val read: ResetRead) :
        ResetReader {
        val seen = mutableListOf<ProviderCredentials>()

        override suspend fun read(credentials: ProviderCredentials): ResetRead {
            seen += credentials
            return read
        }
    }

    private val pool = ResetPool("grok", "Weekly limit reset", 2, ResetScope.of(WindowKind.Weekly))

    @Test
    fun `the resets are read in the same sync, with the same credential`() = runTest {
        saveCredential("g1", Provider.Grok)
        val reader = FixedReader(Provider.Grok, ResetRead.Known(ResetAvailability(listOf(pool))))
        val fetcher =
            AccountQuotaFetcher(
                CredentialProvider(store, emptyMap()),
                QuotaFetchers(listOf(RecordingFetcher(Provider.Grok))),
                ResetClients(readers = listOf(reader), redeemers = emptyList()),
            )

        val result = fetcher.fetch(Account("g1", Provider.Grok, "me"))

        val snapshot = assertIs<QuotaResult.Success>(result).snapshot
        assertEquals(ResetAvailability(listOf(pool)), snapshot.resets)
        assertEquals(false, snapshot.resetsReadFailed)
        assertEquals("access-g1", reader.seen.single().accessToken)
        assertNull(reader.seen.single().zCode)
    }

    @Test
    fun `resets that cannot be read never fail the sync`() = runTest {
        saveCredential("g1", Provider.Grok)
        val fetcher =
            AccountQuotaFetcher(
                CredentialProvider(store, emptyMap()),
                QuotaFetchers(listOf(RecordingFetcher(Provider.Grok))),
                ResetClients(
                    readers = listOf(FixedReader(Provider.Grok, ResetRead.Failed)),
                    redeemers = emptyList(),
                ),
            )

        val snapshot =
            assertIs<QuotaResult.Success>(fetcher.fetch(Account("g1", Provider.Grok, "me")))
                .snapshot

        assertNull(snapshot.resets)
        assertEquals(true, snapshot.resetsReadFailed)
    }

    @Test
    fun `a provider without resets reads none`() = runTest {
        saveCredential("k1", Provider.Kimi)
        val fetcher =
            AccountQuotaFetcher(
                CredentialProvider(store, emptyMap()),
                QuotaFetchers(listOf(RecordingFetcher(Provider.Kimi))),
                ResetClients.None,
            )

        val snapshot =
            assertIs<QuotaResult.Success>(fetcher.fetch(Account("k1", Provider.Kimi, "me")))
                .snapshot

        assertNull(snapshot.resets)
        assertEquals(false, snapshot.resetsReadFailed)
    }

    private suspend fun saveZCode(accountId: String, expiresAt: Instant?) {
        store.save(
            StoredCredential(
                Provider.ZAi,
                ZCodeCredential.idFor(accountId),
                CredentialKind.OAuth,
                "business",
                ZCodeTokens("zai-oauth", "zcode-jwt").encode(),
                expiresAt,
            ),
            expectedRevision = null,
        )
    }

    private val zAiAccount = Account("z1", Provider.ZAi, "sam")

    private val zAiResets = FixedReader(Provider.ZAi, ResetRead.Known(null))

    private fun zAiFetcherFor(
        zAi: QuotaFetcher,
        refreshers: Map<Provider, TokenRefresher> = emptyMap(),
    ) =
        AccountQuotaFetcher(
            CredentialProvider(store, refreshers),
            QuotaFetchers(listOf(zAi)),
            ResetClients(readers = listOf(zAiResets), redeemers = emptyList()),
        )

    @Test
    fun `Z_AI usage uses the API key, and its resets the ZCode sign-in`() = runTest {
        val zAi = RecordingFetcher(Provider.ZAi)
        saveCredential("z1", Provider.ZAi)
        saveZCode("z1", expiresAt = null)

        zAiFetcherFor(zAi).fetch(zAiAccount)

        val usage = zAi.seen.single()
        assertEquals("access-z1", usage.accessToken)
        assertNull(usage.zCode)
        val resets = zAiResets.seen.single()
        assertEquals("access-z1", resets.accessToken)
        assertEquals(ZCodeSignIn.Ready("zcode-jwt", "business"), resets.zCode)
    }

    @Test
    fun `a Z_AI account without a ZCode sign-in says so`() = runTest {
        saveCredential("z1", Provider.ZAi)

        zAiFetcherFor(RecordingFetcher(Provider.ZAi)).fetch(zAiAccount)

        assertEquals(ZCodeSignIn.Missing, zAiResets.seen.single().zCode)
    }

    @Test
    fun `an expired ZCode sign-in is refreshed first`() = runTest {
        saveCredential("z1", Provider.ZAi)
        saveZCode("z1", expiresAt = Instant.EPOCH)
        val refresher = TokenRefresher { old ->
            TokenSet(Provider.ZAi, CredentialKind.OAuth, "business-2", old.refreshToken, null)
        }

        zAiFetcherFor(RecordingFetcher(Provider.ZAi), mapOf(Provider.ZAi to refresher))
            .fetch(zAiAccount)

        assertEquals(ZCodeSignIn.Ready("zcode-jwt", "business-2"), zAiResets.seen.single().zCode)
    }

    @Test
    fun `a ZCode refresh that fails leaves the sign-in unavailable or missing`() = runTest {
        saveCredential("z1", Provider.ZAi)
        saveZCode("z1", expiresAt = Instant.EPOCH)
        listOf(
                AuthException.Network("offline") to ZCodeSignIn.Unavailable,
                AuthException.Rejected(401, null, "revoked") to ZCodeSignIn.Missing,
            )
            .forEach { (failure, expected) ->
                zAiResets.seen.clear()
                val refresher = TokenRefresher { throw failure }
                zAiFetcherFor(RecordingFetcher(Provider.ZAi), mapOf(Provider.ZAi to refresher))
                    .fetch(zAiAccount)
                assertEquals(expected, zAiResets.seen.single().zCode)
            }
    }

    @Test
    fun `a slow ZCode refresh does not hold up the usage`() = runTest {
        saveCredential("z1", Provider.ZAi)
        saveZCode("z1", expiresAt = Instant.EPOCH)
        val refresher = TokenRefresher {
            delay(ZCODE_TIMEOUT_MS)
            throw AuthException.TimedOut("token endpoint unreachable")
        }
        var usageFetchedAt: Long? = null
        val zAi =
            object : QuotaFetcher {
                override val provider = Provider.ZAi

                override suspend fun fetch(credentials: ProviderCredentials): QuotaResult {
                    usageFetchedAt = testScheduler.currentTime
                    return QuotaResult.Success(
                        QuotaSnapshot(provider, "z", "Pro", emptyList(), Instant.EPOCH)
                    )
                }
            }

        val result = zAiFetcherFor(zAi, mapOf(Provider.ZAi to refresher)).fetch(zAiAccount)

        assertEquals(0L, usageFetchedAt)
        // The sync gives up on the ZCode sign-in long before the refresh's own timeout.
        assertEquals(AccountQuotaFetcher.ZCODE_SIGN_IN_WAIT.toMillis(), testScheduler.currentTime)
        assertIs<QuotaResult.Success>(result)
        assertEquals(ZCodeSignIn.Unavailable, zAiResets.seen.single().zCode)
    }

    @Test
    fun `other providers carry no ZCode sign-in`() = runTest {
        val claude = RecordingFetcher(Provider.Claude)
        saveCredential("a1", Provider.Claude)
        fetcherFor(claude).fetch(Account("a1", Provider.Claude, "sam"))
        assertNull(claude.seen.single().zCode)
    }

    private companion object {
        const val ZCODE_TIMEOUT_MS = 30_000L
    }
}
