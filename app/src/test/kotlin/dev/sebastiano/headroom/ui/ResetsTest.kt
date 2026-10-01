package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsToggleable
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.ui.detail.DETAIL_TAG
import dev.sebastiano.headroom.ui.home.HomeUiState
import dev.sebastiano.headroom.ui.home.homeUiState
import dev.sebastiano.headroom.ui.resets.RESETS_TAG
import dev.sebastiano.headroom.ui.resets.ResetsScreen
import dev.sebastiano.headroom.ui.resets.resetAlertTag
import dev.sebastiano.headroom.ui.resets.resetRowTag
import java.time.ZoneOffset
import java.util.Locale
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ResetsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val opened = mutableListOf<String>()
    private val alertChanges = mutableListOf<Triple<String, String, Boolean>>()

    private fun launchApp() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) { HeadroomApp(graph = testGraph(rule.activity)) }
        }
        rule.onNodeWithContentDescription("Resets").performClick()
    }

    private fun showScreen(state: HomeUiState) {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                ResetsScreen(
                    state = state,
                    formatter = ResetFormatter(ZoneOffset.UTC, Locale.UK, is24Hour = true),
                    onOpenAccount = { id, _ -> opened += id },
                    onAlertChange = { account, window, on ->
                        alertChanges += Triple(account, window, on)
                    },
                )
            }
        }
    }

    private fun demoState(
        accounts: List<AccountState> = DemoData.accounts(FIXED_NOW),
        pastResets: Map<Pair<String, String>, List<Double>> = emptyMap(),
        display: QuotaDisplay = QuotaDisplay.Used,
    ) =
        homeUiState(
                accounts = accounts,
                now = FIXED_NOW,
                alerts = mapOf(("demo-copilot" to "premium_interactions") to false),
                pastResets = pastResets,
                isDemo = true,
                isRefreshing = false,
            )
            .copy(display = display)

    @Test
    fun `tapping an upcoming reset opens that account's detail`() {
        launchApp()
        rule.onNodeWithTag(resetRowTag("demo-grok", "weekly")).performClick()
        rule.onNodeWithTag(DETAIL_TAG).assertIsDisplayed()
        rule.onNodeWithText("SuperGrok · sam").assertIsDisplayed()
    }

    @Test
    fun `the bell switches the alert off and on again, and the subtitle follows`() {
        launchApp()
        val bell = rule.onNodeWithContentDescription("Reset alert for Grok · Weekly credits")
        bell.assertIsToggleable().assertIsOn().performClick()
        rule.onNodeWithContentDescription("Reset alert for Grok · Weekly credits").assertIsOff()
        rule.onNodeWithText("Alerts are on for 3 of 5 windows").assertIsDisplayed()

        rule.onNodeWithContentDescription("Reset alert for Grok · Weekly credits").performClick()
        rule.onNodeWithContentDescription("Reset alert for Grok · Weekly credits").assertIsOn()
        rule.onNodeWithText("Alerts are on for 4 of 5 windows").assertIsDisplayed()
        // The bell does not open the account.
        rule.onNodeWithTag(RESETS_TAG).assertIsDisplayed()
    }

    @Test
    fun `the bell and the row each do only their own thing`() {
        showScreen(demoState())

        rule.onNodeWithTag(resetAlertTag("demo-copilot", "premium_interactions")).performClick()
        assertEquals(listOf(Triple("demo-copilot", "premium_interactions", true)), alertChanges)
        assertEquals(emptyList(), opened)

        rule.onNodeWithTag(resetAlertTag("demo-grok", "weekly")).performClick()
        assertEquals(Triple("demo-grok", "weekly", false), alertChanges.last())
        assertEquals(emptyList(), opened)

        rule.onNodeWithTag(resetRowTag("demo-codex", "secondary")).performClick()
        assertEquals(listOf("demo-codex"), opened)
        assertEquals(2, alertChanges.size)
    }

    @Test
    fun `the history names each window and reads out its values`() {
        showScreen(
            demoState(
                pastResets =
                    mapOf(("demo-claude" to "seven_day") to listOf(82.0, 95.0, 100.0, 88.0, 100.0))
            )
        )
        assertChartRow(
            "Claude · Weekly · all models: 82%, 95%, 100%, 88%, 100% at past resets, 71% now"
        )
        assertChartRow("Claude · Weekly · Opus: no past resets yet, 52% now")
        assertChartRow("ChatGPT Codex · Weekly: no past resets yet, 34% now")
    }

    @Test
    fun `in left mode the history reads out what was left`() {
        showScreen(
            demoState(
                pastResets = mapOf(("demo-grok" to "weekly") to listOf(97.0, 100.0)),
                display = QuotaDisplay.Left,
            )
        )
        assertChartRow("Grok · Weekly credits: 3%, 0% left at past resets, 12% left now")
        assertChartRow("Claude · Weekly · Opus: no past resets yet, 48% left now")
        rule.onNodeWithText("Left when each window reset").assertExists()
    }

    @Test
    fun `two accounts with the same name are told apart`() {
        val codex = DemoData.accounts(FIXED_NOW).first { it.account.provider == Provider.Codex }
        val work =
            codex.copy(
                account = Account("work-codex", Provider.Codex, "work@example.com"),
                snapshot = codex.snapshot?.copy(accountId = "work-codex"),
            )
        showScreen(demoState(accounts = listOf(codex, work)))
        rule.onNodeWithText("ChatGPT Codex (sam@example.com) · Weekly").assertExists()
        rule.onNodeWithText("ChatGPT Codex (work@example.com) · Weekly").assertExists()
        assertChartRow("ChatGPT Codex (work@example.com) · Weekly: no past resets yet, 34% now")
    }

    private fun assertChartRow(description: String) {
        rule.onNodeWithTag(RESETS_TAG).performScrollToNode(hasContentDescription(description))
        rule.onNodeWithContentDescription(description).assertIsDisplayed()
    }
}
