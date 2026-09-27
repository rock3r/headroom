package dev.sebastiano.headroom.data.account

import dev.sebastiano.headroom.auth.CredentialKind
import dev.sebastiano.headroom.auth.InMemoryTokenStore
import dev.sebastiano.headroom.auth.TokenSet
import dev.sebastiano.headroom.data.AccountsRepository
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.UsagePoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest

class SignInManagerTest {
    private class MemoryAccounts : AccountsRepository {
        val state = MutableStateFlow<List<AccountState>>(emptyList())
        val refreshed = mutableListOf<String?>()
        override val accounts: StateFlow<List<AccountState>> = state

        override suspend fun addAccount(account: Account) {
            state.value = state.value + AccountState(account, null)
        }

        override suspend fun removeAccount(accountId: String) {
            state.value = state.value.filterNot { it.account.id == accountId }
        }

        override suspend fun refresh(accountId: String?) {
            refreshed += accountId
        }

        override fun history(accountId: String, windowId: String): Flow<List<UsagePoint>> =
            emptyFlow()
    }

    private val tokens =
        TokenSet(
            Provider.Claude,
            CredentialKind.OAuth,
            "access",
            "refresh",
            null,
            label = "sam@example.com",
        )

    @Test
    fun `completing a sign-in stores the credential, adds the account and refreshes it`() =
        runTest {
            val store = InMemoryTokenStore()
            val accounts = MemoryAccounts()
            val account = SignInManager(store, accounts) { "new-id" }.complete(tokens)
            assertEquals(Account("new-id", Provider.Claude, "sam@example.com"), account)
            assertEquals("access", store.load("new-id")?.accessToken)
            assertEquals(listOf(account), accounts.state.value.map { it.account })
            assertEquals(listOf<String?>("new-id"), accounts.refreshed)
        }

    @Test
    fun `an account without a label is named after its provider`() = runTest {
        val account =
            SignInManager(InMemoryTokenStore(), MemoryAccounts()) { "x" }
                .complete(tokens.copy(label = null))
        assertEquals("Claude", account.label)
    }

    @Test
    fun `signing out removes the credential and the account`() = runTest {
        val store = InMemoryTokenStore()
        val accounts = MemoryAccounts()
        val manager = SignInManager(store, accounts) { "new-id" }
        manager.complete(tokens)
        manager.signOut("new-id")
        assertNull(store.load("new-id"))
        assertEquals(emptyList(), accounts.state.value)
    }
}
