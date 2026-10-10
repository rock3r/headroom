package dev.sebastiano.headroom.data.reset

import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.AppSettings
import dev.sebastiano.headroom.model.ExpiringReset
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.QuotaRepository
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetPool
import dev.sebastiano.headroom.model.ResetScope
import dev.sebastiano.headroom.model.UsagePoint
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest

class ResetRemindersTest {
    private val zone = ZoneId.of("UTC")
    private var now = Instant.parse("2026-10-10T08:00:00Z")
    private val tomorrow = Instant.parse("2026-10-11T06:18:00Z")
    private val nextWeek = Instant.parse("2026-10-17T06:18:00Z")

    private fun codex(vararg expiries: Instant, id: String = "codex") =
        AccountState(
            account = Account(id, Provider.Codex, "sam@example.com"),
            snapshot =
                QuotaSnapshot(
                    provider = Provider.Codex,
                    accountId = id,
                    planLabel = null,
                    windows = emptyList(),
                    fetchedAt = now,
                    resets =
                        ResetAvailability(
                            listOf(
                                ResetPool(
                                    id = "credits",
                                    label = "Resets",
                                    available = expiries.size,
                                    scope = ResetScope.Unknown,
                                    expiries = expiries.toList(),
                                )
                            )
                        ),
                ),
        )

    private class FakeRepository(var stored: List<AccountState>) : QuotaRepository {
        var afterRefresh: List<AccountState>? = null
        /** Like an offline phone: the refresh keeps the stored snapshot and records this error. */
        var refreshError: QuotaErrorKind? = null
        /**
         * Like a provider whose reset endpoint does not answer: the refresh reads the usage, keeps
         * the stored resets and marks them as not read.
         */
        var resetsReadFails: Boolean = false
        val refreshed = mutableListOf<String?>()
        override val accounts: StateFlow<List<AccountState>> = MutableStateFlow(stored)

        override suspend fun refresh(accountId: String?) {
            refreshed += accountId
            afterRefresh?.let { stored = it }
            refreshError?.let { error ->
                stored = stored.map {
                    if (it.account.id == accountId) it.copy(lastError = error) else it
                }
            }
            if (resetsReadFails) {
                stored = stored.map {
                    if (it.account.id == accountId) {
                        it.copy(
                            lastError = null,
                            snapshot = it.snapshot?.copy(resetsReadFailed = true),
                        )
                    } else {
                        it
                    }
                }
            }
        }

        override suspend fun current(): List<AccountState> = stored

        override fun history(accountId: String, windowId: String): Flow<List<UsagePoint>> =
            emptyFlow()
    }

    private class MemoryLedger : ResetReminderLedger {
        var record = ReminderRecord()

        override fun load(): ReminderRecord = record

        override fun save(record: ReminderRecord) {
            this.record = record
        }
    }

    private val posted = mutableListOf<List<ExpiringReset>>()
    private val scheduled = mutableListOf<Instant?>()
    private val ledger = MemoryLedger()
    private var settings = AppSettings()

    private fun reminders(repository: QuotaRepository) =
        ResetReminders(
            repository = repository,
            settings = { settings },
            ledger = ledger,
            notifier = { resets, _ -> posted += resets },
            schedule = { scheduled += it },
            clock = { now },
            zone = { zone },
        )

    @Test
    fun `schedules the next check a day before the soonest reset expires`() = runTest {
        reminders(FakeRepository(listOf(codex(nextWeek)))).reschedule()

        assertEquals(listOf<Instant?>(Instant.parse("2026-10-16T06:18:00Z")), scheduled)
    }

    @Test
    fun `cancels the check when reminders are off`() = runTest {
        settings = AppSettings(resetExpiryReminders = false)

        reminders(FakeRepository(listOf(codex(tomorrow)))).reschedule()

        assertEquals(listOf<Instant?>(null), scheduled)
    }

    @Test
    fun `a due check refreshes the account, posts one reminder and remembers the day`() = runTest {
        val repository = FakeRepository(listOf(codex(tomorrow)))

        reminders(repository).remind()

        assertEquals(listOf<String?>("codex"), repository.refreshed)
        assertEquals(listOf(tomorrow), posted.single().map { it.expiresAt })
        assertEquals(LocalDate.of(2026, 10, 10), ledger.record.lastDay)
        assertEquals(setOf("codex/credits/${tomorrow.toEpochMilli()}"), ledger.record.reminded.keys)
    }

    @Test
    fun `a reset used since the last sync is not reminded about`() = runTest {
        val repository = FakeRepository(listOf(codex(tomorrow)))
        repository.afterRefresh = listOf(codex())

        reminders(repository).remind()

        assertEquals(emptyList(), posted)
        assertNull(ledger.record.lastDay)
    }

    @Test
    fun `a check with nothing due posts nothing and does not refresh`() = runTest {
        val repository = FakeRepository(listOf(codex(nextWeek)))

        reminders(repository).remind()

        assertEquals(emptyList(), posted)
        assertEquals(emptyList(), repository.refreshed)
    }

    @Test
    fun `a second check on the same day posts nothing`() = runTest {
        val repository = FakeRepository(listOf(codex(tomorrow)))
        reminders(repository).remind()
        repository.stored = listOf(codex(tomorrow, tomorrow.plusSeconds(3_600)))

        reminders(repository).remind()

        assertEquals(1, posted.size)
    }

    @Test
    fun `after a check, the next one is planned`() = runTest {
        reminders(FakeRepository(listOf(codex(tomorrow, nextWeek)))).remind()

        assertEquals(Instant.parse("2026-10-16T06:18:00Z"), scheduled.last())
    }

    @Test
    fun `resets that have expired are forgotten`() = runTest {
        val repository = FakeRepository(listOf(codex(tomorrow)))
        reminders(repository).remind()
        now = nextWeek.minusSeconds(3_600)
        repository.stored = listOf(codex(nextWeek))

        reminders(repository).remind()

        assertEquals(setOf("codex/credits/${nextWeek.toEpochMilli()}"), ledger.record.reminded.keys)
    }

    @Test
    fun `a check that cannot reach the provider waits and retries`() = runTest {
        val repository = FakeRepository(listOf(codex(tomorrow)))
        repository.refreshError = QuotaErrorKind.Network

        val outcome = reminders(repository).remind()

        assertEquals(ReminderOutcome.Retry(Duration.ofMinutes(5)), outcome)
        assertEquals(emptyList(), posted)
        assertEquals(ReminderRecord(), ledger.record)
    }

    @Test
    fun `retries back off`() = runTest {
        val repository = FakeRepository(listOf(codex(tomorrow)))
        repository.refreshError = QuotaErrorKind.Network

        val outcome = reminders(repository).remind(attempt = 3)

        assertEquals(ReminderOutcome.Retry(Duration.ofMinutes(30)), outcome)
    }

    @Test
    fun `when the retries run out, the reminder comes from the stored resets`() = runTest {
        val repository = FakeRepository(listOf(codex(tomorrow)))
        repository.refreshError = QuotaErrorKind.Network

        val outcome = reminders(repository).remind(attempt = 5)

        assertEquals(ReminderOutcome.Done, outcome)
        assertEquals(listOf(tomorrow), posted.single().map { it.expiresAt })
        assertEquals(LocalDate.of(2026, 10, 10), ledger.record.lastDay)
    }

    @Test
    fun `a reset close to expiring is reminded about without waiting`() = runTest {
        now = tomorrow.minus(Duration.ofHours(12)).minus(Duration.ofMinutes(2))
        val repository = FakeRepository(listOf(codex(tomorrow)))
        repository.refreshError = QuotaErrorKind.Network

        val outcome = reminders(repository).remind()

        assertEquals(ReminderOutcome.Done, outcome)
        assertEquals(1, posted.size)
    }

    @Test
    fun `other refresh errors do not delay the reminder`() = runTest {
        val repository = FakeRepository(listOf(codex(tomorrow)))
        repository.refreshError = QuotaErrorKind.RateLimited

        val outcome = reminders(repository).remind()

        assertEquals(ReminderOutcome.Done, outcome)
        assertEquals(1, posted.size)
    }

    @Test
    fun `a check that cannot read the resets waits and retries`() = runTest {
        val repository = FakeRepository(listOf(codex(tomorrow)))
        repository.resetsReadFails = true

        val outcome = reminders(repository).remind()

        assertEquals(ReminderOutcome.Retry(Duration.ofMinutes(5)), outcome)
        assertEquals(emptyList(), posted)
        assertEquals(ReminderRecord(), ledger.record)
    }

    @Test
    fun `when the reset read keeps failing, the reminder comes from the stored resets`() = runTest {
        val repository = FakeRepository(listOf(codex(tomorrow)))
        repository.resetsReadFails = true

        val outcome = reminders(repository).remind(attempt = 5)

        assertEquals(ReminderOutcome.Done, outcome)
        assertEquals(listOf(tomorrow), posted.single().map { it.expiresAt })
        assertEquals(LocalDate.of(2026, 10, 10), ledger.record.lastDay)
    }

    @Test
    fun `a failed reset read on an account that was not refreshed does not delay the reminder`() =
        runTest {
            val other = codex(nextWeek, id = "other")
            val repository =
                FakeRepository(
                    listOf(
                        codex(tomorrow),
                        other.copy(snapshot = other.snapshot!!.copy(resetsReadFailed = true)),
                    )
                )

            val outcome = reminders(repository).remind()

            assertEquals(ReminderOutcome.Done, outcome)
            assertEquals(1, posted.size)
        }

    @Test
    fun `the retry cutoff uses only the resets of accounts that may be stale`() = runTest {
        val soon = now.plus(Duration.ofHours(12)).plus(Duration.ofMinutes(2))
        val stale = codex(tomorrow)
        val repository = FakeRepository(listOf(codex(soon, id = "healthy"), stale))
        repository.afterRefresh =
            listOf(
                codex(id = "healthy"),
                stale.copy(snapshot = stale.snapshot!!.copy(resetsReadFailed = true)),
            )

        val outcome = reminders(repository).remind()

        assertEquals(ReminderOutcome.Retry(Duration.ofMinutes(5)), outcome)
        assertEquals(emptyList(), posted)
    }
}
