package dev.sebastiano.headroom.data.db

import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.ResetAttemptKey
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetEventKind
import dev.sebastiano.headroom.model.ResetPool
import dev.sebastiano.headroom.model.ResetScope
import dev.sebastiano.headroom.model.ResetUseSource
import dev.sebastiano.headroom.model.WindowKind
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class RoomResetEventLogTest {
    private var now = Instant.parse("2026-10-10T12:00:00Z")
    private val account = Account("codex-1", Provider.Codex, "sam@example.com")
    private val soon = now.plus(2.days)
    private val later = now.plus(9.days)
    private lateinit var db: HeadroomDatabase
    private lateinit var snapshot: QuotaSnapshot

    @BeforeTest
    fun setUp() {
        db = inMemoryDatabase()
        snapshot = snapshot(resets(soon, later), weeklyUsed = 90.0)
    }

    @AfterTest fun tearDown() = db.close()

    private fun weekly(used: Double) =
        QuotaWindow(
            id = "secondary",
            label = "Weekly",
            kind = WindowKind.Weekly,
            usedPercent = used,
            resetsAt = now.plus(3.days),
            length = 7.days,
        )

    private fun resets(vararg expiries: Instant) =
        ResetAvailability(
            listOf(
                ResetPool(
                    id = "credits",
                    label = "Reset credits",
                    available = expiries.size,
                    scope = ResetScope.of(WindowKind.Session, WindowKind.Weekly),
                    expiries = expiries.toList(),
                )
            )
        )

    private fun snapshot(resets: ResetAvailability, weeklyUsed: Double) =
        QuotaSnapshot(
            provider = Provider.Codex,
            accountId = account.id,
            planLabel = "Pro",
            windows = listOf(weekly(weeklyUsed)),
            fetchedAt = now,
            resets = resets,
        )

    private suspend fun setUpAccount(scope: CoroutineScope): RoomQuotaRepository {
        val repo =
            RoomQuotaRepository(
                db.quotaDao(),
                db.accountOrderDao(),
                { QuotaResult.Success(snapshot.copy(fetchedAt = now)) },
                clock = { now },
                scope = scope,
            )
        repo.addAccount(account)
        repo.refresh()
        return repo
    }

    private fun log() = RoomResetEventLog(db.quotaDao(), clock = { now })

    private suspend fun events() = log().events(Instant.fromEpochSeconds(0)).first()

    @Test
    fun `a sync that finds a reset gone before its expiry records a use elsewhere`() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = setUpAccount(backgroundScope)

            now = now.plus(1.hours)
            snapshot = snapshot(resets(later), weeklyUsed = 5.0)
            repo.refresh()

            val event = events().single()
            assertEquals(ResetEventKind.Used, event.kind)
            assertEquals(ResetUseSource.Elsewhere, event.source)
            assertEquals(Provider.Codex, event.provider)
            assertEquals(account.id, event.accountId)
            assertEquals(soon, event.expiresAt)
            assertEquals(mapOf(WindowKind.Weekly to 90.0), event.givenBack)
            assertTrue(event.givenBackEstimated)
        }

    @Test
    fun `a sync that finds a reset gone after its expiry records an expiry`() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = setUpAccount(backgroundScope)

            now = soon.plus(5.minutes)
            snapshot = snapshot(resets(later), weeklyUsed = 95.0)
            repo.refresh()

            assertEquals(ResetEventKind.Expired, events().single().kind)
        }

    @Test
    fun `a redeem in Headroom is recorded once with the usage it gave back`() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = setUpAccount(backgroundScope)
            now = now.plus(10.minutes)

            log().redeemed(account, "credits", ResetAttemptKey("key-1"))
            log().redeemed(account, "credits", ResetAttemptKey("key-1"))
            now = now.plus(2.seconds)
            snapshot = snapshot(resets(later), weeklyUsed = 0.0)
            repo.refresh()

            val event = events().single()
            assertEquals(ResetEventKind.Used, event.kind)
            assertEquals(ResetUseSource.Headroom, event.source)
            assertEquals(soon, event.expiresAt)
            assertEquals("Reset credits", event.poolLabel)
            assertEquals(mapOf(WindowKind.Weekly to 90.0), event.givenBack)
            assertFalse(event.givenBackEstimated)
        }

    @Test
    fun `a redeem recorded after a sync already found its reset gone takes over that use`() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = setUpAccount(backgroundScope)
            // The provider used the reset, and a sync stored that before Headroom recorded it.
            now = now.plus(5.seconds)
            snapshot = snapshot(resets(later), weeklyUsed = 0.0)
            repo.refresh()

            now = now.plus(1.seconds)
            log().redeemed(account, "credits", ResetAttemptKey("key-1"))
            now = now.plus(2.seconds)
            repo.refresh()

            val event = events().single()
            assertEquals(ResetUseSource.Headroom, event.source)
            // The sync measured the usage before the reset; the redeem only sees it after.
            assertEquals(mapOf(WindowKind.Weekly to 90.0), event.givenBack)
        }

    @Test
    fun `an older use elsewhere is not taken over by a redeem`() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = setUpAccount(backgroundScope)
            snapshot = snapshot(resets(later), weeklyUsed = 0.0)
            repo.refresh()

            now = now.plus(1.hours)
            log().redeemed(account, "credits", ResetAttemptKey("key-1"))

            assertEquals(
                listOf(ResetUseSource.Elsewhere, ResetUseSource.Headroom),
                events().map { it.source },
            )
        }

    @Test
    fun `a redeem is still matched by a sync days later`() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = setUpAccount(backgroundScope)
            log().redeemed(account, "credits", ResetAttemptKey("key-1"))

            now = now.plus(3.days)
            snapshot = snapshot(resets(later), weeklyUsed = 0.0)
            repo.refresh()

            assertEquals(listOf(ResetUseSource.Headroom), events().map { it.source })
        }

    @Test
    fun `a redeem measured on usage older than half an hour is an estimate`() =
        runTest(UnconfinedTestDispatcher()) {
            setUpAccount(backgroundScope)
            now = now.plus(1.hours)

            log().redeemed(account, "credits", ResetAttemptKey("key-1"))

            assertTrue(events().single().givenBackEstimated)
        }

    @Test
    fun `a redeem of an account that is not stored records nothing`() =
        runTest(UnconfinedTestDispatcher()) {
            log().redeemed(account, "credits", ResetAttemptKey("key-1"))

            assertEquals(emptyList(), events())
        }

    @Test
    fun `events only list the ones since the time asked`() =
        runTest(UnconfinedTestDispatcher()) {
            setUpAccount(backgroundScope)
            log().redeemed(account, "credits", ResetAttemptKey("key-1"))

            assertEquals(1, log().events(now).first().size)
            assertEquals(0, log().events(now + 1.seconds).first().size)
        }

    @Test
    fun `events older than a year are pruned by the next sync`() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = setUpAccount(backgroundScope)
            log().redeemed(account, "credits", ResetAttemptKey("key-1"))

            now = now.plus(366.days)
            repo.refresh()

            assertEquals(emptyList(), events())
        }

    @Test
    fun `removing an account deletes its events`() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = setUpAccount(backgroundScope)
            log().redeemed(account, "credits", ResetAttemptKey("key-1"))

            repo.removeAccount(account.id)

            assertEquals(emptyList(), events())
        }
}
