package dev.sebastiano.headroom.appdata

import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.FakeQuotaRepository
import dev.sebastiano.headroom.model.Provider
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class DemoModeQuotaRepositoryTest {
    private val now = Instant.parse("2026-09-27T12:32:00Z")

    @Test
    fun `with no real accounts the demo accounts are shown`() = runTest {
        val repository =
            DemoModeQuotaRepository(
                real = FakeQuotaRepository({ now }, initial = emptyList()),
                demo = FakeQuotaRepository({ now }),
                scope = backgroundScope,
                simulatedLatency = Duration.ZERO,
            )
        runCurrent()
        assertTrue(repository.isDemo.value)
        assertEquals(DemoData.accounts(now).map { it.account.id }, repository.accountIds())
    }

    @Test
    fun `real accounts replace the demo accounts as soon as there are any`() = runTest {
        val real = FakeQuotaRepository({ now }, initial = emptyList())
        val repository =
            DemoModeQuotaRepository(
                real,
                FakeQuotaRepository({ now }),
                backgroundScope,
                Duration.ZERO,
            )
        real.set(listOf(AccountState(Account("real-1", Provider.Claude, "me"), snapshot = null)))
        runCurrent()
        assertFalse(repository.isDemo.value)
        assertEquals(listOf("real-1"), repository.accountIds())
    }

    @Test
    fun `refresh goes to the repository that is on screen`() = runTest {
        val demo = FakeQuotaRepository({ now })
        val repository =
            DemoModeQuotaRepository(
                FakeQuotaRepository({ now }, initial = emptyList()),
                demo,
                backgroundScope,
                Duration.ZERO,
            )
        val before = demo.accounts.value.first().primaryWindow!!.usedPercent
        repository.refresh()
        runCurrent()
        assertEquals(before + 1, repository.accounts.value.first().primaryWindow!!.usedPercent)
    }

    private fun DemoModeQuotaRepository.accountIds() = accounts.value.map { it.account.id }
}
