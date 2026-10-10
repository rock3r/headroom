package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.auth.CredentialKind
import dev.sebastiano.headroom.auth.InMemoryTokenStore
import dev.sebastiano.headroom.auth.TokenSet
import dev.sebastiano.headroom.data.AccountsRepository
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.AlertPreferences
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.InMemorySettingsRepository
import dev.sebastiano.headroom.model.OverviewSort
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.ResetPolicy
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.datetime.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class HeadroomTest {
    private val now = Instant.parse("2026-09-27T12:32:00Z")
    private val scope = TestScope()
    private val repository = MemoryAccountsRepository()
    private val settings = InMemorySettingsRepository()
    private val alerts = MemoryAlertPreferences()
    private val headroom =
        Headroom(
            HeadroomParts(
                repository = repository,
                tokenStore = InMemoryTokenStore(),
                signInSteps = ApiKeySteps,
                settings = settings,
                alerts = alerts,
                clock = { now },
                zone = { TimeZone.UTC },
                compute = StandardTestDispatcher(scope.testScheduler),
            ),
            scope,
        )

    private fun <T> latest(watch: ((T) -> Unit) -> Watch): T {
        var latest: T? = null
        val running = watch { latest = it }
        scope.runCurrent()
        running.cancel()
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

        headroom.accounts.remove("a1")
        scope.runCurrent()

        assertTrue(repository.accounts.value.isEmpty())
    }

    @Test
    fun `the overview follows the display and sort settings`() {
        scope.runTestResult {
            settings.setQuotaDisplay(QuotaDisplay.Left)
            settings.setOverviewSort(OverviewSort.MostUsedFirst)
        }

        val overview = latestOverview()

        assertEquals("left", overview.display)
        assertEquals("mostUsedFirst", overview.sort)
        val used = overview.accounts.map { it.primary?.usedPercent ?: -1.0 }
        assertEquals(used.sortedDescending(), used)
    }

    @Test
    fun `weekly alerts are on by default and a switch turns one off`() {
        val claude = latestOverview().accounts.first { it.providerId == "claude" }
        val weekly = claude.windows.first { it.kind == "weekly" }
        val session = claude.windows.first { it.kind == "session" }
        assertTrue(weekly.canAlert && weekly.alertOn)
        assertFalse(session.canAlert || session.alertOn)

        headroom.accounts.setAlert(claude.id, weekly.id, enabled = false)
        scope.runCurrent()

        val after = latestOverview().accounts.first { it.providerId == "claude" }
        assertFalse(after.windows.first { it.id == weekly.id }.alertOn)
    }

    @Test
    fun `the settings come back as ids and changes apply`() {
        assertEquals("used", latest(headroom.settings::watch).quotaDisplay)

        headroom.settings.setQuotaDisplay("left")
        headroom.settings.setSwitch("resetExpiryReminders", false)
        headroom.settings.setSyncFrequency("hour1")
        scope.runCurrent()

        val updated = latest(headroom.settings::watch)
        assertEquals("left", updated.quotaDisplay)
        assertFalse(updated.resetExpiryReminders)
        assertEquals(60, updated.syncMinutes)
    }

    @Test
    fun `the demo stats have history to show`() {
        val stats = latest(headroom.stats::watch)

        assertTrue(stats.isDemo)
        assertTrue(stats.shares.isNotEmpty())
        assertTrue(stats.sparklines.isNotEmpty())
        assertTrue(stats.resetScore != null)
    }

    @Test
    fun `the demo resets tab lists upcoming resets and their history`() {
        val tab = latest(headroom.resets::watchTab)

        assertTrue(tab.isDemo)
        assertTrue(tab.upcoming.isNotEmpty())
        assertEquals(tab.upcoming.sortedBy { it.resetsAtEpochSeconds }, tab.upcoming)
        assertEquals(
            listOf(82.0, 95.0, 100.0, 88.0, 100.0),
            tab.history.first { it.providerId == "claude" }.peaks,
        )
    }

    @Test
    fun `the demo has nothing to notify about`() {
        val plan = scope.runTestResult { headroom.notifications.plan(emptyList(), emptyList()) }

        assertTrue(plan.resetAlerts.isEmpty())
        assertTrue(plan.signInAlerts.isEmpty())
    }

    private fun latestOverview(): OverviewUi = latest(headroom::watchOverview)
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

/** Alert switches in memory. */
private class MemoryAlertPreferences : AlertPreferences {
    private val switches = MutableStateFlow(emptyMap<Pair<String, String>, Boolean>())

    override fun isEnabled(accountId: String, window: QuotaWindow): Flow<Boolean> = switches.map {
        it[accountId to window.id] ?: ResetPolicy.alertsByDefault(window)
    }

    override suspend fun setEnabled(accountId: String, windowId: String, enabled: Boolean) {
        switches.update { it + ((accountId to windowId) to enabled) }
    }
}

/** Runs [block] on the test scheduler and returns what it returned. */
@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> TestScope.runTestResult(block: suspend () -> T): T {
    var result: T? = null
    launch { result = block() }
    runCurrent()
    @Suppress("UNCHECKED_CAST")
    return result as T
}
