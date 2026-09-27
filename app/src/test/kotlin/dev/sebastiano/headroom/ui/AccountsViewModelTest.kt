package dev.sebastiano.headroom.ui

import dev.sebastiano.headroom.MainDispatcherRule
import dev.sebastiano.headroom.model.FakeQuotaRepository
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.signin.FakeSignInController
import dev.sebastiano.headroom.signin.SignInState
import dev.sebastiano.headroom.ui.accounts.AccountsStep
import dev.sebastiano.headroom.ui.accounts.AccountsViewModel
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule

@OptIn(ExperimentalCoroutinesApi::class)
class AccountsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val now = Instant.parse("2026-09-27T12:32:00Z")
    private val signIn = FakeSignInController()

    // Created lazily, after the rule has installed the test Main dispatcher.
    private val viewModel by lazy {
        AccountsViewModel(
            repository = FakeQuotaRepository({ now }),
            signInController = signIn,
            isDemo = MutableStateFlow(true),
        )
    }

    @Test
    fun `it lists the accounts and says when they are demo data`() =
        runTest(main.dispatcher) {
            observe()
            val state = viewModel.state.value
            assertTrue(state.isDemo)
            assertEquals(4, state.accounts.size)
            assertEquals(AccountsStep.List, state.step)
        }

    @Test
    fun `adding an account goes through the provider picker to sign-in`() =
        runTest(main.dispatcher) {
            observe()
            viewModel.addAccount()
            runCurrent()
            assertEquals(AccountsStep.PickProvider, viewModel.state.value.step)

            viewModel.pickProvider(Provider.Claude)
            runCurrent()
            val step = assertIs<AccountsStep.SignIn>(viewModel.state.value.step)
            assertIs<SignInState.Browser>(step.state)
        }

    @Test
    fun `back leaves sign-in for the picker, then the picker for the list`() =
        runTest(main.dispatcher) {
            observe()
            viewModel.addAccount()
            viewModel.pickProvider(Provider.ZAi)
            runCurrent()

            viewModel.back()
            runCurrent()
            assertEquals(AccountsStep.PickProvider, viewModel.state.value.step)
            assertEquals(SignInState.Idle, signIn.state.value)

            viewModel.back()
            runCurrent()
            assertEquals(AccountsStep.List, viewModel.state.value.step)
        }

    @Test
    fun `finishing a sign-in returns to the list`() =
        runTest(main.dispatcher) {
            observe()
            viewModel.addAccount()
            viewModel.pickProvider(Provider.ZAi)
            viewModel.submitApiKey("zai-0123456789abcdef")
            runCurrent()
            val step = assertIs<AccountsStep.SignIn>(viewModel.state.value.step)
            assertIs<SignInState.Success>(step.state)

            viewModel.finish()
            runCurrent()
            assertEquals(AccountsStep.List, viewModel.state.value.step)
        }

    private fun TestScope.observe() {
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
    }
}
