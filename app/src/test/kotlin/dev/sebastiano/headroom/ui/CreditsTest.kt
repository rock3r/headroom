package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.WindowKind
import dev.sebastiano.headroom.ui.detail.DetailScreen
import dev.sebastiano.headroom.ui.home.DetailUiState
import dev.sebastiano.headroom.ui.home.homeUiState
import java.time.ZoneOffset
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Windows that count credits show the credits under their label, used or left as chosen. */
@RunWith(RobolectricTestRunner::class)
class CreditsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val now = Instant.parse("2026-09-27T12:32:00Z")

    private fun window(id: String, label: String, used: Double, limit: Double) =
        QuotaWindow(
            id = id,
            label = label,
            kind = WindowKind.Monthly,
            usedPercent = used / limit * 100,
            resetsAt = now.plus(8.days),
            length = 30.days,
            usedAmount = used,
            limitAmount = limit,
            amountUnit = "credits",
        )

    private val jetBrains =
        AccountState(
            Account("jb", Provider.JetBrains, "sam@example.com"),
            QuotaSnapshot(
                provider = Provider.JetBrains,
                accountId = "jb",
                planLabel = "JetBrains AI Pro",
                windows =
                    listOf(
                        window("jb:ws:team", "JetBrains Team", used = 12.0, limit = 200.0),
                        window("jb:license:aip", "JetBrains AI Pro", used = 0.0119, limit = 10.0),
                    ),
                fetchedAt = now,
            ),
        )

    private fun showDetail(display: QuotaDisplay) {
        val summary =
            homeUiState(
                    listOf(jetBrains),
                    now,
                    emptyMap(),
                    emptyMap(),
                    isDemo = false,
                    isRefreshing = false,
                )
                .accounts
                .single()
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
    fun `shows the credits used under each window`() {
        showDetail(QuotaDisplay.Used)

        rule.onNodeWithText("12 / 200 credits used").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("0.01 / 10 credits used").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `shows the credits left under each window in left mode`() {
        showDetail(QuotaDisplay.Left)

        rule.onNodeWithText("188 / 200 credits left").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("9.99 / 10 credits left").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `formats credits with up to two decimals, and a tiny amount as the smallest step`() {
        assertEquals("200", formatAmount(200.0, Locale.US))
        assertEquals("9.99", formatAmount(9.9881, Locale.US))
        assertEquals("1,234.5", formatAmount(1234.5, Locale.US))
        assertEquals("0.01", formatAmount(0.000125, Locale.US))
        assertEquals("0", formatAmount(0.0, Locale.US))
        assertEquals("9,99", formatAmount(9.9881, Locale.ITALY))
    }
}
