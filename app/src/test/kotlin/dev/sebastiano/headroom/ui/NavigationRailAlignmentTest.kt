package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.ThemePalette
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp-xhdpi")
class NavigationRailAlignmentTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun launch() {
        rule.setContent {
            HeadroomTheme(
                darkTheme = false,
                dynamicColor = false,
                palette = ThemePalette.Wallpaper,
            ) {
                HeadroomApp(graph = testGraph(rule.activity))
            }
        }
    }

    /** The rail is the side/wide layout; the rail FAB refresh is not the (0,0) corner instance. */
    private fun railRefreshCenterX(): Float {
        val nodes = rule.onAllNodesWithTag(REFRESH_TAG).fetchSemanticsNodes()
        val rail = nodes.first { it.boundsInWindow.top > 50f } // the FAB one, not the corner one
        return rail.boundsInWindow.center.x
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun refreshIsHorizontallyAlignedWithTabs() {
        launch()
        val rx = railRefreshCenterX()
        val deltas = mutableListOf<Pair<HomeTab, Float>>()
        val sb = StringBuilder("refresh=$rx\n")
        for (tabItem in HomeTab.entries) {
            val node = rule.onNodeWithTag(navigationItemTag(tabItem)).fetchSemanticsNode()
            val tx = node.boundsInWindow.center.x
            deltas += tabItem to abs(rx - tx)
            sb.append("$tabItem center=$tx delta=${abs(rx - tx)}\n")
        }
        println(sb)
        System.err.println(sb)
        java.io.File("/home/rob/projects/headroom/ralign_diag.txt").writeText(sb.toString())
        val worst = deltas.maxOf { it.second }
        if (worst > 2f)
            throw AssertionError(
                "refresh not aligned with tabs (worst delta $worst): " +
                    deltas.joinToString(", ") { "${it.first}:${it.second}" }
            )
    }
}
