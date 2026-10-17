package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.designsystem.IndicatorStyle
import dev.sebastiano.headroom.model.AppSettings
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.InMemorySettingsRepository
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.UsagePoint
import dev.sebastiano.headroom.model.WindowKind
import dev.sebastiano.headroom.ui.detail.DETAIL_TAG
import dev.sebastiano.headroom.ui.detail.DescriptionMemo
import dev.sebastiano.headroom.ui.detail.HistoryRange
import dev.sebastiano.headroom.ui.detail.USAGE_HISTORY_CHART_TAG
import dev.sebastiano.headroom.ui.detail.UsageHistoryChart
import dev.sebastiano.headroom.ui.detail.alertSwitchTag
import dev.sebastiano.headroom.ui.detail.buildUsageHistoryModel
import dev.sebastiano.headroom.ui.detail.chartRangeTag
import dev.sebastiano.headroom.ui.detail.chartWindowTag
import dev.sebastiano.headroom.ui.overview.accountCardTag
import java.time.ZoneOffset
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class DetailTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun openClaude() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) { HeadroomApp(graph = testGraph(rule.activity)) }
        }
        rule.onNodeWithTag(accountCardTag("demo-claude")).performClick()
        rule.onNodeWithTag(DETAIL_TAG).assertIsDisplayed()
    }

    @Test
    fun `an account's details open at the top, even after scrolling them before`() {
        openClaude()
        rule.onNodeWithTag(alertSwitchTag("demo-claude", "seven_day")).performScrollTo()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }

        rule.onNodeWithTag(accountCardTag("demo-codex")).performClick()

        rule.onNode(indicatorsIn(DETAIL_TAG), true).assertIsDisplayed()
        rule.onNodeWithText("ChatGPT Codex").assertIsDisplayed()
    }

    @Test
    fun `the hero ring shows the weekly window and is wavy because Claude is over pace`() {
        openClaude()
        rule
            .onNode(indicatorsIn(DETAIL_TAG) and hasIndicatorStyle(IndicatorStyle.Wavy), true)
            .assertRangeInfoEquals(ProgressBarRangeInfo(0.71f, 0f..1f))
    }

    @Test
    fun `the chart describes usage against even pace and projects the limit`() {
        openClaude()
        rule
            .onNodeWithContentDescription("above the even-pace line", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        rule
            .onNodeWithText("At this rate you reach 100% in about 1d 17h, 1d 1h before the reset.")
            .assertExists()
    }

    @Test
    fun `the chart overlays all long windows and filter chips keep at least one on`() {
        openClaude()
        rule.onNodeWithText("Usage history").performScrollTo().assertIsDisplayed()
        val weekly = rule.onNodeWithTag(chartWindowTag("seven_day"))
        val opus = rule.onNodeWithTag(chartWindowTag("seven_day_opus"))
        weekly.performScrollTo().assertIsSelected().assertIsOn()
        opus.assertIsSelected().assertIsOn()
        assertEquals(
            Role.Checkbox,
            weekly.fetchSemanticsNode().config[SemanticsProperties.Role],
        )
        assertEquals(
            ToggleableState.On,
            weekly.fetchSemanticsNode().config[SemanticsProperties.ToggleableState],
        )

        // Toggling the primary window off leaves Opus on, updates checked semantics to Off, and
        // keeps the range button row visible and stable.
        weekly.performClick()
        val weeklyAfter = rule.onNodeWithTag(chartWindowTag("seven_day"))
        weeklyAfter.assertIsNotSelected().assertIsOff()
        assertEquals(
            ToggleableState.Off,
            weeklyAfter.fetchSemanticsNode().config[SemanticsProperties.ToggleableState],
        )
        rule.onNodeWithTag(chartWindowTag("seven_day_opus")).assertIsSelected().assertIsOn()
        rule.onNodeWithTag(chartRangeTag(HistoryRange.OneYear)).assertIsDisplayed()

        // Tapping the last remaining chip does not turn it off.
        opus.performClick()
        rule.onNodeWithTag(chartWindowTag("seven_day_opus")).assertIsSelected().assertIsOn()

        // Single-window accounts omit the filter chips altogether.
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithTag(accountCardTag("demo-codex")).performClick()
        rule.onNodeWithTag(chartWindowTag("secondary")).assertDoesNotExist()
    }

    @Test
    fun `range buttons switch the viewport, update the description, and deselect on pan`() {
        openClaude()
        val periodButton = rule.onNodeWithTag(chartRangeTag(HistoryRange.Period))
        val oneMonthButton = rule.onNodeWithTag(chartRangeTag(HistoryRange.OneMonth))
        val threeMonthsButton = rule.onNodeWithTag(chartRangeTag(HistoryRange.ThreeMonths))
        val oneYearButton = rule.onNodeWithTag(chartRangeTag(HistoryRange.OneYear))

        periodButton.performScrollTo().assertIsSelected()
        oneMonthButton.assertIsNotSelected()
        threeMonthsButton.assertIsNotSelected()
        oneYearButton.assertIsNotSelected()
        assertEquals(
            Role.RadioButton,
            periodButton.fetchSemanticsNode().config[SemanticsProperties.Role],
        )
        assertEquals(
            true,
            periodButton.fetchSemanticsNode().config[SemanticsProperties.Selected],
        )
        assertEquals(
            false,
            oneMonthButton.fetchSemanticsNode().config[SemanticsProperties.Selected],
        )

        val chartNode = rule.onNodeWithTag(USAGE_HISTORY_CHART_TAG).performScrollTo()
        val initialDesc =
            chartNode
                .fetchSemanticsNode()
                .config[SemanticsProperties.ContentDescription]
                .joinToString(" ")
        assertTrue(initialDesc.contains("Weekly · all models, Weekly · Opus"), initialDesc)
        assertTrue(initialDesc.contains("No resets in view"), initialDesc)

        // Custom accessibility actions are exposed with localised labels.
        val actions = chartNode.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(
            listOf("Scroll back", "Scroll forward", "Zoom in", "Zoom out"),
            actions.map { it.label },
        )

        // Tapping 1M animates to the 30-day window and brings past resets into view.
        oneMonthButton.performClick()
        rule.waitForIdle()
        oneMonthButton.assertIsSelected()
        periodButton.assertIsNotSelected()
        val monthDesc =
            chartNode
                .fetchSemanticsNode()
                .config[SemanticsProperties.ContentDescription]
                .joinToString(" ")
        assertTrue(monthDesc.contains("resets in view"), monthDesc)

        // Panning back via the accessibility action deselects the preset range button.
        val scrollBack = actions.first { it.label == "Scroll back" }
        rule.runOnIdle { assertTrue(scrollBack.action()) }
        rule.waitForIdle()
        oneMonthButton.assertIsNotSelected()
        periodButton.assertIsNotSelected()
    }

    @Test
    fun `turning off long-term history hides the 1Y range button`() {
        val settings = InMemorySettingsRepository(AppSettings(keepLongHistory = false))
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                HeadroomApp(graph = testGraph(rule.activity, settings = settings))
            }
        }
        rule.onNodeWithTag(accountCardTag("demo-claude")).performClick()
        rule.onNodeWithTag(chartRangeTag(HistoryRange.Period)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(chartRangeTag(HistoryRange.OneMonth)).assertIsDisplayed()
        rule.onNodeWithTag(chartRangeTag(HistoryRange.ThreeMonths)).assertIsDisplayed()
        rule.onNodeWithTag(chartRangeTag(HistoryRange.OneYear)).assertDoesNotExist()
    }

    @Test
    fun `weekly alerts are on by default and can be switched off`() {
        openClaude()
        val weekly = rule.onNodeWithTag(alertSwitchTag("demo-claude", "seven_day"))
        weekly.performScrollTo().assertIsOn().performClick()
        rule.onNodeWithTag(alertSwitchTag("demo-claude", "seven_day")).assertIsOff()
        rule.onNodeWithTag(alertSwitchTag("demo-claude", "seven_day_opus")).assertIsOn()
    }

    @Test
    fun `session windows say they never alert`() {
        openClaude()
        rule
            .onNodeWithText("Session limits never send alerts.")
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithTag(alertSwitchTag("demo-claude", "five_hour")).assertDoesNotExist()
    }

    @Test
    fun `back returns to the overview`() {
        openClaude()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithTag(DETAIL_TAG).assertDoesNotExist()
        rule.onNodeWithTag(accountCardTag("demo-claude")).assertIsDisplayed()
    }

    @Test
    fun `a monthly window is off by default`() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) { HeadroomApp(graph = testGraph(rule.activity)) }
        }
        rule
            .onNodeWithTag(dev.sebastiano.headroom.ui.overview.OVERVIEW_LIST_TAG)
            .performScrollToNode(hasTestTag(accountCardTag("demo-copilot")))
        rule.onNodeWithTag(accountCardTag("demo-copilot")).performClick()
        rule
            .onNodeWithTag(alertSwitchTag("demo-copilot", "premium_interactions"))
            .performScrollTo()
            .assertIsOff()
        rule.onNodeWithText("Monthly windows are off by default").assertExists()
    }

    @Test
    fun `minute clock ticks do not rebuild the chart specs`() {
        val claude = DemoData.accounts(FIXED_NOW).first()
        val windows = claude.snapshot!!.windows
        val histories = windows.associate { w ->
            w.id to listOf(UsagePoint(FIXED_NOW, w.usedPercent, w.resetsAt))
        }
        val formatter = ResetFormatter(ZoneOffset.UTC, Locale.UK, is24Hour = true)
        var currentNow by mutableStateOf(FIXED_NOW)
        var specBuilds = 0

        rule.setContent {
            val model =
                requireNotNull(
                    buildUsageHistoryModel(
                        accountId = claude.account.id,
                        windows = windows,
                        primaryWindowId = claude.primaryWindow?.id,
                        histories = histories,
                        selectedWindowIds = null,
                        now = currentNow,
                        dataFrom = FIXED_NOW,
                    )
                )
            HeadroomTheme(dynamicColor = false) {
                UsageHistoryChart(
                    model = model,
                    formatter = formatter,
                    display = QuotaDisplay.Used,
                    onWindowToggle = {},
                    onBuildSpec = { specBuilds++ },
                )
            }
        }
        rule.waitForIdle()
        assertEquals(1, specBuilds)

        // Advance the clock across several minute ticks without a new sync: specs are not rebuilt.
        currentNow = FIXED_NOW.plus(1.minutes)
        rule.waitForIdle()
        currentNow = FIXED_NOW.plus(2.minutes)
        rule.waitForIdle()
        currentNow = FIXED_NOW.plus(5.minutes)
        rule.waitForIdle()
        assertEquals(1, specBuilds)
    }

    @Test
    fun `description memo invalidates when viewport crosses a reset within the same hour`() {
        val t1200 = Instant.parse("2026-09-20T12:00:00Z")
        val t1220 = Instant.parse("2026-09-20T12:20:00Z")
        val t1225Reset = Instant.parse("2026-09-20T12:25:00Z")
        val t1228 = Instant.parse("2026-09-20T12:28:00Z")
        val r2 = t1225Reset.plus(7.days)
        val window =
            QuotaWindow(
                id = "seven_day",
                label = "Weekly · all models",
                kind = WindowKind.Weekly,
                usedPercent = 50.0,
                resetsAt = r2,
                length = 7.days,
            )
        val points =
            listOf(
                UsagePoint(t1200, 80.0, resetsAt = t1225Reset),
                UsagePoint(t1225Reset, 0.0, resetsAt = r2),
                UsagePoint(FIXED_NOW, 50.0, resetsAt = r2),
            )
        val model =
            requireNotNull(
                buildUsageHistoryModel(
                    accountId = "acc-1",
                    windows = listOf(window),
                    primaryWindowId = "seven_day",
                    histories = mapOf("seven_day" to points),
                    selectedWindowIds = setOf("seven_day"),
                    now = FIXED_NOW,
                    dataFrom = FIXED_NOW,
                )
            )
        val memo =
            DescriptionMemo(
                resources = rule.activity.resources,
                model = model,
                formatter = ResetFormatter(ZoneOffset.UTC, Locale.UK, is24Hour = true),
                paceDescription = "Usage so far: 50%.",
            )
        val beforeCrossing =
            memo.of(
                t1220.toEpochMilliseconds().toDouble(),
                FIXED_NOW.toEpochMilliseconds().toDouble(),
            )
        val afterCrossing =
            memo.of(
                t1228.toEpochMilliseconds().toDouble(),
                FIXED_NOW.toEpochMilliseconds().toDouble(),
            )
        assertTrue(beforeCrossing.contains("1 reset in view"), beforeCrossing)
        assertTrue(afterCrossing.contains("No resets in view"), afterCrossing)
    }

    @Test
    fun `toggling a series chip or receiving a new reset updates the chart content description`() {
        val r2 = FIXED_NOW.plus(3.days)
        val w1 =
            QuotaWindow(
                id = "seven_day",
                label = "Weekly · all models",
                kind = WindowKind.Weekly,
                usedPercent = 50.0,
                resetsAt = r2,
                length = 7.days,
            )
        val w2 =
            QuotaWindow(
                id = "seven_day_opus",
                label = "Weekly · Opus",
                kind = WindowKind.Weekly,
                usedPercent = 30.0,
                resetsAt = r2,
                length = 7.days,
            )
        val formatter = ResetFormatter(ZoneOffset.UTC, Locale.UK, is24Hour = true)
        var selectedIds by mutableStateOf(setOf("seven_day", "seven_day_opus"))
        var histories by
            mutableStateOf(
                mapOf(
                    "seven_day" to listOf(UsagePoint(FIXED_NOW, 50.0, resetsAt = r2)),
                    "seven_day_opus" to listOf(UsagePoint(FIXED_NOW, 30.0, resetsAt = r2)),
                )
            )

        rule.setContent {
            val model =
                requireNotNull(
                    buildUsageHistoryModel(
                        accountId = "acc-1",
                        windows = listOf(w1, w2),
                        primaryWindowId = "seven_day",
                        histories = histories,
                        selectedWindowIds = selectedIds,
                        now = FIXED_NOW,
                        dataFrom = FIXED_NOW,
                    )
                )
            HeadroomTheme(dynamicColor = false) {
                UsageHistoryChart(
                    model = model,
                    formatter = formatter,
                    display = QuotaDisplay.Used,
                    onWindowToggle = { id -> selectedIds = selectedIds - id },
                )
            }
        }
        rule.waitForIdle()

        val chartNode = rule.onNodeWithTag(USAGE_HISTORY_CHART_TAG)
        val bothDesc =
            chartNode
                .fetchSemanticsNode()
                .config[SemanticsProperties.ContentDescription]
                .joinToString(" ")
        assertTrue(bothDesc.contains("Weekly · all models, Weekly · Opus"), bothDesc)

        rule.onNodeWithTag(chartWindowTag("seven_day")).performClick()
        rule.waitForIdle()
        val opusOnlyDesc =
            chartNode
                .fetchSemanticsNode()
                .config[SemanticsProperties.ContentDescription]
                .joinToString(" ")
        assertFalse(opusOnlyDesc.contains("Weekly · all models"), opusOnlyDesc)
        assertTrue(opusOnlyDesc.contains("Weekly · Opus"), opusOnlyDesc)

        val midReset = FIXED_NOW.minus(2.hours)
        histories =
            histories +
                ("seven_day_opus" to
                    listOf(
                        UsagePoint(FIXED_NOW.minus(4.hours), 75.0, resetsAt = r2),
                        UsagePoint(midReset, 0.0, resetsAt = r2),
                        UsagePoint(FIXED_NOW, 30.0, resetsAt = r2),
                    ))
        rule.waitForIdle()
        val afterNewResetDesc =
            chartNode
                .fetchSemanticsNode()
                .config[SemanticsProperties.ContentDescription]
                .joinToString(" ")
        assertTrue(afterNewResetDesc.contains("1 reset in view"), afterNewResetDesc)
    }
}
