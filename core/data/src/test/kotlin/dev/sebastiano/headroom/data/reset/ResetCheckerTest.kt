package dev.sebastiano.headroom.data.reset

import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.QuotaRepository
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.UsagePoint
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
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

    private class ScriptedRepository(
        initial: List<AccountState>,
        private val next: () -> List<AccountState>,
    ) : QuotaRepository {
        val state = MutableStateFlow(initial)
        var refreshes = 0
        override val accounts: StateFlow<List<AccountState>> = state

        override suspend fun refresh(accountId: String?) {
            refreshes++
            state.value = next()
        }

        override fun history(accountId: String, windowId: String): Flow<List<UsagePoint>> =
            emptyFlow()
    }

    private class RecordingNotifier : ResetNotifier {
        val notified = mutableListOf<Pair<AccountState, QuotaWindow>>()

        override fun notifyReset(account: AccountState, window: QuotaWindow) {
            notified += account to window
        }
    }

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
                                    resetsAt = it.resetsAt!!.plus(Duration.ofDays(7)),
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
        val outcome = ResetChecker(repo, notifier).check(alarm, attempt = 1)
        assertEquals(ResetCheckOutcome.Notified, outcome)
        assertEquals(1, repo.refreshes)
        assertEquals("weekly", notifier.notified.single().second.id)
    }

    @Test
    fun `no reset yet asks for a retry with the backoff delay`() = runTest {
        val repo = ScriptedRepository(before) { before }
        val notifier = RecordingNotifier()
        assertEquals(
            ResetCheckOutcome.Retry(Duration.ofMinutes(2)),
            ResetChecker(repo, notifier).check(alarm, 1),
        )
        assertEquals(
            ResetCheckOutcome.Retry(Duration.ofMinutes(60)),
            ResetChecker(repo, notifier).check(alarm, 4),
        )
        assertEquals(ResetCheckOutcome.GaveUp, ResetChecker(repo, notifier).check(alarm, 5))
        assertEquals(emptyList(), notifier.notified)
    }

    @Test
    fun `a removed account or window gives up quietly`() = runTest {
        val repo = ScriptedRepository(before) { before.filterNot { it.account.id == "demo-grok" } }
        assertEquals(
            ResetCheckOutcome.GaveUp,
            ResetChecker(repo, RecordingNotifier()).check(alarm, 1),
        )
    }
}
