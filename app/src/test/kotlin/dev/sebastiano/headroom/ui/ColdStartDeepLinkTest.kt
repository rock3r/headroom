package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import dev.sebastiano.headroom.appdata.LoadingQuotaRepository
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.ui.detail.DETAIL_TAG
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** On a cold start the stored accounts arrive after the first frame, which shows demo data. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ColdStartDeepLinkTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `a widget tap for a real account waits for the stored accounts, then opens it`() {
        val real = LoadingQuotaRepository()
        var consumed = 0
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                HeadroomApp(
                    graph = testGraph(rule.activity, real = real),
                    openAccountRequest = OpenAccountRequest("real-codex", serial = 0),
                    onConsumeOpenAccount = { consumed++ },
                )
            }
        }
        rule.waitForIdle()
        assertEquals(0, consumed, "the request must wait while the accounts are loading")
        rule.onNodeWithTag(DETAIL_TAG).assertDoesNotExist()

        rule.runOnUiThread { real.load(listOf(realCodex())) }
        rule.waitForIdle()
        rule.onNodeWithTag(DETAIL_TAG).assertIsDisplayed()
        rule.onNodeWithText("Pro · me@example.com").assertIsDisplayed()
        assertEquals(1, consumed)
    }

    private fun realCodex(): AccountState {
        val demo = DemoData.accounts(FIXED_NOW).first { it.account.id == "demo-codex" }
        return demo.copy(
            account = demo.account.copy(id = "real-codex", label = "me@example.com"),
            snapshot = demo.snapshot?.copy(accountId = "real-codex"),
        )
    }
}
