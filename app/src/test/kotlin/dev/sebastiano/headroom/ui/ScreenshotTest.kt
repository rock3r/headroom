package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import com.github.takahirom.roborazzi.captureRoboImage
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.AppSettings
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.InMemorySettingsRepository
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.SettingsRepository
import dev.sebastiano.headroom.signin.FakeSignInController
import dev.sebastiano.headroom.signin.SignInController
import dev.sebastiano.headroom.signin.SignInState
import dev.sebastiano.headroom.ui.accounts.ACCOUNTS_TAG
import dev.sebastiano.headroom.ui.accounts.accountRowTag
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

    private fun launch(
        dark: Boolean = false,
        signIn: SignInController = FakeSignInController(),
        realAccounts: List<AccountState> = emptyList(),
        settings: SettingsRepository = InMemorySettingsRepository(),
    ) {
        // Frames are driven by capture(), so that animations are drawn while they run.
        rule.mainClock.autoAdvance = false
        rule.setContent {
            HeadroomTheme(darkTheme = dark, dynamicColor = false) {
                HeadroomApp(
                    graph =
                        testGraph(
                            rule.activity,
                            realAccounts = realAccounts,
                            signInController = signIn,
                            settings = settings,
                        )
                )
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
    fun detailScrolled() {
        launch()
        // A status bar, so the detail scrolls under it and the blur behind it shows.
        rule.runOnUiThread { rule.activity.giveStatusBar(STATUS_BAR_PX) }
        // With the clock paused, the scroll never goes idle once the blur runs, so let it run.
        rule.mainClock.autoAdvance = true
        rule.onNodeWithTag(accountCardTag("demo-claude")).performClick()
        rule.onNodeWithText("Session limits never send alerts.").performScrollTo()
        rule.mainClock.autoAdvance = false
        capture("detail-scrolled")
    }

    @Test
    fun overviewScrolledDark() {
        launch(dark = true)
        rule.runOnUiThread { rule.activity.giveStatusBar(STATUS_BAR_PX) }
        rule.mainClock.autoAdvance = true
        rule.onNodeWithTag(OVERVIEW_LIST_TAG).performScrollToIndex(GROK_INDEX)
        rule.mainClock.autoAdvance = false
        capture("overview-scrolled-dark")
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
        rule.openAccounts()
        capture("accounts")
    }

    @Test
    fun accountEditing() {
        // Demo accounts cannot be edited, so these are the demo accounts signed in for real.
        val accounts =
            DemoData.accounts(FIXED_NOW).map {
                if (it.account.id == "demo-codex")
                    it.copy(account = it.account.copy(nickname = "Work"))
                else it
            }
        launch(realAccounts = accounts)
        rule.mainClock.autoAdvance = true
        rule.openAccounts()
        rule.onNodeWithTag(accountRowTag("demo-codex")).performClick()
        rule.mainClock.autoAdvance = false
        capture("accounts-editing")
    }

    @Test
    fun settings() {
        launch()
        rule.onNodeWithContentDescription("Settings").performClick()
        capture("settings")
    }

    @Test
    fun licences() {
        launch()
        // Two steps in a row, and the licence list loads off the main thread: let the clock run.
        rule.mainClock.autoAdvance = true
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Open-source licences"))
        rule.onNodeWithText("Open-source licences").performClick()
        rule.waitUntil(LOAD_TIMEOUT_MILLIS) {
            rule.onAllNodesWithText("Activity").fetchSemanticsNodes().isNotEmpty()
        }
        rule.mainClock.autoAdvance = false
        capture("licences")
    }

    @Test
    fun overviewLeft() {
        launch(settings = InMemorySettingsRepository(AppSettings(quotaDisplay = QuotaDisplay.Left)))
        capture("overview-left")
    }

    @Test
    fun signInDeviceCode() {
        launch()
        // Several steps in a row: let the clock run between them.
        rule.mainClock.autoAdvance = true
        rule.openAccounts()
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
        rule.openAccounts()
        rule.onNodeWithText("Add account").performClick()
        rule.onNodeWithTag(providerOptionTag(Provider.Claude)).performClick()
        capture("sign-in-browser")
    }

    @Test
    fun signInFinishing() {
        // The clock stays with capture(), so the stream of shapes is drawn part-way through.
        launch(signIn = FakeSignInController(SignInState.Finishing(Provider.Claude)))
        rule.openAccounts()
        capture("sign-in-finishing")
    }

    @Test
    fun signInFinishingDark() {
        launch(dark = true, signIn = FakeSignInController(SignInState.Finishing(Provider.Grok)))
        rule.openAccounts()
        capture("sign-in-finishing-dark")
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
/** The licence data is read from resources off the main thread. */
private const val LOAD_TIMEOUT_MILLIS = 5_000L
/** 24dp at xxhdpi, the height of a phone status bar. */
private const val STATUS_BAR_PX = 72
private const val PHONE = "w411dp-h891dp-xxhdpi"
private const val MEDIUM = "w700dp-h1000dp-xhdpi"
private const val EXPANDED = "w1280dp-h800dp-xhdpi"

/** Where the Roborazzi plugin wants screenshots: `docs/screenshots` for this project. */
private fun screenshot(name: String): String {
    val dir = System.getProperty("roborazzi.output.dir") ?: "build/outputs/roborazzi"
    return "$dir/$name.png"
}
