package dev.sebastiano.headroom.data.db

import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AuthFailureReason
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.HistoryRetention
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetPool
import dev.sebastiano.headroom.model.ResetScope
import dev.sebastiano.headroom.model.UsagePoint
import dev.sebastiano.headroom.model.WindowKind
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class StoreSnapshotTest {
    private val now = Instant.parse("2026-09-27T12:32:00Z")
    private val claude = DemoData.accounts(now).first { it.account.provider == Provider.Claude }
    private lateinit var db: HeadroomDatabase

    @BeforeTest
    fun setUp() {
        db = inMemoryDatabase()
    }

    @AfterTest fun tearDown() = db.close()

    private fun repo(fetch: suspend (Account) -> QuotaResult, scope: CoroutineScope) =
        RoomQuotaRepository(
            db.quotaDao(),
            db.accountOrderDao(),
            fetch,
            clock = { now },
            scope = scope,
        )

    @Test
    fun `storeSnapshot saves a validated snapshot and history without fetching again and clears errors`() =
        runTest(UnconfinedTestDispatcher()) {
            var fetches = 0
            val repo =
                repo(
                    {
                        fetches++
                        QuotaResult.Failure(
                            QuotaErrorKind.Auth,
                            "expired",
                            authReason = AuthFailureReason.KeyExpired,
                        )
                    },
                    backgroundScope,
                )
            repo.addAccount(claude.account)
            repo.refresh()
            assertEquals(1, fetches)
            assertEquals(QuotaErrorKind.Auth, repo.current().single().lastError)

            repo.storeSnapshot(claude.snapshot!!)

            assertEquals(1, fetches)
            val state = repo.current().single()
            assertNull(state.lastError)
            assertNull(state.authReason)
            assertEquals(claude.snapshot!!.windows.toSet(), state.snapshot!!.windows.toSet())
            assertEquals("Max 20x", state.snapshot!!.planLabel)
            val history = repo.history(claude.account.id, "seven_day").first()
            val expectedReset = claude.snapshot!!.windows.first { it.id == "seven_day" }.resetsAt
            assertEquals(listOf(UsagePoint(now, 71.0, expectedReset)), history)
        }

    @Test
    fun `storeSnapshot sets initial ZCode resets for a new Z_AI account and preserves stored resets on a later save`() =
        runTest(UnconfinedTestDispatcher()) {
            val zAiAccount = Account("z1", Provider.ZAi, "Z.AI")
            val zAiSnapshot =
                claude.snapshot!!.copy(
                    provider = Provider.ZAi,
                    accountId = "z1",
                    resets = null,
                    resetsReadFailed = false,
                )
            val resets =
                ResetAvailability(
                    listOf(ResetPool("week", "Weekly", 1, ResetScope.of(WindowKind.Weekly)))
                )
            var fetched = zAiSnapshot.copy(resets = resets)
            val repo = repo({ QuotaResult.Success(fetched) }, backgroundScope)
            repo.addAccount(zAiAccount)

            repo.storeSnapshot(zAiSnapshot)
            assertEquals(
                ResetAvailability(emptyList(), requiresSignIn = true),
                repo.current().single().snapshot!!.resets,
            )

            repo.refresh()
            fetched = zAiSnapshot.copy(resets = null, resetsReadFailed = true)
            repo.refresh()

            repo.storeSnapshot(zAiSnapshot)

            val stored = repo.current().single().snapshot!!
            assertEquals(resets, stored.resets)
            assertEquals(true, stored.resetsReadFailed)
        }

    @Test
    fun `storeSnapshot waits for an in-flight refresh so the validated snapshot wins`() =
        runTest(UnconfinedTestDispatcher()) {
            val gate = CompletableDeferred<Unit>()
            val repo =
                repo(
                    {
                        gate.await()
                        QuotaResult.Failure(
                            QuotaErrorKind.Auth,
                            "expired",
                            authReason = AuthFailureReason.KeyExpired,
                        )
                    },
                    backgroundScope,
                )
            repo.addAccount(claude.account)
            val refreshing = async { repo.refresh(claude.account.id) }
            val storing = async { repo.storeSnapshot(claude.snapshot!!) }

            gate.complete(Unit)
            refreshing.await()
            storing.await()

            val state = repo.current().single()
            assertNull(state.lastError)
            assertEquals(71.0, state.primaryWindow?.usedPercent)
        }

    @Test
    fun `a refresh that starts while storeSnapshot is persisting coalesces without fetching`() =
        runTest(UnconfinedTestDispatcher()) {
            var fetches = 0
            val storeGate = CompletableDeferred<HistoryRetention>()
            val repo =
                RoomQuotaRepository(
                    db.quotaDao(),
                    db.accountOrderDao(),
                    {
                        fetches++
                        QuotaResult.Failure(QuotaErrorKind.RateLimited, "throttled")
                    },
                    clock = { now },
                    scope = backgroundScope,
                    retention = { storeGate.await() },
                )
            repo.addAccount(claude.account)

            val storing = async { repo.storeSnapshot(claude.snapshot!!) }
            val refreshing = async { repo.refresh(claude.account.id) }

            storeGate.complete(HistoryRetention.LongTerm)
            storing.await()
            refreshing.await()

            assertEquals(0, fetches)
            assertNull(repo.current().single().lastError)
            assertEquals(71.0, repo.current().single().primaryWindow?.usedPercent)
        }
}
