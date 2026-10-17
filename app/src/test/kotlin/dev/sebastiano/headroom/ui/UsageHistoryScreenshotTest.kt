package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.FakeQuotaRepository
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaRepository
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.UsagePoint
import dev.sebastiano.headroom.model.WindowKind
import dev.sebastiano.headroom.ui.detail.DETAIL_TAG
import dev.sebastiano.headroom.ui.detail.HistoryRange
import dev.sebastiano.headroom.ui.detail.USAGE_HISTORY_CHART_TAG
import dev.sebastiano.headroom.ui.detail.chartRangeTag
import dev.sebastiano.headroom.ui.overview.OVERVIEW_LIST_TAG
import dev.sebastiano.headroom.ui.overview.accountCardTag
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the detail screen's usage history chart across themes, font scales, window widths,
 * window combinations and history lengths. Recorded with `./gradlew :app:recordRoborazziDebug` into
 * `app/build/outputs/roborazzi/prototypes`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PROTOTYPE_PHONE)
class UsageHistoryScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun captureDetail(
        name: String,
        accountId: String = "demo-claude",
        dark: Boolean = false,
        fontScale: Float = 1f,
        range: HistoryRange? = null,
        realAccounts: List<AccountState> = emptyList(),
        realRepo: QuotaRepository? = null,
    ) {
        rule.mainClock.autoAdvance = false
        val repo = realRepo ?: FakeQuotaRepository({ FIXED_NOW }, initial = realAccounts)
        rule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, fontScale = fontScale)
            ) {
                HeadroomTheme(darkTheme = dark, dynamicColor = false) {
                    HeadroomApp(
                        graph =
                            testGraph(
                                rule.activity,
                                realAccounts = realAccounts,
                                real = repo,
                            )
                    )
                }
            }
        }
        rule.mainClock.autoAdvance = true
        rule
            .onNodeWithTag(OVERVIEW_LIST_TAG)
            .performScrollToNode(hasTestTag(accountCardTag(accountId)))
        rule.onNodeWithTag(accountCardTag(accountId)).performClick()
        rule.onNodeWithTag(DETAIL_TAG).performScrollToNode(hasTestTag(USAGE_HISTORY_CHART_TAG))
        if (range != null) {
            rule
                .onNodeWithTag(chartRangeTag(range))
                .performSemanticsAction(SemanticsActions.OnClick)
        }
        rule.mainClock.autoAdvance = false
        repeat(PROTOTYPE_SETTLE_STEPS) {
            rule.mainClock.advanceTimeBy(PROTOTYPE_STEP_MILLIS)
            rule.onRoot().drawFrame()
        }
        rule.onRoot().captureRoboImage(prototypeScreenshot(name))
    }

    @Test fun multipleWindowsLight() = captureDetail(name = "usage-history-multi-light")

    @Test
    fun multipleWindowsDarkOneMonth() =
        captureDetail(
            name = "usage-history-multi-dark-1m",
            dark = true,
            range = HistoryRange.OneMonth,
        )

    @Test
    fun multipleWindowsOneYear() =
        captureDetail(name = "usage-history-multi-1y", range = HistoryRange.OneYear)

    @Test
    fun singleWindowLight() =
        captureDetail(name = "usage-history-single-light", accountId = "demo-codex")

    @Test fun fontScale150() = captureDetail(name = "usage-history-font-150", fontScale = 1.5f)

    @Test fun fontScale200() = captureDetail(name = "usage-history-font-200", fontScale = 2.0f)

    @Test
    @Config(qualifiers = PROTOTYPE_EXPANDED)
    fun expandedWidth() =
        captureDetail(
            name = "usage-history-expanded",
            range = HistoryRange.ThreeMonths,
        )

    @Test
    fun shortHistoryTwoPoints() {
        val reset = FIXED_NOW.plus(5.days)
        val window =
            QuotaWindow(
                id = "weekly",
                label = "Weekly",
                kind = WindowKind.Weekly,
                usedPercent = 18.0,
                resetsAt = reset,
                length = 7.days,
            )
        val freshAccount =
            AccountState(
                account = Account("fresh-codex", Provider.Codex, "sam@example.com"),
                snapshot =
                    QuotaSnapshot(
                        provider = Provider.Codex,
                        accountId = "fresh-codex",
                        planLabel = "Pro",
                        windows = listOf(window),
                        fetchedAt = FIXED_NOW,
                    ),
            )
        val baseRepo = FakeQuotaRepository({ FIXED_NOW }, initial = listOf(freshAccount))
        val twoPointsRepo =
            object : QuotaRepository by baseRepo {
                override fun history(accountId: String, windowId: String): Flow<List<UsagePoint>> =
                    flowOf(
                        listOf(
                            UsagePoint(FIXED_NOW.minus(3.hours), 6.0, resetsAt = reset),
                            UsagePoint(FIXED_NOW, 18.0, resetsAt = reset),
                        )
                    )
            }
        captureDetail(
            name = "usage-history-short-two-points",
            accountId = "fresh-codex",
            realAccounts = listOf(freshAccount),
            realRepo = twoPointsRepo,
        )
    }
}
