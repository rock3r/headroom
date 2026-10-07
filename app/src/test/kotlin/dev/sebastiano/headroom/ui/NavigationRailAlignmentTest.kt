package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.ThemePalette
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationRailAlignmentTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(qualifiers = "w700dp-h1000dp-xhdpi")
    fun refreshIsAlignedWithTheTabsAtMediumWidth() {
        assertRefreshAlignedWithTabs()
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun refreshIsAlignedWithTheTabsAtExpandedWidth() {
        assertRefreshAlignedWithTabs()
    }

    private fun assertRefreshAlignedWithTabs() {
        rule.setContent {
            HeadroomTheme(
                darkTheme = false,
                dynamicColor = false,
                palette = ThemePalette.Wallpaper,
            ) {
                HeadroomApp(graph = testGraph(rule.activity))
            }
        }
        // In rail mode NavigationSuiteScaffold composes primaryActionContent twice, in the rail
        // header and in its own bar-mode slot, and only places the rail one.
        val refreshX =
            rule
                .onAllNodesWithTag(REFRESH_TAG)
                .fetchSemanticsNodes()
                .single { it.layoutInfo.isPlaced }
                .boundsInWindow
                .center
                .x
        for (tab in HomeTab.entries) {
            val tabX =
                rule
                    .onNodeWithTag(navigationItemTag(tab))
                    .fetchSemanticsNode()
                    .boundsInWindow
                    .center
                    .x
            assertEquals(tabX, refreshX, absoluteTolerance = 1f, "refresh is off-centre from $tab")
        }
    }
}
