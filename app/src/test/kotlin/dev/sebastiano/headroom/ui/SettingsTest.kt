package dev.sebastiano.headroom.ui

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.InMemorySettingsRepository
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.SyncFrequency
import dev.sebastiano.headroom.ui.accounts.ACCOUNTS_TAG
import dev.sebastiano.headroom.ui.overview.OVERVIEW_LIST_TAG
import dev.sebastiano.headroom.ui.overview.accountCardTag
import dev.sebastiano.headroom.ui.settings.LICENCES_LIST_TAG
import dev.sebastiano.headroom.ui.settings.LICENCES_TAG
import dev.sebastiano.headroom.ui.settings.SETTINGS_ACCOUNTS_TAG
import dev.sebastiano.headroom.ui.settings.SETTINGS_LIST_TAG
import dev.sebastiano.headroom.ui.settings.SETTINGS_TAG
import dev.sebastiano.headroom.ui.settings.addWidgetTag
import dev.sebastiano.headroom.ui.settings.syncFrequencyTag
import dev.sebastiano.headroom.widgets.WidgetPinner
import dev.sebastiano.headroom.widgets.WidgetStyle
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class SettingsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val settings = InMemorySettingsRepository()

    private val pinned = mutableListOf<WidgetStyle>()
    private var pinWorks = true
    private val pinner = WidgetPinner { style ->
        pinned += style
        pinWorks
    }

    private val dispatcher
        get() = rule.activity.onBackPressedDispatcher

    private fun openSettings() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                HeadroomApp(
                    graph = testGraph(rule.activity, settings = settings, widgetPinner = pinner)
                )
            }
        }
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithTag(SETTINGS_TAG).assertIsDisplayed()
    }

    @Test
    fun `settings opens with its title and a close button, and the close button closes it`() {
        openSettings()
        rule.onNode(hasText("Settings") and isHeading()).assertIsDisplayed()
        rule.onNodeWithContentDescription("Close settings").assertIsDisplayed()
        // The close button takes the place of the back arrow.
        rule.onNodeWithContentDescription("Back").assertDoesNotExist()

        rule.onNodeWithContentDescription("Close settings").performClick()
        rule.onNodeWithTag(OVERVIEW_LIST_TAG).assertIsDisplayed()
        rule.onNodeWithTag(SETTINGS_TAG).assertDoesNotExist()
        rule.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }

    @Test
    fun `system back closes settings`() {
        openSettings()
        rule.runOnUiThread { dispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithTag(OVERVIEW_LIST_TAG).assertIsDisplayed()
        rule.onNodeWithTag(SETTINGS_TAG).assertDoesNotExist()
    }

    @Test
    fun `the back gesture scrubs settings away and cancelling keeps it`() {
        openSettings()
        rule.runOnUiThread {
            dispatcher.dispatchOnBackStarted(gesture(0f))
            dispatcher.dispatchOnBackProgressed(gesture(0.5f))
        }
        rule.waitForIdle()
        rule.onNodeWithTag(SETTINGS_TAG).assertExists()
        rule.onNodeWithTag(OVERVIEW_LIST_TAG).assertExists()

        rule.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        rule.waitForIdle()
        rule.onNodeWithTag(SETTINGS_TAG).assertIsDisplayed()

        rule.runOnUiThread { dispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithTag(OVERVIEW_LIST_TAG).assertIsDisplayed()
        rule.onNodeWithTag(SETTINGS_TAG).assertDoesNotExist()
    }

    @Test
    fun `switching to left shows what is left on the overview cards`() {
        openSettings()
        rule.onNodeWithText("Used").assertIsSelected()
        rule.onNodeWithText("Left").performClick()
        rule.onNodeWithText("Left").assertIsSelected()
        assertEquals(QuotaDisplay.Left, settings.settings.value.quotaDisplay)

        rule.onNodeWithContentDescription("Close settings").performClick()

        rule
            .onNodeWithTag(accountCardTag("demo-claude"))
            .assert(hasText("29%"))
            .assert(hasText("weekly left"))
    }

    @Test
    fun `choosing a frequency stores it and selects its row`() {
        openSettings()
        rule
            .onNodeWithTag(SETTINGS_LIST_TAG)
            .performScrollToNode(hasTestTag(syncFrequencyTag(SyncFrequency.Minutes15)))
        rule.onNodeWithTag(syncFrequencyTag(SyncFrequency.Minutes15)).assertIsSelected()

        rule.onNodeWithTag(syncFrequencyTag(SyncFrequency.Hours3)).performScrollTo().performClick()

        rule.onNodeWithTag(syncFrequencyTag(SyncFrequency.Hours3)).assertIsSelected()
        rule.onNodeWithTag(syncFrequencyTag(SyncFrequency.Minutes15)).assertIsNotSelected()
        assertEquals(SyncFrequency.Hours3, settings.settings.value.syncFrequency)
    }

    @Test
    fun `the licences open from settings and back returns to settings`() {
        openSettings()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Open-source licences"))
        rule.onNodeWithText("Open-source licences").performClick()
        rule.onNodeWithTag(LICENCES_TAG).assertIsDisplayed()
        // The list comes from the licence data the build bundles into the app.
        val material3 = "Compose Material3 Components"
        rule.waitUntil(LOAD_TIMEOUT_MILLIS) {
            runCatching {
                rule.onNodeWithTag(LICENCES_LIST_TAG).performScrollToNode(hasText(material3))
            }
                .isSuccess
        }
        rule.onAllNodesWithText(material3).onFirst().assertIsDisplayed()

        rule.runOnUiThread { dispatcher.onBackPressed() }
        rule.waitForIdle()

        rule.onNodeWithTag(SETTINGS_TAG).assertIsDisplayed()
        rule.onNodeWithTag(LICENCES_TAG).assertDoesNotExist()
    }

    @Test
    fun `accounts live in settings, and back from them returns to settings`() {
        openSettings()
        rule.onNodeWithContentDescription("Accounts").assertDoesNotExist()
        rule.onNodeWithTag(SETTINGS_ACCOUNTS_TAG).assertIsDisplayed()

        rule.onNodeWithTag(SETTINGS_ACCOUNTS_TAG).performClick()
        rule.onNodeWithTag(ACCOUNTS_TAG).assertIsDisplayed()
        rule.onNodeWithContentDescription("Back").performClick()

        rule.onNodeWithTag(SETTINGS_TAG).assertIsDisplayed()
    }

    @Test
    fun `the demo banner still adds an account in one tap, and back returns home`() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                HeadroomApp(graph = testGraph(rule.activity, settings = settings))
            }
        }
        rule.onNodeWithText("Add account").performClick()
        rule.onNodeWithTag(ACCOUNTS_TAG).assertIsDisplayed()
        rule.onNodeWithContentDescription("Back").performClick()

        rule.onNodeWithTag(OVERVIEW_LIST_TAG).assertIsDisplayed()
    }

    @Test
    fun `the app version is at the bottom of settings`() {
        openSettings()
        val version = "Version $TEST_APP_VERSION"
        rule.onNodeWithTag(SETTINGS_LIST_TAG).performScrollToNode(hasText(version))
        rule.onNodeWithText(version).assertIsDisplayed()
    }

    @Test
    fun `settings lists every widget style, and adding one asks the pinner for it`() {
        openSettings()
        WidgetStyle.entries.forEach { style ->
            rule
                .onNodeWithTag(SETTINGS_LIST_TAG)
                .performScrollToNode(hasTestTag(addWidgetTag(style)))
            rule.onNodeWithTag(addWidgetTag(style)).assertIsDisplayed()
        }
        rule
            .onNodeWithContentDescription("Add the Bars · 4×1 to 4×3 widget to the home screen")
            .performClick()
        assertEquals(listOf(WidgetStyle.Bars), pinned)
    }

    @Test
    fun `when the launcher cannot pin a widget, settings says so`() {
        pinWorks = false
        openSettings()
        rule
            .onNodeWithTag(SETTINGS_LIST_TAG)
            .performScrollToNode(hasTestTag(addWidgetTag(WidgetStyle.Rings)))
        rule.onNodeWithTag(addWidgetTag(WidgetStyle.Rings)).performClick()
        assertEquals(listOf(WidgetStyle.Rings), pinned)
        rule
            .onNodeWithText("Your launcher cannot add widgets from apps", substring = true)
            .assertIsDisplayed()
    }

    private fun gesture(progress: Float) =
        BackEventCompat(0f, 0f, progress, BackEventCompat.EDGE_LEFT)

    private companion object {
        /** The licence data is read from resources off the main thread. */
        const val LOAD_TIMEOUT_MILLIS = 5_000L
    }
}
