package dev.sebastiano.headroom.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.ComposeUiTestConfig
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QuotaBarTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `a bar that needs attention is wavy`() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                QuotaBar(progress = 0.71f, wavy = true, modifier = Modifier.testTag("bar"))
            }
        }
        rule.onNode(indicatorIn("bar")).assert(isIndicator(IndicatorStyle.Wavy))
    }

    @Test
    fun `a calm bar is flat and reports its value`() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                QuotaBar(progress = 0.34f, paceFraction = 0.4f, modifier = Modifier.testTag("bar"))
            }
        }
        rule
            .onNode(indicatorIn("bar"))
            .assert(isIndicator(IndicatorStyle.Flat))
            .assertRangeInfoEquals(ProgressBarRangeInfo(0.34f, 0f..1f))
    }
}

@RunWith(RobolectricTestRunner::class)
class QuotaBarWithoutAnimationsTest {
    @get:Rule val rule = createComposeRule(ComposeUiTestConfig(effectContext = ZeroMotion))

    @Test
    fun `with animations off a wavy bar goes flat and keeps its length`() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                QuotaBar(progress = 0.88f, wavy = true, modifier = Modifier.testTag("bar"))
            }
        }
        rule
            .onNode(indicatorIn("bar"))
            .assert(isIndicator(IndicatorStyle.Flat))
            .assertRangeInfoEquals(ProgressBarRangeInfo(0.88f, 0f..1f))
    }
}

internal fun isIndicator(style: IndicatorStyle) =
    SemanticsMatcher.expectValue(IndicatorStyleKey, style)

internal fun indicatorIn(tag: String) =
    hasAnyAncestor(hasTestTag(tag)) and SemanticsMatcher.keyIsDefined(IndicatorStyleKey)
