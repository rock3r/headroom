package dev.sebastiano.headroom.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.AppSettings
import dev.sebastiano.headroom.model.InMemorySettingsRepository
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.ui.settings.RESET_ISLAND_SETUP_TAG
import dev.sebastiano.headroom.ui.settings.RESET_ISLAND_TAG
import dev.sebastiano.headroom.ui.settings.SETTINGS_LIST_TAG
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The "Reset island (experimental)" row, its Try button and the guided set-up. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ResetIslandSettingsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var settings = InMemorySettingsRepository()
    private val island = FakeResetIslandAccess()

    private fun openRow(on: Boolean = false) {
        if (on) settings = InMemorySettingsRepository(AppSettings(resetIsland = true))
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                HeadroomApp(
                    graph = testGraph(rule.activity, settings = settings, resetIsland = island)
                )
            }
        }
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithTag(SETTINGS_LIST_TAG).performScrollToNode(hasTestTag(RESET_ISLAND_TAG))
    }

    private fun nextIntent(): Intent? = shadowOf(rule.activity.application).nextStartedActivity

    @Test
    fun `the row is off by default, and says so`() {
        openRow()
        rule.onNodeWithText("Reset island (experimental)").assertIsDisplayed()
        rule.onNodeWithTag(RESET_ISLAND_TAG).assertIsOff()
        rule.onNodeWithText("Off").assertIsDisplayed()
    }

    @Test
    fun `with the switch on and no way to draw, the row needs permission`() {
        openRow(on = true)
        rule.onNodeWithTag(RESET_ISLAND_TAG).assertIsOn()
        rule.onNodeWithText("Needs permission").assertIsDisplayed()
    }

    @Test
    fun `with the switch on and the service connected, the row is ready through accessibility`() {
        island.readyState.value = true
        openRow(on = true)
        rule.onNodeWithText("Ready (accessibility)").assertIsDisplayed()
    }

    @Test
    fun `with the switch on and display over other apps allowed, the row says so`() {
        island.overlayState.value = true
        openRow(on = true)
        rule.onNodeWithText("Ready (display over other apps)").assertIsDisplayed()
    }

    @Test
    fun `the service is the better mode when both are there`() {
        island.readyState.value = true
        island.overlayState.value = true
        openRow(on = true)
        rule.onNodeWithText("Ready (accessibility)").assertIsDisplayed()
        rule.onNodeWithText("Ready (display over other apps)").assertDoesNotExist()
    }

    @Test
    fun `with the switch off the row says Off, even when a mode is available`() {
        island.overlayState.value = true
        openRow(on = false)
        rule.onNodeWithText("Off").assertIsDisplayed()
        rule.onNodeWithText("Ready (display over other apps)").assertDoesNotExist()
    }

    @Test
    fun `when the service is turned off later, the row needs permission again`() {
        island.readyState.value = true
        openRow(on = true)
        rule.onNodeWithText("Ready (accessibility)").assertIsDisplayed()

        island.readyState.value = false
        rule.waitForIdle()

        rule.onNodeWithText("Needs permission").assertIsDisplayed()
        rule.onNodeWithText("Ready (accessibility)").assertDoesNotExist()
    }

    @Test
    fun `when the service goes away but display over other apps is allowed, the row falls back`() {
        island.readyState.value = true
        island.overlayState.value = true
        openRow(on = true)

        island.readyState.value = false
        rule.waitForIdle()

        rule.onNodeWithText("Ready (display over other apps)").assertIsDisplayed()
    }

    @Test
    fun `Try is disabled while there is no way to draw`() {
        openRow(on = true)
        rule.onNodeWithContentDescription("Try the reset island").assertIsNotEnabled()
    }

    @Test
    fun `Try is enabled once the service is connected, and shows the demo`() {
        island.readyState.value = true
        openRow(on = true)
        rule.onNodeWithContentDescription("Try the reset island").assertIsEnabled().performClick()
        assertEquals(listOf(Provider.Claude to "Claude weekly limit reset"), island.demos)
    }

    @Test
    fun `Try works even with the switch off, as long as the service is connected`() {
        island.readyState.value = true
        openRow(on = false)
        rule.onNodeWithText("Off").assertIsDisplayed()
        rule.onNodeWithContentDescription("Try the reset island").assertIsEnabled().performClick()
        assertEquals(1, island.demos.size)
    }

    @Test
    fun `Try works through display over other apps when the service is not connected`() {
        island.overlayState.value = true
        openRow(on = true)
        rule.onNodeWithContentDescription("Try the reset island").assertIsEnabled().performClick()
        assertEquals(listOf(Provider.Claude to "Claude weekly limit reset"), island.demos)
    }

    @Test
    fun `the row reads the accessibility settings when the user comes back`() {
        openRow()
        assertTrue(island.refreshes > 0)
    }

    @Test
    fun `turning the switch on without the service opens the set-up and stores the choice`() {
        openRow()
        rule.onNodeWithTag(RESET_ISLAND_TAG).performClick()

        rule.onNodeWithTag(RESET_ISLAND_SETUP_TAG).assertIsDisplayed()
        rule.onNodeWithText("Turn on the reset island").assertIsDisplayed()
        assertTrue(settings.settings.value.resetIsland)
    }

    @Test
    fun `turning the switch on with the service connected needs no set-up`() {
        island.readyState.value = true
        openRow()
        rule.onNodeWithTag(RESET_ISLAND_TAG).performClick()

        rule.onNodeWithTag(RESET_ISLAND_SETUP_TAG).assertDoesNotExist()
        assertTrue(settings.settings.value.resetIsland)
        rule.onNodeWithText("Ready (accessibility)").assertIsDisplayed()
    }

    @Test
    fun `turning the switch on with display over other apps allowed needs no set-up`() {
        island.overlayState.value = true
        openRow()
        rule.onNodeWithTag(RESET_ISLAND_TAG).performClick()

        rule.onNodeWithTag(RESET_ISLAND_SETUP_TAG).assertDoesNotExist()
        assertTrue(settings.settings.value.resetIsland)
    }

    @Test
    fun `turning the switch off needs no set-up`() {
        openRow(on = true)
        rule.onNodeWithTag(RESET_ISLAND_TAG).performClick()
        rule.onNodeWithTag(RESET_ISLAND_SETUP_TAG).assertDoesNotExist()
        assertFalse(settings.settings.value.resetIsland)
    }

    @Test
    fun `the set-up says what the island is, why it needs access, and what it does not do`() {
        openRow()
        rule.onNodeWithTag(RESET_ISLAND_TAG).performClick()

        rule
            .onNodeWithText("Why it needs accessibility access")
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithText("What it does not do").performScrollTo().assertIsDisplayed()
        rule
            .onNodeWithText("It does not read your screen.", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithText("It does not read other apps.", substring = true).assertIsDisplayed()
    }

    @Test
    fun `an app not installed from an APK file only needs the service turned on`() {
        // As adb installs it.
        shadowOf(rule.activity.packageManager)
            .setInstallSourceInfo(rule.activity.packageName, "com.android.shell", null)
        openRow()
        rule.onNodeWithTag(RESET_ISLAND_TAG).performClick()

        rule.onNodeWithText("Open App info").assertDoesNotExist()
        rule.onNodeWithText("Open accessibility settings").performScrollTo().performClick()

        val intent = nextIntent()!!
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, intent.action)
        assertEquals(
            "${rule.activity.packageName}/dev.sebastiano.headroom.island.ResetIslandService",
            intent.getStringExtra(":settings:fragment_args_key"),
        )
    }

    @Test
    fun `the set-up keeps accessibility as the way and adds display over other apps as another`() {
        openRow()
        rule.onNodeWithTag(RESET_ISLAND_TAG).performClick()

        val accessibility =
            rule.onNodeWithText("Turn on the service").performScrollTo().assertIsDisplayed()
        val overlay =
            rule
                .onNodeWithText(
                    "Blocked by your device's admin? Use Display over other apps instead"
                )
                .performScrollTo()
                .assertIsDisplayed()
        assertTrue(
            accessibility.fetchSemanticsNode().positionInRoot.y <
                overlay.fetchSemanticsNode().positionInRoot.y
        )
    }

    @Test
    fun `the set-up says what display over other apps cannot do`() {
        openRow()
        rule.onNodeWithTag(RESET_ISLAND_TAG).performClick()

        rule
            .onNodeWithText("The pill shows below the status bar", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        rule
            .onNodeWithText("It does not show on the lock screen.", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        rule
            .onNodeWithText("You can tap it to open Headroom", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `the display over other apps button opens Headroom's page for that permission`() {
        openRow()
        rule.onNodeWithTag(RESET_ISLAND_TAG).performClick()
        rule.onNodeWithText("Allow display over other apps").performScrollTo().performClick()

        val intent = nextIntent()!!
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, intent.action)
        assertEquals("package:${rule.activity.packageName}", intent.dataString)
    }

    @Test
    fun `when display over other apps is allowed the set-up shows success and offers Try it`() {
        openRow()
        rule.onNodeWithTag(RESET_ISLAND_TAG).performClick()
        rule.onNodeWithText("The reset island is ready").assertDoesNotExist()

        island.overlayState.value = true
        rule.waitForIdle()

        rule.onNodeWithText("The reset island is ready").assertIsDisplayed()
        rule.onNodeWithText("It shows below the status bar", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Allow display over other apps").assertDoesNotExist()
        rule.onNodeWithText("Try it").performClick()
        assertEquals(listOf(Provider.Claude to "Claude weekly limit reset"), island.demos)
    }

    @Test
    fun `nothing is started until a step button is tapped`() {
        openRow()
        rule.onNodeWithTag(RESET_ISLAND_TAG).performClick()
        assertNull(nextIntent())
    }

    @Test
    fun `when the service connects the set-up shows success and offers Try it`() {
        openRow()
        rule.onNodeWithTag(RESET_ISLAND_TAG).performClick()
        rule.onNodeWithText("The reset island is ready").assertDoesNotExist()

        island.readyState.value = true
        rule.waitForIdle()

        rule.onNodeWithText("The reset island is ready").assertIsDisplayed()
        rule.onNodeWithText("Open App info").assertDoesNotExist()
        rule.onNodeWithText("Try it").performClick()
        assertEquals(listOf(Provider.Claude to "Claude weekly limit reset"), island.demos)
    }

    @Test
    fun `when Android is still starting the service the set-up says so`() {
        openRow()
        rule.onNodeWithTag(RESET_ISLAND_TAG).performClick()
        island.enabledState.value = true
        rule.waitForIdle()

        rule
            .onNodeWithText("Almost there. Android is starting the service.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `Done closes the set-up and the switch stays on`() {
        island.readyState.value = true
        island.enabledState.value = true
        openRow()
        island.readyState.value = false
        rule.onNodeWithTag(RESET_ISLAND_TAG).performClick()
        island.readyState.value = true
        rule.waitForIdle()

        rule.onNodeWithText("Done").performClick()

        rule.onNodeWithTag(RESET_ISLAND_SETUP_TAG).assertDoesNotExist()
        rule.onNodeWithTag(RESET_ISLAND_TAG).assertIsOn()
    }
}
