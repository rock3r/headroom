package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.auth.TokenSet
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AskOutcome
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.RedeemIntent
import dev.sebastiano.headroom.model.RedeemOutcome
import dev.sebastiano.headroom.model.RedeemSession
import dev.sebastiano.headroom.model.RedeemStep
import dev.sebastiano.headroom.model.ResetAttemptKey
import dev.sebastiano.headroom.model.ResetAttemptMemory
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetEventLog
import dev.sebastiano.headroom.model.ResetPool
import dev.sebastiano.headroom.model.ResetProvider
import dev.sebastiano.headroom.model.ResetRefresh
import dev.sebastiano.headroom.signin.DeviceSession
import dev.sebastiano.headroom.signin.ZCodeSignIn
import dev.sebastiano.headroom.signin.ZCodeSignInState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The Resets tab. */
public data class ResetsTabUi(
    val isDemo: Boolean,
    /** Windows with their reset alert on, out of [alertWindows] that can alert. */
    val alertsOn: Int,
    val alertWindows: Int,
    /** Accounts holding resets, or able to ask for one, as on the overview. */
    val withResets: List<AccountUi>,
    /** Every window that can alert, soonest reset first. */
    val upcoming: List<UpcomingResetUi>,
    /** How much each account's main limit was used when it reset. */
    val history: List<ResetHistoryUi>,
)

public data class UpcomingResetUi(
    val accountId: String,
    val accountTitle: String,
    val providerId: String,
    val windowId: String,
    val windowLabel: String,
    val resetsAtEpochSeconds: Long,
    val alertOn: Boolean,
)

public data class ResetHistoryUi(
    val accountId: String,
    val accountTitle: String,
    val providerId: String,
    val windowLabel: String,
    /** Used at each past reset, oldest first. */
    val peaks: List<Double>,
    /** Used so far in the current window. */
    val current: Double,
)

/** One step of the redeem sheet, as [RedeemStep]. */
public sealed interface RedeemUi {
    public data object Idle : RedeemUi

    public data class ChoosePool(val pools: List<ResetPoolUi>) : RedeemUi

    /** [experimental] is true for Claude, whose resets Headroom uses on an experimental basis. */
    public data class Confirm(
        val pool: ResetPoolUi,
        val canGoBack: Boolean,
        val experimental: Boolean,
    ) : RedeemUi

    public data class Resetting(val pool: ResetPoolUi) : RedeemUi

    public data class Checking(val pool: ResetPoolUi) : RedeemUi

    /**
     * [outcome] is `success`, `nothingToReset`, `noCredit`, `cooldown`, `ineligible`,
     * `unconfirmed`, `rateLimited`, `signInAgain`, `failed` or `unsupported`.
     */
    public data class Finished(
        val outcome: String,
        val resetsLeft: Int?,
        val retryAfterEpochSeconds: Long?,
        /** Try the same attempt again: it reuses its key, so it can never use two resets. */
        val canTryAgain: Boolean,
        /** Read the status of an attempt whose answer was lost, without sending it again. */
        val canCheckAgain: Boolean,
    ) : RedeemUi

    public data object Asking : RedeemUi

    /** [answer] is `granted`, `notYet`, `throttled`, `failed` or `unsupported`. */
    public data class Answered(val answer: String, val retryAfterEpochSeconds: Long?) : RedeemUi

    /** The provider needs its own sign-in (ZCode, for Z.AI) before resets can be used. */
    public data object SignInRequired : RedeemUi
}

/** The ZCode sign-in a Z.AI account's resets need. */
public sealed interface ZCodeUi {
    public data object Idle : ZCodeUi

    public data object Starting : ZCodeUi

    /** The ZCode page at [url] is open; [opened] grows each time the user asks for it again. */
    public data class Waiting(val url: String, val opened: Int) : ZCodeUi

    public data object Done : ZCodeUi

    /** [error] is `denied`, `expired`, `network` or `unknown`. */
    public data class Failed(val error: String) : ZCodeUi
}

/** The Resets tab, the redeem sheet and the ZCode sign-in. */
@OptIn(ExperimentalCoroutinesApi::class)
public class HeadroomResets
internal constructor(
    private val sources: Sources,
    private val overview: Flow<OverviewUi>,
    provider: ResetProvider,
    memory: ResetAttemptMemory,
    events: ResetEventLog,
    private val scope: CoroutineScope,
    startZCode: suspend () -> DeviceSession,
    saveZCode: suspend (accountId: String, tokens: TokenSet) -> Unit,
) {
    public val redeem: HeadroomRedeem = HeadroomRedeem(sources, provider, memory, events, scope)

    public val zCode: HeadroomZCode =
        HeadroomZCode(ZCodeSignIn(startZCode, saveZCode, scope), scope)

    /** Calls [onChange] with the Resets tab now and at every change, until [Watch.cancel]. */
    public fun watchTab(onChange: (ResetsTabUi) -> Unit): Watch = scope.watch(tab(), onChange)

    private fun tab(): Flow<ResetsTabUi> = overview.flatMapLatest { view ->
        val mains = view.accounts.mapNotNull { account -> account.primary?.let { account to it } }
        val peaks =
            if (mains.isEmpty()) flowOf(emptyList())
            else combine(mains.map { (a, w) -> sources.resetPeaks(a.id, w.id) }) { it.toList() }
        peaks.map { all -> ResetsMapping.tab(view, mains.zip(all)) }
    }
}

/** The ZCode sign-in a Z.AI account's resets need. Its page opens in the sign-in sheet. */
public class HeadroomZCode
internal constructor(private val signIn: ZCodeSignIn, private val scope: CoroutineScope) {
    /** Calls [onChange] with the sign-in, until [Watch.cancel]. */
    public fun watch(onChange: (ZCodeUi) -> Unit): Watch =
        scope.watch(signIn.state.map(::ui), onChange)

    /** Starts the ZCode sign-in of a Z.AI account, or opens its page again. */
    public fun start(accountId: String) {
        signIn.start(accountId)
    }

    public fun cancel() {
        signIn.cancel()
    }

    private fun ui(state: ZCodeSignInState): ZCodeUi =
        when (state) {
            ZCodeSignInState.Idle -> ZCodeUi.Idle
            is ZCodeSignInState.Starting -> ZCodeUi.Starting
            is ZCodeSignInState.Waiting -> ZCodeUi.Waiting(state.url, state.opened)
            is ZCodeSignInState.Done -> ZCodeUi.Done
            is ZCodeSignInState.Failed -> ZCodeUi.Failed(state.error.id())
        }
}

/**
 * The redeem sheet. A redeem goes through the same [RedeemSession] as on Android: one key per
 * attempt, kept in [memory] across restarts, so no path uses two resets. After a reset works, it is
 * written to the reset history, and the account syncs [ResetRefresh.DELAY] later, when the provider
 * has applied it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class HeadroomRedeem
internal constructor(
    private val sources: Sources,
    private val provider: ResetProvider,
    private val memory: ResetAttemptMemory,
    private val events: ResetEventLog,
    private val scope: CoroutineScope,
) {
    private val session = MutableStateFlow<RedeemSession?>(null)
    private var pools: (ResetPool) -> ResetPoolUi = {
        UiMapping.pool(Provider.Codex, emptyList(), it)
    }
    private var experimental = false

    /** True while the provider is working: the sheet must stay open. */
    public val isBusy: Boolean
        get() = session.value?.busy == true

    /** Calls [onChange] with the redeem step, until [Watch.cancel]. */
    public fun watch(onChange: (RedeemUi) -> Unit): Watch =
        scope.watch(
            session.flatMapLatest { it?.step?.map(::ui) ?: flowOf(RedeemUi.Idle) },
            onChange,
        )

    /**
     * Opens the sheet for an account: to use one of its resets, or, with [askForMore], to ask the
     * provider for another.
     */
    public fun start(accountId: String, askForMore: Boolean) {
        scope.launch {
            val state =
                sources.real.current().firstOrNull { it.account.id == accountId } ?: return@launch
            val availability = state.snapshot?.resets ?: return@launch
            val windows = state.snapshot?.windows.orEmpty()
            pools = { UiMapping.pool(state.account.provider, windows, it) }
            experimental = state.account.provider == Provider.Claude
            val redeem =
                RedeemSession(
                    account = state.account,
                    availability = availability,
                    provider = Settling(provider, state.account),
                    intent = if (askForMore) RedeemIntent.AskForMore else RedeemIntent.Use,
                    memory = memory,
                )
            session.value = redeem
            redeem.start()
        }
    }

    public fun choosePool(poolId: String) {
        session.value?.choose(poolId)
    }

    public fun back() {
        session.value?.back()
    }

    public fun confirm(): Unit = run { it.confirm() }

    public fun tryAgain(): Unit = run { it.tryAgain() }

    public fun checkAgain(): Unit = run { it.checkAgain() }

    public fun ask(): Unit = run { it.ask() }

    /** Uses the reset a granted ask gave. */
    public fun useNow(): Unit = run { it.useNow() }

    /** Closes the sheet. Nothing happens while the provider is working: see [isBusy]. */
    public fun close() {
        if (session.value?.busy != true) session.value = null
    }

    private fun run(action: suspend (RedeemSession) -> Unit) {
        val current = session.value ?: return
        scope.launch { action(current) }
    }

    private fun ui(step: RedeemStep): RedeemUi =
        when (step) {
            is RedeemStep.ChoosePool -> RedeemUi.ChoosePool(step.pools.map(pools))
            is RedeemStep.Confirm ->
                RedeemUi.Confirm(pools(step.pool), step.canGoBack, experimental)
            is RedeemStep.Resetting -> RedeemUi.Resetting(pools(step.pool))
            is RedeemStep.Checking -> RedeemUi.Checking(pools(step.pool))
            is RedeemStep.Finished -> ResetsMapping.finished(step.outcome)
            RedeemStep.Asking -> RedeemUi.Asking
            is RedeemStep.Answered -> ResetsMapping.answered(step.answer)
            RedeemStep.SignInRequired -> RedeemUi.SignInRequired
        }

    /**
     * Records a reset that worked and syncs its account once the provider has applied it, as the
     * Android app's `ResetCenter` does. A granted ask syncs at once, so "use it now" finds the
     * reset.
     */
    private inner class Settling(private val inner: ResetProvider, private val account: Account) :
        ResetProvider {
        override suspend fun availability(account: Account): ResetAvailability? =
            inner.availability(account)

        override suspend fun redeem(
            account: Account,
            poolId: String,
            attemptKey: ResetAttemptKey,
        ): RedeemOutcome =
            inner.redeem(account, poolId, attemptKey).also { settle(poolId, attemptKey, it) }

        override suspend fun check(
            account: Account,
            poolId: String,
            attemptKey: ResetAttemptKey,
        ): RedeemOutcome =
            inner.check(account, poolId, attemptKey).also { settle(poolId, attemptKey, it) }

        override suspend fun askForMore(account: Account): AskOutcome =
            inner.askForMore(account).also { outcome ->
                if (outcome is AskOutcome.Granted) sources.refresh(account.id)
            }

        private suspend fun settle(
            poolId: String,
            attemptKey: ResetAttemptKey,
            outcome: RedeemOutcome,
        ) {
            if (outcome !is RedeemOutcome.Success) return
            events.redeemed(account, poolId, attemptKey)
            scope.launch {
                delay(ResetRefresh.DELAY)
                sources.refresh(account.id)
            }
        }
    }
}

internal object ResetsMapping {
    fun tab(
        view: OverviewUi,
        mains: List<Pair<Pair<AccountUi, WindowUi>, List<Double>>>,
    ): ResetsTabUi {
        val alertable =
            view.accounts.flatMap { account ->
                account.windows.filter { it.canAlert }.map { account to it }
            }
        return ResetsTabUi(
            isDemo = view.isDemo,
            alertsOn = alertable.count { (_, window) -> window.alertOn },
            alertWindows = alertable.size,
            withResets =
                view.accounts.filter { account ->
                    val resets = account.resets ?: return@filter false
                    resets.availableNow > 0 ||
                        resets.queued > 0 ||
                        resets.canAskForMore ||
                        resets.requiresSignIn
                },
            upcoming =
                alertable
                    .mapNotNull { (account, window) ->
                        val resetsAt = window.resetsAtEpochSeconds ?: return@mapNotNull null
                        UpcomingResetUi(
                            account.id,
                            account.title,
                            account.providerId,
                            window.id,
                            window.label,
                            resetsAt,
                            window.alertOn,
                        )
                    }
                    .sortedBy { it.resetsAtEpochSeconds },
            history =
                mains.map { (main, peaks) ->
                    val (account, window) = main
                    ResetHistoryUi(
                        account.id,
                        account.title,
                        account.providerId,
                        window.label,
                        peaks,
                        window.usedPercent,
                    )
                },
        )
    }

    fun finished(outcome: RedeemOutcome): RedeemUi.Finished =
        RedeemUi.Finished(
            outcome = outcomeId(outcome),
            resetsLeft = (outcome as? RedeemOutcome.Success)?.resetsLeft,
            retryAfterEpochSeconds =
                (outcome as? RedeemOutcome.RateLimited)?.retryAfter?.epochSeconds,
            canTryAgain = outcome is RedeemOutcome.Failed || outcome is RedeemOutcome.RateLimited,
            canCheckAgain = outcome == RedeemOutcome.Unconfirmed,
        )

    fun answered(answer: AskOutcome): RedeemUi.Answered =
        RedeemUi.Answered(
            answer =
                when (answer) {
                    is AskOutcome.Granted -> "granted"
                    is AskOutcome.NotYet -> "notYet"
                    AskOutcome.Throttled -> "throttled"
                    is AskOutcome.Failed -> "failed"
                    AskOutcome.Unsupported -> "unsupported"
                },
            retryAfterEpochSeconds = (answer as? AskOutcome.NotYet)?.retryAfter?.epochSeconds,
        )

    private fun outcomeId(outcome: RedeemOutcome): String =
        when (outcome) {
            is RedeemOutcome.Success -> "success"
            RedeemOutcome.NothingToReset -> "nothingToReset"
            RedeemOutcome.NoCredit -> "noCredit"
            RedeemOutcome.Cooldown -> "cooldown"
            RedeemOutcome.Ineligible -> "ineligible"
            RedeemOutcome.Unconfirmed -> "unconfirmed"
            is RedeemOutcome.RateLimited -> "rateLimited"
            RedeemOutcome.SignInAgain -> "signInAgain"
            is RedeemOutcome.Failed -> "failed"
            RedeemOutcome.Unsupported -> "unsupported"
        }
}
