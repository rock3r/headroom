package dev.sebastiano.headroom.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.ui.settings.RowAction
import dev.sebastiano.headroom.ui.settings.SettingsRow
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A settings row behaves like the Material list item it replaces, for taps and screen readers. */
@RunWith(RobolectricTestRunner::class)
class SettingsRowTest {
    @get:Rule val rule = createComposeRule()

    private fun show(action: RowAction) {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                SettingsRow(
                    headline = "Every hour",
                    action = action,
                    index = 0,
                    count = 1,
                    supporting = "Checks in the background",
                    modifier = Modifier.testTag(ROW),
                )
            }
        }
    }

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    @Test
    fun `a selectable row is a radio button that reports whether it is selected`() {
        var picked = 0
        show(RowAction.Select(selected = false) { picked++ })
        rule.onNodeWithTag(ROW).assert(hasRole(Role.RadioButton)).assertIsNotSelected()
        rule.onNodeWithTag(ROW).performClick()
        assertEquals(1, picked)
    }

    @Test
    fun `a selected row says so`() {
        show(RowAction.Select(selected = true) {})
        rule.onNodeWithTag(ROW).assertIsSelected()
    }

    @Test
    fun `a toggle row is a switch that reports whether it is on`() {
        var checked: Boolean? = null
        show(RowAction.Toggle(checked = true) { checked = it })
        rule.onNodeWithTag(ROW).assert(hasRole(Role.Switch)).assertIsOn()
        rule.onNodeWithTag(ROW).performClick()
        assertEquals(false, checked)
    }

    @Test
    fun `a toggle row that is off says so`() {
        show(RowAction.Toggle(checked = false) {})
        rule.onNodeWithTag(ROW).assertIsOff()
    }

    @Test
    fun `a plain row is a button, and its text is merged into it`() {
        var clicks = 0
        show(RowAction.Click { clicks++ })
        rule.onNodeWithTag(ROW).assert(hasRole(Role.Button))
        rule.onNodeWithText("Checks in the background", useUnmergedTree = false).performClick()
        assertEquals(1, clicks)
    }

    private companion object {
        const val ROW = "row"
    }
}
