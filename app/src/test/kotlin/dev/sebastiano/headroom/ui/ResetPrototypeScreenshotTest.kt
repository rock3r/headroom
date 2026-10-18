package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.AppSettings
import dev.sebastiano.headroom.model.InMemorySettingsRepository
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.prototype.FakeResetProvider
import dev.sebastiano.headroom.prototype.PROTOTYPES_ENTRY_TAG
import dev.sebastiano.headroom.prototype.ResetScenarios
import dev.sebastiano.headroom.prototype.ScenarioAccounts
import dev.sebastiano.headroom.prototype.prototypeTools
import dev.sebastiano.headroom.ui.components.rememberResetFormatter
import dev.sebastiano.headroom.ui.delights.DelightsHost
import dev.sebastiano.headroom.ui.detail.DETAIL_TAG
import dev.sebastiano.headroom.ui.home.toSummary
import dev.sebastiano.headroom.ui.overview.AccountCard
import dev.sebastiano.headroom.ui.overview.accountCardTag
import dev.sebastiano.headroom.ui.resets.RESETS_CARD_TAG
import dev.sebastiano.headroom.ui.resets.ResetsCard
import dev.sebastiano.headroom.ui.resets.availableResetTag
import dev.sebastiano.headroom.ui.resets.resetRowTag
import dev.sebastiano.headroom.ui.settings.SETTINGS_LIST_TAG
import java.time.ZoneOffset
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the 1.1 prototype of usage limit resets and the Quick Settings tile, recorded with
 * `./gradlew :app:recordRoborazziDebug` into `app/build/outputs/roborazzi/prototypes`. They use the
 * fake reset data of the debug build and a fixed clock. They are recordings for review, not
 * assertions. The sheet steps are in [RedeemSheetScreenshotTest].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PROTOTYPE_PHONE)
class ResetPrototypeScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun launch(
        dark: Boolean = false,
        display: QuotaDisplay = QuotaDisplay.Used,
    ) {
        rule.mainClock.autoAdvance = false
        val settings = InMemorySettingsRepository(AppSettings(quotaDisplay = display))
        rule.setContent {
            HeadroomTheme(darkTheme = dark, dynamicColor = false) {
                DelightsHost(refreshShimmer = false, resetConfetti = true) {
                    HeadroomApp(
                        graph =
                            testGraph(
                                rule.activity,
                                settings = settings,
                                resetProvider =
                                    FakeResetProvider(
                                        ResetScenarios.demo(FIXED_NOW),
                                        latency = Duration.ZERO,
                                    ),
                                prototypes = prototypeTools,
                            )
                    )
                }
            }
        }
    }

    private fun capture(name: String) {
        repeat(PROTOTYPE_SETTLE_STEPS) {
            rule.mainClock.advanceTimeBy(PROTOTYPE_STEP_MILLIS)
            rule.onRoot().drawFrame()
        }
        rule.onRoot().captureRoboImage(prototypeScreenshot(name))
    }

    private fun openDetailResets(accountId: String) {
        rule.onNodeWithTag(accountCardTag(accountId)).performClick()
        rule.mainClock.autoAdvance = true
        rule.onNodeWithTag(RESETS_CARD_TAG).performScrollTo()
        rule.mainClock.autoAdvance = false
    }

    /** An overview card while its usage is refreshed after a reset: its bars shimmer. */
    private fun refreshingCard(name: String, dark: Boolean, reduceMotion: Boolean) {
        rule.mainClock.autoAdvance = false
        val account =
            ScenarioAccounts.claudeAccount(FIXED_NOW).toSummary(FIXED_NOW, emptyMap(), emptyMap())
        rule.setContent {
            HeadroomTheme(darkTheme = dark, dynamicColor = false, reduceMotion = reduceMotion) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column {
                        AccountCard(
                            account = account,
                            now = FIXED_NOW,
                            formatter = rememberResetFormatter(ZoneOffset.UTC),
                            onClick = {},
                            refreshing = true,
                            modifier = Modifier.testTag(CARD_TAG).padding(16.dp),
                        )
                    }
                }
            }
        }
        repeat(REFRESHING_FRAMES) {
            rule.mainClock.advanceTimeBy(FRAME_MILLIS)
            rule.onRoot().drawFrame()
        }
        rule.onNodeWithTag(CARD_TAG).captureRoboImage(prototypeScreenshot(name))
    }

    @Test fun overviewCardRefreshing() = refreshingCard("card-overview-refreshing", false, false)

    @Test
    fun overviewCardRefreshingDark() = refreshingCard("card-overview-refreshing-dark", true, false)

    @Test
    fun overviewCardRefreshingReduced() =
        refreshingCard("card-overview-refreshing-reduced", false, true)

    /**
     * Frames of the Resets row turning into the account detail, and back: written to
     * `app/build/transition` for review, not to the docs.
     */
    private fun transitionFrames(name: String, row: String) {
        launch()
        rule.onNodeWithContentDescription("Resets").clickNow()
        repeat(PROTOTYPE_SETTLE_STEPS) {
            rule.mainClock.advanceTimeBy(PROTOTYPE_STEP_MILLIS)
            rule.onRoot().drawFrame()
        }
        fun frames(phase: String) {
            repeat(TRANSITION_FRAMES) { index ->
                rule.mainClock.advanceTimeBy(TRANSITION_FRAME_MILLIS)
                rule
                    .onRoot()
                    .captureRoboImage(
                        "build/transition/$name-$phase-${index.toString().padStart(2, '0')}.png"
                    )
            }
        }
        rule.onNodeWithTag(row).clickNow()
        frames("open")
        rule.onNodeWithContentDescription("Back").clickNow()
        frames("back")
    }

    @Test
    fun availableRowTransition() = transitionFrames("available", availableResetTag("demo-codex"))

    @Test
    fun upcomingRowTransition() =
        transitionFrames("upcoming", resetRowTag("demo-claude", "seven_day"))

    @Test
    fun resetsTab() {
        launch()
        rule.onNodeWithContentDescription("Resets").clickNow()
        capture("resets-tab")
    }

    @Test
    fun resetsTabDark() {
        launch(dark = true)
        rule.onNodeWithContentDescription("Resets").clickNow()
        capture("resets-tab-dark")
    }

    @Test
    fun claudeDetail() {
        launch()
        openDetailResets("demo-claude")
        capture("detail-claude")
    }

    @Test
    fun claudeDetailDark() {
        launch(dark = true)
        openDetailResets("demo-claude")
        capture("detail-claude-dark")
    }

    @Test
    fun codexDetailLeft() {
        launch(display = QuotaDisplay.Left)
        openDetailResets("demo-codex")
        capture("detail-codex-left")
    }

    @Test
    fun settingsRows() {
        launch()
        rule.mainClock.autoAdvance = true
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithTag(SETTINGS_LIST_TAG).performScrollToNode(hasTestTag(PROTOTYPES_ENTRY_TAG))
        rule.mainClock.autoAdvance = false
        capture("settings-tile")
    }

    @Test
    fun settingsRowsDark() {
        launch(dark = true)
        rule.mainClock.autoAdvance = true
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithTag(SETTINGS_LIST_TAG).performScrollToNode(hasTestTag(PROTOTYPES_ENTRY_TAG))
        rule.mainClock.autoAdvance = false
        capture("settings-tile-dark")
    }

    @Test
    fun prototypesPage() {
        launch()
        rule.mainClock.autoAdvance = true
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithTag(SETTINGS_LIST_TAG).performScrollToNode(hasTestTag(PROTOTYPES_ENTRY_TAG))
        rule.onNodeWithTag(PROTOTYPES_ENTRY_TAG).clickNow()
        rule.mainClock.autoAdvance = false
        capture("prototypes-page")
    }

    /** The detail card alone, for states the demo accounts do not have. */
    private fun card(
        name: String,
        dark: Boolean = false,
        redeem: Boolean = true,
        card: () -> Card,
    ) {
        rule.mainClock.autoAdvance = false
        val shown = card()
        rule.setContent {
            HeadroomTheme(darkTheme = dark, dynamicColor = false) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column {
                        ResetsCard(
                            provider = shown.provider,
                            availability = shown.availability,
                            formatter = rememberResetFormatter(ZoneOffset.UTC),
                            onUse = {},
                            onAsk = {},
                            redeemEnabled = redeem,
                            modifier = Modifier.testTag(CARD_TAG).padding(16.dp),
                        )
                    }
                }
            }
        }
        repeat(PROTOTYPE_SETTLE_STEPS) {
            rule.mainClock.advanceTimeBy(PROTOTYPE_STEP_MILLIS)
            rule.onRoot().drawFrame()
        }
        rule.onNodeWithTag(CARD_TAG).captureRoboImage(prototypeScreenshot(name))
    }

    @Test
    fun zaiCard() =
        card("card-zai-pools") { Card(Provider.ZAi, ResetScenarios.zaiPools(FIXED_NOW, 2, 1)) }

    @Test
    fun zaiCardSignIn() =
        card("card-zai-sign-in") {
            Card(
                Provider.ZAi,
                ResetAvailability(emptyList(), requiresSignIn = true),
            )
        }

    @Test
    fun zaiCardSignInDark() =
        card("card-zai-sign-in-dark", dark = true) {
            Card(Provider.ZAi, ResetAvailability(emptyList(), requiresSignIn = true))
        }

    @Test
    fun claudeCard() =
        card("card-claude", redeem = false) {
            Card(Provider.Claude, ResetScenarios.claudeGrants(FIXED_NOW))
        }

    @Test
    fun claudeCardIneligible() =
        card("card-claude-ineligible") { Card(Provider.Claude, ResetScenarios.claudeIneligible()) }

    @Test
    fun claudeCardNeedsLimit() =
        card("card-claude-needs-limit") {
            Card(Provider.Claude, ResetScenarios.claudeWaiting(FIXED_NOW))
        }

    @Test
    fun claudeCardAnyTimeDark() =
        card("card-claude-any-time-dark", dark = true) {
            Card(Provider.Claude, ResetScenarios.claudeAnyTime(FIXED_NOW))
        }

    @Test
    fun grokCard() = card("card-grok") { Card(Provider.Grok, ResetScenarios.grokPool(FIXED_NOW)) }

    @Test
    fun grokCardNone() =
        card("card-grok-none") {
            val pool = ResetScenarios.grokPool(FIXED_NOW).pools.single()
            Card(
                Provider.Grok,
                ResetAvailability(listOf(pool.copy(available = 0, expiries = emptyList()))),
            )
        }

    @Test
    fun codexCardThree() =
        card("card-codex-three") {
            val pool = ResetScenarios.codexPool(FIXED_NOW, available = 3).pools.single()
            Card(
                Provider.Codex,
                ResetAvailability(
                    listOf(
                        pool.copy(
                            expiries =
                                listOf(
                                    FIXED_NOW.plus(4.days),
                                    FIXED_NOW.plus(9.days),
                                    FIXED_NOW.plus(9.days),
                                )
                        )
                    )
                ),
            )
        }

    @Test
    @Config(qualifiers = PROTOTYPE_EXPANDED)
    fun expandedDetail() {
        launch()
        rule.onNodeWithTag(accountCardTag("demo-claude")).performClick()
        rule.mainClock.autoAdvance = true
        rule.onNodeWithTag(DETAIL_TAG).performScrollToNode(hasTestTag(RESETS_CARD_TAG))
        rule.mainClock.autoAdvance = false
        capture("expanded-detail")
    }

    /** A provider and its resets, for one card screenshot. */
    data class Card(
        val provider: Provider,
        val availability: ResetAvailability,
    )
}

/**
 * The frame the sheet steps are drawn in: the sheet's shape, colour and drag handle over a scrim,
 * at the sheet's width. The real sheet is a separate window, which Robolectric does not capture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SheetFrame(content: @Composable () -> Unit) {
    Box(
        modifier =
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .background(Color.Black.copy(alpha = SCRIM_ALPHA)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = BottomSheetDefaults.ExpandedShape,
            color = BottomSheetDefaults.ContainerColor,
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().testTag(SHEET_FRAME_TAG),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BottomSheetDefaults.DragHandle()
                content()
            }
        }
    }
}

private const val SCRIM_ALPHA = 0.32f

/** The sheet itself, which the step screenshots are cropped to. */
internal const val SHEET_FRAME_TAG = "sheet-frame"

private const val CARD_TAG = "card-shot"

/** Clicks through the node's click action, without a press ripple that differs between runs. */
private fun SemanticsNodeInteraction.clickNow() {
    performSemanticsAction(SemanticsActions.OnClick)
}

internal const val PROTOTYPE_PHONE = "w411dp-h891dp-xxhdpi"
internal const val PROTOTYPE_EXPANDED = "w1280dp-h800dp-xhdpi"
internal const val PROTOTYPE_SETTLE_STEPS = 40
internal const val PROTOTYPE_STEP_MILLIS = 50L
private const val TRANSITION_FRAMES = 10
private const val TRANSITION_FRAME_MILLIS = 48L

/** About a third of the way through the shimmer's sweep. */
private const val REFRESHING_FRAMES = 22
private const val FRAME_MILLIS = 16L

/**
 * Where the reset flow's review screenshots go: the build folder, not `docs/`. There are over a
 * hundred of them, for reviewing the flow, and they stay out of the repository.
 */
internal fun prototypeScreenshot(name: String): String =
    "build/outputs/roborazzi/prototypes/$name.png"
