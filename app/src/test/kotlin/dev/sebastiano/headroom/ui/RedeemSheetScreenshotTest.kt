package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.AskOutcome
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.RedeemOutcome
import dev.sebastiano.headroom.model.RedeemStep
import dev.sebastiano.headroom.model.ResetPool
import dev.sebastiano.headroom.model.ThemePalette
import dev.sebastiano.headroom.prototype.ResetScenarios
import dev.sebastiano.headroom.prototype.ScenarioAccounts
import dev.sebastiano.headroom.ui.components.rememberResetFormatter
import dev.sebastiano.headroom.ui.delights.DelightsHost
import dev.sebastiano.headroom.ui.home.toSummary
import dev.sebastiano.headroom.ui.resets.RedeemActions
import dev.sebastiano.headroom.ui.resets.RedeemSheetContent
import java.time.Duration
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every step of the redeem sheet, for every scenario, in light and dark: recorded with `./gradlew
 * :app:recordRoborazziDebug` into `app/build/outputs/roborazzi/prototypes`. Recordings for review,
 * not assertions.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PROTOTYPE_PHONE)
class RedeemSheetScreenshotTest(private val shot: SheetShot, private val dark: Boolean) {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun record() {
        recordSheet(rule, shot, dark, if (dark) "sheet-${shot.name}-dark" else "sheet-${shot.name}")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} dark={1}")
        fun shots(): List<Array<Any>> =
            sheetShots().flatMap { listOf(arrayOf<Any>(it, false), arrayOf<Any>(it, true)) }
    }
}

/**
 * The moments between steps, and the sheet in other palettes, display modes and widths: recordings
 * for review, not assertions.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PROTOTYPE_PHONE)
class RedeemSheetMomentsScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    /**
     * The bars refilling once the refresh after a success brings the new usage in: part-way through
     * the stagger, with the confetti in the air.
     */
    private fun refill(
        name: String,
        account: AccountState,
        from: RedeemStep.Confirm,
        left: Int,
        display: QuotaDisplay = QuotaDisplay.Used,
    ) {
        rule.mainClock.autoAdvance = false
        val step = RedeemStep.Finished(from.pool, RedeemOutcome.Success(left))
        var state by mutableStateOf(account)
        var refreshing by mutableStateOf(true)
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                DelightsHost(refreshShimmer = false, resetConfetti = true) {
                    SheetFrame {
                        RedeemSheetContent(
                            step = step,
                            summary = state.toSummary(FIXED_NOW, emptyMap(), emptyMap()),
                            display = display,
                            formatter = rememberResetFormatter(ZoneOffset.UTC),
                            actions = RedeemActions(),
                            refreshing = refreshing,
                        )
                    }
                }
            }
        }
        repeat(PROTOTYPE_SETTLE_STEPS) {
            rule.mainClock.advanceTimeBy(PROTOTYPE_STEP_MILLIS)
            rule.onRoot().drawFrame()
        }
        state = account.afterReset(from.pool)
        refreshing = false
        repeat(REFILL_FRAMES) {
            rule.mainClock.advanceTimeBy(FRAME_MILLIS)
            rule.onRoot().drawFrame()
        }
        rule.onNodeWithTag(SHEET_FRAME_TAG).captureRoboImage(prototypeScreenshot(name))
    }

    /**
     * Frames of the gloss that sweeps each refilled bar, for review: written to `app/build/gloss`,
     * not to the docs, which get a strip made from them.
     */
    private fun glossFrames(
        name: String,
        dark: Boolean,
        display: QuotaDisplay,
        palette: ThemePalette = ThemePalette.Wallpaper,
    ) {
        rule.mainClock.autoAdvance = false
        val account = ScenarioAccounts.claudeAccount(FIXED_NOW)
        val pool = ResetScenarios.claudeGrants(FIXED_NOW).pools.first()
        val step = RedeemStep.Finished(pool, RedeemOutcome.Success(3))
        var state by mutableStateOf(account)
        var refreshing by mutableStateOf(true)
        rule.setContent {
            HeadroomTheme(darkTheme = dark, dynamicColor = false, palette = palette) {
                // Confetti on: the gloss plays only when the reset confetti would.
                DelightsHost(refreshShimmer = false, resetConfetti = true) {
                    SheetFrame {
                        RedeemSheetContent(
                            step = step,
                            summary = state.toSummary(FIXED_NOW, emptyMap(), emptyMap()),
                            display = display,
                            formatter = rememberResetFormatter(ZoneOffset.UTC),
                            actions = RedeemActions(),
                            refreshing = refreshing,
                        )
                    }
                }
            }
        }
        repeat(PROTOTYPE_SETTLE_STEPS) {
            rule.mainClock.advanceTimeBy(PROTOTYPE_STEP_MILLIS)
            rule.onRoot().drawFrame()
        }
        state = account.afterReset(pool)
        refreshing = false
        var elapsed = 0L
        repeat(GLOSS_FRAMES) { index ->
            while (elapsed < GLOSS_FIRST_MILLIS + index * GLOSS_FRAME_MILLIS) {
                rule.mainClock.advanceTimeBy(FRAME_MILLIS)
                elapsed += FRAME_MILLIS
                rule.onRoot().drawFrame()
            }
            rule
                .onNodeWithTag(SHEET_FRAME_TAG)
                .captureRoboImage("build/gloss/$name-${index.toString().padStart(2, '0')}.png")
        }
    }

    @Test fun glossUsed() = glossFrames("used", dark = false, display = QuotaDisplay.Used)

    @Test fun glossUsedDark() = glossFrames("used-dark", dark = true, display = QuotaDisplay.Used)

    @Test fun glossLeft() = glossFrames("left", dark = false, display = QuotaDisplay.Left)

    @Test fun glossLeftDark() = glossFrames("left-dark", dark = true, display = QuotaDisplay.Left)

    @Test
    fun glossLeftLemon() =
        glossFrames("left-lemon", dark = false, display = QuotaDisplay.Left, ThemePalette.Lemon)

    @Test
    fun codexRefilling() =
        refill(
            "sheet-codex-refilling",
            ScenarioAccounts.codexAccount(FIXED_NOW),
            RedeemStep.Confirm(ResetScenarios.codexPool(FIXED_NOW, 2).pools.single(), false),
            left = 1,
        )

    @Test
    fun codexRefillingLeft() =
        refill(
            "sheet-codex-refilling-left",
            ScenarioAccounts.codexAccount(FIXED_NOW),
            RedeemStep.Confirm(ResetScenarios.codexPool(FIXED_NOW, 2).pools.single(), false),
            left = 1,
            display = QuotaDisplay.Left,
        )

    @Test
    fun claudeRefilling() =
        refill(
            "sheet-claude-refilling",
            ScenarioAccounts.claudeAccount(FIXED_NOW),
            RedeemStep.Confirm(ResetScenarios.claudeGrants(FIXED_NOW).pools.first(), false),
            left = 3,
        )

    @Test
    fun confirmGrapeDark() =
        recordSheet(
            rule,
            sheetShots().first { it.name == "codex-confirm" }.copy(palette = ThemePalette.Grape),
            dark = true,
            name = "sheet-codex-confirm-grape-dark",
        )

    @Test
    fun successLagoon() =
        recordSheet(
            rule,
            sheetShots().first { it.name == "zai-success" }.copy(palette = ThemePalette.Lagoon),
            dark = false,
            name = "sheet-zai-success-lagoon",
        )

    @Test
    fun confirmLeftMode() =
        recordSheet(
            rule,
            sheetShots().first { it.name == "codex-confirm" }.copy(display = QuotaDisplay.Left),
            dark = false,
            name = "sheet-codex-confirm-left",
        )

    @Test
    fun successLeftMode() =
        recordSheet(
            rule,
            sheetShots().first { it.name == "codex-success" }.copy(display = QuotaDisplay.Left),
            dark = false,
            name = "sheet-codex-success-left",
        )

    @Test
    @Config(qualifiers = PROTOTYPE_EXPANDED)
    fun confirmExpanded() =
        recordSheet(
            rule,
            sheetShots().first { it.name == "claude-confirm" },
            dark = false,
            name = "sheet-claude-confirm-expanded",
            wholeScreen = true,
        )

    private companion object {
        /** Part-way through the stagger: the first bar has landed, the next one is moving. */
        const val REFILL_FRAMES = 36
        const val FRAME_MILLIS = 16L
        const val GLOSS_FRAMES = 14
        const val GLOSS_FIRST_MILLIS = 700L
        const val GLOSS_FRAME_MILLIS = 112L
    }
}

/** One sheet step of one scenario. */
data class SheetShot(
    val name: String,
    val account: AccountState,
    val step: RedeemStep,
    val display: QuotaDisplay = QuotaDisplay.Used,
    val palette: ThemePalette = ThemePalette.Wallpaper,
    val canAskForMore: Boolean = false,
    val signInNote: String? = null,
    /** The usage is still being refreshed after a success. */
    val refreshing: Boolean = false,
    val reduceMotion: Boolean = false,
) {
    override fun toString(): String = name
}

/**
 * The account after a reset from [pool] and the refresh that follows: its cleared windows empty.
 */
internal fun AccountState.afterReset(pool: ResetPool): AccountState {
    val snapshot = snapshot ?: return this
    val windows =
        snapshot.windows.map {
            if (pool.scope.covers(it.id, it.kind)) it.copy(usedPercent = 0.0) else it
        }
    return copy(snapshot = snapshot.copy(windows = windows))
}

/** Draws [shot] in [SheetFrame], lets it settle, and records it as [name]. */
internal fun recordSheet(
    rule: ComposeContentTestRule,
    shot: SheetShot,
    dark: Boolean,
    name: String,
    wholeScreen: Boolean = false,
) {
    rule.mainClock.autoAdvance = false
    val summary = shot.account.toSummary(FIXED_NOW, emptyMap(), emptyMap())
    rule.setContent {
        HeadroomTheme(
            darkTheme = dark,
            dynamicColor = false,
            palette = shot.palette,
            reduceMotion = shot.reduceMotion,
        ) {
            DelightsHost(refreshShimmer = false, resetConfetti = true) {
                SheetFrame {
                    RedeemSheetContent(
                        step = shot.step,
                        summary = summary,
                        display = shot.display,
                        formatter = rememberResetFormatter(ZoneOffset.UTC),
                        actions = RedeemActions(),
                        canAskForMore = shot.canAskForMore,
                        signInNote = shot.signInNote,
                        refreshing = shot.refreshing,
                    )
                }
            }
        }
    }
    repeat(PROTOTYPE_SETTLE_STEPS) {
        rule.mainClock.advanceTimeBy(PROTOTYPE_STEP_MILLIS)
        rule.onRoot().drawFrame()
    }
    val target = if (wholeScreen) rule.onRoot() else rule.onNodeWithTag(SHEET_FRAME_TAG)
    target.captureRoboImage(prototypeScreenshot(name))
}

/** Every step of every scenario, as the sheet shows it. */
@Suppress("LongMethod") // One flat list of every step, in the order of the flows.
internal fun sheetShots(): List<SheetShot> {
    val now = FIXED_NOW
    val codex = ScenarioAccounts.codexAccount(now)
    val codexPool = ResetScenarios.codexPool(now, 2).pools.single()
    val grok = ScenarioAccounts.grokAccount(now)
    val grokPool = ResetScenarios.grokPool(now).pools.single()
    val zai = ScenarioAccounts.zaiAccount(now)
    val zaiPools = ResetScenarios.zaiPools(now, fiveHour = 2, week = 1).pools
    val zaiEmpty = ResetScenarios.zaiPools(now, fiveHour = 0, week = 0).pools.first()
    val claude = ScenarioAccounts.claudeAccount(now)
    val grant = ResetScenarios.claudeGrants(now).pools.first()
    val anyTime = ResetScenarios.claudeAnyTime(now).pools.single()
    val waiting = ResetScenarios.claudeWaiting(now).pools.single()
    return listOf(
        SheetShot("codex-confirm", codex, RedeemStep.Confirm(codexPool, false)),
        SheetShot("codex-resetting", codex, RedeemStep.Resetting(codexPool)),
        SheetShot(
            "codex-success",
            codex.afterReset(codexPool),
            RedeemStep.Finished(codexPool, RedeemOutcome.Success(1)),
        ),
        SheetShot(
            "codex-nothing-to-reset",
            codex,
            RedeemStep.Finished(codexPool, RedeemOutcome.NothingToReset),
        ),
        SheetShot("codex-no-credit", codex, RedeemStep.Finished(codexPool, RedeemOutcome.NoCredit)),
        SheetShot(
            "grok-failed",
            grok,
            RedeemStep.Finished(grokPool, RedeemOutcome.Failed(QuotaErrorKind.Network)),
        ),
        SheetShot(
            "grok-success",
            grok.afterReset(grokPool),
            RedeemStep.Finished(grokPool, RedeemOutcome.Success(0)),
        ),
        SheetShot("zai-choose", zai, RedeemStep.ChoosePool(zaiPools)),
        SheetShot(
            "zai-confirm",
            zai,
            RedeemStep.Confirm(zaiPools.first(), canGoBack = true),
        ),
        SheetShot(
            "zai-success",
            zai.afterReset(zaiPools.first()),
            RedeemStep.Finished(zaiPools.first(), RedeemOutcome.Success(2)),
        ),
        SheetShot(
            "zai-no-credit-ask",
            zai,
            RedeemStep.Finished(zaiEmpty, RedeemOutcome.NoCredit),
            canAskForMore = true,
        ),
        SheetShot("zai-asking", zai, RedeemStep.Asking),
        SheetShot("zai-ask-granted", zai, RedeemStep.Answered(AskOutcome.Granted("five_hour"))),
        SheetShot(
            "zai-ask-not-yet",
            zai,
            RedeemStep.Answered(AskOutcome.NotYet(now.plus(Duration.ofHours(6)))),
        ),
        SheetShot("zai-ask-throttled", zai, RedeemStep.Answered(AskOutcome.Throttled)),
        SheetShot("zai-sign-in", zai, RedeemStep.SignInRequired),
        SheetShot(
            "zai-sign-in-tapped",
            zai,
            RedeemStep.SignInRequired,
            signInNote = "Sign in to ZCode in your browser. Headroom carries on when you are done.",
        ),
        SheetShot("claude-confirm", claude, RedeemStep.Confirm(grant, false)),
        SheetShot("claude-resetting", claude, RedeemStep.Resetting(grant)),
        SheetShot(
            "claude-updating",
            claude,
            RedeemStep.Finished(grant, RedeemOutcome.Success(3)),
            refreshing = true,
        ),
        SheetShot(
            "claude-success",
            claude.afterReset(grant),
            RedeemStep.Finished(grant, RedeemOutcome.Success(3)),
        ),
        SheetShot(
            "claude-resetting-reduced-motion",
            claude,
            RedeemStep.Resetting(grant),
            reduceMotion = true,
        ),
        SheetShot(
            "claude-updating-reduced-motion",
            claude,
            RedeemStep.Finished(grant, RedeemOutcome.Success(3)),
            refreshing = true,
            reduceMotion = true,
        ),
        SheetShot("claude-cooldown", claude, RedeemStep.Finished(grant, RedeemOutcome.Cooldown)),
        SheetShot(
            "claude-not-limited",
            claude,
            RedeemStep.Finished(grant, RedeemOutcome.NothingToReset),
        ),
        SheetShot(
            "claude-unconfirmed",
            claude,
            RedeemStep.Finished(grant, RedeemOutcome.Unconfirmed),
        ),
        SheetShot("claude-checking", claude, RedeemStep.Checking(grant)),
        SheetShot(
            "claude-confirmed",
            claude.afterReset(grant),
            RedeemStep.Finished(grant, RedeemOutcome.Success(3, replayed = true)),
        ),
        SheetShot(
            "claude-any-time",
            claude,
            RedeemStep.Confirm(anyTime, false),
        ),
        SheetShot(
            "claude-needs-limit",
            claude,
            RedeemStep.Confirm(waiting, false),
        ),
        SheetShot(
            "claude-rate-limited",
            claude,
            RedeemStep.Finished(
                grant,
                RedeemOutcome.RateLimited(now.plus(Duration.ofMinutes(2))),
            ),
        ),
        SheetShot(
            "claude-sign-in-again",
            claude,
            RedeemStep.Finished(grant, RedeemOutcome.SignInAgain),
        ),
        SheetShot(
            "claude-ineligible",
            claude,
            RedeemStep.Finished(grant, RedeemOutcome.Ineligible),
        ),
    )
}
