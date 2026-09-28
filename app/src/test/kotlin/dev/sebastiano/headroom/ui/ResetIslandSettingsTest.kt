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
    fun `with the switch on and no service, the row needs accessibility access`() {
        openRow(on = true)
        rule.onNodeWithTag(RESET_ISLAND_TAG).assertIsOn()
        rule.onNodeWithText("Needs accessibility access").assertIsDisplayed()
    }

    @Test
    fun `with the switch on and the service connected, the row is ready`() {
        island.readyState.value = true
        openRow(on = true)
        rule.onNodeWithText("Ready").assertIsDisplayed()
    }

    @Test
    fun `when the service is turned off later, the row needs access again`() {
        island.readyState.value = true
        openRow(on = true)
        rule.onNodeWithText("Ready").assertIsDisplayed()

        island.readyState.value = false
        rule.waitForIdle()

        rule.onNodeWithText("Needs accessibility access").assertIsDisplayed()
        rule.onNodeWithText("Ready").assertDoesNotExist()
    }

    @Test
    fun `Try is disabled while the service is not connected`() {
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
        rule.onNodeWithText("Ready").assertIsDisplayed()
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
    fun `step 1 opens the app's own App info`() {
        openRow()
        rule.onNodeWithTag(RESET_ISLAND_TAG).performClick()
        rule.onNodeWithText("Open App info").performScrollTo().performClick()

        val intent = nextIntent()!!
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:${rule.activity.packageName}", intent.dataString)
    }

    @Test
    fun `step 2 opens the service's page in accessibility settings`() {
        openRow()
        rule.onNodeWithTag(RESET_ISLAND_TAG).performClick()
        rule.onNodeWithText("Open accessibility settings").performScrollTo().performClick()

        val intent = nextIntent()!!
        assertEquals("android.settings.ACCESSIBILITY_DETAILS_SETTINGS", intent.action)
        assertEquals(
            "${rule.activity.packageName}/dev.sebastiano.headroom.island.ResetIslandService",
            intent.getStringExtra(Intent.EXTRA_COMPONENT_NAME),
        )
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
