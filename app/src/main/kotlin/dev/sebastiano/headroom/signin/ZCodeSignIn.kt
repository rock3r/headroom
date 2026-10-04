package dev.sebastiano.headroom.signin

import dev.sebastiano.headroom.auth.AuthException
import dev.sebastiano.headroom.auth.TokenSet
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where the ZCode sign-in of a Z.AI account is. */
sealed interface ZCodeSignInState {
    /** The Z.AI account this sign-in is for; null when none runs. */
    val accountId: String?

    data object Idle : ZCodeSignInState {
        override val accountId: String? = null
    }

    data class Starting(override val accountId: String) : ZCodeSignInState

    /**
     * The ZCode page at [url] is open in the browser, and the app waits for the user. [opened]
     * counts the times the user asked for the page, so each ask is a new state that opens it again.
     */
    data class Waiting(override val accountId: String, val url: String, val opened: Int = 1) :
        ZCodeSignInState

    /** The user signed in. The tokens are stored and the account's resets read again. */
    data class Done(override val accountId: String) : ZCodeSignInState

    data class Failed(override val accountId: String, val error: SignInError) : ZCodeSignInState
}

/**
 * Runs the ZCode sign-in a Z.AI account's resets need, one at a time. [start] opens a ZCode flow,
 * whose page the app shows in the browser; the flow polls until the user signed in there. ZCode
 * finishes the sign-in on its own server, with no redirect back to the app, so [returnToApp] then
 * brings the app in front of the browser tab. [save] stores the tokens next to the account's API
 * key and refreshes the account, so its resets show. [log] gets one line per step, with no token.
 */
class ZCodeSignIn(
    private val start: suspend () -> DeviceSession,
    private val save: suspend (accountId: String, tokens: TokenSet) -> Unit,
    private val scope: CoroutineScope,
    private val returnToApp: () -> Unit = {},
    private val log: (String) -> Unit = {},
) {
    private val mutableState = MutableStateFlow<ZCodeSignInState>(ZCodeSignInState.Idle)
    val state: StateFlow<ZCodeSignInState> = mutableState.asStateFlow()

    private var job: Job? = null

    /**
     * Starts signing [accountId] in to ZCode. While its page already waits, this opens the same
     * page again: a new flow would leave the user on a page whose sign-in the app no longer
     * watches. A sign-in for another account stops.
     */
    fun start(accountId: String) {
        val current = mutableState.value
        if (current is ZCodeSignInState.Waiting && current.accountId == accountId) {
            log("ZCode sign-in: opening the page again")
            mutableState.value = current.copy(opened = current.opened + 1)
            return
        }
        if (current is ZCodeSignInState.Starting && current.accountId == accountId) return
        job?.cancel()
        mutableState.value = ZCodeSignInState.Starting(accountId)
        job = scope.launch {
            mutableState.value =
                try {
                    val session = start()
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
                    log("ZCode sign-in failed: ${failure.javaClass.simpleName}: ${failure.message}")
                    ZCodeSignInState.Failed(accountId, failure.toSignInError())
                } catch (failure: IOException) {
                    // The sign-in worked but storing it did not.
                    log("ZCode sign-in could not be saved: ${failure.javaClass.simpleName}")
                    ZCodeSignInState.Failed(accountId, SignInError.Unknown)
                }
        }
    }

    /** Stops the sign-in and forgets it. */
    fun cancel() {
        job?.cancel()
        job = null
        mutableState.value = ZCodeSignInState.Idle
    }

    private fun AuthException.toSignInError(): SignInError =
        when (this) {
            is AuthException.TimedOut -> SignInError.Expired
            is AuthException.Network -> SignInError.Network
            is AuthException.SignInFailed -> SignInError.Denied
            else -> SignInError.Unknown
        }
}
