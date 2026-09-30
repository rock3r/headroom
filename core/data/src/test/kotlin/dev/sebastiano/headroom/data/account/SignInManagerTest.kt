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
import kotlin.test.assertFailsWith
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

        override suspend fun renameAccount(accountId: String, nickname: String?) = Unit

        override suspend fun relabelAccount(accountId: String, label: String) {
            state.value =
                state.value.map {
                    if (it.account.id == accountId)
                        it.copy(account = it.account.copy(label = label))
                    else it
                }
        }

        override suspend fun reorderAccounts(orderedIds: List<String>) = Unit

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

    @Test
    fun `signing in again keeps the account and its name, and replaces the credential`() = runTest {
        val store = InMemoryTokenStore()
        val accounts = MemoryAccounts()
        SignInManager(store, accounts) { "first" }
            .complete(tokens.copy(providerAccountId = "org-1"))
        accounts.state.value =
            accounts.state.value.map { it.copy(account = it.account.copy(nickname = "Work")) }

        val account =
            SignInManager(store, accounts) { "second" }
                .reauthenticate(
                    "first",
                    tokens.copy(accessToken = "fresh", providerAccountId = "org-1"),
                )

        assertEquals("first", account.id)
        assertEquals("Work", account.nickname)
        assertEquals("fresh", store.load("first")?.accessToken)
        assertNull(store.load("second"))
        assertEquals(listOf("first"), accounts.state.value.map { it.account.id })
        assertEquals(listOf<String?>("first", "first"), accounts.refreshed)
    }

    @Test
    fun `signing in again works when nothing is stored for the account any more`() = runTest {
        val store = InMemoryTokenStore()
        val accounts = MemoryAccounts()
        accounts.addAccount(Account("first", Provider.Claude, "sam@example.com"))

        SignInManager(store, accounts) { "second" }.reauthenticate("first", tokens)

        assertEquals("access", store.load("first")?.accessToken)
        assertEquals(listOf("first"), accounts.state.value.map { it.account.id })
    }

    @Test
    fun `signing in again as a different account is refused and changes nothing`() = runTest {
        val store = InMemoryTokenStore()
        val accounts = MemoryAccounts()
        SignInManager(store, accounts) { "first" }
            .complete(tokens.copy(providerAccountId = "org-1"))

        assertFailsWith<DifferentAccountException> {
            SignInManager(store, accounts)
                .reauthenticate(
                    "first",
                    tokens.copy(accessToken = "other", providerAccountId = "org-2"),
                )
        }
        assertEquals("access", store.load("first")?.accessToken)
    }

    @Test
    fun `signing in again to an account that was removed adds it as a new one`() = runTest {
        val store = InMemoryTokenStore()
        val accounts = MemoryAccounts()

        val account = SignInManager(store, accounts) { "new-id" }.reauthenticate("gone", tokens)

        assertEquals("new-id", account.id)
        assertEquals(listOf("new-id"), accounts.state.value.map { it.account.id })
    }

    @Test
    fun `signing in again updates the account's label when the provider renamed it`() = runTest {
        val store = InMemoryTokenStore()
        val accounts = MemoryAccounts()
        SignInManager(store, accounts) { "first" }
            .complete(tokens.copy(providerAccountId = "org-1"))

        val account =
            SignInManager(store, accounts)
                .reauthenticate(
                    "first",
                    tokens.copy(providerAccountId = "org-1", label = "sam@new.example.com"),
                )

        assertEquals("sam@new.example.com", account.label)
        assertEquals("sam@new.example.com", accounts.state.value.single().account.label)
    }

    @Test
    fun `without a provider account id, a different email means a different account`() = runTest {
        val store = InMemoryTokenStore()
        val accounts = MemoryAccounts()
        SignInManager(store, accounts) { "first" }.complete(tokens)

        assertFailsWith<DifferentAccountException> {
            SignInManager(store, accounts)
                .reauthenticate("first", tokens.copy(label = "alex@example.com"))
        }
        // The same email, in another case, is the same account.
        SignInManager(store, accounts)
            .reauthenticate("first", tokens.copy(label = "Sam@Example.com"))
    }

    @Test
    fun `a sign-in that names no account, such as an API key, is accepted`() = runTest {
        val store = InMemoryTokenStore()
        val accounts = MemoryAccounts()
        val key = TokenSet(Provider.ZAi, CredentialKind.ApiKey, "old-key", null, null, label = null)
        SignInManager(store, accounts) { "z" }.complete(key)

        SignInManager(store, accounts).reauthenticate("z", key.copy(accessToken = "new-key"))

        assertEquals("new-key", store.load("z")?.accessToken)
        assertEquals("Z.AI", accounts.state.value.single().account.label)
    }
}
