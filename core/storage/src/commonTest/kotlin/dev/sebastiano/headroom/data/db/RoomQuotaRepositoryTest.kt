package dev.sebastiano.headroom.data.db

import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetPool
import dev.sebastiano.headroom.model.ResetPoolStatus
import dev.sebastiano.headroom.model.ResetScope
import dev.sebastiano.headroom.model.ResetTiming
import dev.sebastiano.headroom.model.WindowKind
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class RoomQuotaRepositoryTest {
    private val now = Instant.parse("2026-09-27T12:32:00Z")
    private val demo = DemoData.accounts(now)
    private val claude = demo.first { it.account.provider == Provider.Claude }
    private lateinit var db: HeadroomDatabase

    @BeforeTest
    fun setUp() {
        db = inMemoryDatabase()
    }

    @AfterTest fun tearDown() = db.close()

    private fun repo(
        fetch: suspend (Account) -> QuotaResult,
        scope: kotlinx.coroutines.CoroutineScope,
    ) =
        RoomQuotaRepository(
            db.quotaDao(),
            db.accountOrderDao(),
            fetch,
            clock = { now },
            scope = scope,
        )

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

    private val resets =
        ResetAvailability(
            listOf(
                ResetPool(
                    id = "grant",
                    label = "Launch week",
                    available = 1,
                    scope = ResetScope.ofWindows("five_hour", "seven_day"),
                    expiries = listOf(now.plus(3.days)),
                    total = 2,
                    status = ResetPoolStatus.Queued,
                    timing = ResetTiming.AnyTime,
                ),
                ResetPool("other", "Other", 0, ResetScope.of(WindowKind.Weekly)),
            ),
            requiresSignIn = false,
            canAskForMore = true,
            ineligibleReason = null,
        )

    @Test
    fun `a sync stores the resets with the snapshot`() =
        runTest(UnconfinedTestDispatcher()) {
            val repo =
                repo(
                    { QuotaResult.Success(claude.snapshot!!.copy(resets = resets)) },
                    backgroundScope,
                )
            repo.addAccount(claude.account)
            repo.refresh()

            val state = repo.accounts.first { it.firstOrNull()?.snapshot != null }.single()
            assertEquals(resets, state.snapshot!!.resets)
            assertEquals(resets, repo.current().single().snapshot!!.resets)
        }

    @Test
    fun `resets that could not be read keep the last ones and none clears them`() =
        runTest(UnconfinedTestDispatcher()) {
            var snapshot = claude.snapshot!!.copy(resets = resets)
            val repo = repo({ QuotaResult.Success(snapshot) }, backgroundScope)
            repo.addAccount(claude.account)
            repo.refresh()

            snapshot = claude.snapshot!!.copy(resets = null, resetsReadFailed = true)
            repo.refresh()
            assertEquals(resets, repo.current().single().snapshot!!.resets)
            assertEquals(true, repo.current().single().snapshot!!.resetsReadFailed)

            snapshot = claude.snapshot!!.copy(resets = null)
            repo.refresh()
            assertNull(repo.current().single().snapshot!!.resets)
        }

    @Test
    fun `a failed reset read is kept until a sync reads the resets again`() =
        runTest(UnconfinedTestDispatcher()) {
            var result: QuotaResult =
                QuotaResult.Success(claude.snapshot!!.copy(resets = null, resetsReadFailed = true))
            val repo = repo({ result }, backgroundScope)
            repo.addAccount(claude.account)
            repo.refresh()
            assertEquals(
                true,
                repo.accounts
                    .first { it.firstOrNull()?.snapshot != null }
                    .single()
                    .snapshot!!
                    .resetsReadFailed,
            )

            // A failed sync reads nothing, so it does not change what is known about the resets.
            result = QuotaResult.Failure(QuotaErrorKind.Network, "offline")
            repo.refresh()
            assertEquals(true, repo.current().single().snapshot!!.resetsReadFailed)

            result = QuotaResult.Success(claude.snapshot!!.copy(resets = resets))
            repo.refresh()
            assertEquals(false, repo.current().single().snapshot!!.resetsReadFailed)
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
    fun `an expired sign-in stays expired through later errors until a sync works`() =
        runTest(UnconfinedTestDispatcher()) {
            var result: QuotaResult = QuotaResult.Failure(QuotaErrorKind.Auth, "expired")
            val repo = repo({ result }, backgroundScope)
            repo.addAccount(claude.account)
            repo.refresh()

            QuotaErrorKind.entries
                .filterNot { it == QuotaErrorKind.Auth }
                .forEach { kind ->
                    result = QuotaResult.Failure(kind, "later")
                    repo.refresh()
                    assertEquals(QuotaErrorKind.Auth, repo.current().single().lastError, kind.name)
                }

            result = QuotaResult.Success(claude.snapshot!!)
            repo.refresh()
            assertNull(repo.current().single().lastError)
        }

    @Test
    fun `an account's label can be updated`() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = repo({ QuotaResult.Success(claude.snapshot!!) }, backgroundScope)
            repo.addAccount(claude.account)
            repo.relabelAccount(claude.account.id, "sam@new.example.com")
            assertEquals("sam@new.example.com", repo.current().single().account.label)
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
    fun `renaming an account keeps the name and a blank name clears it`() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = repo({ QuotaResult.Success(claude.snapshot!!) }, backgroundScope)
            repo.addAccount(claude.account)

            repo.renameAccount(claude.account.id, "  Work  ")
            assertEquals(
                "Work",
                repo.accounts
                    .first { it.singleOrNull()?.account?.nickname != null }
                    .single()
                    .account
                    .name,
            )

            repo.renameAccount(claude.account.id, " ")
            assertEquals(
                "Claude",
                repo.accounts
                    .first { it.singleOrNull()?.account?.nickname == null }
                    .single()
                    .account
                    .name,
            )
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
            var clock = now.minus(90.days)
            val repo =
                RoomQuotaRepository(
                    db.quotaDao(),
                    db.accountOrderDao(),
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

    @Test
    fun `a window's credit amounts survive storage`() =
        runTest(UnconfinedTestDispatcher()) {
            val jetbrains = Account("jb", Provider.JetBrains, "sam@example.com")
            val window =
                dev.sebastiano.headroom.model.QuotaWindow(
                    id = "jb:ws:0123456789ab",
                    label = "JetBrains Team",
                    kind = dev.sebastiano.headroom.model.WindowKind.Monthly,
                    usedPercent = 6.0,
                    resetsAt = now.plus(10.days),
                    length = 30.days,
                    usedAmount = 12.0,
                    limitAmount = 200.0,
                    amountUnit = "credits",
                )
            val snapshot =
                dev.sebastiano.headroom.model.QuotaSnapshot(
                    provider = Provider.JetBrains,
                    accountId = "jb",
                    planLabel = "JetBrains AI Pro",
                    windows = listOf(window),
                    fetchedAt = now,
                )
            val repo = repo({ QuotaResult.Success(snapshot) }, backgroundScope)
            repo.addAccount(jetbrains)
            repo.refresh()
            assertEquals(listOf(window), repo.current().single().snapshot!!.windows)
        }

    @Test
    fun `a credit's expiry and an unknown window survive storage`() =
        runTest(UnconfinedTestDispatcher()) {
            val credit =
                dev.sebastiano.headroom.model.QuotaWindow(
                    id = "iguana_necktie",
                    label = "Cloud session credit",
                    kind = dev.sebastiano.headroom.model.WindowKind.Credit,
                    usedPercent = 40.8,
                    resetsAt = null,
                    length = null,
                    usedAmount = 102.0,
                    limitAmount = 250.0,
                    amountUnit = "USD",
                    expiresAt = now.plus(40.days),
                )
            val unknown =
                dev.sebastiano.headroom.model.QuotaWindow(
                    id = "nimbus_quill",
                    label = "Nimbus quill",
                    kind = dev.sebastiano.headroom.model.WindowKind.Other,
                    usedPercent = 0.0,
                    resetsAt = null,
                    length = null,
                    isRecognised = false,
                )
            val snapshot =
                claude.snapshot!!.copy(windows = claude.snapshot!!.windows + credit + unknown)
            val repo = repo({ QuotaResult.Success(snapshot) }, backgroundScope)
            repo.addAccount(claude.account)
            repo.refresh()
            val stored = repo.current().single().snapshot!!.windows
            assertEquals(credit, stored.single { it.id == credit.id })
            assertEquals(unknown, stored.single { it.id == unknown.id })
        }

    @Test
    fun `accounts are listed in the order they were added`() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = repo({ QuotaResult.Success(claude.snapshot!!) }, backgroundScope)
            listOf("zeta", "alpha", "mid").forEach { repo.addAccount(account(it)) }

            assertEquals(listOf("zeta", "alpha", "mid"), repo.current().ids())
        }

    @Test
    fun `reordering saves the new order`() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = repo({ QuotaResult.Success(claude.snapshot!!) }, backgroundScope)
            listOf("a", "b", "c").forEach { repo.addAccount(account(it)) }

            repo.reorderAccounts(listOf("c", "a", "b"))

            assertEquals(listOf("c", "a", "b"), repo.current().ids())
            assertEquals(
                listOf("c", "a", "b"),
                repo.accounts.first { it.ids() == listOf("c", "a", "b") }.ids(),
            )
            // A new repository over the same database, as after a restart, reads the same order.
            val reopened = repo({ QuotaResult.Success(claude.snapshot!!) }, backgroundScope)
            assertEquals(listOf("c", "a", "b"), reopened.current().ids())
        }

    @Test
    fun `a new account goes last after a reorder`() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = repo({ QuotaResult.Success(claude.snapshot!!) }, backgroundScope)
            listOf("a", "b", "c").forEach { repo.addAccount(account(it)) }
            repo.reorderAccounts(listOf("c", "a", "b"))
            repo.removeAccount("b")

            repo.addAccount(account("d"))

            assertEquals(listOf("c", "a", "d"), repo.current().ids())
        }

    @Test
    fun `a refresh keeps the order`() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = repo({ QuotaResult.Success(claude.snapshot!!) }, backgroundScope)
            listOf("a", "b").forEach { repo.addAccount(account(it)) }
            repo.reorderAccounts(listOf("b", "a"))

            repo.refresh()

            assertEquals(listOf("b", "a"), repo.current().ids())
        }

    @Test
    fun `accounts missing from a reorder keep their order after the listed ones`() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = repo({ QuotaResult.Success(claude.snapshot!!) }, backgroundScope)
            listOf("a", "b", "c", "d").forEach { repo.addAccount(account(it)) }

            repo.reorderAccounts(listOf("c", "unknown", "a"))

            assertEquals(listOf("c", "a", "b", "d"), repo.current().ids())
        }

    private fun account(id: String) = Account(id, Provider.Claude, "$id@example.com")

    private fun List<dev.sebastiano.headroom.model.AccountState>.ids() = map { it.account.id }
}
