package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.InMemorySettingsRepository
import dev.sebastiano.headroom.ui.settings.RESET_EXPIRY_REMINDERS_TAG
import dev.sebastiano.headroom.ui.settings.SETTINGS_LIST_TAG
import kotlin.test.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings turns off the reminder that a reset expires soon. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ResetReminderSettingsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val settings = InMemorySettingsRepository()

    private fun openReminderRow() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                HeadroomApp(
                    graph =
                        testGraph(
                            rule.activity,
                            realAccounts = DemoData.accounts(FIXED_NOW),
                            settings = settings,
                        )
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.waitForIdle()
        rule
            .onNodeWithTag(SETTINGS_LIST_TAG)
            .performScrollToNode(hasTestTag(RESET_EXPIRY_REMINDERS_TAG))
    }

    @Test
    fun `the reminder switch is on, with a note`() {
        openReminderRow()

        rule.onNodeWithTag(RESET_EXPIRY_REMINDERS_TAG).assertIsOn()
        rule.onNodeWithText("Remind me before a reset expires").assertIsDisplayed()
        rule.onNodeWithText("At most one a day", substring = true).assertIsDisplayed()
    }

    @Test
    fun `the switch turns reminders off`() {
        openReminderRow()

        rule.onNodeWithTag(RESET_EXPIRY_REMINDERS_TAG).performClick()
        rule.waitForIdle()

        rule.onNodeWithTag(RESET_EXPIRY_REMINDERS_TAG).assertIsOff()
        assertFalse(settings.settings.value.resetExpiryReminders)
    }
}
