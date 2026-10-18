package dev.sebastiano.headroom.signin

import dev.sebastiano.headroom.auth.AuthException
import dev.sebastiano.headroom.auth.TokenSet
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.io.IOException

/**
 * Where a second sign-in of an account is. Z.AI accounts need one for their resets (ZCode), and
 * Grok accounts need one for their Cursor model pools and Grok Bot pool (Cursor). Both run the same
 * way: the app opens a page in the browser and polls until the user signed in there.
 */
public sealed interface ServiceSignInState {
    /** The account this sign-in is for; null when none runs. */
    public val accountId: String?

    public data object Idle : ServiceSignInState {
        override val accountId: String? = null
    }

    public data class Starting(override val accountId: String) : ServiceSignInState

    /**
     * The sign-in page at [url] is open in the browser, and the app waits for the user. [opened]
     * counts the times the user asked for the page, so each ask is a new state that opens it again.
     */
    public data class Waiting(
        override val accountId: String,
        val url: String,
        val opened: Int = 1,
    ) : ServiceSignInState

    /** The user signed in. The tokens are stored and the account's quotas read again. */
    public data class Done(override val accountId: String) : ServiceSignInState

    public data class Failed(override val accountId: String, val error: SignInError) :
        ServiceSignInState
}

/**
 * Runs a second sign-in that an account's data needs, one at a time. [start] opens the flow, whose
 * page the app shows in the browser; the flow polls until the user signed in there. The service
 * finishes the sign-in on its own server, with no redirect back to the app, so [returnToApp] then
 * brings the app in front of the browser tab. [save] stores the tokens next to the account's own
 * credential and refreshes the account, so the data that needs them shows. [log] gets one line per
 * step, with no token. [label] names the service in those lines, such as "ZCode" or "Cursor".
 * [onWaiting] gets the page's URL each time the app waits for the user, including when the page is
 * asked for again.
 */
public class ServiceSignIn(
    private val label: String = "Sign-in",
    private val start: suspend () -> DeviceSession,
    private val save: suspend (accountId: String, tokens: TokenSet) -> Unit,
    private val scope: CoroutineScope,
    private val returnToApp: () -> Unit = {},
    private val onWaiting: (url: String) -> Unit = {},
    private val log: (String) -> Unit = {},
) {
    private val mutableState = MutableStateFlow<ServiceSignInState>(ServiceSignInState.Idle)
    public val state: StateFlow<ServiceSignInState> = mutableState.asStateFlow()

    private var job: Job? = null

    /**
     * Starts signing [accountId] in. While its page already waits, this opens the same page again:
     * a new flow would leave the user on a page whose sign-in the app no longer watches. A sign-in
     * for another account stops.
     */
    public fun start(accountId: String) {
        val current = mutableState.value
        if (current is ServiceSignInState.Waiting && current.accountId == accountId) {
            log("$label sign-in: opening the page again")
            mutableState.value = current.copy(opened = current.opened + 1)
            onWaiting(current.url)
            return
        }
        if (current is ServiceSignInState.Starting && current.accountId == accountId) return
        job?.cancel()
        mutableState.value = ServiceSignInState.Starting(accountId)
        job = scope.launch {
            mutableState.value =
                try {
                    val session = start()
                    mutableState.value =
                        ServiceSignInState.Waiting(accountId, session.verificationUrl)
                    log("$label sign-in: waiting for the browser")
                    onWaiting(session.verificationUrl)
                    val tokens = session.awaitTokens()
                    log("$label sign-in: signed in, saving")
                    returnToApp()
                    save(accountId, tokens)
                    log("$label sign-in: saved")
                    ServiceSignInState.Done(accountId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: AuthException) {
                    log("$label sign-in failed: ${failure::class.simpleName}: ${failure.message}")
                    ServiceSignInState.Failed(accountId, failure.toSignInError())
                } catch (failure: IOException) {
                    // The sign-in worked but storing it did not.
                    log("$label sign-in could not be saved: ${failure::class.simpleName}")
                    ServiceSignInState.Failed(accountId, SignInError.Unknown)
                }
        }
    }

    /** Stops the sign-in and forgets it. */
    public fun cancel() {
        job?.cancel()
        job = null
        mutableState.value = ServiceSignInState.Idle
    }

    private fun AuthException.toSignInError(): SignInError =
        when (this) {
            is AuthException.TimedOut -> SignInError.Expired
            is AuthException.Network -> SignInError.Network
            is AuthException.SignInFailed -> SignInError.Denied
            else -> SignInError.Unknown
        }
}
