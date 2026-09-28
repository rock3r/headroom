package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.signin.SignInState
import dev.sebastiano.headroom.ui.accounts.AccountsActions
import dev.sebastiano.headroom.ui.accounts.AccountsScreen
import dev.sebastiano.headroom.ui.accounts.AccountsStep
import dev.sebastiano.headroom.ui.accounts.AccountsUiState
import dev.sebastiano.headroom.ui.accounts.SIGN_IN_CODE_FIELD_TAG
import dev.sebastiano.headroom.ui.accounts.SIGN_IN_KEY_FIELD_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Secrets typed into sign-in fields must never be written into the saved instance state. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class CredentialStateTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

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
            onRename = { _, _ -> },
            onRemove = {},
            onReorder = {},
        )

    private fun restoreWith(state: SignInState, fieldTag: String) {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            HeadroomTheme(dynamicColor = false) {
                AccountsScreen(
                    state =
                        AccountsUiState(emptyList(), isDemo = false, AccountsStep.SignIn(state)),
                    actions = actions,
                )
            }
        }
        rule.onNodeWithTag(fieldTag).performTextInput("secret-0123456789")
        tester.emulateSavedInstanceStateRestore()
        rule
            .onNodeWithTag(fieldTag)
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(""))
            )
    }

    @Test
    fun `an api key is not kept in the saved state`() {
        restoreWith(SignInState.ApiKey(Provider.ZAi), SIGN_IN_KEY_FIELD_TAG)
    }

    @Test
    fun `a pasted authorization code is not kept in the saved state`() {
        restoreWith(
            SignInState.Browser(Provider.Claude, "https://example.com"),
            SIGN_IN_CODE_FIELD_TAG,
        )
    }
}
