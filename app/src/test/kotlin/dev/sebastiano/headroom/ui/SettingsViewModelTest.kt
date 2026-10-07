package dev.sebastiano.headroom.ui

import dev.sebastiano.headroom.MainDispatcherRule
import dev.sebastiano.headroom.model.AppSettings
import dev.sebastiano.headroom.model.InMemorySettingsRepository
import dev.sebastiano.headroom.model.MotionPreference
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.SyncFrequency
import dev.sebastiano.headroom.model.ThemeMode
import dev.sebastiano.headroom.model.ThemePalette
import dev.sebastiano.headroom.ui.settings.SettingsUiState
import dev.sebastiano.headroom.ui.settings.SettingsViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repository =
        InMemorySettingsRepository(AppSettings(QuotaDisplay.Used, SyncFrequency.Hour1))

    // Created lazily, after the rule has installed the test Main dispatcher.
    private val viewModel by lazy { SettingsViewModel(repository, appVersion = "1.2.3") }

    private fun TestScope.observe() {
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
    }

    @Test
    fun `it shows the stored settings and the app version`() =
        runTest(main.dispatcher) {
            observe()
            assertEquals(
                SettingsUiState(QuotaDisplay.Used, SyncFrequency.Hour1, appVersion = "1.2.3"),
                viewModel.state.value,
            )
        }

    @Test
    fun `choosing left stores it`() =
        runTest(main.dispatcher) {
            observe()
            viewModel.setQuotaDisplay(QuotaDisplay.Left)
            runCurrent()
            assertEquals(QuotaDisplay.Left, repository.settings.value.quotaDisplay)
            assertEquals(QuotaDisplay.Left, viewModel.state.value.quotaDisplay)
        }

    @Test
    fun `it shows the stored appearance`() =
        runTest(main.dispatcher) {
            observe()
            val state = viewModel.state.value
            assertEquals(ThemeMode.System, state.theme)
            assertEquals(MotionPreference.System, state.motion)
            assertEquals(ThemePalette.Wallpaper, state.palette)
        }

    @Test
    fun `choosing a theme, motion and palette stores them`() =
        runTest(main.dispatcher) {
            observe()
            viewModel.setTheme(ThemeMode.Dark)
            viewModel.setMotion(MotionPreference.Reduced)
            viewModel.setPalette(ThemePalette.Tangerine)
            runCurrent()
            val stored = repository.settings.value
            assertEquals(ThemeMode.Dark, stored.theme)
            assertEquals(MotionPreference.Reduced, stored.motion)
            assertEquals(ThemePalette.Tangerine, stored.palette)
            val state = viewModel.state.value
            assertEquals(ThemeMode.Dark, state.theme)
            assertEquals(MotionPreference.Reduced, state.motion)
            assertEquals(ThemePalette.Tangerine, state.palette)
        }

    @Test
    fun `the delights are on until the user turns them off`() =
        runTest(main.dispatcher) {
            observe()
            assertTrue(viewModel.state.value.refreshShimmer)
            assertTrue(viewModel.state.value.resetConfetti)

            viewModel.setRefreshShimmer(false)
            viewModel.setResetConfetti(false)
            runCurrent()

            assertFalse(repository.settings.value.refreshShimmer)
            assertFalse(repository.settings.value.resetConfetti)
            assertFalse(viewModel.state.value.refreshShimmer)
            assertFalse(viewModel.state.value.resetConfetti)
        }

    @Test
    fun `the reset island is off until the user turns it on, and then it is stored`() =
        runTest(main.dispatcher) {
            observe()
            assertFalse(viewModel.state.value.resetIsland)

            viewModel.setResetIsland(true)
            runCurrent()

            assertTrue(repository.settings.value.resetIsland)
            assertTrue(viewModel.state.value.resetIsland)
        }

    @Test
    fun `redeeming Claude resets is off until the user turns it on, and then it is stored`() =
        runTest(main.dispatcher) {
            observe()
            assertFalse(viewModel.state.value.redeemClaudeResets)

            viewModel.setRedeemClaudeResets(true)
            runCurrent()

            assertTrue(repository.settings.value.redeemClaudeResets)
            assertTrue(viewModel.state.value.redeemClaudeResets)
        }

    @Test
    fun `choosing a frequency stores it`() =
        runTest(main.dispatcher) {
            observe()
            viewModel.setSyncFrequency(SyncFrequency.OnOpen)
            runCurrent()
            assertEquals(SyncFrequency.OnOpen, repository.settings.value.syncFrequency)
            assertEquals(SyncFrequency.OnOpen, viewModel.state.value.syncFrequency)
        }
}
