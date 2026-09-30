package dev.sebastiano.headroom.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StaleTest {
    @get:Rule val rule = createComposeRule()

    private fun show(stale: Boolean) {
        rule.setContent {
            Box(Modifier.background(Color.White)) {
                Box(Modifier.testTag("content").stale(stale).size(8.dp).background(Color.Red))
            }
        }
    }

    @Test
    fun `stale content says so to tests and tools`() {
        show(stale = true)
        rule.onNodeWithTag("content").assert(SemanticsMatcher.expectValue(StaleKey, true))
    }

    @Test
    fun `fresh content is not marked`() {
        show(stale = false)
        rule.onNodeWithTag("content").assert(SemanticsMatcher.keyNotDefined(StaleKey))
    }

    @Test
    fun `stale content is drawn faded and towards grey`() {
        show(stale = true)
        val pixel = rule.onNodeWithTag("content").captureToImage().toPixelMap()[2, 2]
        // Pure red on white: fading raises green and blue, desaturating narrows the gap to red.
        assertTrue(pixel.green > 0.3f, "not faded: $pixel")
        assertTrue(pixel.red - pixel.green < 0.5f, "not desaturated: $pixel")
    }
}
