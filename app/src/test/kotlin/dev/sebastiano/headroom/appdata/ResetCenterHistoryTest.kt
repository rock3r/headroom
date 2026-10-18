package dev.sebastiano.headroom.appdata

import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.RedeemOutcome
import dev.sebastiano.headroom.model.ResetAttemptKey
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/**
 * A redeem that works in Headroom goes into the reset history before the usage is refreshed, so the
 * history measures what the reset gave back from the usage before it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ResetCenterHistoryTest {
    private var now = Instant.parse("2026-10-10T12:00:00Z")
    private val account = Account("c1", Provider.Codex, "me")
    private val key = ResetAttemptKey("key-1")

    private class FakeProvider(
        var redeemAnswer: RedeemOutcome,
        val checkAnswer: RedeemOutcome = RedeemOutcome.Unconfirmed,
    ) : ResetProvider {
        override suspend fun availability(account: Account): ResetAvailability =
            ResetAvailability.None

        override suspend fun redeem(
            account: Account,
            poolId: String,
            attemptKey: ResetAttemptKey,
        ): RedeemOutcome = redeemAnswer

        override suspend fun check(
            account: Account,
            poolId: String,
            attemptKey: ResetAttemptKey,
        ): RedeemOutcome = checkAnswer
    }

    private val calls = mutableListOf<String>()
    private val recordedAt = mutableListOf<Instant>()

    private fun center(provider: ResetProvider, scope: kotlinx.coroutines.CoroutineScope) =
        ResetCenter(
            provider,
            MutableStateFlow(listOf(AccountState(account, null))),
            scope,
            refreshUsage = { calls += "refresh $it" },
            refreshDelay = Duration.ZERO,
            clock = { now },
            recordRedeem = { account, poolId, attemptKey, attemptedAt ->
                calls += "record ${account.id} $poolId ${attemptKey.value}"
                recordedAt += attemptedAt
            },
        )

    @Test
    fun `a redeem that works is recorded before the usage is refreshed`() =
        runTest(UnconfinedTestDispatcher()) {
            val center =
                center(
                    FakeProvider(RedeemOutcome.Success(2), RedeemOutcome.Unconfirmed),
                    backgroundScope,
                )

            center.redeem(account, "credits", key)
            advanceUntilIdle()

            assertEquals(listOf("record c1 credits key-1", "refresh c1"), calls)
            assertEquals(listOf(now), recordedAt)
        }

    @Test
    fun `a retry that confirms an earlier try records the time of the first try`() =
        runTest(UnconfinedTestDispatcher()) {
            val firstTry = now
            val provider = FakeProvider(RedeemOutcome.Failed(QuotaErrorKind.Network))
            val center = center(provider, backgroundScope)

            center.redeem(account, "credits", key)
            now = firstTry.plus(2.minutes)
            provider.redeemAnswer = RedeemOutcome.Success(resetsLeft = null, replayed = true)
            center.redeem(account, "credits", key)
            advanceUntilIdle()

            assertEquals(listOf("record c1 credits key-1", "refresh c1"), calls)
            assertEquals(listOf(firstTry), recordedAt)
        }

    @Test
    fun `a check that confirms an earlier redeem records it with the first try time`() =
        runTest(UnconfinedTestDispatcher()) {
            val firstTry = now
            val center =
                center(
                    FakeProvider(RedeemOutcome.Unconfirmed, RedeemOutcome.Success(null)),
                    backgroundScope,
                )

            center.redeem(account, "credits", key)
            now = firstTry.plus(2.minutes)
            center.check(account, "credits", key)
            advanceUntilIdle()

            assertEquals(listOf("record c1 credits key-1", "refresh c1"), calls)
            assertEquals(listOf(firstTry), recordedAt)
        }

    @Test
    fun `a redeem that used nothing records nothing`() =
        runTest(UnconfinedTestDispatcher()) {
            val center =
                center(
                    FakeProvider(RedeemOutcome.NoCredit, RedeemOutcome.Unconfirmed),
                    backgroundScope,
                )

            center.redeem(account, "credits", key)
            advanceUntilIdle()

            assertEquals(emptyList(), calls)
        }
}
