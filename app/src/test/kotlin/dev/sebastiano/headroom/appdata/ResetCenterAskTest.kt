package dev.sebastiano.headroom.appdata

import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.AskOutcome
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.RedeemOutcome
import dev.sebastiano.headroom.model.ResetAttemptKey
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetPool
import dev.sebastiano.headroom.model.ResetProvider
import dev.sebastiano.headroom.model.ResetScope
import dev.sebastiano.headroom.model.WindowKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * A granted reset card only shows in the resets after the account's next sync, because the resets
 * come from the stored snapshot. So a grant refreshes the account before the ask returns, and "Use
 * it now" finds the new card.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ResetCenterAskTest {
    private val account = Account("z1", Provider.ZAi, "me")

    private fun pools(cards: Int) =
        ResetAvailability(
            listOf(
                ResetPool("five_hour", "5-hour limit", cards, ResetScope.of(WindowKind.Session))
            ),
            canAskForMore = true,
        )

    private class FakeProvider(var stored: ResetAvailability, val answer: AskOutcome) :
        ResetProvider {
        override suspend fun availability(account: Account): ResetAvailability = stored

        override suspend fun redeem(
            account: Account,
            poolId: String,
            attemptKey: ResetAttemptKey,
        ): RedeemOutcome = RedeemOutcome.Unsupported

        override suspend fun askForMore(account: Account): AskOutcome = answer
    }

    @Test
    fun `a granted card refreshes the account, so its resets show the card`() =
        runTest(UnconfinedTestDispatcher()) {
            val provider = FakeProvider(pools(0), AskOutcome.Granted(poolId = null))
            val refreshed = mutableListOf<String>()
            val center =
                ResetCenter(
                    provider,
                    MutableStateFlow(listOf(AccountState(account, null))),
                    backgroundScope,
                    refreshUsage = { id ->
                        refreshed += id
                        // The sync reads the new card and stores it.
                        provider.stored = pools(1)
                    },
                )

            assertEquals(AskOutcome.Granted(poolId = null), center.askForMore(account))

            assertEquals(listOf("z1"), refreshed)
            assertEquals(1, center.availability(account)?.availableNow)
            assertEquals(1, center.availability.value.getValue("z1").availableNow)
        }

    @Test
    fun `an ask that is not granted refreshes nothing`() =
        runTest(UnconfinedTestDispatcher()) {
            val provider = FakeProvider(pools(0), AskOutcome.NotYet(retryAfter = null))
            val refreshed = mutableListOf<String>()
            val center =
                ResetCenter(
                    provider,
                    MutableStateFlow(listOf(AccountState(account, null))),
                    backgroundScope,
                    refreshUsage = { refreshed += it },
                )

            center.askForMore(account)

            assertEquals(emptyList(), refreshed)
        }
}
