package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.ui.accounts.ACCOUNT_NAME_FIELD_TAG
import dev.sebastiano.headroom.ui.accounts.AccountRow
import dev.sebastiano.headroom.ui.accounts.AccountsActions
import dev.sebastiano.headroom.ui.accounts.AccountsScreen
import dev.sebastiano.headroom.ui.accounts.AccountsStep
import dev.sebastiano.headroom.ui.accounts.AccountsUiState
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
// No screen size qualifier here: at w411dp Robolectric never idles with a Material 3 dialog that
// holds a text field, even a bare one.
class AccountRenameTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val renames = mutableListOf<Pair<String, String>>()
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

    @Test
    fun `tapping an account lets the user name it`() {
        show(isDemo = false)
        rule.onNodeWithText("Work").assertIsDisplayed()

        rule.onNodeWithText("Work").performClick()
        rule.onNodeWithText("Rename account").assertIsDisplayed()
        rule.onNodeWithTag(ACCOUNT_NAME_FIELD_TAG).performTextReplacement("Side project")
        rule.onNodeWithText("Save").performClick()

        assertEquals(listOf("a2" to "Side project"), renames)
        rule.onNodeWithText("Rename account").assertDoesNotExist()
    }

    @Test
    fun `demo accounts cannot be renamed`() {
        show(isDemo = true)

        rule.onNodeWithText("Work").performClick()

        rule.onNodeWithText("Rename account").assertDoesNotExist()
    }
}
