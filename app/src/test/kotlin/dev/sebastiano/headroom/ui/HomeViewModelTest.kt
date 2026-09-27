package dev.sebastiano.headroom.ui

import androidx.lifecycle.SavedStateHandle
import dev.sebastiano.headroom.MainDispatcherRule
import dev.sebastiano.headroom.appdata.DemoResetHistory
import dev.sebastiano.headroom.appdata.InMemoryAlertPreferences
import dev.sebastiano.headroom.designsystem.PaceChipState
import dev.sebastiano.headroom.model.FakeQuotaRepository
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.WindowKind
import dev.sebastiano.headroom.ui.home.HomeViewModel
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val now = Instant.parse("2026-09-27T12:32:00Z")
    private val repository = FakeQuotaRepository({ now })
    // Created lazily, after the rule has installed the test Main dispatcher.
    private val viewModel by lazy {
        HomeViewModel(
            repository = repository,
            alertPreferences = InMemoryAlertPreferences(),
            clock = { now },
            isDemo = MutableStateFlow(true),
            accountsLoaded = MutableStateFlow(true),
            resetHistory = DemoResetHistory,
            tickInterval = null,
            savedStateHandle = SavedStateHandle(),
        )
    }

    @Test
    fun `the demo accounts are listed in order`() =
        runTest(main.dispatcher) {
            observe()
            val state = viewModel.state.value
            assertTrue(state.isDemo)
            assertEquals(
                listOf(Provider.Claude, Provider.Codex, Provider.Grok, Provider.Copilot),
                state.accounts.map { it.provider },
            )
            assertEquals(now, state.lastSyncedAt)
        }

    @Test
    fun `each account says how it compares with even pace`() =
        runTest(main.dispatcher) {
            observe()
            assertEquals(
                listOf(
                    PaceChipState.Over(11),
                    PaceChipState.Under(7),
                    PaceChipState.OnPace,
                    PaceChipState.Under(30),
                ),
                viewModel.state.value.accounts.map { it.pace },
            )
        }

    @Test
    fun `accounts over pace or nearly full need attention`() =
        runTest(main.dispatcher) {
            observe()
            val attention = viewModel.state.value.accounts.map { it.provider to it.needsAttention }
            assertEquals(
                listOf(
                    Provider.Claude to true,
                    Provider.Codex to false,
                    Provider.Grok to true,
                    Provider.Copilot to false,
                ),
                attention,
            )
        }

    @Test
    fun `the card leads with the weekly window and shows the session one`() =
        runTest(main.dispatcher) {
            observe()
            val claude = viewModel.state.value.accounts.first()
            assertEquals(71.0, claude.primary?.usedPercent)
            assertEquals(WindowKind.Weekly, claude.primary?.kind)
            assertEquals(38.0, claude.session?.usedPercent)
            assertEquals("Max 20x", claude.plan)
        }

    @Test
    fun `the next reset is the soonest weekly one`() =
        runTest(main.dispatcher) {
            observe()
            val next = assertNotNull(viewModel.state.value.nextReset)
            assertEquals(Provider.Grok, next.provider)
            assertEquals(now.plus(Duration.ofMinutes(928)), next.resetsAt)
            assertTrue(next.alertEnabled)
        }

    @Test
    fun `switching an alert off is reflected in the state`() =
        runTest(main.dispatcher) {
            observe()
            viewModel.setAlert("demo-claude", "seven_day", enabled = false)
            runCurrent()
            val windows = viewModel.state.value.accounts.first().windows.associateBy { it.id }
            assertFalse(windows.getValue("seven_day").alertEnabled)
            assertTrue(windows.getValue("seven_day_opus").alertEnabled)
            assertFalse(windows.getValue("five_hour").canAlert)
        }

    @Test
    fun `refreshing shows progress and then the new numbers`() =
        runTest(main.dispatcher) {
            val seen = mutableListOf<Boolean>()
            backgroundScope.launch { viewModel.state.collect { seen += it.isRefreshing } }
            runCurrent()
            viewModel.refresh()
            runCurrent()
            val state = viewModel.state.value
            assertEquals(listOf(false, true, false), seen.distinctConsecutive())
            assertEquals(72.0, state.accounts.first().primary?.usedPercent)
        }

    @Test
    fun `the detail follows the selection and projects the limit`() =
        runTest(main.dispatcher) {
            observe()
            val first = assertNotNull(viewModel.detail.value)
            assertEquals("demo-claude", first.account.id)
            val chart = assertNotNull(first.chart)
            assertTrue(chart.points.isNotEmpty())
            assertEquals(
                now.plus(Duration.ofMinutes(2488)).epochSecond / 60,
                chart.projectedLimitAt!!.epochSecond / 60,
            )

            viewModel.select("demo-codex")
            runCurrent()
            val codex = assertNotNull(viewModel.detail.value)
            assertEquals("demo-codex", codex.account.id)
            assertEquals(null, codex.chart?.projectedLimitAt)
            assertEquals(84, codex.chart?.projectedEndPercent?.roundToInt())
        }

    @Test
    fun `the usage at past resets comes with each account`() =
        runTest(main.dispatcher) {
            observe()
            assertEquals(
                listOf(82.0, 95.0, 100.0, 88.0, 100.0),
                viewModel.state.value.accounts.first().pastResets,
            )
        }

    @Test
    fun `the selection survives the view model being recreated`() =
        runTest(main.dispatcher) {
            val saved = SavedStateHandle()
            val first = viewModel(saved)
            first.select("demo-codex")

            val restored = viewModel(saved)
            backgroundScope.launch { restored.detail.collect {} }
            runCurrent()
            assertEquals("demo-codex", restored.detail.value?.account?.id)
        }

    @Test
    fun `a weekly reset while the app is open shows the account as just reset`() =
        runTest(main.dispatcher) {
            observe()
            repository.set(
                repository.accounts.value.map { state ->
                    if (state.account.id != "demo-grok") return@map state
                    val snapshot = requireNotNull(state.snapshot)
                    state.copy(
                        snapshot =
                            snapshot.copy(
                                windows =
                                    snapshot.windows.map {
                                        it.copy(
                                            usedPercent = 1.0,
                                            resetsAt = it.resetsAt?.plus(Duration.ofDays(7)),
                                        )
                                    }
                            )
                    )
                }
            )
            runCurrent()
            val grok = viewModel.state.value.accounts.first { it.id == "demo-grok" }
            assertTrue(grok.justReset)
            assertEquals(PaceChipState.JustReset, grok.pace)
            assertFalse(viewModel.state.value.accounts.first().justReset)
        }

    @Test
    fun `the first state agrees with the repository on demo mode and loading`() =
        runTest(main.dispatcher) {
            val loading =
                HomeViewModel(
                    repository = repository,
                    alertPreferences = InMemoryAlertPreferences(),
                    clock = { now },
                    isDemo = MutableStateFlow(true),
                    accountsLoaded = MutableStateFlow(false),
                    resetHistory = DemoResetHistory,
                    tickInterval = null,
                    savedStateHandle = SavedStateHandle(),
                )
            val first = loading.state.value
            assertTrue(first.isDemo)
            assertFalse(first.accountsLoaded)
        }

    private fun viewModel(saved: SavedStateHandle) =
        HomeViewModel(
            repository = repository,
            alertPreferences = InMemoryAlertPreferences(),
            clock = { now },
            isDemo = MutableStateFlow(true),
            accountsLoaded = MutableStateFlow(true),
            resetHistory = DemoResetHistory,
            tickInterval = null,
            savedStateHandle = saved,
        )

    private fun <T> List<T>.distinctConsecutive() = filterIndexed { index, value ->
        index == 0 || this[index - 1] != value
    }

    private fun TestScope.observe() {
        backgroundScope.launch { viewModel.state.collect {} }
        backgroundScope.launch { viewModel.detail.collect {} }
        runCurrent()
    }
}
