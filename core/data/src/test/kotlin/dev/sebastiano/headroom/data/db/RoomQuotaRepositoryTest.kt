package dev.sebastiano.headroom.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.QuotaResult
import java.time.Duration
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RoomQuotaRepositoryTest {
    private val now = Instant.parse("2026-09-27T12:32:00Z")
    private val demo = DemoData.accounts(now)
    private val claude = demo.first { it.account.provider == Provider.Claude }
    private lateinit var db: HeadroomDatabase

    @BeforeTest
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    HeadroomDatabase::class.java,
                )
                .allowMainThreadQueries()
                .build()
    }

    @AfterTest fun tearDown() = db.close()

    private fun repo(
        fetch: suspend (Account) -> QuotaResult,
        scope: kotlinx.coroutines.CoroutineScope,
    ) = RoomQuotaRepository(db.quotaDao(), fetch, clock = { now }, scope = scope)

    @Test
    fun `a new account shows up with no snapshot until the first refresh`() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = repo({ QuotaResult.Success(claude.snapshot!!) }, backgroundScope)
            repo.addAccount(claude.account)
            val state = repo.accounts.first { it.isNotEmpty() }.single()
            assertEquals(claude.account, state.account)
            assertNull(state.snapshot)
        }

    @Test
    fun `refresh stores the snapshot and a history point per window`() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = repo({ QuotaResult.Success(claude.snapshot!!) }, backgroundScope)
            repo.addAccount(claude.account)
            repo.refresh()
            val state = repo.accounts.first { it.firstOrNull()?.snapshot != null }.single()
            assertEquals(claude.snapshot!!.windows.toSet(), state.snapshot!!.windows.toSet())
            assertEquals("Max 20x", state.snapshot!!.planLabel)
            val history = repo.history(claude.account.id, "seven_day").first()
            assertEquals(listOf(71.0), history.map { it.usedPercent })
        }

    @Test
    fun `a failed refresh keeps the last snapshot and records the error`() =
        runTest(UnconfinedTestDispatcher()) {
            var result: QuotaResult = QuotaResult.Success(claude.snapshot!!)
            val repo = repo({ result }, backgroundScope)
            repo.addAccount(claude.account)
            repo.refresh()
            result = QuotaResult.Failure(QuotaErrorKind.Auth, "expired")
            repo.refresh(claude.account.id)
            val state = repo.accounts.first { it.singleOrNull()?.lastError != null }.single()
            assertEquals(QuotaErrorKind.Auth, state.lastError)
            assertEquals(71.0, state.primaryWindow!!.usedPercent)
        }

    @Test
    fun `concurrent refreshes of one account fetch only once`() =
        runTest(UnconfinedTestDispatcher()) {
            var calls = 0
            val gate = CompletableDeferred<Unit>()
            val repo =
                repo(
                    {
                        calls++
                        gate.await()
                        QuotaResult.Success(claude.snapshot!!)
                    },
                    backgroundScope,
                )
            repo.addAccount(claude.account)
            val first = async { repo.refresh(claude.account.id) }
            val second = async { repo.refresh(claude.account.id) }
            assertEquals(
                true,
                repo.accounts
                    .first { it.singleOrNull()?.isRefreshing == true }
                    .single()
                    .isRefreshing,
            )
            gate.complete(Unit)
            first.await()
            second.await()
            assertEquals(1, calls)
        }

    @Test
    fun `removing an account deletes its windows and history`() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = repo({ QuotaResult.Success(claude.snapshot!!) }, backgroundScope)
            repo.addAccount(claude.account)
            repo.refresh()
            repo.removeAccount(claude.account.id)
            assertEquals(emptyList(), repo.accounts.first { it.isEmpty() })
            assertEquals(emptyList(), repo.history(claude.account.id, "seven_day").first())
        }

    @Test
    fun `history older than the retention period is pruned`() =
        runTest(UnconfinedTestDispatcher()) {
            var clock = now.minus(Duration.ofDays(90))
            val repo =
                RoomQuotaRepository(
                    db.quotaDao(),
                    { QuotaResult.Success(claude.snapshot!!) },
                    { clock },
                    backgroundScope,
                )
            repo.addAccount(claude.account)
            repo.refresh()
            clock = now
            repo.refresh()
            assertEquals(1, repo.history(claude.account.id, "seven_day").first().size)
        }

    @Test
    fun `removing an account during a refresh does not bring it back`() =
        runTest(UnconfinedTestDispatcher()) {
            val gate = CompletableDeferred<Unit>()
            var result: QuotaResult = QuotaResult.Success(claude.snapshot!!)
            val repo =
                repo(
                    {
                        gate.await()
                        result
                    },
                    backgroundScope,
                )
            repo.addAccount(claude.account)
            val refresh = async { repo.refresh(claude.account.id) }
            repo.removeAccount(claude.account.id)
            gate.complete(Unit)
            refresh.await()
            assertEquals(emptyList(), repo.current())

            // The failure path must not resurrect it either.
            result = QuotaResult.Failure(QuotaErrorKind.Network, "offline")
            repo.addAccount(claude.account)
            val gate2 = CompletableDeferred<Unit>()
            val repo2 =
                repo(
                    {
                        gate2.await()
                        result
                    },
                    backgroundScope,
                )
            val refresh2 = async { repo2.refresh(claude.account.id) }
            repo2.removeAccount(claude.account.id)
            gate2.complete(Unit)
            refresh2.await()
            assertEquals(emptyList(), repo2.current())
        }

    @Test
    fun `current reads the committed state right after a refresh`() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = repo({ QuotaResult.Success(claude.snapshot!!) }, backgroundScope)
            repo.addAccount(claude.account)
            repo.refresh()
            assertEquals(71.0, repo.current().single().primaryWindow!!.usedPercent)
        }

    @Test
    fun `a balance-only snapshot keeps its balance`() =
        runTest(UnconfinedTestDispatcher()) {
            val jetbrains = Account("jb", Provider.JetBrains, "sam@example.com")
            val snapshot =
                dev.sebastiano.headroom.model.QuotaSnapshot(
                    provider = Provider.JetBrains,
                    accountId = "jb",
                    planLabel = "JetBrains AI Pro",
                    windows = emptyList(),
                    fetchedAt = now,
                    balance =
                        dev.sebastiano.headroom.model.QuotaBalance(
                            amount = 1234.5,
                            unit = "credits",
                        ),
                )
            val repo = repo({ QuotaResult.Success(snapshot) }, backgroundScope)
            repo.addAccount(jetbrains)
            repo.refresh()
            assertEquals(1234.5, repo.current().single().snapshot!!.balance!!.amount)
            assertEquals("credits", repo.current().single().snapshot!!.balance!!.unit)
        }
}
