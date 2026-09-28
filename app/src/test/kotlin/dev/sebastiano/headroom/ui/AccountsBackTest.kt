package dev.sebastiano.headroom.ui

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.ui.accounts.ACCOUNTS_TAG
import dev.sebastiano.headroom.ui.overview.OVERVIEW_LIST_TAG
import dev.sebastiano.headroom.ui.settings.SETTINGS_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Back out of the accounts screen follows the predictive back gesture. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class AccountsBackTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val dispatcher
        get() = rule.activity.onBackPressedDispatcher

    private fun openAccounts() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) { HeadroomApp(graph = testGraph(rule.activity)) }
        }
        rule.openAccounts()
        rule.onNodeWithTag(ACCOUNTS_TAG).assertIsDisplayed()
    }

    private fun gesture(progress: Float) =
        BackEventCompat(0f, 0f, progress, BackEventCompat.EDGE_LEFT)

    @Test
    fun `system back from the accounts list returns to settings`() {
        openAccounts()
        rule.runOnUiThread { dispatcher.onBackPressed() }
        rule.onNodeWithTag(SETTINGS_TAG).assertIsDisplayed()
        rule.onNodeWithTag(ACCOUNTS_TAG).assertDoesNotExist()
    }

    @Test
    fun `the gesture reveals the overview and cancelling keeps the accounts screen`() {
        openAccounts()
        rule.runOnUiThread {
            dispatcher.dispatchOnBackStarted(gesture(0f))
            dispatcher.dispatchOnBackProgressed(gesture(0.5f))
        }
        rule.waitForIdle()
        rule.onNodeWithTag(ACCOUNTS_TAG).assertExists()
        rule.onNodeWithTag(SETTINGS_TAG).assertExists()

        rule.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        rule.waitForIdle()
        rule.onNodeWithTag(ACCOUNTS_TAG).assertIsDisplayed()
        rule.onNodeWithTag(OVERVIEW_LIST_TAG).assertDoesNotExist()
    }

    @Test
    fun `completing the gesture closes the accounts screen`() {
        openAccounts()
        rule.runOnUiThread {
            dispatcher.dispatchOnBackStarted(gesture(0f))
            dispatcher.dispatchOnBackProgressed(gesture(0.7f))
            dispatcher.onBackPressed()
        }
        rule.waitForIdle()
        rule.onNodeWithTag(SETTINGS_TAG).assertIsDisplayed()
        rule.onNodeWithTag(ACCOUNTS_TAG).assertDoesNotExist()
    }

    @Test
    fun `inside a sign-in, back still steps back one screen`() {
        openAccounts()
        rule.onNodeWithText("Add account").performClick()
        rule.onNodeWithText("Choose a provider").assertIsDisplayed()
        rule.runOnUiThread { dispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithText("Add account").assertIsDisplayed()
        rule.onNodeWithTag(ACCOUNTS_TAG).assertIsDisplayed()
    }
}
