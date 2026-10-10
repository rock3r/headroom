package dev.sebastiano.headroom.data.account

import dev.sebastiano.headroom.auth.CredentialKind
import dev.sebastiano.headroom.auth.CredentialProvider
import dev.sebastiano.headroom.auth.InMemoryTokenStore
import dev.sebastiano.headroom.auth.StoredCredential
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.AppSettings
import dev.sebastiano.headroom.model.AskOutcome
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.RedeemOutcome
import dev.sebastiano.headroom.model.ResetAttemptKey
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetPool
import dev.sebastiano.headroom.model.ResetScope
import dev.sebastiano.headroom.model.WindowKind
import dev.sebastiano.headroom.quota.ProviderCredentials
import dev.sebastiano.headroom.quota.QuotaFetchers
import dev.sebastiano.headroom.quota.ResetAsker
import dev.sebastiano.headroom.quota.ResetClients
import dev.sebastiano.headroom.quota.ResetLog
import dev.sebastiano.headroom.quota.ResetRedeemer
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class SyncedResetProviderTest {
    private val now = Instant.parse("2026-09-27T12:32:00Z")
    private val store = InMemoryTokenStore()
    private val codex = Account("c1", Provider.Codex, "me")
    private val claude = Account("a1", Provider.Claude, "me")
    private val pool = ResetPool("codex", "Usage limit reset", 2, ResetScope.of(WindowKind.Weekly))
    private val lines = mutableListOf<String>()
    private val log =
        object : ResetLog {
            override fun debug(message: String) {
                lines += message
            }

            override fun warn(message: String) {
                lines += message
            }
        }

    private class RecordingRedeemer(override val provider: Provider) : ResetRedeemer {
        val calls = mutableListOf<Triple<ProviderCredentials, String, String>>()
        val checks = mutableListOf<Triple<ProviderCredentials, String, String>>()

        override suspend fun check(
            credentials: ProviderCredentials,
            poolId: String,
            attemptKey: String,
        ): RedeemOutcome {
            checks += Triple(credentials, poolId, attemptKey)
            return RedeemOutcome.Success(resetsLeft = 0, replayed = true)
        }

        override suspend fun redeem(
            credentials: ProviderCredentials,
            poolId: String,
            attemptKey: String,
        ): RedeemOutcome {
            calls += Triple(credentials, poolId, attemptKey)
            return RedeemOutcome.Success(resetsLeft = 1)
        }
    }

    private class RecordingAsker(override val provider: Provider) : ResetAsker {
        val calls = mutableListOf<ProviderCredentials>()

        override suspend fun ask(credentials: ProviderCredentials): AskOutcome {
            calls += credentials
            return AskOutcome.Granted(poolId = null)
        }
    }

    private val codexRedeemer = RecordingRedeemer(Provider.Codex)
    private val claudeRedeemer = RecordingRedeemer(Provider.Claude)
    private val zAiRedeemer = RecordingRedeemer(Provider.ZAi)
    private val zAiAsker = RecordingAsker(Provider.ZAi)
    private val zAi = Account("z1", Provider.ZAi, "me")

    private val states: List<AccountState> =
        listOf(
            AccountState(
                codex,
                QuotaSnapshot(
                    Provider.Codex,
                    "c1",
                    null,
                    emptyList(),
                    now,
                    resets = ResetAvailability(listOf(pool)),
                ),
            ),
            AccountState(claude, null),
        )

    private var settings = AppSettings()

    private val provider =
        SyncedResetProvider(
            accounts = { states },
            settings = { settings },
            fetcher =
                AccountQuotaFetcher(
                    CredentialProvider(store, emptyMap()),
                    QuotaFetchers(emptyList()),
                ),
            clients =
                ResetClients(
                    emptyList(),
                    listOf(codexRedeemer, claudeRedeemer, zAiRedeemer),
                    listOf(zAiAsker),
                ),
            log = log,
        )

    private suspend fun signIn(account: Account, expiresAt: Instant? = null) {
        store.save(
            StoredCredential(
                account.provider,
                account.id,
                CredentialKind.OAuth,
                "secret-token",
                null,
                expiresAt,
            ),
            expectedRevision = null,
        )
    }

    @Test
    fun `the resets come from the last sync`() = runTest {
        assertEquals(ResetAvailability(listOf(pool)), provider.availability(codex))
        assertNull(provider.availability(claude))
    }

    @Test
    fun `a Codex redeem uses the account's credential and the attempt key`() = runTest {
        signIn(codex)

        val outcome = provider.redeem(codex, "codex", ResetAttemptKey("key-1"))

        assertEquals(RedeemOutcome.Success(resetsLeft = 1), outcome)
        val (credentials, poolId, key) = codexRedeemer.calls.single()
        assertEquals("secret-token", credentials.accessToken)
        assertEquals("codex", poolId)
        assertEquals("key-1", key)
    }

    @Test
    fun `Claude resets cannot be used until the user turns it on`() = runTest {
        signIn(claude)

        val outcome = provider.redeem(claude, "grant", ResetAttemptKey("key-1"))

        assertEquals(RedeemOutcome.Unsupported, outcome)
        assertEquals(
            RedeemOutcome.Unconfirmed,
            provider.check(claude, "grant", ResetAttemptKey("key-1")),
        )
        assertTrue(claudeRedeemer.calls.isEmpty())
        assertTrue(claudeRedeemer.checks.isEmpty())
    }

    @Test
    fun `once turned on, a Claude redeem uses the account's credential and the grant`() = runTest {
        signIn(claude)
        settings = AppSettings(redeemClaudeResets = true)

        val outcome = provider.redeem(claude, "grant", ResetAttemptKey("key-1"))

        assertEquals(RedeemOutcome.Success(resetsLeft = 1), outcome)
        val (credentials, poolId, key) = claudeRedeemer.calls.single()
        assertEquals("secret-token", credentials.accessToken)
        assertEquals("grant", poolId)
        assertEquals("key-1", key)
    }

    @Test
    fun `only a Claude redeem reads the settings, and settings that cannot be read mean off`() =
        runTest {
            signIn(codex)
            signIn(claude)
            val unreadable =
                SyncedResetProvider(
                    accounts = { states },
                    settings = { throw IOException("settings store unreadable") },
                    fetcher =
                        AccountQuotaFetcher(
                            CredentialProvider(store, emptyMap()),
                            QuotaFetchers(emptyList()),
                        ),
                    clients = ResetClients(emptyList(), listOf(codexRedeemer, claudeRedeemer)),
                )

            assertEquals(
                RedeemOutcome.Success(resetsLeft = 1),
                unreadable.redeem(codex, "codex", ResetAttemptKey("key-1")),
            )
            assertEquals(
                RedeemOutcome.Unsupported,
                unreadable.redeem(claude, "grant", ResetAttemptKey("key-2")),
            )
            assertTrue(claudeRedeemer.calls.isEmpty())
        }

    @Test
    fun `a check reads what an unconfirmed attempt did, with the same key`() = runTest {
        signIn(claude)
        settings = AppSettings(redeemClaudeResets = true)

        val outcome = provider.check(claude, "grant", ResetAttemptKey("key-1"))

        assertEquals(RedeemOutcome.Success(resetsLeft = 0, replayed = true), outcome)
        assertEquals("key-1", claudeRedeemer.checks.single().third)
        assertTrue(claudeRedeemer.calls.isEmpty())
    }

    @Test
    fun `a check without a working sign-in stays unconfirmed`() = runTest {
        settings = AppSettings(redeemClaudeResets = true)

        assertEquals(
            RedeemOutcome.Unconfirmed,
            provider.check(claude, "grant", ResetAttemptKey("key-1")),
        )
    }

    @Test
    fun `an account with no working sign-in is asked to sign in again`() = runTest {
        assertEquals(
            RedeemOutcome.SignInAgain,
            provider.redeem(codex, "codex", ResetAttemptKey("key-1")),
        )
        signIn(codex, expiresAt = now - 1.seconds)
        assertEquals(
            RedeemOutcome.SignInAgain,
            provider.redeem(codex, "codex", ResetAttemptKey("key-2")),
        )
        assertTrue(codexRedeemer.calls.isEmpty())
    }

    @Test
    fun `logs the attempt with a hashed account id and never the token`() = runTest {
        signIn(codex)

        provider.redeem(codex, "codex", ResetAttemptKey("key-1"))

        val line = lines.single()
        assertTrue("key-1" in line && "codex" in line && "Success" in line, line)
        assertTrue(lines.none { "secret-token" in it || " c1 " in it || "=c1" in it })
    }

    @Test
    fun `a Z_AI redeem uses the account's credential and the pool`() = runTest {
        signIn(zAi)

        assertEquals(
            RedeemOutcome.Success(resetsLeft = 1),
            provider.redeem(zAi, "five_hour", ResetAttemptKey("key-2")),
        )
        assertEquals("five_hour", zAiRedeemer.calls.single().second)
    }

    @Test
    fun `asking for a Z_AI reset card uses the account's credential`() = runTest {
        signIn(zAi)

        assertEquals(AskOutcome.Granted(poolId = null), provider.askForMore(zAi))
        assertEquals("secret-token", zAiAsker.calls.single().accessToken)
    }

    @Test
    fun `providers without an asker cannot ask`() = runTest {
        signIn(codex)
        assertEquals(AskOutcome.Unsupported, provider.askForMore(codex))
    }

    @Test
    fun `asking without a working sign-in fails for the sign-in`() = runTest {
        assertEquals(AskOutcome.Failed(QuotaErrorKind.Auth), provider.askForMore(zAi))
    }

    @Test
    fun `the resets are read from committed storage, so a sync that just ended shows`() = runTest {
        var committed = emptyList<AccountState>()
        val fromStorage =
            SyncedResetProvider(
                // Like QuotaRepository.current(): a suspending read of what the sync stored.
                accounts = {
                    kotlinx.coroutines.yield()
                    committed
                },
                fetcher =
                    AccountQuotaFetcher(
                        CredentialProvider(store, emptyMap()),
                        QuotaFetchers(emptyList()),
                    ),
                clients = ResetClients(emptyList(), emptyList()),
            )
        assertNull(fromStorage.availability(codex))

        committed = states

        assertEquals(ResetAvailability(listOf(pool)), fromStorage.availability(codex))
    }
}
