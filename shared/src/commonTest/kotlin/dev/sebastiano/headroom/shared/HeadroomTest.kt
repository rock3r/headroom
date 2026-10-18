package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.auth.CredentialKind
import dev.sebastiano.headroom.auth.InMemoryTokenStore
import dev.sebastiano.headroom.auth.TokenSet
import dev.sebastiano.headroom.auth.TokenStore
import dev.sebastiano.headroom.data.AccountsRepository
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.AlertPreferences
import dev.sebastiano.headroom.model.AuthFailureReason
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.InMemorySettingsRepository
import dev.sebastiano.headroom.model.NoResets
import dev.sebastiano.headroom.model.OverviewSort
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.RedeemOutcome
import dev.sebastiano.headroom.model.ResetAttemptKey
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetPolicy
import dev.sebastiano.headroom.model.ResetPool
import dev.sebastiano.headroom.model.ResetProvider
import dev.sebastiano.headroom.model.ResetScope
import dev.sebastiano.headroom.model.UsagePoint
import dev.sebastiano.headroom.model.WindowKind
import dev.sebastiano.headroom.quota.ProviderCredentials
import dev.sebastiano.headroom.quota.QuotaFetcher
import dev.sebastiano.headroom.quota.QuotaFetchers
import dev.sebastiano.headroom.signin.BrowserSession
import dev.sebastiano.headroom.signin.DeviceSession
import dev.sebastiano.headroom.signin.SignInKind
import dev.sebastiano.headroom.signin.SignInSteps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.datetime.TimeZone
import kotlinx.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class HeadroomTest {
    private var now = Instant.parse("2026-09-27T12:32:00Z")
    private val scope = TestScope()
    private val repository = MemoryAccountsRepository()
    private val settings = InMemorySettingsRepository()
    private val alerts = MemoryAlertPreferences()
    private val resetProvider = CountingResetProvider()
    private val headroom =
        Headroom(
            HeadroomParts(
                repository = repository,
                tokenStore = InMemoryTokenStore(),
                signInSteps = ApiKeySteps,
                settings = settings,
                alerts = alerts,
                resetProvider = resetProvider,
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
    fun `an expired API key is rejected inline and keeps the previous key untouched`() {
        val store = InMemoryTokenStore()
        var result: QuotaResult =
            QuotaResult.Failure(
                QuotaErrorKind.Auth,
                "expired",
                authReason = AuthFailureReason.KeyExpired,
            )
        val fetchers =
            QuotaFetchers(
                listOf(
                    object : QuotaFetcher {
                        override val provider = Provider.OpenCodeGo

                        override suspend fun fetch(credentials: ProviderCredentials): QuotaResult =
                            result
                    }
                )
            )
        val validated =
            Headroom(
                HeadroomParts(
                    repository = repository,
                    tokenStore = store,
                    signInSteps = ApiKeySteps,
                    settings = settings,
                    alerts = alerts,
                    quotaFetchers = fetchers,
                    clock = { now },
                    zone = { TimeZone.UTC },
                    compute = StandardTestDispatcher(scope.testScheduler),
                ),
                scope,
            )
        repository.add(Account("oc-1", Provider.OpenCodeGo, "OpenCode Go"))
        scope.runTestResult {
            store.save(
                TokenSet(Provider.OpenCodeGo, CredentialKind.ApiKey, "old-key", null, null)
                    .toCredential("oc-1"),
                expectedRevision = null,
            )
        }

        var signIn: SignInUi = SignInUi.Idle
        val watch = validated.signIn.watch { signIn = it }
        validated.signIn.start("opencode-go", accountId = "oc-1")
        scope.runCurrent()

        validated.signIn.submitApiKey("expired-key")
        scope.runCurrent()

        assertEquals(
            SignInUi.ApiKey(
                "opencode-go",
                "OpenCode Go",
                keyRejected = true,
                rejection = "expired",
            ),
            signIn,
        )
        assertEquals("old-key", scope.runTestResult { store.load("oc-1")?.accessToken })

        result =
            QuotaResult.Success(
                QuotaSnapshot(Provider.OpenCodeGo, "oc-1", "OpenCode Go", emptyList(), now)
            )
        validated.signIn.submitApiKey("fresh-key")
        scope.runCurrent()
        watch.cancel()

        assertEquals(SignInUi.Success("opencode-go", "OpenCode Go", "OpenCode Go"), signIn)
        assertEquals("fresh-key", scope.runTestResult { store.load("oc-1")?.accessToken })
    }

    @Test
    fun `refresh syncs the real accounts once there are some`() {
        repository.add(Account("a1", Provider.Claude, "sam@example.com"))

        headroom.refresh(accountId = null)
        scope.runCurrent()

        assertEquals(listOf<String?>(null), repository.refreshes)
    }

    @Test
    fun `a background refresh leaves the demo accounts alone`() {
        val before = latestOverview()

        scope.launch { headroom.refreshAll() }
        scope.runCurrent()

        assertEquals(before, latestOverview())
    }

    @Test
    fun `a background refresh syncs the real accounts`() {
        repository.add(Account("a1", Provider.Claude, "sam@example.com"))

        scope.launch { headroom.refreshAll() }
        scope.runCurrent()

        assertEquals(listOf<String?>(null), repository.refreshes)
    }

    @Test
    fun `stored accounts not read yet show no demo`() {
        val stored = AccountState(Account("a1", Provider.Claude, "sam@example.com"), null)
        repository.stored = listOf(stored)
        var shown: OverviewUi? = null
        val watch = headroom.watchOverview { shown = it }
        scope.runCurrent()
        val beforeLoad = shown

        repository.accounts.value = listOf(stored)
        scope.runCurrent()
        watch.cancel()

        assertNull(beforeLoad)
        assertEquals(listOf("claude"), shown?.accounts?.map { it.providerId })
    }

    @Test
    fun `a sign-out the keychain refuses keeps the account and the app running`() {
        val refusing =
            Headroom(
                HeadroomParts(
                    repository = repository,
                    tokenStore = RefusingTokenStore(),
                    signInSteps = ApiKeySteps,
                    settings = settings,
                    alerts = alerts,
                    clock = { now },
                    zone = { TimeZone.UTC },
                    compute = StandardTestDispatcher(scope.testScheduler),
                ),
                scope,
            )
        repository.add(Account("a1", Provider.Claude, "sam@example.com"))

        refusing.accounts.remove("a1")
        scope.runCurrent()

        assertTrue(scope.isActive)
        assertEquals(listOf("a1"), repository.accounts.value.map { it.account.id })
    }

    @Test
    fun `providers say how each one signs in`() {
        assertEquals(Provider.entries.map { it.id }, headroom.providers.map { it.id })
        assertTrue(headroom.providers.all { it.signIn == "apiKey" })
    }

    @Test
    fun `a refresh with new numbers shimmers and a live weekly reset bursts once`() {
        val account = Account("a1", Provider.Claude, "sam@example.com")
        fun synced(used: Double, at: Instant) =
            AccountState(
                account,
                QuotaSnapshot(
                    Provider.Claude,
                    "a1",
                    null,
                    listOf(
                        QuotaWindow(
                            "seven_day",
                            "Weekly",
                            WindowKind.Weekly,
                            used,
                            now + 3.days,
                            7.days,
                        )
                    ),
                    at,
                ),
            )
        val events = mutableListOf<DelightUi>()
        val watch = headroom.watchDelights { events += it }
        repository.accounts.value = listOf(synced(80.0, now))
        scope.runCurrent()

        repository.accounts.value = listOf(synced(80.0, now).copy(isRefreshing = true))
        scope.runCurrent()
        repository.accounts.value = listOf(synced(2.0, now + 1.minutes))
        scope.runCurrent()
        watch.cancel()

        assertEquals(
            listOf(DelightUi.Burst("a1", fromNextReset = true), DelightUi.Shimmer),
            events,
        )
        assertTrue(latestOverview().accounts.single().justReset)
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
        assertTrue(latest(headroom.settings::watch).keepLongHistory)

        headroom.settings.setQuotaDisplay("left")
        headroom.settings.setSwitch("resetExpiryReminders", false)
        headroom.settings.setSwitch("keepLongHistory", false)
        headroom.settings.setSyncFrequency("hour1")
        scope.runCurrent()

        val updated = latest(headroom.settings::watch)
        assertEquals("left", updated.quotaDisplay)
        assertFalse(updated.resetExpiryReminders)
        assertFalse(updated.keepLongHistory)
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

    @Test
    fun `closing a redeem sheet cancels a start waiting for storage`() {
        repository.stored =
            DemoData.accounts(now).map { state ->
                state.copy(
                    snapshot = state.snapshot!!.copy(resets = ResetAvailability(emptyList()))
                )
            }
        repository.readGate = CompletableDeferred()
        var step: RedeemUi = RedeemUi.Idle
        val watch = headroom.resets.redeem.watch { step = it }
        headroom.resets.redeem.start("demo-codex", askForMore = true)
        scope.runCurrent()

        headroom.resets.redeem.close()
        repository.readGate!!.complete(Unit)
        scope.runCurrent()

        assertEquals(RedeemUi.Idle, step)
        watch.cancel()
    }

    @Test
    fun `the awaited alert mutation completes only after preferences are stored`() {
        alerts.writeGate = CompletableDeferred()
        var completed = false
        scope.launch {
            headroom.accounts.setAlertAndWait("a", "weekly", enabled = false)
            completed = true
        }
        scope.runCurrent()
        assertFalse(completed)

        alerts.writeGate!!.complete(Unit)
        scope.runCurrent()
        assertTrue(completed)
    }

    @Test
    fun `an open chart advances its pace without new account or history emissions`() {
        repository.accounts.value = DemoData.accounts(now)
        val state = repository.accounts.value.first()
        var chart: ChartUi? = null
        val watch =
            headroom.accounts.watchChart(state.account.id, state.primaryWindow!!.id) { chart = it }
        scope.runCurrent()
        val before = checkNotNull(chart).expectedPercent

        now += 1.minutes
        scope.advanceTimeBy(1.minutes.inWholeMilliseconds)
        scope.runCurrent()

        assertTrue(checkNotNull(chart).expectedPercent > before)
        watch.cancel()
    }

    @Test
    fun `closing a redeem sheet cancels a queued confirmation before the provider runs`() {
        repository.accounts.value =
            DemoData.accounts(now).map { state ->
                state.copy(
                    snapshot =
                        state.snapshot!!.copy(
                            resets =
                                ResetAvailability(
                                    listOf(
                                        ResetPool(
                                            "weekly",
                                            "Weekly",
                                            1,
                                            ResetScope.of(WindowKind.Weekly),
                                        )
                                    )
                                )
                        )
                )
            }
        headroom.resets.redeem.start("demo-codex", askForMore = false)
        scope.runCurrent()
        headroom.resets.redeem.confirm()
        headroom.resets.redeem.close()
        scope.runCurrent()
        assertEquals(0, resetProvider.redeems)
    }
}

private class CountingResetProvider : ResetProvider by NoResets {
    var redeems = 0

    override suspend fun redeem(
        account: Account,
        poolId: String,
        attemptKey: ResetAttemptKey,
    ): RedeemOutcome {
        redeems++
        return RedeemOutcome.NothingToReset
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

    /** What storage holds when [accounts] has not published it yet, as Room does at first. */
    var stored: List<AccountState>? = null
    var readGate: CompletableDeferred<Unit>? = null

    override suspend fun current(): List<AccountState> {
        readGate?.await()
        return stored ?: accounts.value
    }

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

/** A keychain that refuses to delete, as iOS's can. */
private class RefusingTokenStore(private val store: TokenStore = InMemoryTokenStore()) :
    TokenStore by store {
    override suspend fun delete(accountId: String) = throw IOException("Refused")
}

/** Alert switches in memory. */
private class MemoryAlertPreferences : AlertPreferences {
    var writeGate: CompletableDeferred<Unit>? = null
    private val switches = MutableStateFlow(emptyMap<Pair<String, String>, Boolean>())

    override fun isEnabled(accountId: String, window: QuotaWindow): Flow<Boolean> = switches.map {
        it[accountId to window.id] ?: ResetPolicy.alertsByDefault(window)
    }

    override suspend fun setEnabled(accountId: String, windowId: String, enabled: Boolean) {
        writeGate?.await()
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
