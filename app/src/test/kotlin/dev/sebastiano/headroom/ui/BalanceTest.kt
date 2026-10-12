package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaBalance
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.ui.detail.DetailScreen
import dev.sebastiano.headroom.ui.home.DetailUiState
import dev.sebastiano.headroom.ui.home.homeUiState
import java.time.ZoneOffset
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BalanceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val now = Instant.parse("2026-09-27T12:32:00Z")

    private fun account(provider: Provider, label: String, plan: String?, balance: QuotaBalance?) =
        AccountState(
            Account("a1", provider, label),
            QuotaSnapshot(provider, "a1", plan, emptyList(), now, balance),
        )

    private fun summary(state: AccountState) =
        homeUiState(
                listOf(state),
                now,
                emptyMap(),
                emptyMap(),
                isDemo = false,
                isRefreshing = false,
            )
            .accounts
            .single()

    private fun showDetail(state: AccountState) {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                DetailScreen(
                    state = DetailUiState(now, summary(state), chart = null),
                    formatter = ResetFormatter(ZoneOffset.UTC, Locale.US, is24Hour = true),
                    onAlertChange = { _, _, _ -> },
                )
            }
        }
    }

    @Test
    fun `summaries carry the balance`() {
        val state =
            account(Provider.JetBrains, "sam@example.com", "Junie", QuotaBalance(12.5, "USD"))

        assertEquals(QuotaBalance(12.5, "USD"), summary(state).balance)
    }

    @Test
    fun `an account with only a balance shows it on the detail screen`() {
        showDetail(
            account(Provider.JetBrains, "sam@example.com", "Junie", QuotaBalance(12.5, "USD"))
        )

        rule.onNodeWithText("Balance").assertIsDisplayed()
        rule.onNodeWithText("$12.50").assertIsDisplayed()
    }

    @Test
    fun `a plan named like the account is not repeated`() {
        showDetail(account(Provider.OpenCodeGo, "OpenCode Go", "OpenCode Go", null))

        rule.onNodeWithText("OpenCode Go · OpenCode Go").assertDoesNotExist()
    }
}
