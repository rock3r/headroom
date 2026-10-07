package dev.sebastiano.headroom.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class RedeemSessionTest {
    private val account = Account("zai", Provider.ZAi, "sam")
    private val fiveHour =
        ResetPool("five_hour", "5-hour limit", 2, ResetScope.of(WindowKind.Session), listOf(SOON))
    private val week = ResetPool("week", "Weekly limit", 1, ResetScope.of(WindowKind.Weekly))

    @Test
    fun `one pool with resets starts at the confirmation`() {
        val session = session(ResetAvailability(listOf(fiveHour, week.copy(available = 0))))
        assertEquals(RedeemStep.Confirm(fiveHour, canGoBack = false), session.step.value)
    }

    @Test
    fun `two pools with resets start with the choice, and back returns to it`() {
        val session = session(ResetAvailability(listOf(fiveHour, week)))
        assertEquals(RedeemStep.ChoosePool(listOf(fiveHour, week)), session.step.value)

        session.choose("week")
        assertEquals(RedeemStep.Confirm(week, canGoBack = true), session.step.value)

        session.back()
        assertIs<RedeemStep.ChoosePool>(session.step.value)
    }

    @Test
    fun `a missing sign-in comes first`() {
        val session = session(ResetAvailability(emptyList(), requiresSignIn = true))
        assertEquals(RedeemStep.SignInRequired, session.step.value)
    }

    @Test
    fun `confirming redeems the chosen pool with a fresh key`() = runTest {
        val provider = ScriptedProvider(RedeemOutcome.Success(resetsLeft = 1))
        val session = session(ResetAvailability(listOf(fiveHour)), provider, keys("a"))

        session.confirm()

        assertEquals(RedeemStep.Finished(fiveHour, RedeemOutcome.Success(1)), session.step.value)
        assertEquals(listOf("five_hour" to "a"), provider.redeems)
    }

    @Test
    fun `try again sends the same key as the failed attempt`() = runTest {
        val provider =
            ScriptedProvider(
                RedeemOutcome.Failed(QuotaErrorKind.Network),
                RedeemOutcome.Success(resetsLeft = 1),
            )
        val session = session(ResetAvailability(listOf(fiveHour)), provider, keys("a", "b"))

        session.confirm()
        assertEquals(
            RedeemStep.Finished(fiveHour, RedeemOutcome.Failed(QuotaErrorKind.Network)),
            session.step.value,
        )
        session.tryAgain()

        assertEquals(listOf("five_hour" to "a", "five_hour" to "a"), provider.redeems)
        assertEquals(RedeemStep.Finished(fiveHour, RedeemOutcome.Success(1)), session.step.value)
    }

    @Test
    fun `the step shows resetting, and the session is busy, until the provider answers`() =
        runTest {
            val answer = CompletableDeferred<RedeemOutcome>()
            val provider = ScriptedProvider(gate = answer)
            val session = session(ResetAvailability(listOf(fiveHour)), provider)

            val redeem = async { session.confirm() }
            runCurrent()
            assertEquals(RedeemStep.Resetting(fiveHour), session.step.value)
            assertTrue(session.busy)

            answer.complete(RedeemOutcome.NothingToReset)
            redeem.await()
            assertFalse(session.busy)
            assertEquals(
                RedeemStep.Finished(fiveHour, RedeemOutcome.NothingToReset),
                session.step.value,
            )
        }

    @Test
    fun `asking for a card shows the answer`() = runTest {
        val provider = ScriptedProvider(ask = AskOutcome.NotYet(SOON))
        val availability = ResetAvailability(emptyList(), canAskForMore = true)
        val session = session(availability, provider, intent = RedeemIntent.AskForMore)
        assertEquals(RedeemStep.Asking, session.step.value)

        session.start()

        assertEquals(RedeemStep.Answered(AskOutcome.NotYet(SOON)), session.step.value)
    }

    @Test
    fun `a granted card can be used at once, from the new availability`() = runTest {
        val provider =
            ScriptedProvider(ask = AskOutcome.Granted("week")).apply {
                next = ResetAvailability(listOf(week), canAskForMore = true)
            }
        val session =
            session(
                ResetAvailability(emptyList(), canAskForMore = true),
                provider,
                intent = RedeemIntent.AskForMore,
            )
        session.start()

        session.useNow()

        assertEquals(RedeemStep.Confirm(week, canGoBack = false), session.step.value)
    }

    @Test
    fun `an unconfirmed attempt is checked again with its key, never sent again`() = runTest {
        val provider = ScriptedProvider(RedeemOutcome.Unconfirmed, check = RedeemOutcome.Success(0))
        val session = session(ResetAvailability(listOf(fiveHour)), provider, keys("a"))

        session.confirm()
        assertEquals(RedeemStep.Finished(fiveHour, RedeemOutcome.Unconfirmed), session.step.value)
        session.checkAgain()

        assertEquals(listOf("five_hour" to "a"), provider.redeems)
        assertEquals(listOf("five_hour" to "a"), provider.checks)
        assertEquals(RedeemStep.Finished(fiveHour, RedeemOutcome.Success(0)), session.step.value)
    }

    @Test
    fun `a new sheet after a failure sends the remembered key, until it settles`() = runTest {
        val memory = ResetAttemptMemory(clock = { NOW })
        val provider =
            ScriptedProvider(
                RedeemOutcome.Failed(QuotaErrorKind.Network),
                RedeemOutcome.Success(1),
                RedeemOutcome.Success(0),
            )
        val availability = ResetAvailability(listOf(fiveHour))
        val mint = keys("a", "b")

        session(availability, provider, mint, memory = memory).confirm()
        session(availability, provider, mint, memory = memory).confirm()
        session(availability, provider, mint, memory = memory).confirm()

        assertEquals(listOf("a", "a", "b"), provider.redeems.map { it.second })
    }

    @Test
    fun `a remembered key expires after its lifetime`() {
        var now = NOW
        val memory = ResetAttemptMemory(clock = { now })
        memory.remember(account, "five_hour", ResetAttemptKey("a"))

        now = NOW.plusSeconds(9 * 60)
        assertEquals(ResetAttemptKey("a"), memory.recall(account, "five_hour"))
        now = NOW.plusSeconds(10 * 60)
        assertEquals(null, memory.recall(account, "five_hour"))
    }

    @Test
    fun `a reset that needs a limit is offered but cannot be confirmed yet`() = runTest {
        val waiting = fiveHour.copy(status = ResetPoolStatus.WaitingForLimit)
        val provider = ScriptedProvider()
        val session = session(ResetAvailability(listOf(waiting)), provider)
        assertEquals(RedeemStep.Confirm(waiting, canGoBack = false), session.step.value)

        session.confirm()

        assertEquals(emptyList(), provider.redeems)
    }

    @Test
    fun `queued pools are never offered`() {
        val queued = week.copy(status = ResetPoolStatus.Queued)
        val session = session(ResetAvailability(listOf(fiveHour, queued)))
        assertEquals(RedeemStep.Confirm(fiveHour, canGoBack = false), session.step.value)
    }

    @Test
    fun `with no reset left the session says so`() {
        val session = session(ResetAvailability(listOf(week.copy(available = 0))))
        assertIs<RedeemStep.Finished>(session.step.value)
        assertEquals(RedeemOutcome.NoCredit, (session.step.value as RedeemStep.Finished).outcome)
    }

    private fun session(
        availability: ResetAvailability,
        provider: ResetProvider = ScriptedProvider(),
        mintKey: () -> ResetAttemptKey = ResetAttemptKey::mint,
        intent: RedeemIntent = RedeemIntent.Use,
        memory: ResetAttemptMemory = ResetAttemptMemory(clock = { NOW }),
    ) = RedeemSession(account, availability, provider, intent, memory, mintKey)

    private fun keys(vararg values: String): () -> ResetAttemptKey {
        val queue = ArrayDeque(values.toList())
        return { ResetAttemptKey(queue.removeFirst()) }
    }

    private class ScriptedProvider(
        vararg outcomes: RedeemOutcome,
        val ask: AskOutcome = AskOutcome.Unsupported,
        val gate: CompletableDeferred<RedeemOutcome>? = null,
        val check: RedeemOutcome = RedeemOutcome.Unconfirmed,
    ) : ResetProvider {
        private val outcomes = ArrayDeque(outcomes.toList())
        val redeems = mutableListOf<Pair<String, String>>()
        val checks = mutableListOf<Pair<String, String>>()
        var next: ResetAvailability? = null

        override suspend fun check(
            account: Account,
            poolId: String,
            attemptKey: ResetAttemptKey,
        ): RedeemOutcome {
            checks += poolId to attemptKey.value
            return check
        }

        override suspend fun availability(account: Account): ResetAvailability? = next

        override suspend fun redeem(
            account: Account,
            poolId: String,
            attemptKey: ResetAttemptKey,
        ): RedeemOutcome {
            redeems += poolId to attemptKey.value
            return gate?.await() ?: outcomes.removeFirstOrNull() ?: RedeemOutcome.Success(null)
        }

        override suspend fun askForMore(account: Account): AskOutcome = ask
    }

    private companion object {
        val SOON: Instant = Instant.parse("2026-10-02T09:00:00Z")
        val NOW: Instant = Instant.parse("2026-09-30T12:00:00Z")
    }
}
