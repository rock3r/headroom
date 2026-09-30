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
import kotlinx.coroutines.flow.first
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

    /** A sign-in step. [again] is the existing account being signed in again, if any. */
    data class SignIn(val state: SignInState, val again: AccountRow? = null) : AccountsStep
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
    private val isDemo: Flow<Boolean>,
    /** Stores the name the user gives an account. A blank name removes it. */
    private val renameAccount: suspend (accountId: String, name: String?) -> Unit = { _, _ -> },
    /** Signs the account out and forgets it, with its history. */
    private val removeAccount: suspend (accountId: String) -> Unit = {},
    /** Stores the order the user put the accounts in. */
    private val reorderAccounts: suspend (orderedIds: kotlin.collections.List<String>) -> Unit = {},
) : ViewModel() {
    private val picking = MutableStateFlow(false)

    /** The account being signed in again, or null for a new sign-in. */
    private val again = MutableStateFlow<AccountRow?>(null)

    private val accounts = repository.accounts

    val state: StateFlow<AccountsUiState> =
        combine(repository.accounts, isDemo, picking, signInController.state, again) {
                accounts,
                demo,
                pick,
                signIn,
                againFor ->
                AccountsUiState(accounts.map { it.toRow() }, demo, step(pick, signIn, againFor))
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT),
                AccountsUiState(
                    repository.accounts.value.map { it.toRow() },
                    isDemo = false,
                    step = step(picking.value, signInController.state.value, again.value),
                ),
            )

    fun rename(accountId: String, name: String) {
        viewModelScope.launch { renameAccount(accountId, name) }
    }

    /** Saves a new order of the accounts. Demo accounts are not stored, so they keep theirs. */
    fun reorder(orderedIds: kotlin.collections.List<String>) {
        viewModelScope.launch { if (!isDemo.first()) reorderAccounts(orderedIds) }
    }

    fun remove(accountId: String) {
        viewModelScope.launch { removeAccount(accountId) }
    }

    fun addAccount() {
        picking.value = true
    }

    fun pickProvider(provider: Provider) {
        again.value = null
        signInController.start(provider)
    }

    /**
     * Starts signing [accountId] in again with its provider, for example after its sign-in expired.
     * The account keeps its id, history, name and alerts. Unknown accounts are ignored.
     */
    fun signInAgain(accountId: String) {
        val account = accounts.value.firstOrNull { it.account.id == accountId } ?: return
        again.value = account.toRow()
        signInController.start(account.account.provider, accountId)
    }

    fun submitCode(code: String) = signInController.submitCode(code)

    fun submitApiKey(key: String) = signInController.submitApiKey(key)

    fun retry() = signInController.retry()

    /** One step back: out of sign-in to the picker, or out of the picker to the list. */
    fun back() {
        if (signInController.state.value != SignInState.Idle) {
            signInController.cancel()
            again.value = null
        } else {
            picking.value = false
        }
    }

    /** Leaves a finished sign-in and returns to the list. */
    fun finish() {
        signInController.cancel()
        picking.value = false
        again.value = null
    }

    private fun step(picking: Boolean, signIn: SignInState, again: AccountRow?): AccountsStep =
        when {
            signIn != SignInState.Idle -> AccountsStep.SignIn(signIn, again)
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
