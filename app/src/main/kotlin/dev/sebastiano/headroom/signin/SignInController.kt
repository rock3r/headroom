package dev.sebastiano.headroom.signin

import dev.sebastiano.headroom.model.Provider
import kotlinx.coroutines.flow.StateFlow

/**
 * Drives the sign-in screens. The UI only renders [state] and forwards what the user does; the
 * implementation owns the flow for each provider (browser with a pasted-code fallback, device code,
 * or API key), talks to the auth layer and stores the account on success.
 *
 * The real implementation is wired in [dev.sebastiano.headroom.AppGraph] by the auth layer.
 * [FakeSignInController] serves previews, tests and demo mode until then.
 */
interface SignInController {
    val state: StateFlow<SignInState>

    /** Starts signing in to [provider]. The state moves to the provider's first step. */
    fun start(provider: Provider)

    /** The code the user pasted from the browser, for when the automatic return did not work. */
    fun submitCode(code: String)

    fun submitApiKey(key: String)

    /** Starts the same provider again after a failure. */
    fun retry()

    /** Abandons the sign-in and returns to [SignInState.Idle]. */
    fun cancel()
}

/** One step of a sign-in. */
sealed interface SignInState {
    data object Idle : SignInState

    /**
     * The provider's sign-in page is open in the browser, and the app waits for it to return. The
     * user can paste the code the page shows instead; [codeRejected] is true after a bad code.
     */
    data class Browser(
        val provider: Provider,
        val authorizationUrl: String,
        val codeRejected: Boolean = false,
    ) : SignInState

    /** The user enters [userCode] at [verificationUrl] on any device, and the app polls. */
    data class DeviceCode(
        val provider: Provider,
        val userCode: String,
        val verificationUrl: String,
    ) : SignInState

    /** The provider signs in with an API key. [keyRejected] is true after a bad key. */
    data class ApiKey(val provider: Provider, val keyRejected: Boolean = false) : SignInState

    /**
     * The provider said yes: the app has the code, the tokens or the key, and is exchanging them,
     * adding the account and fetching its first quotas. Nothing is left for the user to do.
     */
    data class Finishing(val provider: Provider) : SignInState

    data class Success(val provider: Provider, val accountLabel: String) : SignInState

    data class Failed(val provider: Provider, val error: SignInError) : SignInState
}

/** Why a sign-in stopped. The UI shows a different message for each. */
enum class SignInError {
    /** The user said no on the provider's page. */
    Denied,
    /** The code or the browser session timed out. */
    Expired,
    Network,
    Unknown,
}
