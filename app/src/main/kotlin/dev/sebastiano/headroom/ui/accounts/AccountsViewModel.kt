package dev.sebastiano.headroom.ui.accounts

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaRepository
import dev.sebastiano.headroom.signin.SignInController
import dev.sebastiano.headroom.signin.SignInState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One row of the accounts list. */
@Immutable
data class AccountRow(
    val id: String,
    val provider: Provider,
    val label: String,
    val plan: String?,
    /** The name the user gave the account, if any. */
    val nickname: String? = null,
) {
    val name: String
        get() = nickname ?: provider.displayName
}

/** Where the accounts screen is: the list, the provider picker, or a sign-in step. */
@Immutable
sealed interface AccountsStep {
    data object List : AccountsStep

    data object PickProvider : AccountsStep

    data class SignIn(val state: SignInState) : AccountsStep
}

@Immutable
data class AccountsUiState(
    val accounts: kotlin.collections.List<AccountRow>,
    val isDemo: Boolean,
    val step: AccountsStep,
)

/** The accounts screen: lists accounts and walks the user through adding one. */
class AccountsViewModel(
    repository: QuotaRepository,
    private val signInController: SignInController,
    isDemo: Flow<Boolean>,
    /** Stores the name the user gives an account. A blank name removes it. */
    private val renameAccount: suspend (accountId: String, name: String?) -> Unit = { _, _ -> },
) : ViewModel() {
    private val picking = MutableStateFlow(false)

    val state: StateFlow<AccountsUiState> =
        combine(repository.accounts, isDemo, picking, signInController.state) {
                accounts,
                demo,
                pick,
                signIn ->
                AccountsUiState(accounts.map { it.toRow() }, demo, step(pick, signIn))
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT),
                AccountsUiState(
                    repository.accounts.value.map { it.toRow() },
                    isDemo = false,
                    step = step(picking.value, signInController.state.value),
                ),
            )

    fun rename(accountId: String, name: String) {
        viewModelScope.launch { renameAccount(accountId, name) }
    }

    fun addAccount() {
        picking.value = true
    }

    fun pickProvider(provider: Provider) {
        signInController.start(provider)
    }

    fun submitCode(code: String) = signInController.submitCode(code)

    fun submitApiKey(key: String) = signInController.submitApiKey(key)

    fun retry() = signInController.retry()

    /** One step back: out of sign-in to the picker, or out of the picker to the list. */
    fun back() {
        if (signInController.state.value != SignInState.Idle) {
            signInController.cancel()
        } else {
            picking.value = false
        }
    }

    /** Leaves a finished sign-in and returns to the list. */
    fun finish() {
        signInController.cancel()
        picking.value = false
    }

    private fun step(picking: Boolean, signIn: SignInState): AccountsStep =
        when {
            signIn != SignInState.Idle -> AccountsStep.SignIn(signIn)
            picking -> AccountsStep.PickProvider
            else -> AccountsStep.List
        }

    private fun AccountState.toRow() =
        AccountRow(
            account.id,
            account.provider,
            account.label,
            snapshot?.planLabel,
            account.nickname,
        )

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
