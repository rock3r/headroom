package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.signin.FakeSignInController
import dev.sebastiano.headroom.signin.SignInError
import dev.sebastiano.headroom.ui.accounts.ACCOUNTS_TAG
import dev.sebastiano.headroom.ui.accounts.SIGN_IN_CODE_FIELD_TAG
import dev.sebastiano.headroom.ui.accounts.SIGN_IN_KEY_FIELD_TAG
import dev.sebastiano.headroom.ui.accounts.SIGN_IN_USER_CODE_TAG
import dev.sebastiano.headroom.ui.accounts.providerOptionTag
import dev.sebastiano.headroom.ui.settings.SETTINGS_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class AccountsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val signIn = FakeSignInController()

    private fun openPicker() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                HeadroomApp(graph = testGraph(rule.activity, signInController = signIn))
            }
        }
        rule.openAccounts()
        rule.onNodeWithTag(ACCOUNTS_TAG).assertIsDisplayed()
        rule
            .onNodeWithText("You are looking at demo accounts", substring = true)
            .assertIsDisplayed()
        rule.onNodeWithText("Add account").performClick()
        rule.onNodeWithText("Choose a provider").assertIsDisplayed()
    }

    private fun pick(provider: Provider) {
        rule
            .onNodeWithTag(ACCOUNTS_TAG)
            .performScrollToNode(hasTestTag(providerOptionTag(provider)))
        rule.onNodeWithTag(providerOptionTag(provider)).performClick()
    }

    @Test
    fun `browser sign-in offers the page and a pasted code fallback`() {
        openPicker()
        pick(Provider.Claude)
        rule.onNodeWithText("Sign in to Claude").assertIsDisplayed()
        rule.onNodeWithText("Open the sign-in page").assertIsDisplayed()
        rule.onNodeWithText("Waiting for the browser").assertIsDisplayed()

        rule.onNodeWithText("Continue").performClick()
        rule.onNodeWithText("That code did not work", substring = true).assertIsDisplayed()

        rule.onNodeWithTag(SIGN_IN_CODE_FIELD_TAG).performTextInput("abc#def")
        rule.onNodeWithText("Continue").performClick()
        rule.onNodeWithText("Signed in as sam@example.com").assertIsDisplayed()

        rule.onNodeWithText("Done").performClick()
        rule.onNodeWithText("Add account").assertIsDisplayed()
    }

    @Test
    fun `device code sign-in shows the code to enter and waits`() {
        openPicker()
        pick(Provider.Kimi)
        rule.onNodeWithTag(SIGN_IN_USER_CODE_TAG).assertIsDisplayed()
        rule.onNodeWithText(FakeSignInController.DEMO_USER_CODE).assertIsDisplayed()
        rule.onNodeWithText("Copy code").assertIsDisplayed()
        rule.onNodeWithText("Waiting for you to confirm").assertIsDisplayed()

        signIn.completeDeviceFlow()
        rule.onNodeWithText("Signed in as sam@example.com").assertIsDisplayed()
    }

    @Test
    fun `api key sign-in rejects a short key`() {
        openPicker()
        pick(Provider.ZAi)
        rule.onNodeWithTag(SIGN_IN_KEY_FIELD_TAG).performTextInput("short")
        rule.onNodeWithText("Save key").performClick()
        rule.onNodeWithText("This key does not look right", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a failed sign-in explains why and can be retried`() {
        openPicker()
        pick(Provider.Copilot)
        signIn.fail(SignInError.Expired)
        rule.onNodeWithText("The sign-in took too long and expired.").assertIsDisplayed()
        rule.onNodeWithText("Try again").performClick()
        rule.onNodeWithTag(SIGN_IN_USER_CODE_TAG).assertIsDisplayed()
    }

    @Test
    fun `cancel and back walk out of the accounts screen step by step`() {
        openPicker()
        pick(Provider.Grok)
        rule.onNodeWithContentDescription("Cancel sign-in").performClick()
        rule.onNodeWithText("Choose a provider").assertIsDisplayed()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Add account").assertIsDisplayed()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithTag(SETTINGS_TAG).assertIsDisplayed()
    }
}
