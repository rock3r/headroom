package dev.sebastiano.headroom.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createComposeRule
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ResetDrainTest {
    @get:Rule val rule = createComposeRule()

    private fun progress(): Float =
        rule
            .onNode(indicatorIn("bar"), useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo]
            .current

    @Test
    fun `a reset drains the bar slowly and settles exactly on the new value`() {
        var value by mutableFloatStateOf(0.88f)
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                QuotaBar(
                    progress = value,
                    animationSpec = HeadroomMotion.resetDrainSpec(),
                    modifier = Modifier.testTag("bar"),
                )
            }
        }
        rule.mainClock.autoAdvance = false
        value = 0f
        rule.mainClock.advanceTimeBy(DATA_SETTLED_MILLIS)
        assertTrue(progress() > 0.1f, "the drain should still be running, was ${progress()}")

        val seen = mutableListOf<Float>()
        repeat(STEPS) {
            rule.mainClock.advanceTimeBy(STEP_MILLIS)
            seen += progress()
        }
        assertEquals(0f, seen.last(), 0.001f)
        assertTrue(seen.all { it >= 0f }, "the bar must never pass its value")
    }

    private companion object {
        /** By now the regular data spring has settled. */
        const val DATA_SETTLED_MILLIS = 300L
        const val STEPS = 60
        const val STEP_MILLIS = 50L
    }
}
