package dev.sebastiano.headroom.glasses

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.xr.glimmer.GlimmerTheme
import com.github.takahirom.roborazzi.captureRoboImage
import dev.sebastiano.headroom.ui.ResetFormatter
import dev.sebastiano.headroom.ui.home.HomeViewModel
import dev.sebastiano.headroom.ui.testGraph
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the glasses cards for the docs, recorded with `./gradlew
 * :app:recordRoborazziDebug` into `docs/screenshots`. They use the demo accounts and a fixed clock.
 * Robolectric cannot download Google Sans Flex, so the text uses the fallback font.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = GLASSES)
class GlassesScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun launch() {
        rule.setContent {
            val home: HomeViewModel =
                viewModel(factory = testGraph(rule.activity).homeViewModelFactory)
            val state by home.state.collectAsStateWithLifecycle()
            GlimmerTheme {
                GlassesScreen(
                    state = state,
                    formatter = ResetFormatter(ZoneOffset.UTC, Locale.UK, is24Hour = true),
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun nextReset() {
        launch()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage(screenshot("glasses-next-reset"))
    }

    @Test
    fun account() {
        launch()
        rule.onNodeWithTag(GLASSES_SCREEN_TAG).performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage(screenshot("glasses-account"))
    }
}

/** Android Studio's "Display Glasses" device (`id:ai_glasses_device`): 450 x 450 px at mdpi. */
private const val GLASSES = "w450dp-h450dp-mdpi"

private fun screenshot(name: String): String {
    val dir = System.getProperty("roborazzi.output.dir") ?: "build/outputs/roborazzi"
    return "$dir/$name.png"
}
