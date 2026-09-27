package dev.sebastiano.headroom.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.ComposeUiTestConfig
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private val outerRing = indicatorIn("ring")

@RunWith(RobolectricTestRunner::class)
class QuotaRingTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `the ring sweeps in from zero to its value`() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                QuotaRing(progress = 0.71f, modifier = Modifier.testTag("ring"))
            }
        }
        rule.mainClock.advanceTimeByFrame()
        rule.onNode(outerRing).assertRangeInfoEquals(ProgressBarRangeInfo(0f, 0f..1f))
        rule.mainClock.advanceTimeBy(3_000)
        rule.onNode(outerRing).assertRangeInfoEquals(ProgressBarRangeInfo(0.71f, 0f..1f))
    }

    @Test
    fun `a large ring that needs attention is wavy`() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                QuotaRing(progress = 0.71f, wavy = true, modifier = Modifier.testTag("ring"))
            }
        }
        rule.onNode(outerRing).assert(isIndicator(IndicatorStyle.Wavy))
    }

    @Test
    fun `a small ring stays flat because the wave would read as noise`() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                QuotaRing(
                    progress = 0.71f,
                    wavy = true,
                    size = 56.dp,
                    modifier = Modifier.testTag("ring"),
                )
            }
        }
        rule.onNode(outerRing).assert(isIndicator(IndicatorStyle.Flat))
    }
}

@RunWith(RobolectricTestRunner::class)
class QuotaRingWithoutAnimationsTest {
    @get:Rule val rule = createComposeRule(ComposeUiTestConfig(effectContext = ZeroMotion))

    @Test
    fun `with animations off the ring shows its value at once, flat`() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                QuotaRing(progress = 0.71f, wavy = true, modifier = Modifier.testTag("ring"))
            }
        }
        rule.mainClock.advanceTimeByFrame()
        rule
            .onNode(outerRing)
            .assert(isIndicator(IndicatorStyle.Flat))
            .assertRangeInfoEquals(ProgressBarRangeInfo(0.71f, 0f..1f))
    }
}
