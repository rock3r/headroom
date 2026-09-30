package dev.sebastiano.headroom.data.account

import dev.sebastiano.headroom.data.AccountsRepository
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.UsagePoint
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest

class SignInAlertsTest {
    private val now = Instant.parse("2026-09-27T12:32:00Z")
    private val claude = DemoData.accounts(now).first { it.account.id == "demo-claude" }
    private val expired = claude.copy(lastError = QuotaErrorKind.Auth)

    private class RecordingNotifier : SignInNotifier {
        val posted = mutableListOf<Pair<String, Boolean>>()
        val cancelled = mutableListOf<String>()

        override fun notifyExpired(account: AccountState, showAccountName: Boolean) {
            posted += account.account.id to showAccountName
        }

        override fun cancel(accountId: String) {
            cancelled += accountId
        }
    }

    private class MemoryLedger : SignInAlertLedger {
        var ids: Set<String> = emptySet()

        override fun notified(): Set<String> = ids

        override fun save(ids: Set<String>) {
            this.ids = ids
        }
    }

    @Test
    fun `an expiry is posted once, and cancelled when the account syncs again`() {
        val notifier = RecordingNotifier()
        val ledger = MemoryLedger()
        val alerts = SignInAlerts(notifier, ledger)

        alerts.update(listOf(expired))
        alerts.update(listOf(expired))
        assertEquals(listOf("demo-claude" to false), notifier.posted)
        assertEquals(setOf("demo-claude"), ledger.ids)

        alerts.update(listOf(claude))
        assertEquals(listOf("demo-claude"), notifier.cancelled)
        assertEquals(emptySet(), ledger.ids)
    }

    @Test
    fun `every refresh and removal of the repository updates the alerts`() = runTest {
        val notifier = RecordingNotifier()
        val stored = MutableStateFlow(listOf(expired))
        val repository =
            SignInAlertingRepository(StoredAccounts(stored), SignInAlerts(notifier, MemoryLedger()))

        repository.refresh()
        assertEquals(listOf("demo-claude" to false), notifier.posted)

        repository.removeAccount("demo-claude")
        assertEquals(listOf("demo-claude"), notifier.cancelled)
    }

    private class StoredAccounts(val state: MutableStateFlow<List<AccountState>>) :
        AccountsRepository {
        override val accounts: StateFlow<List<AccountState>> = state

        override suspend fun addAccount(account: Account) = Unit

        override suspend fun removeAccount(accountId: String) {
            state.value = state.value.filterNot { it.account.id == accountId }
        }

        override suspend fun renameAccount(accountId: String, nickname: String?) = Unit

        override suspend fun reorderAccounts(orderedIds: List<String>) = Unit

        override suspend fun refresh(accountId: String?) = Unit

        override fun history(accountId: String, windowId: String): Flow<List<UsagePoint>> =
            emptyFlow()
    }

    @Test
    fun `the account is named when its provider has more than one account`() {
        val notifier = RecordingNotifier()
        val second = AccountState(Account("second", Provider.Claude, "alex@example.com"), null)

        SignInAlerts(notifier, MemoryLedger()).update(listOf(expired, second))

        assertEquals(listOf("demo-claude" to true), notifier.posted)
    }
}
