package dev.sebastiano.headroom.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PaceChipTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `each state has its own words`() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                Column {
                    PaceChip(PaceChipState.Over(11))
                    PaceChip(PaceChipState.Under(1))
                    PaceChip(PaceChipState.OnPace)
                    PaceChip(PaceChipState.JustReset)
                }
            }
        }
        rule.onNodeWithText("11 pts over pace").assertExists()
        rule.onNodeWithText("1 pt under pace").assertExists()
        rule.onNodeWithText("On pace").assertExists()
        rule.onNodeWithText("Just reset").assertExists()
    }
}
