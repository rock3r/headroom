package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.ui.accounts.ACCOUNTS_TAG
import dev.sebastiano.headroom.ui.accounts.ACCOUNT_NAME_FIELD_TAG
import dev.sebastiano.headroom.ui.accounts.AccountRow
import dev.sebastiano.headroom.ui.accounts.AccountsActions
import dev.sebastiano.headroom.ui.accounts.AccountsScreen
import dev.sebastiano.headroom.ui.accounts.AccountsStep
import dev.sebastiano.headroom.ui.accounts.AccountsUiState
import dev.sebastiano.headroom.ui.accounts.accountRowTag
import dev.sebastiano.headroom.ui.settings.SETTINGS_TAG
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tapping an account edits it in place: rename it, or remove it after confirming. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class AccountRenameTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val renames = mutableListOf<Pair<String, String>>()
    private val removals = mutableListOf<String>()
    private val actions =
        AccountsActions(
            onClose = {},
            onAddAccount = {},
            onPickProvider = {},
            onBack = {},
            onSubmitCode = {},
            onSubmitApiKey = {},
            onRetry = {},
            onFinish = {},
            onOpenUrl = {},
            onCopy = {},
            onRename = { id, name -> renames += id to name },
            onRemove = { id -> removals += id },
        )

    private fun show(isDemo: Boolean) {
        val accounts =
            listOf(
                AccountRow("a1", Provider.Claude, "sam@example.com", "Max"),
                AccountRow("a2", Provider.Claude, "sam@work.example", "Pro", nickname = "Work"),
            )
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                AccountsScreen(AccountsUiState(accounts, isDemo, AccountsStep.List), actions)
            }
        }
    }

    private fun field() = rule.onNodeWithTag(ACCOUNT_NAME_FIELD_TAG)

    @Test
    fun `tapping an account edits its name in place`() {
        show(isDemo = false)

        rule.onNodeWithText("Work").performClick()
        field().assertIsDisplayed().assertIsFocused()
        rule
            .onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Edit Work"))
            .assertExists()
        field().performTextReplacement("Side project")
        rule.onNodeWithText("Save").performClick()

        assertEquals(listOf("a2" to "Side project"), renames)
        field().assertDoesNotExist()
    }

    @Test
    fun `the keyboard's done action saves the name`() {
        show(isDemo = false)

        rule.onNodeWithText("Work").performClick()
        field().performTextReplacement("Side project")
        field().performImeAction()

        assertEquals(listOf("a2" to "Side project"), renames)
        field().assertDoesNotExist()
    }

    @Test
    fun `cancel closes the editor without renaming`() {
        show(isDemo = false)

        rule.onNodeWithText("Work").performClick()
        field().performTextReplacement("Side project")
        rule.onNodeWithText("Cancel").performClick()

        assertEquals(emptyList(), renames)
        field().assertDoesNotExist()
        rule.onNodeWithText("Work").assertIsDisplayed()
    }

    @Test
    fun `while one account is edited the others cannot be tapped`() {
        show(isDemo = false)

        rule.onNodeWithText("Work").performClick()
        rule.onNodeWithTag(accountRowTag("a1")).assertIsNotEnabled()
        rule.onNodeWithTag(accountRowTag("a1")).performClick()
        assertEquals("Work", field().editableText())

        rule.onNodeWithText("Cancel").performClick()
        rule.onNodeWithTag(accountRowTag("a1")).assertIsEnabled()
    }

    @Test
    fun `an account can be removed after confirming in place`() {
        show(isDemo = false)

        rule.onNodeWithText("Work").performClick()
        rule.onNodeWithText("Remove account").performClick()
        rule.onNodeWithText("Remove Work?").assertIsDisplayed()
        rule
            .onNodeWithText("Headroom signs it out and forgets its usage history.")
            .assertIsDisplayed()
        assertEquals(emptyList(), removals)
        rule.onNodeWithText("Remove").performClick()

        assertEquals(listOf("a2"), removals)
        rule.onNodeWithText("Remove Work?").assertDoesNotExist()
        field().assertDoesNotExist()
    }

    @Test
    fun `cancelling the removal goes back to the name`() {
        show(isDemo = false)

        rule.onNodeWithText("Work").performClick()
        field().performTextReplacement("Side project")
        rule.onNodeWithText("Remove account").performClick()
        rule.onNodeWithText("Cancel").performClick()

        rule.onNodeWithText("Remove Work?").assertDoesNotExist()
        assertEquals("Side project", field().editableText())
        assertEquals(emptyList(), removals)
    }

    @Test
    fun `demo accounts cannot be edited`() {
        show(isDemo = true)

        rule
            .onNodeWithTag(accountRowTag("a2"))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        rule.onNodeWithText("Work").performClick()

        field().assertDoesNotExist()
    }

    @Test
    fun `back cancels the edit before it closes the accounts screen`() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                HeadroomApp(
                    graph = testGraph(rule.activity, realAccounts = DemoData.accounts(FIXED_NOW))
                )
            }
        }
        rule.openAccounts()
        rule.onNodeWithTag(accountRowTag("demo-codex")).performClick()
        field().assertIsDisplayed()

        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        field().assertDoesNotExist()
        rule.onNodeWithTag(ACCOUNTS_TAG).assertIsDisplayed()

        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithTag(SETTINGS_TAG).assertIsDisplayed()
    }

    @Test
    fun `back from the removal question goes back to the name`() {
        show(isDemo = false)

        rule.onNodeWithText("Work").performClick()
        rule.onNodeWithText("Remove account").performClick()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()

        rule.onNodeWithText("Remove Work?").assertDoesNotExist()
        field().assertIsDisplayed()
    }

    private fun SemanticsNodeInteraction.editableText(): String =
        fetchSemanticsNode().config[SemanticsProperties.EditableText].text
}
