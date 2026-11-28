package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.designsystem.IndicatorStyle
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.WindowKind
import dev.sebastiano.headroom.ui.home.homeUiState
import dev.sebastiano.headroom.ui.overview.AccountCard
import dev.sebastiano.headroom.ui.overview.accountCardTag
import java.time.ZoneOffset
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * When a subscription has both a weekly and a monthly limit, the overview card shows the monthly
 * bar beside the weekly primary bar across every provider, while never duplicating monthly-only
 * plans, separate allowances, or secondary Cursor windows.
 */
@RunWith(RobolectricTestRunner::class)
class MonthlyOverviewRowTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val now = Instant.parse("2026-09-27T12:32:00Z")

    private val session =
        QuotaWindow(
            id = "rolling",
            label = "5 hour",
            kind = WindowKind.Session,
            usedPercent = 25.0,
            resetsAt = now.plus(3.hours),
            length = 5.hours,
        )

    private val weekly =
        QuotaWindow(
            id = "weekly",
            label = "Weekly",
            kind = WindowKind.Weekly,
            usedPercent = 90.0,
            resetsAt = now.plus(3.days),
            length = 7.days,
        )

    private val monthly =
        QuotaWindow(
            id = "monthly",
            label = "Monthly",
            kind = WindowKind.Monthly,
            usedPercent = 40.0,
            resetsAt = now.plus(14.days),
            length = 30.days,
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

    private fun showCard(
        state: AccountState,
        display: QuotaDisplay = QuotaDisplay.Used,
        is24Hour: Boolean = true,
    ) {
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
                    formatter = ResetFormatter(ZoneOffset.UTC, Locale.US, is24Hour = is24Hour),
                    onClick = {},
                    display = display,
                )
            }
        }
    }

    @Test
    fun `OpenCode Go shows weekly, monthly and session bars with only the primary wavy`() {
        val highMonthly = monthly.copy(usedPercent = 90.0)
        showCard(
            account(Provider.OpenCodeGo, listOf(session, weekly, highMonthly)),
            is24Hour = false,
        )

        rule.onNodeWithText("Weekly").assertExists()
        rule.onNodeWithText("Monthly").assertExists()
        rule.onNodeWithText("Session").assertExists()
        val bars =
            rule.onAllNodes(
                indicatorsIn(accountCardTag(Provider.OpenCodeGo.id)),
                useUnmergedTree = true,
            )
        bars.assertCountEquals(3)
        bars[0].assert(hasIndicatorStyle(IndicatorStyle.Wavy))
        bars[0].assertRangeInfoEquals(ProgressBarRangeInfo(0.90f, 0f..1f))
        // Even at 90% used (which would need attention if primary), the monthly bar stays flat.
        bars[1].assert(hasIndicatorStyle(IndicatorStyle.Flat))
        bars[1].assertRangeInfoEquals(ProgressBarRangeInfo(0.90f, 0f..1f))
        bars[2].assert(hasIndicatorStyle(IndicatorStyle.Flat))
        bars[2].assertRangeInfoEquals(ProgressBarRangeInfo(0.25f, 0f..1f))
        // All bars in the card share one width even when weekly uses a longer 12-hour reset label.
        assertEquals(
            bars[0].getUnclippedBoundsInRoot().width,
            bars[1].getUnclippedBoundsInRoot().width,
        )
        assertEquals(
            bars[0].getUnclippedBoundsInRoot().width,
            bars[2].getUnclippedBoundsInRoot().width,
        )
    }

    @Test
    fun `narrow card with enlarged text keeps every bar wide enough to draw its pace tick`() {
        val summary =
            homeUiState(
                    listOf(account(Provider.OpenCodeGo, listOf(session, weekly, monthly))),
                    now,
                    emptyMap(),
                    emptyMap(),
                    isDemo = false,
                    isRefreshing = false,
                )
                .accounts
                .single()
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2f)) {
                HeadroomTheme(dynamicColor = false) {
                    AccountCard(
                        account = summary,
                        now = now,
                        formatter = ResetFormatter(ZoneOffset.UTC, Locale.US, is24Hour = false),
                        onClick = {},
                        modifier = Modifier.width(240.dp),
                    )
                }
            }
        }

        val bars =
            rule.onAllNodes(
                indicatorsIn(accountCardTag(Provider.OpenCodeGo.id)),
                useUnmergedTree = true,
            )
        bars.assertCountEquals(3)
        for (index in 0 until 3) {
            assertTrue(bars[index].getUnclippedBoundsInRoot().width >= 40.dp)
        }
    }

    @Test
    fun `narrow separate-allowance card with enlarged text keeps every bar at least 40dp wide`() {
        val summary =
            homeUiState(
                    listOf(
                        account(
                            Provider.JetBrains,
                            listOf(
                                weekly.copy(id = "jb:combined", label = "Combined"),
                                monthly.copy(
                                    id = "jb:license:a",
                                    label = "JetBrains Team",
                                    isSecondary = true,
                                ),
                            ),
                        )
                    ),
                    now,
                    emptyMap(),
                    emptyMap(),
                    isDemo = false,
                    isRefreshing = false,
                )
                .accounts
                .single()
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2f)) {
                HeadroomTheme(dynamicColor = false) {
                    AccountCard(
                        account = summary,
                        now = now,
                        formatter = ResetFormatter(ZoneOffset.UTC, Locale.US, is24Hour = false),
                        onClick = {},
                        modifier = Modifier.width(240.dp),
                    )
                }
            }
        }

        val bars =
            rule.onAllNodes(
                indicatorsIn(accountCardTag(Provider.JetBrains.id)),
                useUnmergedTree = true,
            )
        bars.assertCountEquals(2)
        for (index in 0 until 2) {
            assertTrue(bars[index].getUnclippedBoundsInRoot().width >= 40.dp)
        }
    }

    @Test
    fun `expired sign-in shows Has reset on a past monthly window and keeps all bars flat`() {
        val expired =
            account(
                    Provider.OpenCodeGo,
                    listOf(
                        weekly,
                        monthly.copy(usedPercent = 95.0, resetsAt = now.minus(1.hours)),
                    ),
                )
                .copy(lastError = QuotaErrorKind.Auth)
        showCard(expired)

        rule.onNodeWithText("Monthly").assertExists()
        rule.onNodeWithText("Has reset").assertExists()
        val bars =
            rule.onAllNodes(
                indicatorsIn(accountCardTag(Provider.OpenCodeGo.id)),
                useUnmergedTree = true,
            )
        bars.assertCountEquals(2)
        bars[0].assert(hasIndicatorStyle(IndicatorStyle.Flat))
        bars[1].assert(hasIndicatorStyle(IndicatorStyle.Flat))
    }

    @Test
    fun `other providers with a monthly limit beside weekly show the monthly bar too`() {
        showCard(account(Provider.Kimi, listOf(weekly, monthly)), display = QuotaDisplay.Left)

        rule.onNodeWithText("Weekly").assertExists()
        rule.onNodeWithText("Monthly").assertExists()
        val bars =
            rule.onAllNodes(indicatorsIn(accountCardTag(Provider.Kimi.id)), useUnmergedTree = true)
        bars.assertCountEquals(2)
        bars[1].assertRangeInfoEquals(ProgressBarRangeInfo(0.60f, 0f..1f))
    }

    @Test
    fun `monthly-only accounts show a single monthly bar`() {
        showCard(
            account(
                Provider.Copilot,
                listOf(monthly, monthly.copy(id = "chat", label = "Chat", usedPercent = 10.0)),
            )
        )

        rule.onAllNodesWithText("Monthly").assertCountEquals(1)
        rule
            .onAllNodes(indicatorsIn(accountCardTag(Provider.Copilot.id)), useUnmergedTree = true)
            .assertCountEquals(1)
    }

    @Test
    fun `Grok does not show a secondary Cursor monthly pool on the overview card`() {
        showCard(
            account(
                Provider.Grok,
                listOf(
                    weekly,
                    monthly.copy(id = "cursor-models", label = "Cursor Models", isSecondary = true),
                ),
            )
        )

        rule.onAllNodesWithText("Monthly").assertCountEquals(0)
        rule
            .onAllNodes(indicatorsIn(accountCardTag(Provider.Grok.id)), useUnmergedTree = true)
            .assertCountEquals(1)
    }

    @Test
    fun `Claude does not show a monthly bar on the overview card`() {
        showCard(
            account(
                Provider.Claude,
                listOf(
                    session,
                    weekly,
                    monthly.copy(id = "extra_usage", label = "Extra usage", resetsAt = null),
                ),
            )
        )

        rule.onAllNodesWithText("Monthly").assertCountEquals(0)
        rule
            .onAllNodes(indicatorsIn(accountCardTag(Provider.Claude.id)), useUnmergedTree = true)
            .assertCountEquals(2)
    }

    @Test
    fun `Claude with only session and extra usage leads with session without duplicating it`() {
        showCard(
            account(
                Provider.Claude,
                listOf(
                    session,
                    monthly.copy(
                        id = "extra_usage",
                        label = "Extra usage",
                        resetsAt = null,
                        isSecondary = true,
                    ),
                ),
            )
        )

        rule.onAllNodesWithText("Session").assertCountEquals(1)
        rule.onAllNodesWithText("Monthly").assertCountEquals(0)
        rule
            .onAllNodes(indicatorsIn(accountCardTag(Provider.Claude.id)), useUnmergedTree = true)
            .assertCountEquals(1)
    }

    @Test
    fun `JetBrains separate allowances do not duplicate a monthly window`() {
        showCard(
            account(
                Provider.JetBrains,
                listOf(
                    weekly.copy(id = "jb:combined", label = "Combined"),
                    monthly.copy(id = "jb:license:a", label = "AI Pro", isSecondary = true),
                ),
            )
        )

        rule.onAllNodesWithText("Monthly").assertCountEquals(0)
        rule.onAllNodesWithText("AI Pro").assertCountEquals(1)
        rule
            .onAllNodes(indicatorsIn(accountCardTag(Provider.JetBrains.id)), useUnmergedTree = true)
            .assertCountEquals(2)
    }
}
