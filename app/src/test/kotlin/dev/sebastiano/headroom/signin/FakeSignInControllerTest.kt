package dev.sebastiano.headroom.signin

import dev.sebastiano.headroom.model.Provider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FakeSignInControllerTest {
    private val controller = FakeSignInController()

    @Test
    fun `it starts idle`() {
        assertEquals(SignInState.Idle, controller.state.value)
    }

    @Test
    fun `browser providers wait for the browser and accept a pasted code`() {
        controller.start(Provider.Claude)
        val browser = assertIs<SignInState.Browser>(controller.state.value)
        assertEquals(Provider.Claude, browser.provider)

        controller.submitCode("  ")
        assertEquals(true, assertIs<SignInState.Browser>(controller.state.value).codeRejected)

        controller.submitCode("abc#def")
        assertEquals(
            Provider.Claude,
            assertIs<SignInState.Success>(controller.state.value).provider,
        )
    }

    @Test
    fun `device code providers show a code and a verification address`() {
        controller.start(Provider.Codex)
        val device = assertIs<SignInState.DeviceCode>(controller.state.value)
        assertEquals(FakeSignInController.DEMO_USER_CODE, device.userCode)

        controller.completeDeviceFlow()
        assertIs<SignInState.Success>(controller.state.value)
    }

    @Test
    fun `api key providers reject keys that are too short`() {
        controller.start(Provider.ZAi)
        assertIs<SignInState.ApiKey>(controller.state.value)

        controller.submitApiKey("short")
        assertEquals(true, assertIs<SignInState.ApiKey>(controller.state.value).keyRejected)

        controller.submitApiKey("zai-0123456789abcdef")
        assertIs<SignInState.Success>(controller.state.value)
    }

    @Test
    fun `a failure can be retried and cancel returns to idle`() {
        controller.start(Provider.Copilot)
        controller.fail(SignInError.Expired)
        assertEquals(
            SignInError.Expired,
            assertIs<SignInState.Failed>(controller.state.value).error,
        )

        controller.retry()
        assertIs<SignInState.DeviceCode>(controller.state.value)

        controller.cancel()
        assertEquals(SignInState.Idle, controller.state.value)
    }
}
