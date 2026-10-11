package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.WindowKind
import dev.sebastiano.headroom.ui.home.homeUiState
import dev.sebastiano.headroom.ui.overview.AccountCard
import java.time.ZoneOffset
import java.util.Locale
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * JetBrains licences and workspace seats are separate allowances, so the overview card lists each
 * one. For other providers the card keeps to the primary and session windows.
 */
@RunWith(RobolectricTestRunner::class)
class AllowanceRowsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val now = Instant.parse("2026-09-27T12:32:00Z")

    private fun window(id: String, label: String, kind: WindowKind, used: Double) =
        QuotaWindow(
            id = id,
            label = label,
            kind = kind,
            usedPercent = used,
            resetsAt = now.plus(3.days),
            length = if (kind == WindowKind.Weekly) 7.days else 30.days,
        )

    private fun account(provider: Provider, windows: List<QuotaWindow>) =
        AccountState(
            Account(provider.id, provider, "sam@example.com"),
            QuotaSnapshot(
                provider = provider,
                accountId = provider.id,
                planLabel = null,
                windows = windows,
                fetchedAt = now,
            ),
        )

    private fun showCard(state: AccountState) {
        val summary =
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
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                AccountCard(
                    account = summary,
                    now = now,
                    formatter = ResetFormatter(ZoneOffset.UTC, Locale.US, is24Hour = true),
                    onClick = {},
                )
            }
        }
    }

    @Test
    fun `a JetBrains card lists every licence and seat by name`() {
        showCard(
            account(
                Provider.JetBrains,
                listOf(
                    window("jb:license:a", "AI Pro", WindowKind.Monthly, 1.0),
                    window("jb:ws:b", "Alumni", WindowKind.Monthly, 0.0),
                    window("jb:ws:c", "Team", WindowKind.Monthly, 5.0),
                ),
            )
        )

        for (name in listOf("AI Pro", "Alumni", "Team")) {
            rule.onAllNodesWithText(name).assertCountEquals(1)
        }
    }

    @Test
    fun `other providers keep to the primary window`() {
        showCard(
            account(
                Provider.Claude,
                listOf(
                    window("seven_day", "All models", WindowKind.Weekly, 40.0),
                    window("seven_day_sonnet", "Sonnet only", WindowKind.Weekly, 10.0),
                ),
            )
        )

        rule.onAllNodesWithText("Sonnet only").assertCountEquals(0)
    }
}
