package dev.sebastiano.headroom.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
import java.time.Duration
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RoomResetEventLogTest {
    private var now = Instant.parse("2026-10-10T12:00:00Z")
    private val account = Account("codex-1", Provider.Codex, "sam@example.com")
    private val soon = now.plus(Duration.ofDays(2))
    private val later = now.plus(Duration.ofDays(9))
    private lateinit var db: HeadroomDatabase
    private lateinit var snapshot: QuotaSnapshot

    @BeforeTest
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    HeadroomDatabase::class.java,
                )
                .allowMainThreadQueries()
                .build()
        snapshot = snapshot(resets(soon, later), weeklyUsed = 90.0)
    }

    @AfterTest fun tearDown() = db.close()

    private fun weekly(used: Double) =
        QuotaWindow(
            id = "secondary",
            label = "Weekly",
            kind = WindowKind.Weekly,
            usedPercent = used,
            resetsAt = now.plus(Duration.ofDays(3)),
            length = Duration.ofDays(7),
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

    private suspend fun events() = log().events(Instant.EPOCH).first()

    @Test
    fun `a sync that finds a reset gone before its expiry records a use elsewhere`() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = setUpAccount(backgroundScope)

            now = now.plus(Duration.ofHours(1))
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

            now = soon.plus(Duration.ofMinutes(5))
            snapshot = snapshot(resets(later), weeklyUsed = 95.0)
            repo.refresh()

            assertEquals(ResetEventKind.Expired, events().single().kind)
        }

    @Test
    fun `a redeem in Headroom is recorded once, with the usage it gave back`() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = setUpAccount(backgroundScope)
            now = now.plus(Duration.ofMinutes(10))

            log().redeemed(account, "credits", ResetAttemptKey("key-1"))
            log().redeemed(account, "credits", ResetAttemptKey("key-1"))
            now = now.plus(Duration.ofSeconds(2))
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
    fun `a redeem measured on usage older than half an hour is an estimate`() =
        runTest(UnconfinedTestDispatcher()) {
            setUpAccount(backgroundScope)
            now = now.plus(Duration.ofHours(1))

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
            assertEquals(0, log().events(now.plusSeconds(1)).first().size)
        }

    @Test
    fun `events older than a year are pruned by the next sync`() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = setUpAccount(backgroundScope)
            log().redeemed(account, "credits", ResetAttemptKey("key-1"))

            now = now.plus(Duration.ofDays(366))
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
