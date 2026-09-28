package dev.sebastiano.headroom.island

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** What a touch on the overlay pill does: a tap opens the app, a swipe up dismisses it. */
@RunWith(RobolectricTestRunner::class)
class IslandTouchTest {
    @get:Rule val rule = createComposeRule()

    private var taps = 0
    private var swipesUp = 0

    private fun setPill() {
        rule.setContent {
            Box(
                Modifier.size(300.dp, 60.dp)
                    .islandTouch(onTap = { taps++ }, onSwipeUp = { swipesUp++ })
                    .testTag(PILL)
            )
        }
    }

    @Test
    fun `a tap opens the app and is not a swipe`() {
        setPill()
        rule.onNodeWithTag(PILL).performClick()
        assertEquals(1, taps)
        assertEquals(0, swipesUp)
    }

    @Test
    fun `a swipe up dismisses the pill and is not a tap`() {
        setPill()
        rule.onNodeWithTag(PILL).performTouchInput { swipeUp() }
        assertEquals(1, swipesUp)
        assertEquals(0, taps)
    }

    @Test
    fun `a swipe down does nothing`() {
        setPill()
        rule.onNodeWithTag(PILL).performTouchInput { swipeDown() }
        assertEquals(0, swipesUp)
        assertEquals(0, taps)
    }

    private companion object {
        const val PILL = "pill"
    }
}
