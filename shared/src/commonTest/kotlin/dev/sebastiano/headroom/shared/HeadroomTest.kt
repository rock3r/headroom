package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.auth.CredentialKind
import dev.sebastiano.headroom.auth.InMemoryTokenStore
import dev.sebastiano.headroom.auth.TokenSet
import dev.sebastiano.headroom.data.AccountsRepository
import dev.sebastiano.headroom.data.account.SignInManager
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.UsagePoint
import dev.sebastiano.headroom.signin.BrowserSession
import dev.sebastiano.headroom.signin.DeviceSession
import dev.sebastiano.headroom.signin.SignInKind
import dev.sebastiano.headroom.signin.SignInSteps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent

@OptIn(ExperimentalCoroutinesApi::class)
class HeadroomTest {
    private val now = Instant.parse("2026-09-27T12:32:00Z")
    private val scope = TestScope()
    private val repository = MemoryAccountsRepository()
    private val headroom =
        Headroom(
            repository = repository,
            signInManager = SignInManager(InMemoryTokenStore(), repository),
            signInSteps = ApiKeySteps,
            scope = scope,
            clock = { now },
        )

    private fun latestOverview(): OverviewUi {
        var latest: OverviewUi? = null
        val watch = headroom.watchOverview { latest = it }
        scope.runCurrent()
        watch.cancel()
        return checkNotNull(latest)
    }

    @Test
    fun `without accounts the overview shows the demo accounts`() {
        val overview = latestOverview()

        assertTrue(overview.isDemo)
        assertEquals(DemoData.accounts(now).size, overview.accounts.size)
    }

    @Test
    fun `an API key sign-in adds a real account and ends the demo`() {
        var signIn: SignInUi = SignInUi.Idle
        val watch = headroom.signIn.watch { signIn = it }

        headroom.signIn.start("zai", accountId = null)
        scope.runCurrent()
        assertEquals(SignInUi.ApiKey("zai", "Z.AI", keyRejected = false), signIn)

        headroom.signIn.submitApiKey("secret-key")
        scope.runCurrent()
        watch.cancel()

        assertTrue(signIn is SignInUi.Success, "was $signIn")
        val overview = latestOverview()
        assertFalse(overview.isDemo)
        assertEquals(listOf("zai"), overview.accounts.map { it.providerId })
    }

    @Test
    fun `refresh syncs the real accounts once there are some`() {
        repository.add(Account("a1", Provider.Claude, "sam@example.com"))

        headroom.refresh(accountId = null)
        scope.runCurrent()

        assertEquals(listOf<String?>(null), repository.refreshes)
    }

    @Test
    fun `providers say how each one signs in`() {
        assertEquals(Provider.entries.map { it.id }, headroom.providers.map { it.id })
        assertTrue(headroom.providers.all { it.signIn == "apiKey" })
    }

    @Test
    fun `removing an account signs it out`() {
        repository.add(Account("a1", Provider.Claude, "sam@example.com"))

        headroom.removeAccount("a1")
        scope.runCurrent()

        assertTrue(repository.accounts.value.isEmpty())
    }
}

/** Every provider signs in with an API key, which needs no network. */
private object ApiKeySteps : SignInSteps {
    override fun kindOf(provider: Provider): SignInKind = SignInKind.ApiKey

    override suspend fun startBrowser(provider: Provider): BrowserSession = error("not used")

    override suspend fun startDeviceCode(provider: Provider): DeviceSession = error("not used")

    override fun apiKeyTokens(provider: Provider, key: String): TokenSet =
        TokenSet(provider, CredentialKind.ApiKey, key, null, null)
}

private class MemoryAccountsRepository : AccountsRepository {
    override val accounts = MutableStateFlow(emptyList<AccountState>())
    val refreshes = mutableListOf<String?>()

    fun add(account: Account) {
        accounts.update { it + AccountState(account, snapshot = null) }
    }

    override suspend fun addAccount(account: Account) = add(account)

    override suspend fun removeAccount(accountId: String) {
        accounts.update { list -> list.filterNot { it.account.id == accountId } }
    }

    override suspend fun renameAccount(accountId: String, nickname: String?) = Unit

    override suspend fun relabelAccount(accountId: String, label: String) = Unit

    override suspend fun reorderAccounts(orderedIds: List<String>) = Unit

    override suspend fun refresh(accountId: String?) {
        refreshes += accountId
    }

    override fun history(accountId: String, windowId: String): Flow<List<UsagePoint>> =
        flowOf(emptyList())
}
