package dev.sebastiano.headroom.data.reset

import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.QuotaRepository
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.UsagePoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest

class ResetCheckerTest {
    private val now = Instant.parse("2026-09-28T06:01:00Z")
    private val before = DemoData.accounts(Instant.parse("2026-09-27T12:32:00Z"))
    private val grok = before.first { it.account.id == "demo-grok" }
    private val alarm =
        ResetAlarm(
            accountId = "demo-grok",
            windowId = "weekly",
            triggerAt = now,
            expectedResetAt = grok.primaryWindow!!.resetsAt!!,
            usedBefore = 88.0,
        )

    /**
     * Like the Room repository, [accounts] can lag behind a refresh; only [current] is guaranteed
     * to reflect it. Pass `publish = false` to keep [accounts] stale.
     */
    private class ScriptedRepository(
        initial: List<AccountState>,
        private val publish: Boolean = true,
        private val next: () -> List<AccountState>,
    ) : QuotaRepository {
        val state = MutableStateFlow(initial)
        private var committed = initial
        var refreshes = 0
        override val accounts: StateFlow<List<AccountState>> = state

        override suspend fun refresh(accountId: String?) {
            refreshes++
            committed = next()
            if (publish) state.value = committed
        }

        override suspend fun current(): List<AccountState> = committed

        override fun history(accountId: String, windowId: String): Flow<List<UsagePoint>> =
            emptyFlow()
    }

    private class RecordingNotifier : ResetNotifier {
        val notified = mutableListOf<Pair<AccountState, QuotaWindow>>()
        val usedBefore = mutableListOf<Double?>()
        val otherNames = mutableListOf<List<String>>()

        override suspend fun notifyReset(
            account: AccountState,
            window: QuotaWindow,
            usedBefore: Double?,
            otherNames: List<String>,
        ) {
            notified += account to window
            this.usedBefore += usedBefore
            this.otherNames += otherNames
        }
    }

    private class MemoryLedger : ResetLedger {
        val notified = mutableSetOf<Pair<Int, Instant>>()

        override fun wasNotified(alarm: ResetAlarm) =
            (alarm.requestCode to alarm.expectedResetAt) in notified

        override fun markNotified(alarm: ResetAlarm) {
            notified += alarm.requestCode to alarm.expectedResetAt
        }
    }

    private fun checker(
        repo: QuotaRepository,
        notifier: ResetNotifier,
        ledger: ResetLedger = MemoryLedger(),
        enabled: Boolean = true,
    ) = ResetChecker(repo, notifier, ledger) { _, _ -> enabled }

    private fun afterReset(): List<AccountState> = before.map { state ->
        if (state.account.id != "demo-grok") {
            state
        } else {
            val snapshot = state.snapshot!!
            state.copy(
                snapshot =
                    snapshot.copy(
                        windows =
                            snapshot.windows.map {
                                it.copy(
                                    usedPercent = 0.0,
                                    resetsAt = it.resetsAt!!.plus(7.days),
                                )
                            }
                    )
            )
        }
    }

    @Test
    fun `a confirmed reset posts one notification`() = runTest {
        val repo = ScriptedRepository(before) { afterReset() }
        val notifier = RecordingNotifier()
        val outcome = checker(repo, notifier).check(alarm, attempt = 1)
        assertEquals(ResetCheckOutcome.Notified, outcome)
        assertEquals(1, repo.refreshes)
        assertEquals("weekly", notifier.notified.single().second.id)
    }

    @Test
    fun `the notifier learns the usage before the reset and the provider's other accounts`() =
        runTest {
            val second =
                grok.copy(account = grok.account.copy(id = "grok-2", nickname = "Grok work"))
            val repo = ScriptedRepository(before + second) { afterReset() + second }
            val notifier = RecordingNotifier()

            checker(repo, notifier).check(alarm, attempt = 1)

            assertEquals(listOf<Double?>(88.0), notifier.usedBefore)
            assertEquals(listOf(listOf("Grok work")), notifier.otherNames)
        }

    @Test
    fun `no reset yet asks for a retry with the backoff delay`() = runTest {
        val repo = ScriptedRepository(before) { before }
        val notifier = RecordingNotifier()
        assertEquals(
            ResetCheckOutcome.Retry(30.seconds),
            checker(repo, notifier).check(alarm, 1),
        )
        assertEquals(
            ResetCheckOutcome.Retry(60.minutes),
            checker(repo, notifier).check(alarm, 5),
        )
        assertEquals(ResetCheckOutcome.GaveUp, checker(repo, notifier).check(alarm, 6))
        assertEquals(emptyList(), notifier.notified)
    }

    @Test
    fun `a removed account or window gives up quietly`() = runTest {
        val repo = ScriptedRepository(before) { before.filterNot { it.account.id == "demo-grok" } }
        assertEquals(
            ResetCheckOutcome.GaveUp,
            checker(repo, RecordingNotifier()).check(alarm, 1),
        )
    }

    @Test
    fun `the check reads the committed state even when the flow has not caught up`() = runTest {
        val repo = ScriptedRepository(before, publish = false) { afterReset() }
        val notifier = RecordingNotifier()
        assertEquals(ResetCheckOutcome.Notified, checker(repo, notifier).check(alarm, 1))
        assertEquals(1, notifier.notified.size)
    }

    @Test
    fun `a disabled alert does not notify, even on a retry`() = runTest {
        val repo = ScriptedRepository(before) { afterReset() }
        val notifier = RecordingNotifier()
        assertEquals(
            ResetCheckOutcome.GaveUp,
            checker(repo, notifier, enabled = false).check(alarm, 2),
        )
        assertEquals(emptyList(), notifier.notified)
    }

    @Test
    fun `the same reset is notified only once`() = runTest {
        val ledger = MemoryLedger()
        val notifier = RecordingNotifier()
        checker(ScriptedRepository(before) { afterReset() }, notifier, ledger).check(alarm, 1)
        val second =
            checker(ScriptedRepository(before) { afterReset() }, notifier, ledger).check(alarm, 1)
        assertEquals(ResetCheckOutcome.Notified, second)
        assertEquals(1, notifier.notified.size)
    }
}
