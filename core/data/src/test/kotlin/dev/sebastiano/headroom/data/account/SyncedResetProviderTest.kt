package dev.sebastiano.headroom.data.account

import dev.sebastiano.headroom.auth.CredentialKind
import dev.sebastiano.headroom.auth.CredentialProvider
import dev.sebastiano.headroom.auth.InMemoryTokenStore
import dev.sebastiano.headroom.auth.StoredCredential
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.RedeemOutcome
import dev.sebastiano.headroom.model.ResetAttemptKey
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetPool
import dev.sebastiano.headroom.model.ResetScope
import dev.sebastiano.headroom.model.WindowKind
import dev.sebastiano.headroom.quota.ProviderCredentials
import dev.sebastiano.headroom.quota.QuotaFetchers
import dev.sebastiano.headroom.quota.ResetClients
import dev.sebastiano.headroom.quota.ResetLog
import dev.sebastiano.headroom.quota.ResetRedeemer
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
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

        override suspend fun redeem(
            credentials: ProviderCredentials,
            poolId: String,
            attemptKey: String,
        ): RedeemOutcome {
            calls += Triple(credentials, poolId, attemptKey)
            return RedeemOutcome.Success(resetsLeft = 1)
        }
    }

    private val codexRedeemer = RecordingRedeemer(Provider.Codex)
    private val claudeRedeemer = RecordingRedeemer(Provider.Claude)

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

    private val provider =
        SyncedResetProvider(
            accounts = { states },
            fetcher =
                AccountQuotaFetcher(
                    CredentialProvider(store, emptyMap()),
                    QuotaFetchers(emptyList()),
                ),
            clients = ResetClients(emptyList(), listOf(codexRedeemer, claudeRedeemer)),
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
    fun `Claude resets cannot be used`() = runTest {
        signIn(claude)

        val outcome = provider.redeem(claude, "grant", ResetAttemptKey("key-1"))

        assertEquals(RedeemOutcome.Unsupported, outcome)
        assertTrue(claudeRedeemer.calls.isEmpty())
    }

    @Test
    fun `an account with no working sign-in is asked to sign in again`() = runTest {
        assertEquals(
            RedeemOutcome.SignInAgain,
            provider.redeem(codex, "codex", ResetAttemptKey("key-1")),
        )
        signIn(codex, expiresAt = now.minusSeconds(1))
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
}
