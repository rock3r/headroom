package dev.sebastiano.headroom.signin

import dev.sebastiano.headroom.auth.AuthException
import dev.sebastiano.headroom.auth.TokenSet
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.io.IOException

/** Where the ZCode sign-in of a Z.AI account is. */
public sealed interface ZCodeSignInState {
    /** The Z.AI account this sign-in is for; null when none runs. */
    public val accountId: String?

    public data object Idle : ZCodeSignInState {
        override val accountId: String? = null
    }

    public data class Starting(override val accountId: String) : ZCodeSignInState

    /**
     * The ZCode page at [url] is open in the browser, and the app waits for the user. [opened]
     * counts the times the user asked for the page, so each ask is a new state that opens it again.
     */
    public data class Waiting(
        override val accountId: String,
        val url: String,
        val opened: Int = 1,
    ) : ZCodeSignInState

    /** The user signed in. The tokens are stored and the account's resets read again. */
    public data class Done(override val accountId: String) : ZCodeSignInState

    public data class Failed(override val accountId: String, val error: SignInError) :
        ZCodeSignInState
}

/**
 * Runs the ZCode sign-in a Z.AI account's resets need, one at a time. [start] opens a ZCode flow,
 * whose page the app shows in the browser; the flow polls until the user signed in there. [prepare]
 * readies the page ahead of the tap, so the tap opens it at once. ZCode finishes the sign-in on its
 * own server, with no redirect back to the app, so [returnToApp] then brings the app in front of
 * the browser tab. [save] stores the tokens next to the account's API key and refreshes the
 * account, so its resets show. [log] gets one line per step, with no token.
 */
public class ZCodeSignIn(
    private val start: suspend () -> DeviceSession,
    private val save: suspend (accountId: String, tokens: TokenSet) -> Unit,
    private val scope: CoroutineScope,
    private val returnToApp: () -> Unit = {},
    private val log: (String) -> Unit = {},
) {
    private val mutableState = MutableStateFlow<ZCodeSignInState>(ZCodeSignInState.Idle)
    public val state: StateFlow<ZCodeSignInState> = mutableState.asStateFlow()

    private var job: Job? = null

    /** The page being readied by [prepare], and the account it is for. */
    private var prepared: Pair<String, Deferred<DeviceSession?>>? = null

    /**
     * Readies the ZCode page of [accountId] before [start] is asked for it, so the tap opens the
     * page at once instead of waiting for the round trip that makes it. A sign-in already running
     * for the account is left alone; a failed prepare is tried again on the tap.
     */
    public fun prepare(accountId: String) {
        val current = mutableState.value
        val running =
            current.accountId == accountId &&
                current !is ZCodeSignInState.Idle &&
                current !is ZCodeSignInState.Failed
        if (running) return
        val ready = prepared
        if (ready?.first == accountId && !ready.second.isCancelled) return
        ready?.second?.cancel()
        prepared = accountId to scope.async { readyPage() }
    }

    /**
     * Forgets the page [prepare] readied for [accountId], when whoever asked for it is gone. A
     * readied page expires a few minutes after it is made, so it must not be kept for later.
     */
    public fun forgetPrepared(accountId: String) {
        val ready = prepared
        if (ready?.first != accountId) return
        ready.second.cancel()
        prepared = null
    }

    /** The sign-in page, or null when making it failed: a failed prepare is not a failed tap. */
    private suspend fun readyPage(): DeviceSession? =
        try {
            start()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: AuthException) {
            log("ZCode sign-in: readying the page failed: ${failure.message}")
            null
        } catch (failure: IOException) {
            log("ZCode sign-in: readying the page failed: ${failure::class.simpleName}")
            null
        }

    /**
     * Starts signing [accountId] in to ZCode. While its page already waits, this opens the same
     * page again: a new flow would leave the user on a page whose sign-in the app no longer
     * watches. A sign-in for another account stops.
     */
    public fun start(accountId: String) {
        val current = mutableState.value
        if (current is ZCodeSignInState.Waiting && current.accountId == accountId) {
            log("ZCode sign-in: opening the page again")
            mutableState.value = current.copy(opened = current.opened + 1)
            return
        }
        if (current is ZCodeSignInState.Starting && current.accountId == accountId) return
        job?.cancel()
        val ready = prepared
        prepared = null
        if (ready != null && ready.first != accountId) ready.second.cancel()
        mutableState.value = ZCodeSignInState.Starting(accountId)
        job = scope.launch {
            mutableState.value =
                try {
                    val session = ready?.let { awaitPrepared(accountId, it) } ?: start()
                    mutableState.value =
                        ZCodeSignInState.Waiting(accountId, session.verificationUrl)
                    log("ZCode sign-in: waiting for the browser")
                    val tokens = session.awaitTokens()
                    log("ZCode sign-in: signed in, saving")
                    returnToApp()
                    save(accountId, tokens)
                    log("ZCode sign-in: saved")
                    ZCodeSignInState.Done(accountId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: AuthException) {
                    log("ZCode sign-in failed: ${failure::class.simpleName}: ${failure.message}")
                    ZCodeSignInState.Failed(accountId, failure.toSignInError())
                } catch (failure: IOException) {
                    // The sign-in worked but storing it did not.
                    log("ZCode sign-in could not be saved: ${failure::class.simpleName}")
                    ZCodeSignInState.Failed(accountId, SignInError.Unknown)
                }
        }
    }

    /** Stops the sign-in and forgets it. */
    public fun cancel() {
        job?.cancel()
        job = null
        prepared?.second?.cancel()
        prepared = null
        mutableState.value = ZCodeSignInState.Idle
    }

    /**
     * The page [prepare] readied for [accountId], or null when it is for another account or it
     * failed: a failed prepare is not a failed sign-in, so the tap starts again.
     */
    private suspend fun awaitPrepared(
        accountId: String,
        ready: Pair<String, Deferred<DeviceSession?>>,
    ): DeviceSession? = ready.takeIf { it.first == accountId }?.second?.await()

    private fun AuthException.toSignInError(): SignInError =
        when (this) {
            is AuthException.TimedOut -> SignInError.Expired
            is AuthException.Network -> SignInError.Network
            is AuthException.SignInFailed -> SignInError.Denied
            else -> SignInError.Unknown
        }
}
