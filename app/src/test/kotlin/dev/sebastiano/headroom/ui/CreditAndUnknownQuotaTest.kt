package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.WindowKind
import dev.sebastiano.headroom.ui.detail.DetailScreen
import dev.sebastiano.headroom.ui.detail.alertSwitchTag
import dev.sebastiano.headroom.ui.home.DetailUiState
import dev.sebastiano.headroom.ui.home.homeUiState
import java.time.ZoneOffset
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Credits show what is spent and when they expire. Quotas Headroom does not recognise show with an
 * info icon that explains them. Neither drives alerts, pace or the next reset.
 */
@RunWith(RobolectricTestRunner::class)
class CreditAndUnknownQuotaTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val now = Instant.parse("2026-10-01T12:00:00Z")

    private val weekly =
        QuotaWindow(
            id = "seven_day",
            label = "Weekly · all models",
            kind = WindowKind.Weekly,
            usedPercent = 30.0,
            resetsAt = now.plus(4.days),
            length = 7.days,
        )

    private val cloudCredit =
        QuotaWindow(
            id = "iguana_necktie",
            label = "Cloud session credit",
            kind = WindowKind.Credit,
            usedPercent = 40.8,
            resetsAt = null,
            length = null,
            usedAmount = 102.0,
            limitAmount = 250.0,
            amountUnit = "USD",
            expiresAt = Instant.parse("2026-11-05T07:59:00Z"),
        )

    private val coworkCredit =
        QuotaWindow(
            id = "cinder_cove",
            label = "Claude Code and Cowork credit",
            kind = WindowKind.Credit,
            usedPercent = 96.0,
            resetsAt = null,
            length = null,
            expiresAt = Instant.parse("2026-12-01T08:00:00Z"),
        )

    private val unknown =
        QuotaWindow(
            id = "nimbus_quill",
            label = "Nimbus quill",
            kind = WindowKind.Other,
            usedPercent = 0.0,
            resetsAt = null,
            length = null,
            isRecognised = false,
        )

    private val claude =
        AccountState(
            Account("claude", Provider.Claude, "sam@example.com"),
            QuotaSnapshot(
                provider = Provider.Claude,
                accountId = "claude",
                planLabel = "Max 20x",
                windows = listOf(weekly, cloudCredit, coworkCredit, unknown),
                fetchedAt = now,
            ),
        )

    private fun summary() =
        homeUiState(
                listOf(claude),
                now,
                emptyMap(),
                emptyMap(),
                isDemo = false,
                isRefreshing = false,
            )
            .accounts
            .single()

    private fun showDetail(display: QuotaDisplay = QuotaDisplay.Used) {
        val summary = summary()
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                DetailScreen(
                    state = DetailUiState(now, summary, chart = null, display = display),
                    formatter = ResetFormatter(ZoneOffset.UTC, Locale.US, is24Hour = true),
                    onAlertChange = { _, _, _ -> },
                )
            }
        }
    }

    @Test
    fun `a credit shows the dollars used and when it expires`() {
        showDetail()

        rule.onNodeWithText("Cloud session credit").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("$102 of $250 used").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Expires 5 Nov 2026").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a credit shows the dollars left in left mode`() {
        showDetail(QuotaDisplay.Left)

        rule.onNodeWithText("$148 left of $250").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a credit without dollars shows only its expiry and percentage`() {
        showDetail()

        rule.onNodeWithText("Claude Code and Cowork credit").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Expires 1 Dec 2026").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("96%").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `credits never say they reset`() {
        showDetail()

        assertEquals(
            1,
            rule.onAllNodesWithText("Resets", substring = true).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun `an unknown quota shows with an info button and no reset line`() {
        showDetail()

        rule.onNodeWithText("Nimbus quill").performScrollTo().assertIsDisplayed()
        val infoButtons =
            rule.onAllNodesWithContentDescription("About this quota").fetchSemanticsNodes().size
        assertEquals(1, infoButtons)
        rule
            .onNode(hasContentDescription("About this quota") and hasClickAction())
            .assertIsDisplayed()
    }

    @Test
    fun `tapping the info button explains the unknown quota`() {
        showDetail()

        rule.onNode(hasContentDescription("About this quota")).performScrollTo().performClick()

        rule.onNodeWithText(UNKNOWN_EXPLANATION).assertIsDisplayed()
    }

    @Test
    fun `long-pressing the info button explains the unknown quota`() {
        showDetail()

        rule.onNode(hasContentDescription("About this quota")).performScrollTo().performTouchInput {
            longClick()
        }

        rule.onNodeWithText(UNKNOWN_EXPLANATION).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `hovering the info button explains the unknown quota`() {
        showDetail()

        rule.onNode(hasContentDescription("About this quota")).performScrollTo().performMouseInput {
            enter(center)
        }

        rule.onNodeWithText(UNKNOWN_EXPLANATION).assertIsDisplayed()
    }

    @Test
    fun `money has cents only when it needs them`() {
        assertEquals("$102", formatMoney(102.0, "USD", Locale.US))
        assertEquals("$102.50", formatMoney(102.5, "USD", Locale.US))
        assertEquals("$0", formatMoney(0.0, "USD", Locale.US))
        assertEquals("12 credits", formatMoney(12.0, "credits", Locale.US))
    }

    @Test
    fun `credits and unknown quotas have no reset alert switch`() {
        showDetail()

        rule.onNodeWithTag(alertSwitchTag("claude", "seven_day")).performScrollTo()
        listOf("iguana_necktie", "cinder_cove", "nimbus_quill").forEach { id ->
            rule.onNodeWithTag(alertSwitchTag("claude", id)).assertDoesNotExist()
        }
    }

    @Test
    fun `credits and unknown quotas never lead, set the pace or count as the next reset`() {
        val state =
            homeUiState(
                listOf(claude),
                now,
                emptyMap(),
                emptyMap(),
                isDemo = false,
                isRefreshing = false,
            )
        val account = state.accounts.single()

        assertEquals("seven_day", account.primary?.id)
        assertEquals("seven_day", state.nextReset?.windowId)
        // The cowork credit is 96% used, but a credit never needs attention.
        assertFalse(account.needsAttention)
        val credit = account.windows.single { it.id == "cinder_cove" }
        assertNull(credit.expectedPercent)
        assertFalse(credit.canAlert)
        assertFalse(account.windows.single { it.id == "nimbus_quill" }.canAlert)
    }

    @Test
    fun `an account with only credits and unknown quotas has no next reset and no primary`() {
        val onlyExtras =
            claude.copy(snapshot = claude.snapshot!!.copy(windows = listOf(cloudCredit, unknown)))
        val state =
            homeUiState(
                listOf(onlyExtras),
                now,
                emptyMap(),
                emptyMap(),
                isDemo = false,
                isRefreshing = false,
            )

        assertNull(state.nextReset)
        assertNull(state.accounts.single().primary)
        assertNull(state.accounts.single().pace)
    }

    private companion object {
        const val UNKNOWN_EXPLANATION =
            "This is a new quota we don't recognise yet. Headroom shows it for your " +
                "information, but doesn't know what it's for."
    }
}
