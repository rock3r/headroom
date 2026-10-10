package dev.sebastiano.headroom.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Why the redeem sheet opened: to use a reset, or to ask the provider for one. */
public enum class RedeemIntent {
    Use,
    AskForMore,
}

/** One step of the redeem sheet. Every provider goes through the same steps. */
public sealed interface RedeemStep {
    /** More than one pool has resets: the user picks which limit to reset. */
    public data class ChoosePool(val pools: List<ResetPool>) : RedeemStep

    /** The user confirms the reset. [canGoBack] returns to the choice of pool. */
    public data class Confirm(val pool: ResetPool, val canGoBack: Boolean) : RedeemStep

    /** The provider is resetting. The sheet cannot be closed now. */
    public data class Resetting(val pool: ResetPool) : RedeemStep

    /** The app reads the status of an unconfirmed attempt. The sheet cannot be closed now. */
    public data class Checking(val pool: ResetPool) : RedeemStep

    public data class Finished(val pool: ResetPool?, val outcome: RedeemOutcome) : RedeemStep

    /** The app is asking the provider for another reset. The sheet cannot be closed now. */
    public data object Asking : RedeemStep

    public data class Answered(val answer: AskOutcome) : RedeemStep

    /** The provider needs its own sign-in before resets can be seen or used. */
    public data object SignInRequired : RedeemStep
}

/**
 * The state of one redeem sheet, for one [account]. A confirmation sends the key that [memory]
 * holds for an unsettled attempt on the same pool, or a new one. [tryAgain] sends that same key
 * again, and [checkAgain] reads the status of an unconfirmed attempt without sending it again, so
 * no path can use two resets for one attempt.
 *
 * The calls are not meant to overlap: the sheet offers no action while [busy].
 */
public class RedeemSession(
    private val account: Account,
    private var availability: ResetAvailability,
    private val provider: ResetProvider,
    private val intent: RedeemIntent = RedeemIntent.Use,
    private val memory: ResetAttemptMemory = ResetAttemptMemory(),
    private val mintKey: () -> ResetAttemptKey = ResetAttemptKey::mint,
) {
    private val state = MutableStateFlow(firstStep())

    public val step: StateFlow<RedeemStep> = state.asStateFlow()

    /** The key of the attempt in progress, or of the last unsettled one; null before a confirm. */
    public var attemptKey: ResetAttemptKey? = null
        private set

    /** True while the provider is working: the sheet must not be dismissed. */
    public val busy: Boolean
        get() =
            state.value.let {
                it is RedeemStep.Resetting || it is RedeemStep.Checking || it is RedeemStep.Asking
            }

    /** Runs the first action of an ask. It does nothing for a session that uses a reset. */
    public suspend fun start() {
        if (state.value == RedeemStep.Asking) ask()
    }

    public fun choose(poolId: String) {
        val pool = availability.usablePools.firstOrNull { it.id == poolId } ?: return
        state.value = RedeemStep.Confirm(pool, canGoBack = availability.usablePools.size > 1)
    }

    /** From the confirmation, back to the choice of pool. */
    public fun back() {
        val current = state.value
        if (current is RedeemStep.Confirm && current.canGoBack) {
            state.value = RedeemStep.ChoosePool(availability.usablePools)
        }
    }

    /** Uses a reset from the pool being confirmed, when it can be used now. */
    public suspend fun confirm() {
        val pool = (state.value as? RedeemStep.Confirm)?.pool?.takeIf { it.canUseNow } ?: return
        val key = memory.recall(account, pool.id) ?: mintKey()
        attemptKey = key
        memory.remember(account, pool.id, key)
        redeem(pool, key)
    }

    /** Sends a failed or rate-limited attempt again, with the same key. */
    public suspend fun tryAgain() {
        val finished = state.value as? RedeemStep.Finished ?: return
        val pool = finished.pool ?: return
        val key = attemptKey ?: return
        val outcome = finished.outcome
        if (outcome is RedeemOutcome.Failed || outcome is RedeemOutcome.RateLimited) {
            redeem(pool, key)
        }
    }

    /** Reads what an unconfirmed attempt did, with its key. It never sends the reset again. */
    public suspend fun checkAgain() {
        val finished = state.value as? RedeemStep.Finished ?: return
        val pool = finished.pool ?: return
        val key = attemptKey ?: return
        if (finished.outcome != RedeemOutcome.Unconfirmed) return
        state.value = RedeemStep.Checking(pool)
        settle(pool, provider.check(account, pool.id, key))
    }

    /** Asks the provider for another reset. */
    public suspend fun ask() {
        state.value = RedeemStep.Asking
        state.value = RedeemStep.Answered(provider.askForMore(account))
    }

    /** After a granted reset, reads the resets again and goes on to use one. */
    public suspend fun useNow() {
        provider.availability(account)?.let { availability = it }
        attemptKey = null
        state.value = firstUseStep()
    }

    private suspend fun redeem(pool: ResetPool, key: ResetAttemptKey) {
        state.value = RedeemStep.Resetting(pool)
        settle(pool, provider.redeem(account, pool.id, key))
    }

    private fun settle(pool: ResetPool, outcome: RedeemOutcome) {
        if (outcome.isSettled) memory.forget(account, pool.id)
        state.value = RedeemStep.Finished(pool, outcome)
    }

    private fun firstStep(): RedeemStep =
        when {
            availability.requiresSignIn -> RedeemStep.SignInRequired
            intent == RedeemIntent.AskForMore -> RedeemStep.Asking
            else -> firstUseStep()
        }

    private fun firstUseStep(): RedeemStep {
        val pools = availability.usablePools
        return when (pools.size) {
            0 -> RedeemStep.Finished(availability.pools.firstOrNull(), RedeemOutcome.NoCredit)
            1 -> RedeemStep.Confirm(pools.single(), canGoBack = false)
            else -> RedeemStep.ChoosePool(pools)
        }
    }
}
