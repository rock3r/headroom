package dev.sebastiano.headroom.data.account

import dev.sebastiano.headroom.auth.AuthException
import dev.sebastiano.headroom.auth.CredentialKind
import dev.sebastiano.headroom.auth.InMemoryTokenStore
import dev.sebastiano.headroom.auth.TokenSet
import dev.sebastiano.headroom.auth.ZCodeCredential
import dev.sebastiano.headroom.data.AccountsRepository
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.AuthFailureReason
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.UsagePoint
import dev.sebastiano.headroom.quota.ProviderCredentials
import dev.sebastiano.headroom.quota.QuotaFetcher
import dev.sebastiano.headroom.quota.QuotaFetchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class SignInManagerTest {
    private class MemoryAccounts : AccountsRepository {
        val state = MutableStateFlow<List<AccountState>>(emptyList())
        val refreshed = mutableListOf<String?>()

        /** When set, removing an account waits for it. */
        var removeGate: CompletableDeferred<Unit>? = null
        override val accounts: StateFlow<List<AccountState>> = state

        override suspend fun addAccount(account: Account) {
            state.value = state.value + AccountState(account, null)
        }

        override suspend fun removeAccount(accountId: String) {
            removeGate?.await()
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
    fun `completing a sign-in stores the credential adds the account and refreshes it`() = runTest {
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
    fun `signing in again keeps the account and its name and replaces the credential`() = runTest {
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
    fun `without a provider account id a different email means a different account`() = runTest {
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
    fun `a sign-in that names no account such as an API key is accepted`() = runTest {
        val store = InMemoryTokenStore()
        val accounts = MemoryAccounts()
        val key = TokenSet(Provider.ZAi, CredentialKind.ApiKey, "old-key", null, null, label = null)
        SignInManager(store, accounts) { "z" }.complete(key)

        SignInManager(store, accounts).reauthenticate("z", key.copy(accessToken = "new-key"))

        assertEquals("new-key", store.load("z")?.accessToken)
        assertEquals("Z.AI", accounts.state.value.single().account.label)
    }

    private val zCodeTokens =
        TokenSet(Provider.ZAi, CredentialKind.OAuth, "business", "bundle", null, label = "ada")

    @Test
    fun `a ZCode sign-in is stored next to the Z_AI account and refreshes it`() = runTest {
        val store = InMemoryTokenStore()
        val accounts = MemoryAccounts()
        val manager = SignInManager(store, accounts) { "z1" }
        manager.complete(zCodeTokens.copy(kind = CredentialKind.ApiKey, accessToken = "api-key"))
        accounts.refreshed.clear()

        manager.signInToZCode("z1", zCodeTokens)
        manager.signInToZCode("z1", zCodeTokens.copy(accessToken = "business-2"))

        assertEquals("api-key", store.load("z1")?.accessToken)
        assertEquals("business-2", store.load(ZCodeCredential.idFor("z1"))?.accessToken)
        assertEquals(listOf<String?>("z1", "z1"), accounts.refreshed)
    }

    @Test
    fun `signing out removes the ZCode sign-in too`() = runTest {
        val store = InMemoryTokenStore()
        val accounts = MemoryAccounts()
        val manager = SignInManager(store, accounts) { "z1" }
        manager.complete(zCodeTokens.copy(kind = CredentialKind.ApiKey, accessToken = "api-key"))
        manager.signInToZCode("z1", zCodeTokens)

        manager.signOut("z1")

        assertNull(store.load("z1"))
        assertNull(store.load(ZCodeCredential.idFor("z1")))
    }

    @Test
    fun `a ZCode sign-in that finishes after its account was removed is dropped`() = runTest {
        val store = InMemoryTokenStore()
        val accounts = MemoryAccounts()
        val manager = SignInManager(store, accounts) { "z1" }
        manager.complete(zCodeTokens.copy(kind = CredentialKind.ApiKey, accessToken = "api-key"))
        manager.signOut("z1")
        accounts.refreshed.clear()

        manager.signInToZCode("z1", zCodeTokens)

        assertNull(store.load(ZCodeCredential.idFor("z1")))
        assertEquals(emptyList<String?>(), accounts.refreshed)
    }

    @Test
    fun `a ZCode sign-in that races a sign-out leaves no credential behind`() = runTest {
        val store = InMemoryTokenStore()
        val accounts = MemoryAccounts()
        val manager = SignInManager(store, accounts) { "z1" }
        manager.complete(zCodeTokens.copy(kind = CredentialKind.ApiKey, accessToken = "api-key"))
        val removing = CompletableDeferred<Unit>()
        accounts.removeGate = removing

        // The sign-out deletes the credentials, then waits to remove the account.
        val signOut = launch { manager.signOut("z1") }
        runCurrent()
        val signIn = launch { manager.signInToZCode("z1", zCodeTokens) }
        runCurrent()
        removing.complete(Unit)
        joinAll(signOut, signIn)

        assertNull(store.load(ZCodeCredential.idFor("z1")))
        assertEquals(emptyList(), accounts.state.value)
    }

    private val openCodeKey =
        TokenSet(Provider.OpenCodeGo, CredentialKind.ApiKey, "old-key", null, null)

    private fun openCodeFetchers(onFetch: (ProviderCredentials) -> QuotaResult): QuotaFetchers =
        QuotaFetchers(
            listOf(
                object : QuotaFetcher {
                    override val provider: Provider = Provider.OpenCodeGo

                    override suspend fun fetch(credentials: ProviderCredentials): QuotaResult =
                        onFetch(credentials)
                }
            )
        )

    private val openCodeSnapshot =
        QuotaSnapshot(
            provider = Provider.OpenCodeGo,
            accountId = "oc",
            planLabel = "OpenCode Go",
            windows = emptyList(),
            fetchedAt = Instant.fromEpochSeconds(0),
        )

    @Test
    fun `an expired API key is rejected and leaves the stored credential untouched`() = runTest {
        val store = InMemoryTokenStore()
        val accounts = MemoryAccounts()
        SignInManager(store, accounts) { "oc" }.complete(openCodeKey)
        accounts.refreshed.clear()
        val fetchers = openCodeFetchers {
            QuotaResult.Failure(
                QuotaErrorKind.Auth,
                "expired",
                authReason = AuthFailureReason.KeyExpired,
            )
        }
        val manager = SignInManager(store, accounts, fetchers) { "oc-2" }

        val rejected =
            assertFailsWith<InvalidApiKeyException> {
                manager.reauthenticate("oc", openCodeKey.copy(accessToken = "expired-key"))
            }
        assertEquals(Provider.OpenCodeGo, rejected.provider)
        assertEquals(AuthFailureReason.KeyExpired, rejected.reason)
        assertEquals("old-key", store.load("oc")?.accessToken)
        assertEquals(emptyList<String?>(), accounts.refreshed)

        val rejectedNew =
            assertFailsWith<InvalidApiKeyException> {
                manager.complete(openCodeKey.copy(accessToken = "expired-key"))
            }
        assertEquals(AuthFailureReason.KeyExpired, rejectedNew.reason)
        assertNull(store.load("oc-2"))
    }

    @Test
    fun `a valid API key passes validation replaces the credential and refreshes`() = runTest {
        val store = InMemoryTokenStore()
        val accounts = MemoryAccounts()
        SignInManager(store, accounts) { "oc" }.complete(openCodeKey)
        accounts.refreshed.clear()
        val seenKeys = mutableListOf<String>()
        val fetchers = openCodeFetchers { credentials ->
            seenKeys += credentials.accessToken
            QuotaResult.Success(openCodeSnapshot)
        }
        val manager = SignInManager(store, accounts, fetchers) { "oc-2" }

        manager.reauthenticate("oc", openCodeKey.copy(accessToken = "fresh-key"))

        assertEquals(listOf("fresh-key"), seenKeys)
        assertEquals("fresh-key", store.load("oc")?.accessToken)
        assertEquals(listOf<String?>("oc"), accounts.refreshed)
    }

    @Test
    fun `a network error while validating an API key does not save it`() = runTest {
        val store = InMemoryTokenStore()
        val accounts = MemoryAccounts()
        SignInManager(store, accounts) { "oc" }.complete(openCodeKey)
        accounts.refreshed.clear()
        val fetchers = openCodeFetchers { QuotaResult.Failure(QuotaErrorKind.Network, "offline") }
        val manager = SignInManager(store, accounts, fetchers) { "oc-2" }

        assertFailsWith<AuthException.Network> {
            manager.reauthenticate("oc", openCodeKey.copy(accessToken = "new-key"))
        }
        assertEquals("old-key", store.load("oc")?.accessToken)
        assertEquals(emptyList<String?>(), accounts.refreshed)

        assertFailsWith<AuthException.Network> {
            manager.complete(openCodeKey.copy(accessToken = "new-key"))
        }
        assertNull(store.load("oc-2"))
    }
}
