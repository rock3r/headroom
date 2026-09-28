package dev.sebastiano.headroom.ui

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.headroom.MainActivity
import dev.sebastiano.headroom.TestHeadroomApplication
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.ui.accounts.ACCOUNTS_TAG
import dev.sebastiano.headroom.ui.accounts.SIGN_IN_KEY_FIELD_TAG
import dev.sebastiano.headroom.ui.accounts.providerOptionTag
import dev.sebastiano.headroom.ui.detail.DETAIL_TAG
import dev.sebastiano.headroom.ui.overview.OVERVIEW_LIST_TAG
import dev.sebastiano.headroom.ui.overview.accountCardTag
import dev.sebastiano.headroom.widget.WidgetIntents
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/** A widget tap launches the app with an account id; the app opens that account. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestHeadroomApplication::class, qualifiers = "w411dp-h891dp")
class DeepLinkTest {
    @get:Rule val rule = createEmptyComposeRule()

    private fun widgetTap(accountId: String?) =
        Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .apply { accountId?.let { putExtra(WidgetIntents.EXTRA_ACCOUNT_ID, it) } }

    private fun launch(intent: Intent): ActivityController<MainActivity> =
        Robolectric.buildActivity(MainActivity::class.java, intent).setup().also {
            rule.waitForIdle()
        }

    @Test
    fun `a widget tap opens that account's detail`() {
        launch(widgetTap("demo-codex"))
        rule.onNodeWithTag(DETAIL_TAG).assertIsDisplayed()
        rule.onNodeWithText("Pro · sam@example.com").assertIsDisplayed()
    }

    @Test
    fun `a widget tap while the app is open opens that account`() {
        val activity = launch(widgetTap(null))
        rule.onNodeWithTag(OVERVIEW_LIST_TAG).assertIsDisplayed()

        activity.newIntent(widgetTap("demo-grok"))
        rule.waitForIdle()
        rule.onNodeWithTag(DETAIL_TAG).assertIsDisplayed()
        rule.onNodeWithText("SuperGrok · sam").assertIsDisplayed()
    }

    @Test
    fun `a widget tap while another tab is open shows the account, not a list without a toolbar`() {
        val activity = launch(widgetTap(null))
        rule.onNodeWithContentDescription("Resets").performClick()
        rule.waitForIdle()

        activity.newIntent(widgetTap("demo-grok"))
        rule.waitForIdle()

        // The pane scaffold was not on screen when the account was opened. It must still end up
        // showing that account's detail.
        rule.onNodeWithTag(DETAIL_TAG).assertIsDisplayed()
        rule.onNodeWithText("SuperGrok · sam").assertIsDisplayed()
        rule.onNodeWithTag(OVERVIEW_LIST_TAG).assertDoesNotExist()
    }

    @Test
    fun `a widget tap from the accounts screen closes it and opens the account`() {
        val activity = launch(widgetTap(null))
        rule.openAccounts()
        rule.onNodeWithTag(ACCOUNTS_TAG).assertIsDisplayed()

        activity.newIntent(widgetTap("demo-claude"))
        rule.waitForIdle()
        rule.onNodeWithText("Max 20x · sam@example.com").assertIsDisplayed()
    }

    @Test
    fun `an unknown account id is ignored`() {
        launch(widgetTap("not-an-account"))
        rule.onNodeWithTag(OVERVIEW_LIST_TAG).assertIsDisplayed()
        rule.onNodeWithTag(DETAIL_TAG).assertDoesNotExist()
    }

    @Test
    fun `an unknown account in demo mode does not block the accounts screen`() {
        launch(widgetTap("removed-account"))
        rule.onNodeWithTag(OVERVIEW_LIST_TAG).assertIsDisplayed()
        rule.openAccounts()
        rule.onNodeWithTag(ACCOUNTS_TAG).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun `on a wide screen the account is selected in the detail pane`() {
        launch(widgetTap("demo-copilot"))
        rule.onNodeWithTag(accountCardTag("demo-claude")).assertIsDisplayed()
        rule.onNodeWithText("Pro+ · sam-dev").assertIsDisplayed()
    }

    @Test
    fun `the sign-in return link leaves a finished sign-in on screen`() {
        val activity = launch(widgetTap(null))
        rule.openAccounts()
        rule.onNodeWithText("Add account").performClick()
        rule
            .onNodeWithTag(ACCOUNTS_TAG)
            .performScrollToNode(hasTestTag(providerOptionTag(Provider.ZAi)))
        rule.onNodeWithTag(providerOptionTag(Provider.ZAi)).performClick()
        rule.onNodeWithTag(SIGN_IN_KEY_FIELD_TAG).performTextInput("zai-0123456789abcdef")
        rule.onNodeWithText("Save key").performClick()
        rule.onNodeWithText("Signed in as sam@example.com").assertIsDisplayed()

        activity.newIntent(Intent(Intent.ACTION_VIEW, "headroom://signed-in".toUri()))
        rule.waitForIdle()
        rule.onNodeWithText("Signed in as sam@example.com").assertIsDisplayed()
    }
}
