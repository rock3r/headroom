package dev.sebastiano.headroom.data.account

import dev.sebastiano.headroom.auth.AuthException
import dev.sebastiano.headroom.auth.CredentialExtras
import dev.sebastiano.headroom.auth.CredentialKind
import dev.sebastiano.headroom.auth.CredentialProvider
import dev.sebastiano.headroom.auth.InMemoryTokenStore
import dev.sebastiano.headroom.auth.StoredCredential
import dev.sebastiano.headroom.auth.TokenRefresher
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.quota.ProviderCredentials
import dev.sebastiano.headroom.quota.QuotaFetcher
import dev.sebastiano.headroom.quota.QuotaFetchers
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
}
