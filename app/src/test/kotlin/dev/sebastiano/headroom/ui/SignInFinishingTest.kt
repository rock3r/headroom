package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTestConfig
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.signin.FakeSignInController
import dev.sebastiano.headroom.signin.SignInState
import dev.sebastiano.headroom.ui.accounts.SIGN_IN_FINISHING_TAG
import dev.sebastiano.headroom.ui.detail.DETAIL_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Between the provider saying yes and the account being added, the sign-in screen says that it is
 * finishing, so the few seconds of work do not look like a sign-in that failed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class SignInFinishingTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var request by mutableStateOf<OpenAccountRequest?>(null)

    private fun openFinishingSignIn() {
        val signIn = FakeSignInController(SignInState.Finishing(Provider.Claude))
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                HeadroomApp(
                    graph = testGraph(rule.activity, signInController = signIn),
                    openAccountRequest = request,
                )
            }
        }
        rule.openAccounts()
    }

    @Test
    fun `a sign-in being finished says so and names the provider`() {
        openFinishingSignIn()
        rule.onNodeWithText("Finishing sign-in").assertIsDisplayed()
        rule.onNodeWithText("Getting your Claude limits…").assertIsDisplayed()
        rule.onNodeWithTag(SIGN_IN_FINISHING_TAG).assertIsDisplayed()
    }

    @Test
    fun `a widget tap does not pull the user out of a sign-in being finished`() {
        openFinishingSignIn()

        request = OpenAccountRequest("demo-codex", serial = 0)
        rule.waitForIdle()

        rule.onNodeWithText("Finishing sign-in").assertIsDisplayed()
        rule.onNodeWithTag(DETAIL_TAG).assertDoesNotExist()
    }
}

/** With animations off, the finishing screen shows its still illustration and the same words. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class SignInFinishingReducedMotionTest {
    @get:Rule
    val rule =
        createAndroidComposeRule<ComponentActivity>(ComposeUiTestConfig(effectContext = ZeroMotion))

    @Test
    fun `with animations off the finishing screen is complete and still`() {
        val signIn = FakeSignInController(SignInState.Finishing(Provider.Grok))
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                HeadroomApp(graph = testGraph(rule.activity, signInController = signIn))
            }
        }
        rule.openAccounts()
        rule.onNodeWithTag(SIGN_IN_FINISHING_TAG).assertIsDisplayed()
        rule.onNodeWithText("Getting your Grok limits…").assertIsDisplayed()
    }
}
