package dev.sebastiano.headroom.ui.stats

import dev.sebastiano.headroom.MainDispatcherRule
import dev.sebastiano.headroom.appdata.DemoResetHistory
import dev.sebastiano.headroom.appdata.DemoUsageHistory
import dev.sebastiano.headroom.appdata.UsageHistory
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.FakeQuotaRepository
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.ResetAttemptKey
import dev.sebastiano.headroom.model.ResetEvent
import dev.sebastiano.headroom.model.ResetEventKind
import dev.sebastiano.headroom.model.ResetEventLog
import dev.sebastiano.headroom.model.ResetUseSource
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val now = Instant.parse("2026-09-27T12:32:00Z")

    private fun viewModel(
        history: UsageHistory,
        isDemo: Boolean = true,
        resetEvents: ResetEventLog = ResetEventLog.None,
    ) =
        StatsViewModel(
            repository = FakeQuotaRepository({ now }),
            history = history,
            isDemo = MutableStateFlow(isDemo),
            clock = { now },
            zone = ZoneOffset.UTC,
            computeDispatcher = main.dispatcher,
            resetEvents = resetEvents,
        )

    /** Answers with [events], and remembers since when it was asked. */
    private class FakeResetEvents(private val events: List<ResetEvent>) : ResetEventLog {
        var askedSince: Instant? = null

        override fun events(since: Instant): Flow<List<ResetEvent>> {
            askedSince = since
            return flowOf(events)
        }

        override suspend fun redeemed(
            account: Account,
            poolId: String,
            attemptKey: ResetAttemptKey,
        ) = Unit
    }

    @Test
    fun `the reset stat comes from the reset history of the last year`() =
        runTest(main.dispatcher) {
            val event =
                ResetEvent(
                    accountId = "c1",
                    provider = Provider.Codex,
                    poolId = "credits",
                    poolLabel = "Reset credits",
                    kind = ResetEventKind.Used,
                    at = now.minusSeconds(60),
                    source = ResetUseSource.Headroom,
                )
            val log = FakeResetEvents(listOf(event))
            val stats = viewModel(UsageHistory { _, _ -> flowOf(emptyList()) }, resetEvents = log)
            backgroundScope.launch { stats.state.collect {} }
            runCurrent()

            val usage = stats.state.value.stats.resetUsage
            assertEquals(1, usage?.of(ResetPeriod.FourWeeks)?.used)
            assertEquals(now.minus(Duration.ofDays(365)), log.askedSince)
        }

    @Test
    fun `the state is loading until the history is read`() =
        runTest(main.dispatcher) {
            val stats = viewModel(DemoUsageHistory(DemoResetHistory, { now }, ZoneOffset.UTC))
            assertTrue(stats.state.value.loading)
            backgroundScope.launch { stats.state.collect {} }
            runCurrent()
            assertFalse(stats.state.value.loading)
        }

    @Test
    fun `demo mode shows stats from the demo history of each main limit`() =
        runTest(main.dispatcher) {
            val stats = viewModel(DemoUsageHistory(DemoResetHistory, { now }, ZoneOffset.UTC))
            backgroundScope.launch { stats.state.collect {} }
            runCurrent()

            val state = stats.state.value
            assertTrue(state.isDemo)
            // Five past weeks each for Claude, Codex and Grok, and two months for Copilot.
            assertEquals(17, state.stats.resets?.total)
            assertEquals(4, state.stats.resets?.hits)
            assertEquals(99.0, state.stats.closestCall?.peak)
            assertEquals(Provider.Grok, state.stats.closestCall?.account?.provider)
            assertEquals(4, state.stats.sparklines.size)
            assertEquals(4, state.stats.shares.size)
            assertTrue(state.stats.heatmap != null)
        }

    @Test
    fun `without any history the stats are empty, not loading`() =
        runTest(main.dispatcher) {
            val stats = viewModel(UsageHistory { _, _ -> flowOf(emptyList()) }, isDemo = false)
            backgroundScope.launch { stats.state.collect {} }
            runCurrent()

            val state = stats.state.value
            assertFalse(state.loading)
            assertEquals(null, state.stats.resets)
            assertEquals(null, state.stats.coverage)
        }
}
