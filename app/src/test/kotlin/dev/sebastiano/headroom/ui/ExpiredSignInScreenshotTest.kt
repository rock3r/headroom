package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.AppSettings
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.InMemorySettingsRepository
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.ThemePalette
import dev.sebastiano.headroom.ui.overview.accountCardTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pictures of an expired sign-in, recorded with `./gradlew :app:recordRoborazziDebug` into
 * `docs/screenshots`, named `expired-sign-in-*`. Claude's sign-in expired two hours ago; the other
 * demo accounts are fresh.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ExpiredSignInScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun launch(
        dark: Boolean = false,
        palette: ThemePalette = ThemePalette.Wallpaper,
        display: QuotaDisplay = QuotaDisplay.Used,
    ) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            HeadroomTheme(darkTheme = dark, dynamicColor = false, palette = palette) {
                HeadroomApp(
                    graph =
                        testGraph(
                            rule.activity,
                            realAccounts = DemoData.accountsWithExpiredSignIn(FIXED_NOW),
                            settings =
                                InMemorySettingsRepository(AppSettings(quotaDisplay = display)),
                        )
                )
            }
        }
    }

    private fun settle() {
        repeat(SETTLE_STEPS) {
            rule.mainClock.advanceTimeBy(STEP_MILLIS)
            rule.onRoot().captureToImage()
        }
    }

    private fun capture(name: String) {
        settle()
        rule.onRoot().captureRoboImage(screenshot(name))
    }

    private fun captureCard(name: String) {
        settle()
        rule.onNodeWithTag(CLAUDE_CARD).captureRoboImage(screenshot(name))
    }

    private fun openDetail() {
        settle()
        rule.onNodeWithTag(CLAUDE_CARD).performClick()
    }

    @Test fun overview() = launch().also { capture("overview") }

    @Test fun overviewDark() = launch(dark = true).also { capture("overview-dark") }

    @Test fun card() = launch().also { captureCard("card") }

    @Test fun cardDark() = launch(dark = true).also { captureCard("card-dark") }

    @Test fun cardLeft() = launch(display = QuotaDisplay.Left).also { captureCard("card-left") }

    @Test
    fun cardTangerineDark() =
        launch(dark = true, palette = ThemePalette.Tangerine).also {
            captureCard("card-tangerine-dark")
        }

    @Test
    fun detail() {
        launch()
        openDetail()
        capture("detail")
    }

    @Test
    fun detailDark() {
        launch(dark = true)
        openDetail()
        capture("detail-dark")
    }

    /** An unfolded foldable: two columns of cards. */
    @Test @Config(qualifiers = MEDIUM) fun medium() = launch().also { capture("medium") }

    /** A tablet: the list and the detail with its banner side by side. */
    @Test
    @Config(qualifiers = EXPANDED)
    fun expanded() {
        launch()
        openDetail()
        capture("expanded")
    }

    @Test
    @Config(qualifiers = EXPANDED)
    fun expandedDark() {
        launch(dark = true)
        openDetail()
        capture("expanded-dark")
    }

    private companion object {
        val CLAUDE_CARD = accountCardTag("demo-claude")
        const val SETTLE_STEPS = 40
        const val STEP_MILLIS = 50L
        const val MEDIUM = "w700dp-h1000dp-xhdpi"
        const val EXPANDED = "w1280dp-h800dp-xhdpi"

        fun screenshot(name: String): String {
            val dir = System.getProperty("roborazzi.output.dir") ?: "build/outputs/roborazzi"
            return "$dir/expired-sign-in-$name.png"
        }
    }
}
