package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import com.github.takahirom.roborazzi.captureRoboImage
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.ui.accounts.ACCOUNTS_TAG
import dev.sebastiano.headroom.ui.accounts.providerOptionTag
import dev.sebastiano.headroom.ui.overview.OVERVIEW_LIST_TAG
import dev.sebastiano.headroom.ui.overview.accountCardTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the main screens for the README, recorded with `./gradlew
 * :app:recordRoborazziDebug` into `docs/screenshots`. They use demo data and a fixed clock. They
 * are recordings, not assertions: CI does not compare them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE)
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun launch(dark: Boolean = false) {
        // Frames are driven by capture(), so that animations are drawn while they run.
        rule.mainClock.autoAdvance = false
        rule.setContent {
            HeadroomTheme(darkTheme = dark, dynamicColor = false) {
                HeadroomApp(graph = testGraph(rule.activity))
            }
        }
    }

    private fun capture(name: String) {
        // Robolectric only draws when asked. The wavy ring builds its wave while its sweep-in
        // animation is drawn, so draw every step until transitions and sweeps have settled.
        repeat(SETTLE_STEPS) {
            rule.mainClock.advanceTimeBy(STEP_MILLIS)
            rule.onRoot().captureToImage()
        }
        rule.onRoot().captureRoboImage(screenshot(name))
    }

    @Test
    fun overview() {
        launch()
        capture("overview")
    }

    @Test
    fun overviewDark() {
        launch(dark = true)
        capture("overview-dark")
    }

    @Test
    fun detail() {
        launch()
        rule.onNodeWithTag(accountCardTag("demo-claude")).performClick()
        capture("detail")
    }

    @Test
    fun detailDark() {
        launch(dark = true)
        // Scroll Grok to the top, clear of the floating toolbar, before tapping it.
        rule.onNodeWithTag(OVERVIEW_LIST_TAG).performScrollToIndex(GROK_INDEX)
        rule.onNodeWithTag(accountCardTag("demo-grok")).performClick()
        capture("detail-dark")
    }

    @Test
    fun resets() {
        launch()
        rule.onNodeWithContentDescription("Resets").performClick()
        capture("resets")
    }

    @Test
    fun widgets() {
        launch()
        rule.onNodeWithContentDescription("Widgets").performClick()
        capture("widgets")
    }

    @Test
    fun accounts() {
        launch()
        rule.onNodeWithContentDescription("Accounts").performClick()
        capture("accounts")
    }

    @Test
    fun signInDeviceCode() {
        launch()
        // Several steps in a row: let the clock run between them.
        rule.mainClock.autoAdvance = true
        rule.onNodeWithContentDescription("Accounts").performClick()
        rule.onNodeWithText("Add account").performClick()
        rule
            .onNodeWithTag(ACCOUNTS_TAG)
            .performScrollToNode(hasTestTag(providerOptionTag(Provider.Copilot)))
        rule.onNodeWithTag(providerOptionTag(Provider.Copilot)).performClick()
        capture("sign-in-device-code")
    }

    @Test
    fun signInBrowser() {
        launch()
        // Several steps in a row: let the clock run between them.
        rule.mainClock.autoAdvance = true
        rule.onNodeWithContentDescription("Accounts").performClick()
        rule.onNodeWithText("Add account").performClick()
        rule.onNodeWithTag(providerOptionTag(Provider.Claude)).performClick()
        capture("sign-in-browser")
    }

    @Test
    @Config(qualifiers = MEDIUM)
    fun medium() {
        launch()
        capture("medium")
    }

    @Test
    @Config(qualifiers = EXPANDED)
    fun expanded() {
        launch()
        capture("expanded")
    }

    @Test
    @Config(qualifiers = EXPANDED)
    fun expandedDark() {
        launch(dark = true)
        rule.onNodeWithTag(accountCardTag("demo-codex")).performClick()
        capture("expanded-dark")
    }
}

/** Header, demo banner, next reset, section label, Claude, Codex, then Grok. */
private const val GROK_INDEX = 6
private const val SETTLE_STEPS = 40
private const val STEP_MILLIS = 50L
private const val PHONE = "w411dp-h891dp-xxhdpi"
private const val MEDIUM = "w700dp-h1000dp-xhdpi"
private const val EXPANDED = "w1280dp-h800dp-xhdpi"

/** Where the Roborazzi plugin wants screenshots: `docs/screenshots` for this project. */
private fun screenshot(name: String): String {
    val dir = System.getProperty("roborazzi.output.dir") ?: "build/outputs/roborazzi"
    return "$dir/$name.png"
}
