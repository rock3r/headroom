package dev.sebastiano.headroom.signin

import dev.sebastiano.headroom.auth.AuthException
import dev.sebastiano.headroom.auth.AuthMethod
import dev.sebastiano.headroom.auth.AuthMethods
import dev.sebastiano.headroom.auth.BrowserSignIn
import dev.sebastiano.headroom.auth.DeviceCodePrompt
import dev.sebastiano.headroom.auth.TokenSet
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.Provider
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How a provider signs in, from the UI's point of view. */
enum class SignInKind {
    Browser,
    DeviceCode,
    ApiKey,
}

/** A browser sign-in in progress: the loopback redirect or a pasted code finishes it. */
interface BrowserSession : AutoCloseable {
    val authorizeUrl: String

    /** @throws AuthException.SignInFailed when the text is not a usable code. */
    fun submitPastedCode(code: String)

    /** [onCodeReceived] runs once a code arrives, before it is exchanged for the tokens. */
    suspend fun awaitTokens(onCodeReceived: () -> Unit): TokenSet
}

interface DeviceSession {
    val userCode: String
    val verificationUrl: String

    suspend fun awaitTokens(): TokenSet
}

/** The auth layer, seen through the steps the sign-in screens need. */
interface SignInSteps {
    fun kindOf(provider: Provider): SignInKind

    suspend fun startBrowser(provider: Provider): BrowserSession

    suspend fun startDeviceCode(provider: Provider): DeviceSession

    /** @throws AuthException.SignInFailed when the key is not usable. */
    fun apiKeyTokens(provider: Provider, key: String): TokenSet
}

/**
 * Runs sign-ins against the auth layer. When a sign-in produces tokens, [complete] stores them and
 * adds the account.
 */
class RealSignInController(
    private val steps: SignInSteps,
    private val scope: CoroutineScope,
    private val complete: suspend (TokenSet) -> Account,
) : SignInController {
    private val mutableState = MutableStateFlow<SignInState>(SignInState.Idle)
    override val state: StateFlow<SignInState> = mutableState.asStateFlow()

    /** One sign-in attempt. Only the current attempt may change [browser] or the state. */
    private var attempt = 0
    private var job: Job? = null
    private var browser: BrowserSession? = null
    private var provider: Provider? = null

    override fun start(provider: Provider) {
        stop()
        this.provider = provider
        when (steps.kindOf(provider)) {
            SignInKind.ApiKey -> mutableState.value = SignInState.ApiKey(provider)
            SignInKind.Browser -> run(provider) { id -> browserSignIn(provider, id) }
            SignInKind.DeviceCode -> run(provider) { _ -> deviceSignIn(provider) }
        }
    }

    private suspend fun browserSignIn(provider: Provider, id: Int): TokenSet {
        val session = steps.startBrowser(provider)
        if (id == attempt) browser = session
        return session.use {
            mutableState.value = SignInState.Browser(provider, session.authorizeUrl)
            session.awaitTokens(onCodeReceived = { if (id == attempt) finishing(provider) })
        }
    }

    private suspend fun deviceSignIn(provider: Provider): TokenSet {
        val session = steps.startDeviceCode(provider)
        mutableState.value =
            SignInState.DeviceCode(provider, session.userCode, session.verificationUrl)
        return session.awaitTokens()
    }

    private fun run(provider: Provider, signIn: suspend (attemptId: Int) -> TokenSet) {
        val id = attempt
        job = scope.launch {
            try {
                finish(provider, signIn(id))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: AuthException) {
                mutableState.value = SignInState.Failed(provider, failure.toSignInError())
            } catch (_: IOException) {
                // The sign-in worked but storing it did not.
                mutableState.value = SignInState.Failed(provider, SignInError.Unknown)
            } finally {
                // A cancelled attempt can finish unwinding after a new one started.
                if (id == attempt) browser = null
            }
        }
    }

    /** From here until [finish] returns, nothing is left for the user to do. */
    private fun finishing(provider: Provider) {
        mutableState.value = SignInState.Finishing(provider)
    }

    private suspend fun finish(provider: Provider, tokens: TokenSet) {
        finishing(provider)
        val account = complete(tokens)
        mutableState.value = SignInState.Success(provider, account.label)
    }

    override fun submitCode(code: String) {
        val session = browser ?: return
        try {
            session.submitPastedCode(code.trim())
            mutableState.update {
                if (it is SignInState.Browser) it.copy(codeRejected = false) else it
            }
        } catch (_: AuthException.SignInFailed) {
            mutableState.update {
                if (it is SignInState.Browser) it.copy(codeRejected = true) else it
            }
        }
    }

    override fun submitApiKey(key: String) {
        val current = provider ?: return
        val state = mutableState.value
        // One key at a time: once a key is accepted the form is gone, so a second tap while
        // finishing cannot add a second copy of the account.
        if (state !is SignInState.ApiKey) return
        val tokens =
            try {
                steps.apiKeyTokens(current, key)
            } catch (_: AuthException.SignInFailed) {
                mutableState.value = SignInState.ApiKey(current, keyRejected = true)
                return
            }
        finishing(current)
        job = scope.launch {
            try {
                finish(current, tokens)
            } catch (_: IOException) {
                mutableState.value = SignInState.Failed(current, SignInError.Unknown)
            }
        }
    }

    override fun retry() {
        provider?.let(::start)
    }

    override fun cancel() {
        stop()
        provider = null
        mutableState.value = SignInState.Idle
    }

    private fun stop() {
        attempt++
        job?.cancel()
        job = null
        browser?.close()
        browser = null
    }

    private fun AuthException.toSignInError(): SignInError =
        when (this) {
            is AuthException.Rejected ->
                if (error == "access_denied") SignInError.Denied else SignInError.Unknown
            is AuthException.TimedOut -> SignInError.Expired
            is AuthException.Network -> SignInError.Network
            else -> SignInError.Unknown
        }
}

/** [SignInSteps] backed by the auth layer's [AuthMethods]. */
class AuthSignInSteps(
    private val methods: AuthMethods,
    /** Where the success page's "Return to Headroom" link points. */
    private val returnUrl: String = RETURN_URL,
    /** Brings the app back in front of the browser when a redirect cannot. */
    private val bringAppToFront: () -> Unit = {},
) : SignInSteps {
    override fun kindOf(provider: Provider): SignInKind =
        when (methods.forProvider(provider)) {
            is AuthMethod.Browser -> SignInKind.Browser
            is AuthMethod.DeviceCode -> SignInKind.DeviceCode
            is AuthMethod.ApiKey -> SignInKind.ApiKey
        }

    override suspend fun startBrowser(provider: Provider): BrowserSession {
        val method = methods.forProvider(provider) as AuthMethod.Browser
        return BrowserAdapter(method.flow.start(returnUrl, bringAppToFront))
    }

    override suspend fun startDeviceCode(provider: Provider): DeviceSession {
        val method = methods.forProvider(provider) as AuthMethod.DeviceCode
        val prompt = method.flow.start()
        return DeviceAdapter(prompt) { method.flow.awaitTokens(prompt) }
    }

    override fun apiKeyTokens(provider: Provider, key: String): TokenSet =
        (methods.forProvider(provider) as AuthMethod.ApiKey).tokens(key)

    private class BrowserAdapter(private val signIn: BrowserSignIn) : BrowserSession {
        override val authorizeUrl: String = signIn.authorizeUrl

        override fun submitPastedCode(code: String) = signIn.submitPastedCode(code)

        override suspend fun awaitTokens(onCodeReceived: () -> Unit): TokenSet =
            signIn.awaitTokens(onCodeReceived)

        override fun close() = signIn.close()
    }

    private class DeviceAdapter(
        prompt: DeviceCodePrompt,
        private val await: suspend () -> TokenSet,
    ) : DeviceSession {
        override val userCode: String = prompt.userCode
        override val verificationUrl: String =
            prompt.verificationUriComplete ?: prompt.verificationUri

        override suspend fun awaitTokens(): TokenSet = await()
    }

    companion object {
        const val RETURN_URL: String = "headroom://signed-in"
    }
}
