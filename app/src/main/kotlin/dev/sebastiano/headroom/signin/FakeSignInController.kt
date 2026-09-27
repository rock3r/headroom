package dev.sebastiano.headroom.signin

import dev.sebastiano.headroom.model.Provider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A scripted [SignInController] for previews, tests and demo mode. It never talks to a network:
 * each provider goes to the step its real flow starts with, a non-blank code or a long enough key
 * succeeds, and tests move a device-code flow on with [completeDeviceFlow] or stop it with [fail].
 */
class FakeSignInController(initial: SignInState = SignInState.Idle) : SignInController {
    private val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<SignInState> = mutableState.asStateFlow()

    private var provider: Provider? = null

    override fun start(provider: Provider) {
        this.provider = provider
        mutableState.value = firstStep(provider)
    }

    override fun submitCode(code: String) {
        val current = mutableState.value as? SignInState.Browser ?: return
        mutableState.value =
            if (code.isBlank()) current.copy(codeRejected = true)
            else SignInState.Success(current.provider, DEMO_ACCOUNT)
    }

    override fun submitApiKey(key: String) {
        val current = mutableState.value as? SignInState.ApiKey ?: return
        mutableState.value =
            if (key.trim().length < MIN_KEY_LENGTH) current.copy(keyRejected = true)
            else SignInState.Success(current.provider, DEMO_ACCOUNT)
    }

    override fun retry() {
        provider?.let { start(it) }
    }

    override fun cancel() {
        provider = null
        mutableState.value = SignInState.Idle
    }

    /** Pretends the user confirmed the device code on the provider's page. */
    fun completeDeviceFlow() {
        val current = mutableState.value as? SignInState.DeviceCode ?: return
        mutableState.value = SignInState.Success(current.provider, DEMO_ACCOUNT)
    }

    fun fail(error: SignInError) {
        provider?.let { mutableState.value = SignInState.Failed(it, error) }
    }

    private fun firstStep(provider: Provider): SignInState =
        when (provider) {
            Provider.Claude,
            Provider.Grok,
            Provider.JetBrains -> SignInState.Browser(provider, DEMO_AUTHORIZATION_URL)
            Provider.Codex,
            Provider.Copilot,
            Provider.Kimi -> SignInState.DeviceCode(provider, DEMO_USER_CODE, DEMO_VERIFICATION_URL)
            Provider.ZAi,
            Provider.OpenCodeGo -> SignInState.ApiKey(provider)
        }

    companion object {
        const val DEMO_USER_CODE: String = "HDRM-7K2Q"
        const val DEMO_VERIFICATION_URL: String = "https://example.com/device"
        const val DEMO_AUTHORIZATION_URL: String = "https://example.com/oauth/authorize"
        const val DEMO_ACCOUNT: String = "sam@example.com"
        private const val MIN_KEY_LENGTH = 12
    }
}
