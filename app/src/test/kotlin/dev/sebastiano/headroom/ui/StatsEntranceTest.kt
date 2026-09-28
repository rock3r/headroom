package dev.sebastiano.headroom.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.ui.stats.rememberEntrance
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A chart's entrance plays once. Scrolling it off screen and back does not play it again. */
@RunWith(RobolectricTestRunner::class)
class StatsEntranceTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `a chart scrolled off screen and back does not play its entrance again`() {
        val list = LazyListState()
        var entrance = -1f
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                LazyColumn(state = list) {
                    item {
                        val value = rememberEntrance(key = "shares").value
                        entrance = value
                        Box(Modifier.fillMaxWidth().height(200.dp))
                    }
                    items(ROWS) { Box(Modifier.fillMaxWidth().height(200.dp)) }
                }
            }
        }
        rule.waitForIdle()
        assertEquals(1f, entrance, "the entrance played to the end")

        rule.runOnIdle { runBlocking { list.scrollToItem(ROWS) } }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { runBlocking { list.scrollToItem(0) } }
        rule.mainClock.advanceTimeByFrame()

        assertEquals(1f, entrance, "the chart came back already drawn")
    }

    private companion object {
        const val ROWS = 30
    }
}
